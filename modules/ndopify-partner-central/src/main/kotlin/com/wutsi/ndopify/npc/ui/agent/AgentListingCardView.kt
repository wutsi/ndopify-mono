package com.wutsi.ndopify.npc.ui.agent

/** One card of *Ses annonces*: a listing's generated labels, hero image and NPC listing page. */
data class AgentListingCardView(
    val id: Long,
    val title: String,
    val place: String?,
    val facts: String?,
    val price: String?,
    val photoUrl: String?,
    val url: String,
)
