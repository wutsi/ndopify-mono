package com.wutsi.ndopify.npc.ui.agent

/** The agent profile page's model: the identity card, *Ses annonces* and its pager. */
data class AgentProfileView(
    val id: Long,
    val name: String,
    val initials: String,
    val photoUrl: String?,
    val verified: Boolean,
    val typeLabel: String?,
    val city: String?,
    val since: String?,
    val sectorCity: String?,
    val sectorNeighborhoods: List<String>,
    val biography: String?,
    val whatsappUrl: String?,
    val listings: List<AgentListingCardView>,
    val page: Int,
    val pages: Int,
    val prevUrl: String?,
    val nextUrl: String?,
    val listingsUnavailable: Boolean,
)
