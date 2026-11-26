package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailImages
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells the listing agent (B) about a co-brokerage visit organized by the search agent (A). B is informed, never
 * asked: A has already agreed with him by phone. The e-mail speaks in A's voice ([AgentMailContext]), and never
 * carries A's customer's name, e-mail, WhatsApp, code, note, address, report, feedback, mandate, match or search
 * criteria — the customer is only ever "mon client".
 * The photo is resized through [MailImages] for the e-mail (never the original URL). The confirmation and the reminder
 * carry a `Voir la visite` button and link their photo to the visit's card on B's Partner Central home page
 * (`/#co-visit-{id}`): B can't read `/visits/{id}`. The cancellation and the no-show carry no button and leave the
 * photo unlinked — the visit is no longer on the home page.
 */
@Service
class CoBrokerageVisitMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val listingImageService: ListingImageService,
    private val imageService: ImageService,
) {
    companion object {
        const val SUBJECT_CONFIRMED = "{{tenant_name}}: visite de votre bien confirmée"
        const val SUBJECT_REMINDER = "{{tenant_name}}: rappel de la visite de votre bien"
        const val SUBJECT_CANCELLED = "{{tenant_name}}: la visite de votre bien est annulée"
        const val SUBJECT_NO_SHOW = "{{tenant_name}}: la visite de votre bien n'a pas eu lieu"

        const val TEMPLATE_CONFIRMED = "/mail/visit/co_brokerage_confirmed.mjml"
        const val TEMPLATE_REMINDER = "/mail/visit/co_brokerage_reminder.mjml"
        const val TEMPLATE_CANCELLED = "/mail/visit/co_brokerage_cancelled.mjml"
        const val TEMPLATE_NO_SHOW = "/mail/visit/co_brokerage_no_show.mjml"
    }

    fun sendConfirmed(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) =
        send(visit, listing, agent, TEMPLATE_CONFIRMED, SUBJECT_CONFIRMED, withVisitLink = true)

    fun sendReminder(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) =
        send(visit, listing, agent, TEMPLATE_REMINDER, SUBJECT_REMINDER, withVisitLink = true)

    fun sendCancelled(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) =
        send(
            visit = visit,
            listing = listing,
            agent = agent,
            template = TEMPLATE_CANCELLED,
            subject = SUBJECT_CANCELLED,
            extra = mapOf(
                "cancelled_by_label" to CoBrokerageVisitLabels.cancelledBy(visit.cancelledBy),
                "cancellation_reason" to null,
            ),
        )

    fun sendNoShow(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) =
        send(
            visit = visit,
            listing = listing,
            agent = agent,
            template = TEMPLATE_NO_SHOW,
            subject = SUBJECT_NO_SHOW,
            extra = mapOf(
                "reason_label" to CoBrokerageVisitLabels.noShowReason(visit.noShowReason),
            ),
        )

    private fun send(
        visit: VisitEntity,
        listing: ListingEntity,
        agent: AgentEntity,
        template: String,
        subject: String,
        extra: Map<String, Any?> = emptyMap(),
        withVisitLink: Boolean = false,
    ) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val recipient = listing.agent.party
        mailService.send(
            Email(
                sender = AgentMailContext.from(agent),
                recipient = Receipient(email = recipient.email, displayName = recipient.fullName()),
                subject = subject.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = template,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to recipient.fullName(),
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "image_url" to MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER),
                        "visit_date" to visit.scheduledAt?.let { formatter.date(it) },
                        "visit_time" to visit.scheduledAt?.let { formatter.time(it) },
                        "type_label" to CoBrokerageVisitLabels.typeLabel(visit.type),
                        "on_site" to (visit.type == VisitType.ON_SITE),
                        "agent_profile_url" to tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/agent?id=${agent.id}" },
                        "visit_url" to visitUrl(visit, tenant).takeIf { withVisitLink },
                    ) + extra + AgentMailContext.of(agent, formatter),
                ),
            ),
        )
    }

    private fun visitUrl(visit: VisitEntity, tenant: TenantEntity): String? =
        tenant.partnerCentralUrl?.removeSuffix("/")?.takeIf { it.isNotBlank() }?.let { "$it/#co-visit-${visit.id}" }
}
