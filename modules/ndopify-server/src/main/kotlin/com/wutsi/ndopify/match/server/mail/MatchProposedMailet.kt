package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.mail.ListingUrls
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
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
 * Sends the customer of a search the listing its agent proposes: title, price and a link to its page on the public
 * portal (none without the tenant's `publicPortalUrl`, or the listing's `url`).
 */
@Service
class MatchProposedMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "{{tenant_name}}: {{agent_name}} vous propose un bien à {{place}}"
        const val TEMPLATE = "/mail/match/proposed.mjml"
    }

    fun send(match: MatchEntity, customer: CustomerEntity, agent: AgentEntity) {
        val mandate = match.mandate
        val listing = match.listing
        val tenant = tenantService.findById(mandate.tenantId!!)
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val party = customer.party
        val fullName = party.fullName()
        val agentName = agent.party.fullName()
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name)
                    .replace("{{agent_name}}", agentName)
                    .replace("{{place}}", MandateMailLabels.place(mandate, city, locationService)),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "agent_full_name" to agentName,
                        "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                        "price_label" to MandateMailLabels.priceLabel(mandate),
                        "price" to MandateMailLabels.price(
                            listing.price,
                            listing.currencyCode,
                            listing.pricePeriodicityType,
                            TenantFormatter(tenant),
                        ),
                        "listing_url" to ListingUrls.publicUrl(tenant, listing),
                    ),
                ),
            ),
        )
    }
}
