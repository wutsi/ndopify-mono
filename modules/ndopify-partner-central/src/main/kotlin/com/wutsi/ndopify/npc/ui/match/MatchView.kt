package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.npc.ui.mandate.MandateRules
import com.wutsi.ndopify.npc.ui.visit.VisitCardView
import com.wutsi.ndopify.visit.dto.VisitStatus

/** One line of "Pourquoi ce bien ?": a partial score in words, [full] when the listing fully meets that criterion. */
data class FitLine(val full: Boolean, val text: String)

/** A listing matched to the mandate, as a card of *Biens correspondants*. */
data class MatchView(
    val id: Long,
    val listingId: Long,
    val coBrokerage: Boolean = false,
    val title: String,
    val tag: MandateRules.Tag,
    val listingUrl: String,
    /** One line: the score, neighbourhood, rent ("245K FCFA / mois") and availability; the terms are for screen readers. */
    val facts: List<Pair<String, String>>,
    val answer: String?,
    val fit: List<FitLine>,
    val actionable: Boolean, // ACTIVE or EXPIRED: can be sent to the client or set aside

    /** The visit the client asked for, inside the card that led to it; null without one. */
    val visit: VisitCardView? = null,
    /** "Envoyé le 28 oct. 2026"; the only meta line a visit card shows. */
    val sent: String? = null,
    val group: MatchGroup,
)

/**
 * The *Biens correspondants* section: the cards grouped by what the agent does next ([groups]), the refused and
 * unanswered ones only counted ([outcomes]), how many were set aside, and [unavailable] when the matches or their
 * listings couldn't be read. [visitsUnavailable] leaves every card without its visit block.
 */
data class MatchSectionView(
    val matches: List<MatchView> = emptyList(),
    val dismissed: Int = 0,
    val unavailable: Boolean = false,
    val visitsUnavailable: Boolean = false,
    val outcomes: String? = null,
) {
    val groups: List<MatchGroupView>
        get() = matches.groupBy { it.group }.map { MatchGroupView(it.key.label, it.value) }

    /** Envoyer is primary only while no visit waits for an answer. */
    val hasPendingVisit: Boolean
        get() = matches.any { it.visit?.status == VisitStatus.REQUESTED }
}
