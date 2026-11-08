package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.visit.dto.VisitStatus

/** The approved panel's callout down to the first visit to confirm (the prototype's `renderPendingVisit`). */
data class PendingVisitView(val label: String, val url: String)

/** A visit that may still be to confirm, the card it sits in, and that card's [anchor] ("match-9001"). */
data class PendingVisitCandidate(
    val visit: VisitCardView,
    /** "Sandrine Eboa" for a listing client, "Appartement · 3 chambres · annonce n° 3012" for a match. */
    val name: String,
    /** "match-9001" or "proposal-9002"; the URL becomes "#$anchor". */
    val anchor: String,
)

/**
 * The one callout at the top of the approved panel: the first visit to confirm, soonest expiry first. Several of them
 * only count, without a name (the group headings already name the cards).
 */
object PendingVisit {
    fun of(candidates: List<PendingVisitCandidate>): PendingVisitView? {
        val requested = candidates
            .filter { it.visit.status == VisitStatus.REQUESTED }
            .sortedWith(compareBy(nullsLast()) { it.visit.expiresAt })
        if (requested.isEmpty()) {
            return null
        }

        val first = requested.first()
        val timing = first.visit.expiresIn
        val label = if (requested.size == 1) {
            "1 visite à confirmer" + (timing?.let { " · $it" } ?: "") + " — ${first.name}"
        } else {
            "${requested.size} visites à confirmer" + (timing?.let { " · la plus urgente dans $it" } ?: "")
        }
        return PendingVisitView(label, "#${first.anchor}")
    }
}
