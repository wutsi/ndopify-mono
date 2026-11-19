package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.match.dto.Match

/** One option of the visit page's picker: the accepted match the agent books, and how the option names it. */
data class MandateVisitProperty(val matchId: Long, val label: String) {
    companion object {
        /**
         * One option per match, named after its listing ("Nice apartment — annonce n° 101"); a listing that can't be
         * read is left out of the label, never offered as a blank one.
         */
        fun of(matches: List<Match>, listings: Map<Long, ListingSummary>): List<MandateVisitProperty> =
            matches.map { match ->
                val title = listings[match.listingId]?.title?.ifBlank { null }
                MandateVisitProperty(match.id, if (title == null) "Bien n° ${match.listingId}" else "$title — annonce n° ${match.listingId}")
            }
    }
}
