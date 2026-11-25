package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.server.service.MandateConversation
import com.wutsi.ndopify.mandate.server.service.MandateConversationService
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailImages
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Tells the other side of a party-initiated cancelled visit: the agent when the customer cancelled, the customer when
 * the agent did. Each copy has its own method, so that only the agent's can be rerouted to Telegram.
 *
 * A cancellation the platform caused (`SYSTEM`, ex: the mandate ended) e-mails nobody: the platform's own decision is
 * not the other side's appointment problem.
 * The photo is resized through [MailImages] for the e-mail (never the original URL).
 */
@Service
class VisitCancelledMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val listingImageService: ListingImageService,
    private val imageService: ImageService,
    private val conversationService: MandateConversationService,
) {
    companion object {
        const val SUBJECT_AGENT = "{{tenant_name}}: la visite a été annulée"
        const val TEMPLATE = "/mail/visit/cancelled.mjml"

        const val LABEL_BY_CUSTOMER = "Le client a annulé la visite."
        const val LABEL_BY_AGENT = "J'ai annulé la visite."
    }

    /**
     * The customer's copy: the agent cancelled, so the customer has to know. Nothing when the customer or the system
     * cancelled.
     */
    fun sendToCustomer(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        if (visit.cancelledBy != VisitParty.AGENT) {
            return
        }

        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) }
        val scheduled = visit.scheduledAt?.let { " le ${formatter.date(it)} à ${formatter.time(it)}" } ?: ""
        send(
            tenant = tenant,
            formatter = formatter,
            visit = visit,
            listing = listing,
            recipient = customer.party,
            otherParty = agent.party.fullName(),
            sender = AgentMailContext.from(agent),
            conversation = conversationService.of(visit.mandateId),
            label = LABEL_BY_AGENT,
            visitUrl = publicPortalUrl(tenant, visit, token),
            reason = visit.cancellationReason,
            footer = AgentMailContext.customer(agent, formatter) + mapOf(
                "agent_whatsapp_reply_url" to AgentMailContext.replyUrl(
                    agent,
                    "de notre visite du bien ${listingTitle(listing)}$scheduled",
                ),
            ),
        )
    }

    /**
     * The agent's copy: the customer cancelled, so the agent has to free the slot. Nothing when the agent or the system
     * cancelled.
     */
    fun sendToAgent(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        if (visit.cancelledBy != VisitParty.CUSTOMER) {
            return
        }

        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        send(
            tenant = tenant,
            formatter = formatter,
            visit = visit,
            listing = listing,
            recipient = agent.party,
            otherParty = customer.party.fullName(),
            sender = null,
            conversation = null,
            label = LABEL_BY_CUSTOMER,
            visitUrl = partnerCentralUrl(tenant, visit),
            reason = visit.cancellationReason,
            footer = emptyMap(),
        )
    }

    private fun send(
        tenant: TenantEntity,
        formatter: TenantFormatter,
        visit: VisitEntity,
        listing: ListingEntity,
        recipient: PartyEntity,
        otherParty: String,
        sender: Receipient?,
        conversation: MandateConversation?,
        label: String,
        visitUrl: String?,
        reason: String?,
        footer: Map<String, Any?>,
    ) {
        mailService.send(
            Email(
                sender = sender,
                recipient = Receipient(email = recipient.email, displayName = recipient.fullName()),
                subject = conversation?.subject ?: SUBJECT_AGENT.replace("{{tenant_name}}", tenant.name),
                messageId = conversation?.messageId,
                inReplyTo = conversation?.inReplyTo,
                references = conversation?.references ?: emptyList(),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = context(
                        tenant = tenant,
                        formatter = formatter,
                        recipientName = recipient.fullName(),
                        otherParty = otherParty,
                        visit = visit,
                        listing = listing,
                        cancelledByLabel = label,
                        visitUrl = visitUrl,
                        reason = reason,
                    ) + footer,
                ),
            ),
        )
    }

    private fun listingTitle(listing: ListingEntity): String =
        listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"

    private fun partnerCentralUrl(tenant: TenantEntity, visit: VisitEntity): String? =
        tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/visits/${visit.id}" }

    private fun publicPortalUrl(tenant: TenantEntity, visit: VisitEntity, token: String?): String? =
        tenant.publicPortalUrl?.removeSuffix("/")?.let { base ->
            val url = "$base/visits/${visit.id}"
            token?.let { "$url?token=$it" } ?: url
        }

    private fun context(
        tenant: TenantEntity,
        formatter: TenantFormatter,
        recipientName: String,
        otherParty: String,
        visit: VisitEntity,
        listing: ListingEntity,
        cancelledByLabel: String,
        visitUrl: String?,
        reason: String?,
    ): Map<String, Any?> =
        mapOf(
            "tenant_logo_url" to tenant.logoUrl,
            "tenant_name" to tenant.name,
            "recipient_name" to recipientName,
            "other_party" to otherParty,
            "listing_title" to listingTitle(listing),
            "image_url" to MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER),
            "visit_date" to visit.scheduledAt?.let { formatter.date(it) },
            "visit_time" to visit.scheduledAt?.let { formatter.time(it) },
            "cancelled_by_label" to cancelledByLabel,
            "cancellation_reason" to reason?.ifBlank { null },
            "visit_url" to visitUrl,
            "system_cancelled" to (visit.cancelledBy == VisitParty.SYSTEM),
        )
}
