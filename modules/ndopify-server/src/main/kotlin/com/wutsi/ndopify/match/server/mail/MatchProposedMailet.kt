package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Sends the customer of a search the listings its agent proposes, in one e-mail: for each, its title, price and a link
 * to its proposal's page on the public portal, `{publicPortalUrl}/match/{matchId}?token={token}`, where the customer
 * accepts or rejects it (none without the tenant's `publicPortalUrl`: the customer then answers its agent). The token
 * signs the customer in to the public portal ([MatchLinkTokenService]); without one, the link has no `token`.
 */
@Service
class MatchProposedMailet(
    private val tenantService: TenantService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val linkTokenService: MatchLinkTokenService,
) {
    companion object {
        const val SUBJECT_ONE = "{{tenant_name}}: {{agent_name}} vous propose un bien à {{place}}"
        const val SUBJECT_MANY = "{{tenant_name}}: {{agent_name}} vous propose {{count}} biens à {{place}}"
        const val TEMPLATE = "/mail/match/proposed.mjml"
    }

    /** [matches]: of one mandate, in the order shown. */
    fun send(matches: List<MatchEntity>, customer: CustomerEntity, agent: AgentEntity) {
        val mandate = matches.first().mandate
        val tenant = tenantService.findById(mandate.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
        val party = customer.party
        val fullName = party.fullName()
        val agentName = agent.party.fullName()
        val subject = if (matches.size == 1) SUBJECT_ONE else SUBJECT_MANY
        val token = tenant.publicPortalUrl?.let { linkTokenService.create(customer) } // One for all the links
        mailService.send(
            Email(
                recipient = Receipient(email = party.email, displayName = fullName),
                subject = subject.replace("{{tenant_name}}", tenant.name)
                    .replace("{{agent_name}}", agentName)
                    .replace("{{count}}", matches.size.toString())
                    .replace("{{place}}", MandateMailLabels.place(mandate, city, locationService)),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "agent_full_name" to agentName,
                        "count" to matches.size.takeIf { it > 1 },
                        "online" to tenant.publicPortalUrl?.let { true },
                        "listings" to matches.map { match -> listing(match, tenant, formatter, token) },
                    ),
                ),
            ),
        )
    }

    private fun listing(
        match: MatchEntity,
        tenant: TenantEntity,
        formatter: TenantFormatter,
        token: String?,
    ): Map<String, Any?> {
        val listing = match.listing
        return mapOf(
            "listing_title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
            "price_label" to MandateMailLabels.priceLabel(match.mandate),
            "price" to MandateMailLabels.price(
                listing.price,
                listing.currencyCode,
                listing.pricePeriodicityType,
                formatter,
            ),
            "listing_url" to proposalUrl(tenant, match, token),
        )
    }

    private fun proposalUrl(tenant: TenantEntity, match: MatchEntity, token: String?): String? {
        val baseUrl = tenant.publicPortalUrl ?: return null
        val url = baseUrl.removeSuffix("/") + "/match/" + match.id
        return token?.let { "$url?token=$it" } ?: url
    }
}
