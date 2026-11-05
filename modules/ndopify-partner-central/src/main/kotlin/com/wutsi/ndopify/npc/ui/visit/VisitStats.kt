package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary

/**
 * The mandate panel's visits total: the ones still to come and the completed ones. Cancelled, expired and no-show
 * visits aren't visits that happened, so they're left out.
 */
data class VisitStats(val upcoming: Int, val completed: Int) {
    val total: Int get() = upcoming + completed

    /** "Aucune visite", "2 visites à venir", "1 visite terminée", "3 visites · 1 à venir · 2 terminées". */
    val label: String
        get() = when {
            total == 0 -> "Aucune visite"
            upcoming == 0 -> "$completed " + plural(completed, "visite terminée", "visites terminées")
            completed == 0 -> "$upcoming " + plural(upcoming, "visite à venir", "visites à venir")
            else -> "$total visites · $upcoming à venir · $completed " + plural(completed, "terminée", "terminées")
        }

    companion object {
        /**
         * [visits] are the mandate's open and completed ones; null when they can't be read (no total shown). A stale
         * request or confirmed window isn't upcoming, as on the page's *Visites* section.
         */
        fun of(visits: List<VisitSummary>?, now: Long): VisitStats? {
            if (visits == null) return null
            val upcoming = visits.map { VisitRules.asVisit(it) }.count { VisitRules.isOpen(it, now) }
            val completed = visits.count { it.status == VisitStatus.COMPLETED }
            return VisitStats(upcoming, completed)
        }
    }

    private fun plural(count: Int, one: String, many: String): String = if (count > 1) many else one
}
