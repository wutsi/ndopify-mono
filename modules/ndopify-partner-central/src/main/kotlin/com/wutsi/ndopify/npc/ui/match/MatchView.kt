package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.npc.ui.mandate.MandateRules

/** One line of "Pourquoi ce bien ?": a partial score in words, [full] when the listing fully meets that criterion. */
data class FitLine(val full: Boolean, val text: String)

/** A listing matched to the mandate, as a card of *Biens correspondants*. */
data class MatchView(
    val id: Long,
    val listingId: Long,
    val title: String,
    val tag: MandateRules.Tag,
    val listingUrl: String,
    /** One line: the score, neighbourhood, rent ("245K FCFA / mois") and availability; the terms are for screen readers. */
    val facts: List<Pair<String, String>>,
    val answer: String?,
    val fit: List<FitLine>,
    val actionable: Boolean, // ACTIVE: can be sent to the client or set aside
)

/**
 * The *Biens correspondants* section: the cards in [MatchRules.ORDER], what the client answered ([proposals], the
 * refused ones included), how many were set aside, and [unavailable] when the matches or their listings couldn't be
 * read.
 */
data class MatchSectionView(
    val matches: List<MatchView> = emptyList(),
    val proposals: String? = null,
    val dismissed: Int = 0,
    val unavailable: Boolean = false,
)
