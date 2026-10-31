package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.npc.ui.listing.ListingLineView

/** A mandate in progress, as its card in the Espace agent's *Mes mandats* shows it. */
data class MandateCardView(
    val id: Long,
    val title: String,
    val tag: MandateRules.Tag,
    /** Sector (neighbourhoods only: the city is the agent's), amount and moving date: term → value. */
    val facts: List<Pair<String, String>>,
    /** A landlord's signed mandate only: its listing's number and status; null when the listings can't be had. */
    val listing: ListingLineView?,
    val nextStep: String?,
)
