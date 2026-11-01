package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.npc.ui.listing.ListingLineView

/** A mandate in progress, as its card in the Espace agent's *Mes mandats* shows it. */
data class MandateCardView(
    val id: Long,
    val title: String,
    val tag: MandateRules.Tag,
    /** Sector (neighbourhoods only: the city is the agent's), amount and moving date: term → value. */
    val facts: List<Pair<String, String>>,
    /** A landlord's signed mandate only: its listing's status; null when the listings can't be had. */
    val listing: ListingLineView?,
    /** A published listing only: "2 personnes intéressées"; null before, or when its matches can't be had. */
    val interest: String? = null,
    val nextStep: String?,
    /** A signed search only: "3 nouveaux biens correspondants" (ACTIVE matches), linking to [matchesUrl]; null at 0. */
    val matches: String? = null,
    val matchesUrl: String? = null,
    /** A signed search only: "1 intéressé · 1 refusé · 1 en attente"; null when none was sent. */
    val proposals: String? = null,
)
