package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
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
 */
@Service
class CoBrokerageVisitMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val imageService: ListingImageService,
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
        send(visit, listing, agent, TEMPLATE_CONFIRMED, SUBJECT_CONFIRMED)

    fun sendReminder(visit: VisitEntity, listing: ListingEntity, agent: AgentEntity) =
        send(visit, listing, agent, TEMPLATE_REMINDER, SUBJECT_REMINDER)

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
                        "image_url" to imageService.cover(listing)?.url?.ifBlank { null },
                        "visit_date" to visit.scheduledAt?.let { formatter.date(it) },
                        "visit_time" to visit.scheduledAt?.let { formatter.time(it) },
                        "type_label" to CoBrokerageVisitLabels.typeLabel(visit.type),
                        "on_site" to (visit.type == VisitType.ON_SITE),
                        "agent_profile_url" to tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/agent?id=${agent.id}" },
                        "listing_url" to tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/listings/${listing.id}#visits-title" },
                    ) + extra + AgentMailContext.of(agent, formatter),
                ),
            ),
        )
    }
}
