package com.wutsi.ndopify.npc.ui.visit

/**
 * A visit on the Espace agent, as its card shows it. Anti-bypass: no code, no note, no address — the listing's line
 * only, until the visit's page.
 */
data class VisitCardView(
    val id: Long,
    val customerName: String,
    /** Null for a requested visit (the card shows the timer instead). */
    val tag: VisitRules.Tag?,
    val whenLabel: String,
    val listing: String,
    /** Epoch ms, for home.js' countdown; null once confirmed (no deadline left). */
    val expiresAt: Long?,
    val expiresIn: String?,
    val urgent: Boolean,
    val nextStep: String,
)

/** The *Visites* section of the Espace agent: the requests to confirm, then the visits to come. */
data class VisitCardsView(
    val requested: List<VisitCardView>,
    val upcoming: List<VisitCardView>,
    val unavailable: Boolean = false,
)
