package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.visit.VisitStats

/** A published listing's activity: who it interested and its visits. */
data class ListingStatsView(val interest: String, val visits: VisitStats?) {
    /** "2 personnes intéressées · 3 visites"; the visits are left out when they can't be read. */
    val label: String get() = listOfNotNull(interest, visits?.totalLabel).joinToString(" · ")

    companion object {
        /** Null when the stats can't be had: the line is then left out. */
        fun of(stats: ListingStats?): ListingStatsView? = stats?.let {
            ListingStatsView(
                interest = MatchRules.interest(it.interested),
                visits = VisitStats(it.upcomingVisits, it.completedVisits),
            )
        }
    }
}
