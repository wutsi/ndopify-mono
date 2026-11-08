package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.visit.VisitStats

/**
 * A signed search's one-line activity: the current matching pool, the client's interest and the visits on the
 * mandate. See `docs/superpowers/specs/2026-11-05-search-mandate-stats-design.md`.
 */
data class SearchStatsView(
    val mandateId: Long,
    /** Matches still in play: ACTIVE, PROPOSED, ACCEPTED and EXPIRED. */
    val properties: Int,
    /** Matches the client accepted. */
    val interested: Int,
    /** Upcoming + completed visits; null when they can't be read. */
    val visits: Int?,
) {
    /** Whether the mandate page shows the line: a pool or a visit to count. */
    val shown: Boolean get() = properties > 0 || (visits ?: 0) > 0

    /** "12 biens · 1 intéressé · 3 visites", with zero segments left out; the empty state when there's nothing. */
    val label: String
        get() {
            if (!shown) return EMPTY
            return listOfNotNull(
                properties.takeIf { it > 0 }?.let { "$it " + plural(it, "bien", "biens") },
                interested.takeIf { it > 0 }?.let { "$it " + plural(it, "intéressé", "intéressés") },
                visits?.takeIf { it > 0 }?.let { "$it " + plural(it, "visite", "visites") },
            ).joinToString(" · ")
        }

    val url: String get() = "/mandates/$mandateId#matches-title"

    companion object {
        const val EMPTY = "Aucun bien correspondant pour l'instant"

        /** The home card: null when the stats can't be read, so the line is left out. */
        fun of(mandate: Mandate, stats: MandateStats?): SearchStatsView? = stats?.let {
            SearchStatsView(
                mandateId = mandate.id,
                properties = it.properties,
                interested = it.interested,
                visits = it.upcomingVisits + it.completedVisits,
            )
        }

        /** The mandate page, from the matches and visits it already loaded. */
        fun of(mandate: Mandate, matches: List<Match>, visits: VisitStats?): SearchStatsView =
            SearchStatsView(
                mandateId = mandate.id,
                properties = matches.count { it.status in MatchRules.ORDER },
                interested = matches.count { it.status == MatchStatus.ACCEPTED },
                visits = visits?.total,
            )

        private fun plural(count: Int, one: String, many: String): String = if (count > 1) many else one
    }
}
