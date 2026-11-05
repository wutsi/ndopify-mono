package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.npc.ui.listing.ListingLineView

/** A mandate in progress, as its card in the Espace agent's *Mes mandats* shows it. */
data class MandateCardView(
    val id: Long,
    val title: String,
    /** A landlord's listing (the key icon before the title), not a search (the magnifying glass). */
    val owner: Boolean,
    val tag: MandateRules.Tag,
    /** Sector (neighbourhoods only: the city is the agent's), amount and moving date: term → value. */
    val facts: List<Pair<String, String>>,
    /** A landlord's signed mandate only: its listing's status; null when the listings can't be had. */
    val listing: ListingLineView?,
    /** A published listing only: "2 personnes intéressées · 3 visites"; null before, or when its stats can't be had. */
    val stats: String? = null,
    /** A signed search only: "12 biens · 1 intéressé · 3 visites"; null before or when its stats can't be had. */
    val searchStats: SearchStatsView? = null,
)
