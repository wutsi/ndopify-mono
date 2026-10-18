package com.wutsi.ndopify.npc.ui.mandate

/** A mandate in progress, as its card in the Espace agent's *Mes mandats* shows it. */
data class MandateCardView(
    val id: Long,
    val title: String,
    val tag: MandateRules.Tag,
    /** Sector (neighbourhoods only: the city is the agent's), amount and moving date: term → value. */
    val facts: List<Pair<String, String>>,
    val nextStep: String?,
)
