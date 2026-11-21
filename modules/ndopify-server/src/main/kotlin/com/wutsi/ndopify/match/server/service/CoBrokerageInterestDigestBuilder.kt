package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.mail.AgentMailContext
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.server.mail.MandateMailLabels
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.stereotype.Service

/**
 * Builds the listing agent's [CoBrokerageInterestDigest] from its un-notified interest matches: one row per listing
 * (best score first), then per interested search agent (best score first), at most [MAX_AGENTS], the others counted.
 *
 * An interested agent that no longer exists is dropped, and so is a listing left without one. Amounts and phone numbers
 * go through `TenantFormatter`. The search customer is never read: there is no `CustomerService` here (anti-sautage).
 */
@Service
class CoBrokerageInterestDigestBuilder(
    private val tenantService: TenantService,
    private val agentService: AgentService,
    private val locationService: LocationService,
    private val imageService: ListingImageService,
) {
    companion object {
        const val MAX_AGENTS = 5
    }

    /** [matches]: one listing agent's interest matches, all of one tenant. Never empty: the caller skips an empty one. */
    fun build(matches: List<MatchEntity>): CoBrokerageInterestDigest {
        val tenant = tenantService.findById(matches.first().listing.tenantId!!)
        val formatter = TenantFormatter(tenant)

        // Each search agent is loaded once, however many of its searches matched: an unknown one is dropped
        val agents = mutableMapOf<Long, AgentEntity?>()
        val listings = matches
            .groupBy { match -> match.listing.id }
            .values
            .map { group -> group.sortedByDescending { match -> match.score } }
            .sortedByDescending { group -> group.first().score }
            .mapNotNull { group -> listing(group, tenant, formatter, agents) }

        return CoBrokerageInterestDigest(
            tenant = tenant,
            agentCount = agents.values.count { agent -> agent != null },
            listings = listings,
        )
    }

    private fun listing(
        group: List<MatchEntity>,
        tenant: TenantEntity,
        formatter: TenantFormatter,
        agents: MutableMap<Long, AgentEntity?>,
    ): ListingInterest? {
        val listing = group.first().listing
        val interest = group
            .groupBy { match -> match.mandate.agentId }
            .filterKeys { agentId -> agentId != null }
            .values
            .map { perAgent -> perAgent.sortedByDescending { match -> match.score } }
            .sortedByDescending { perAgent -> perAgent.first().score }
            .mapNotNull { perAgent -> agent(perAgent, formatter, agents) }
        if (interest.isEmpty()) {
            return null
        }

        return ListingInterest(
            listingId = listing.id!!,
            title = listing.title?.ifBlank { null },
            price = MandateMailLabels.price(
                listing.price,
                listing.currencyCode,
                listing.pricePeriodicityType,
                formatter,
            ),
            place = place(listing.cityId, listing.neighborhoodId),
            neighbourhood = neighbourhood(listing.neighborhoodId),
            imageUrl = imageService.cover(listing)?.url?.ifBlank { null },
            mandateUrl = tenant.partnerCentralUrl?.let { url -> "$url/mandates/${listing.mandate.id}" },
            agents = interest.take(MAX_AGENTS),
            more = (interest.size - MAX_AGENTS).takeIf { it > 0 },
        )
    }

    private fun agent(
        perAgent: List<MatchEntity>,
        formatter: TenantFormatter,
        agents: MutableMap<Long, AgentEntity?>,
    ): InterestedAgent? {
        val mandate = perAgent.first().mandate
        val agentId = mandate.agentId!!
        val agent = agents.getOrPut(agentId) { agentService.findByIdOrNull(agentId) } ?: return null
        val contact = AgentMailContext.of(agent, formatter)
        val email = agent.party.email.takeIf { agent.whatsappNumber.isNullOrBlank() && it.isNotBlank() }
        val city = locationService.findByIdOrNull(mandate.cityId)?.name ?: ""

        return InterestedAgent(
            fullName = agent.party.fullName(),
            whatsapp = contact["agent_whatsapp"] as? String,
            whatsappUrl = contact["agent_whatsapp_url"] as? String,
            email = email,
            emailUrl = email?.let { "mailto:$it" },
            photoUrl = agent.party.photoUrl?.ifBlank { null },
            listingType = MandateMailLabels.listingType(mandate),
            propertyType = MandateMailLabels.propertyType(mandate),
            place = MandateMailLabels.place(mandate, city, locationService),
            roomsLabel = mandate.rooms?.trim()?.toIntOrNull()?.let { rooms ->
                "$rooms pièce" + if (rooms > 1) "s" else ""
            },
            budget = MandateMailLabels.budget(mandate, formatter),
            searchCount = perAgent.size.takeIf { it > 1 },
        )
    }

    // Ex: "Bastos (Yaounde)", "Yaounde" without a neighbourhood, or "" without either
    private fun place(cityId: Long, neighborhoodId: Long?): String {
        val city = locationService.findByIdOrNull(cityId)?.name ?: ""
        val neighbourhood = neighborhoodId?.let { id -> locationService.findByIdOrNull(id) }?.name ?: ""
        return when {
            neighbourhood.isEmpty() -> city
            city.isEmpty() -> neighbourhood
            else -> "$neighbourhood ($city)"
        }
    }

    // The neighbourhood alone: "" when unknown, so that a channel omits its parenthesis
    private fun neighbourhood(neighborhoodId: Long?): String =
        neighborhoodId?.let { id -> locationService.findByIdOrNull(id)?.name?.ifBlank { null } } ?: ""
}
