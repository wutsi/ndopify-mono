package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The visit page's and the Espace agent's cards' rules, after the prototype's `visit.js`: the panel of a visit at a
 * given instant, the server's windows and deadlines, and the wording of each state. The windows mirror
 * ndopify-server's: check-in from `scheduledAt − 1 h` to `scheduledAt + 4 h`, the customer's no-show from
 * `scheduledAt + 15 min`, check-out 10 min after the check-in, a request expiring 48 h after its creation.
 */
object VisitRules {
    /** The visits shown on the Espace agent: every one still to confirm, to come or under way. */
    val OPEN: List<VisitStatus> = listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)

    /** What the mandate panel's total counts: the open visits and the completed ones; cancelled, expired and no-shows are left out. */
    val COUNTED: List<VisitStatus> = OPEN + VisitStatus.COMPLETED

    /** What the visit page shows: the status, except that a CONFIRMED visit in its window is [WINDOW]. */
    enum class Panel { REQUESTED, CONFIRMED, WINDOW, CHECKED_IN, COMPLETED, CANCELLED, EXPIRED, NO_SHOW, UNKNOWN }

    data class Tag(val label: String, val style: String)

    private const val HOUR = 3_600_000L
    private const val MINUTE = 60_000L
    private const val CHECK_IN_BEFORE = HOUR
    private const val CHECK_IN_AFTER = 4 * HOUR
    private const val NO_SHOW_AFTER = 15 * MINUTE
    private const val CHECK_OUT_AFTER = 10 * MINUTE
    private const val EXPIRES_AFTER = 48 * HOUR

    private val TAGS: Map<Panel, Tag> = mapOf(
        Panel.REQUESTED to Tag("À confirmer", "warning"),
        Panel.CONFIRMED to Tag("Confirmée", "success"),
        Panel.WINDOW to Tag("À démarrer", "info"),
        Panel.CHECKED_IN to Tag("En cours", "info"),
        Panel.COMPLETED to Tag("Terminée", "success"),
        Panel.CANCELLED to Tag("Annulée", "neutral"),
        Panel.EXPIRED to Tag("Expirée", "neutral"),
        Panel.NO_SHOW to Tag("Absence", "warning"),
        Panel.UNKNOWN to Tag("Visite", "neutral"),
    )

    val REJECTION_LABELS: Map<MatchRejectionReason, String> = mapOf(
        MatchRejectionReason.PRICE to "trop cher",
        MatchRejectionReason.LOCATION to "le quartier ne convient pas",
        MatchRejectionReason.SIZE to "trop petit ou trop grand",
        MatchRejectionReason.AMENITIES to "il manque des équipements",
        MatchRejectionReason.AVAILABILITY to "disponible trop tard",
        MatchRejectionReason.OTHER to "autre raison",
    )

    /**
     * True while the visit is still to confirm, to come or under way; a request past its 48 h or a confirmed visit past
     * its `+ 4 h` window is stale (ndopify-server's job may not have moved it yet) and isn't open.
     */
    fun isOpen(visit: Visit, now: Long): Boolean =
        when (visit.status) {
            VisitStatus.REQUESTED -> expiresAt(visit) > now
            VisitStatus.CONFIRMED -> visit.scheduledAt?.let { it.time + CHECK_IN_AFTER >= now } == true
            VisitStatus.CHECKED_IN -> true
            else -> false
        }

    /** A [VisitSummary] as a [Visit]: a summary has every field the rules read. */
    fun asVisit(summary: VisitSummary): Visit = Visit(
        id = summary.id,
        matchId = summary.matchId,
        mandateId = summary.mandateId,
        listingId = summary.listingId,
        agentId = summary.agentId,
        customerId = summary.customerId,
        status = summary.status,
        type = summary.type,
        requestedAt = summary.requestedAt,
        note = summary.note,
        scheduledAt = summary.scheduledAt,
        checkedInAt = summary.checkedInAt,
        checkInDistanceMeters = summary.checkInDistanceMeters,
        checkInFlagged = summary.checkInFlagged,
        checkedOutAt = summary.checkedOutAt,
        noShowParty = summary.noShowParty,
        noShowReason = summary.noShowReason,
        cancelledBy = summary.cancelledBy,
        cancellationReason = summary.cancellationReason,
        cancelledAt = summary.cancelledAt,
        createdAt = summary.createdAt,
        version = summary.version,
    )

    /** A CONFIRMED visit in its check-in window is [Panel.WINDOW]; every other status is its own panel. */
    fun panel(visit: Visit, now: Long): Panel =
        when (visit.status) {
            VisitStatus.CONFIRMED -> {
                val at = visit.scheduledAt?.time
                if (at != null && now >= at - CHECK_IN_BEFORE && now <= at + CHECK_IN_AFTER) {
                    Panel.WINDOW
                } else {
                    Panel.CONFIRMED
                }
            }

            VisitStatus.UNKNOWN -> Panel.UNKNOWN
            else -> Panel.valueOf(visit.status.name)
        }

    fun tag(panel: Panel): Tag = TAGS.getValue(panel)

    fun typeLabel(type: VisitType): String =
        when (type) {
            VisitType.ON_SITE -> "Sur place"
            VisitType.VIDEO -> "Visite vidéo"
            else -> "Visite"
        }

    /** A request expires 48 h after its creation. */
    fun expiresAt(visit: Visit): Long = visit.createdAt.time + EXPIRES_AFTER

    /** The instant the visit is for: its scheduled slot, else when it was asked; null without either. */
    fun timeOf(visit: Visit): Long? = (visit.scheduledAt ?: visit.requestedAt)?.time

    /** "42 h", or the minutes left under an hour ("45 min", "1 min" at zero). */
    fun expiresIn(left: Long): String {
        val hours = Math.floorDiv(left, HOUR)
        return if (hours >= 1) {
            "$hours h"
        } else {
            "${maxOf(1L, ceil(left / MINUTE.toDouble()).toLong())} min"
        }
    }

    fun checkInFrom(visit: Visit): Long? = visit.scheduledAt?.time?.minus(CHECK_IN_BEFORE)

    fun noShowFrom(visit: Visit): Long? = visit.scheduledAt?.time?.plus(NO_SHOW_AFTER)

    fun checkOutFrom(visit: Visit): Long? = visit.checkedInAt?.time?.plus(CHECK_OUT_AFTER)

    fun canCancel(visit: Visit): Boolean =
        visit.status == VisitStatus.REQUESTED || visit.status == VisitStatus.CONFIRMED

    fun canCheckIn(visit: Visit, now: Long): Boolean = panel(visit, now) == Panel.WINDOW

    fun canNoShow(visit: Visit, now: Long): Boolean {
        val from = noShowFrom(visit) ?: return false
        return panel(visit, now) == Panel.WINDOW && now >= from
    }

    fun checkOutReady(visit: Visit, now: Long): Boolean {
        val from = checkOutFrom(visit) ?: return false
        return visit.status == VisitStatus.CHECKED_IN && now >= from
    }

    /** "Aujourd'hui · 10:30" on the tenant's current day, else "sam. 7 novembre 2026 à 10:30". */
    fun whenLabel(at: Date, today: LocalDate, tenant: Tenant, formatter: TenantFormatter): String =
        if (VisitSlots.localDate(at, tenant) == today) {
            "Aujourd'hui · ${formatter.time(at)}"
        } else {
            VisitSlots.dateTimeLabel(at, tenant, formatter)
        }

    /** "1 h 05", "25 min"; null without both ends. */
    fun duration(start: Date?, end: Date?): String? {
        if (start == null || end == null) return null
        val minutes = ((end.time - start.time) / MINUTE.toDouble()).roundToInt()
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)}"
    }

    /** "820 m", "1,2 km"; null without a value. */
    fun meters(value: Int?): String? =
        when {
            value == null -> null
            value < 1000 -> "$value m"
            else -> String.format(Locale.FRANCE, "%.1f km", value / 1000.0)
        }
}
