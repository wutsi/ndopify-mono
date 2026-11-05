package com.wutsi.ndopify.visit.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/** Tells the other side of a cancelled visit: the agent when the customer cancelled, the customer when the agent did. */
@Service
class VisitCancelledMailet(
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: la visite a été annulée"
        const val TEMPLATE = "/mail/visit/cancelled.mjml"

        const val LABEL_BY_CUSTOMER = "Le client a annulé la visite."
        const val LABEL_BY_AGENT = "Votre agent a annulé la visite."
    }

    fun send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity) {
        val tenant = tenantService.findById(visit.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val byCustomer = visit.cancelledBy == VisitParty.CUSTOMER
        val recipientParty = if (byCustomer) agent.party else customer.party
        val otherParty = if (byCustomer) customer.party.fullName() else agent.party.fullName()
        val recipientName = recipientParty.fullName()
        val visitUrl = if (byCustomer) {
            tenant.partnerCentralUrl?.removeSuffix("/")?.let { "$it/visits/${visit.id}" }
        } else {
            val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) }
            tenant.publicPortalUrl?.removeSuffix("/")?.let { base ->
                val url = "$base/visits/${visit.id}"
                token?.let { "$url?token=$it" } ?: url
            }
        }
        mailService.send(
            Email(
                recipient = Receipient(email = recipientParty.email, displayName = recipientName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = context(
                        tenant = tenant,
                        formatter = formatter,
                        recipientName = recipientName,
                        otherParty = otherParty,
                        visit = visit,
                        listing = listing,
                        cancelledByLabel = if (byCustomer) LABEL_BY_CUSTOMER else LABEL_BY_AGENT,
                        visitUrl = visitUrl,
                    ),
                ),
            ),
        )
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
    ): Map<String, Any?> =
        mapOf(
            "tenant_logo_url" to tenant.logoUrl,
            "tenant_name" to tenant.name,
            "recipient_name" to recipientName,
            "other_party" to otherParty,
            "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
            "image_url" to imageService.cover(listing)?.url?.ifBlank { null },
            "visit_date" to visit.scheduledAt?.let { formatter.date(it) },
            "visit_time" to visit.scheduledAt?.let { formatter.time(it) },
            "cancelled_by_label" to cancelledByLabel,
            "cancellation_reason" to visit.cancellationReason?.ifBlank { null },
            "visit_url" to visitUrl,
        )
}
