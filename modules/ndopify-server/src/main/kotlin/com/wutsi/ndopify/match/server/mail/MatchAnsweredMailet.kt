package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Tells the search agent that its customer accepted or rejected a proposed match: the customer, the listing and its
 * price, the mandate's criteria, and a rejection's reason and its comment in the customer's own words. The counterpart
 * of the Telegram message (`MatchAnsweredBotlet`), and its fallback when the agent's chat isn't linked or Telegram
 * fails.
 */
@Service
class MatchAnsweredMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT_ACCEPTED = "{{tenant_name}}: {{customer_name}} a accepté la proposition"
        const val SUBJECT_REJECTED = "{{tenant_name}}: {{customer_name}} a refusé la proposition"
        const val TEMPLATE = "/mail/match/answered.mjml"
    }

    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity) {
        val tenant = tenantService.findById(match.tenantId!!)
        val mandate = match.mandate
        val listing = match.listing
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val party = agent.party
        val fullName = party.fullName()
        val customerName = customer.party.fullName()
        val formatter = TenantFormatter(tenant)
        val accepted = match.status == MatchStatus.ACCEPTED
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = (if (accepted) SUBJECT_ACCEPTED else SUBJECT_REJECTED)
                    .replace("{{tenant_name}}", tenant.name)
                    .replace("{{customer_name}}", customerName),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "customer_name" to customerName,
                        "accepted" to accepted,
                        "rejection_reason" to MatchAnsweredLabels.rejectionReason(match.rejectionReasonCode),
                        "comment" to match.rejectionReason?.ifBlank { null },
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "price_label" to MandateMailLabels.priceLabel(mandate),
                        "price" to MandateMailLabels.price(
                            listing.price,
                            listing.currencyCode,
                            listing.pricePeriodicityType,
                            formatter,
                        ),
                        "listing_type" to MandateMailLabels.listingType(mandate),
                        "property_type" to MandateMailLabels.propertyType(mandate),
                        "city" to city,
                        "neighbourhoods" to MandateMailLabels.neighbourhoods(mandate, locationService),
                        "budget" to MandateMailLabels.budget(mandate, formatter),
                        "mandate_url" to tenant.partnerCentralUrl?.removeSuffix("/")
                            ?.let { url -> "$url/mandates/${mandate.id}" },
                    ),
                ),
            ),
        )
    }
}
