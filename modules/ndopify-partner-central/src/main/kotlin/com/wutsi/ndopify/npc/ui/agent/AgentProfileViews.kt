package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.npc.ui.join.JoinRules
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.profile.ProfileRules
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.core.service.TenantFormatter

/** Builds the agent profile page's model from the viewed agent, one page of listings, their images and locations. */
object AgentProfileViews {
    /**
     * [listings] null means the listing read failed: the profile is still usable, with
     * [AgentProfileView.listingsUnavailable]. [images] maps a listing id to its images; the card shows the hero one,
     * else the first. [locations] holds the agent's and the visible listings' city and neighbourhood names.
     */
    fun of(
        agent: Agent,
        listings: AgentListingsPage?,
        images: Map<Long, List<ListingImage>>,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
        back: String?,
    ): AgentProfileView {
        val page = listings?.page ?: 1
        val pages = listings?.pages ?: 1
        val city = agent.cityId?.let { locations[it]?.name }
        return AgentProfileView(
            id = agent.id,
            name = ProfileRules.fullName(agent),
            initials = KycRules.initials(agent.party.firstName, agent.party.lastName),
            photoUrl = agent.party.photoUrl?.ifBlank { null },
            verified = agent.party.kycStatus == KycStatus.VERIFIED,
            typeLabel = JoinRules.AGENT_TYPE_LABELS[agent.agentType],
            city = city,
            since = agent.joinedAt?.let { "Membre depuis " + formatter.date(it, "MMMM yyyy") },
            sectorCity = city,
            sectorNeighborhoods = agent.neighborhoodIds
                .mapNotNull { locations[it]?.name }
                .take(JoinRules.MAX_NEIGHBORHOODS),
            biography = agent.biography?.trim()?.ifEmpty { null },
            whatsappUrl = AgentProfileRules.whatsappUrl(agent.whatsappNumber),
            listings = listings?.listings?.map { card(it, images, locations, formatter) } ?: emptyList(),
            page = page,
            pages = pages,
            prevUrl = if (page > 1) AgentProfileRules.pageUrl(agent.id, page - 1, back) else null,
            nextUrl = if (page < pages) AgentProfileRules.pageUrl(agent.id, page + 1, back) else null,
            listingsUnavailable = listings == null,
        )
    }

    private fun card(
        summary: ListingSummary,
        images: Map<Long, List<ListingImage>>,
        locations: Map<Long, Location>,
        formatter: TenantFormatter,
    ): AgentListingCardView =
        AgentListingCardView(
            id = summary.id,
            title = AgentProfileRules.listingTitle(summary),
            place = AgentProfileRules.listingPlace(summary, locations),
            facts = AgentProfileRules.listingFacts(summary, formatter),
            price = AgentProfileRules.listingPrice(summary, formatter),
            photoUrl = heroImage(summary, images),
            url = "/listings/${summary.id}",
        )

    private fun heroImage(summary: ListingSummary, images: Map<Long, List<ListingImage>>): String? {
        val listingImages = images[summary.id].orEmpty()
        val image = summary.heroImageId?.let { id -> listingImages.find { it.id == id } } ?: listingImages.firstOrNull()
        return image?.url?.ifBlank { null }
    }
}
