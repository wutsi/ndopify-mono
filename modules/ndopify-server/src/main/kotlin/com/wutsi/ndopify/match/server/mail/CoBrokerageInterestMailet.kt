package com.wutsi.ndopify.match.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.server.service.ListingImageService
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
 * Tells a listing agent that OTHER agents' searches showed interest in its listings: one e-mail grouped by listing
 * (best score first), then, within a listing, one row per interested search agent (best score first) with its
 * anonymized criteria, at most [MAX_AGENTS] each, the others counted. The interested agent's identity/contact may be
 * shown, but the search CUSTOMER is never read nor rendered — by construction there is no `CustomerService` here. The
 * CTA is the listing agent's own mandate, absent without a partner portal. Sent by the co-brokerage job.
 */
@Service
class CoBrokerageInterestMailet(
    private val tenantService: TenantService,
    private val agentService: AgentService,
    private val locationService: LocationService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
    private val imageService: ListingImageService,
) {
    companion object {
        const val SUBJECT_ONE = "{{tenant_name}}: 1 agent s'intéresse à votre bien"
        const val SUBJECT_MANY = "{{tenant_name}}: {{count}} agents s'intéressent à vos biens"
        const val TEMPLATE = "/mail/match/co_brokerage_interest.mjml"
        const val MAX_AGENTS = 5
    }

    /** [matches]: the listing agent's, all of one tenant. [agent]: the listing agent (the recipient). */
    fun send(agent: AgentEntity, matches: List<MatchEntity>) {
        val tenant = tenantService.findById(matches.first().listing.tenantId!!)
        val formatter = TenantFormatter(tenant)
        val fullName = agent.party.fullName()

        val listings = matches.groupBy { match -> match.listing.id }
            .values
            .map { group -> group.sortedByDescending { match -> match.score } }
            .sortedByDescending { group -> group.first().score }
            .map { group ->
                val listing = group.first().listing
                val agents = group.groupBy { match -> match.mandate.agentId }
                    .filterKeys { it != null }
                    .values
                    .map { perAgent -> perAgent.sortedByDescending { match -> match.score } }
                    .sortedByDescending { perAgent -> perAgent.first().score }
                    .mapNotNull { perAgent ->
                        val mandate = perAgent.first().mandate
                        val interested = agentService.findByIdOrNull(mandate.agentId!!) ?: return@mapNotNull null
                        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""
                        val row = AgentMailContext.of(interested, formatter).toMutableMap()
                        if (interested.whatsappNumber.isNullOrBlank()) {
                            val email = interested.party.email.takeIf { it.isNotBlank() }
                            row["agent_email"] = email
                            row["agent_email_url"] = email?.let { "mailto:$it" }
                        }
                        row["search_count"] = perAgent.size.takeIf { it > 1 }
                        row["listing_type"] = MandateMailLabels.listingType(mandate)
                        row["property_type"] = MandateMailLabels.propertyType(mandate)
                        row["place"] = MandateMailLabels.place(mandate, city, locationService)
                        row["rooms"] = mandate.rooms
                        row["budget"] = MandateMailLabels.budget(mandate, formatter)
                        row
                    }
                mapOf(
                    "image_url" to imageService.cover(listing)?.url?.ifBlank { null },
                    "title" to (listing.title?.ifBlank { null } ?: "Bien n° ${listing.id}"),
                    "price" to MandateMailLabels.price(
                        listing.price,
                        listing.currencyCode,
                        listing.pricePeriodicityType,
                        formatter,
                    ),
                    "mandate_url" to tenant.partnerCentralUrl?.let { url -> "$url/mandates/${listing.mandate.id}" },
                    "agents" to agents.take(MAX_AGENTS),
                    "more" to (agents.size - MAX_AGENTS).takeIf { it > 0 },
                )
            }

        val distinctAgents = matches.map { match -> match.mandate.agentId }.filterNotNull().distinct().size
        val subject = if (distinctAgents == 1) SUBJECT_ONE else SUBJECT_MANY.replace("{{count}}", "$distinctAgents")
        mailService.send(
            Email(
                recipient = Receipient(email = agent.party.email, displayName = fullName),
                subject = subject.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = TEMPLATE,
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to fullName,
                        "listings" to listings,
                    ),
                ),
            ),
        )
    }
}
