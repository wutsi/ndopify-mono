package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate
import java.util.Date

/**
 * Builds the visits' cards after the prototype's `renderVisits` and `fillVisit`: the Espace agent's *Visites* section,
 * and, on a mandate page, the visits attached to the cards of a signed search or of a landlord's published listing.
 * The requests still within their 48 h, soonest expiry first, and the visits to come. A requested visit past its 48 h
 * and a confirmed one past its `+ 4 h` window are dropped even when ndopify-server's job hasn't moved them yet.
 * [listings] and [customers] are secondary: null or a missing entry only leaves that line out.
 */
object VisitCards {
    private const val URGENT = 12 * 3_600_000L
    private const val PREVIEW = 4

    fun of(
        visits: List<VisitSummary>?,
        listings: Map<Long, ListingSummary>?,
        customers: Map<Long, CustomerSummary>,
        locations: Map<Long, Location>,
        now: Long,
        tenant: Tenant,
        formatter: TenantFormatter,
    ): VisitCardsView {
        if (visits == null) {
            return VisitCardsView(emptyList(), emptyList(), unavailable = true)
        }

        val today = VisitSlots.localDate(Date(now), tenant)
        val days = VisitSlots.days(today, formatter)
        val times = VisitSlots.times()
        val requested = visits
            .map { VisitRules.asVisit(it) }
            .filter { it.status == VisitStatus.REQUESTED && VisitRules.isOpen(it, now) }
            .sortedBy { VisitRules.expiresAt(it) }
            .map { visit ->
                val expiresAt = VisitRules.expiresAt(visit)
                val left = expiresAt - now
                val label = VisitSlots.dateTimeLabel(visit.requestedAt, tenant, formatter)
                val requestedDay = VisitSlots.localDate(visit.requestedAt, tenant).toString()
                val requestedTime = VisitSlots.localTime(visit.requestedAt, tenant).toString()
                val dayValid = days.any { it.value == requestedDay }
                val timeValid = times.any { it.first == requestedTime }
                VisitCardView(
                    id = visit.id,
                    matchId = visit.matchId,
                    customerName = name(customers[visit.customerId]),
                    type = VisitRules.typeLabel(visit.type),
                    video = visit.type == VisitType.VIDEO,
                    status = visit.status,
                    kindLabel = visitKind(visit.status, visit.type == VisitType.VIDEO),
                    whenText = whenText(visit, label, formatter),
                    tag = null,
                    whenLabel = label,
                    at = VisitRules.timeOf(visit),
                    listing = listing(visit, listings, locations),
                    expiresAt = expiresAt,
                    expiresIn = VisitRules.expiresIn(left),
                    urgent = left < URGENT,
                    days = days,
                    times = times,
                    selectedDay = if (dayValid) requestedDay else days.first().value,
                    selectedTime = if (timeValid) requestedTime else times.first().first,
                    requestedSlotValid = dayValid && timeValid,
                )
            }

        val upcoming = visits
            .map { VisitRules.asVisit(it) }
            .filter { it.status != VisitStatus.REQUESTED && VisitRules.isOpen(it, now) }
            .sortedWith(compareBy(nullsLast()) { it.scheduledAt?.time })
            .map { visit ->
                val label = whenLabel(visit, today, tenant, formatter)
                VisitCardView(
                    id = visit.id,
                    matchId = visit.matchId,
                    customerName = name(customers[visit.customerId]),
                    type = VisitRules.typeLabel(visit.type),
                    video = visit.type == VisitType.VIDEO,
                    status = visit.status,
                    kindLabel = visitKind(visit.status, visit.type == VisitType.VIDEO),
                    whenText = whenText(visit, label, formatter),
                    tag = VisitRules.tag(VisitRules.panel(visit, now)),
                    whenLabel = label,
                    at = VisitRules.timeOf(visit),
                    listing = listing(visit, listings, locations),
                    expiresAt = null,
                    expiresIn = null,
                    urgent = false,
                )
            }

        val ongoing = upcoming.filter { it.status == VisitStatus.CHECKED_IN }
        val groups = group(upcoming.filter { it.status != VisitStatus.CHECKED_IN }, today, tenant, formatter)
        val (preview, more) = split(groups, PREVIEW)

        return VisitCardsView(
            requested = requested,
            upcoming = upcoming,
            ongoing = ongoing,
            upcomingGroups = groups,
            upcomingPreviewGroups = preview,
            upcomingMoreGroups = more,
            upcomingMoreCount = more.sumOf { it.visits.size },
        )
    }

    /** Groups consecutive upcoming visits by day, in the order they were sorted. */
    private fun group(
        cards: List<VisitCardView>,
        today: LocalDate,
        tenant: Tenant,
        formatter: TenantFormatter,
    ): List<VisitGroupView> =
        cards.groupBy { dayLabel(it, today, tenant, formatter) }
            .map { (label, visits) -> VisitGroupView(label, visits) }

    private fun dayLabel(card: VisitCardView, today: LocalDate, tenant: Tenant, formatter: TenantFormatter): String {
        val at = card.at ?: return "Plus tard"
        val date = VisitSlots.localDate(Date(at), tenant)
        return when (date) {
            today -> "Aujourd'hui"
            today.plusDays(1) -> "Demain"
            else -> formatter.date(date, "EEE d MMMM")
        }
    }

    /** The first [limit] cards, and the rest, without splitting a group when it fits. */
    private fun split(
        groups: List<VisitGroupView>,
        limit: Int,
    ): Pair<List<VisitGroupView>, List<VisitGroupView>> {
        val preview = mutableListOf<VisitGroupView>()
        val more = mutableListOf<VisitGroupView>()
        var shown = 0
        groups.forEach { group ->
            when {
                shown >= limit -> more += group
                shown + group.visits.size <= limit -> {
                    preview += group
                    shown += group.visits.size
                }

                else -> {
                    val take = limit - shown
                    preview += group.copy(visits = group.visits.take(take))
                    more += group.copy(visits = group.visits.drop(take))
                    shown = limit
                }
            }
        }
        return preview to more
    }

    /**
     * The state itself, since a card with a visit hides its header tag (the prototype's `visitKind`).
     *
     * COMPLETED never reaches these cards in this pass: [of] only returns open visits, so its *Visité* wording is kept
     * for the follow-up *Visités* group. Do not add completed visits to the cards here.
     */
    private fun visitKind(status: VisitStatus, video: Boolean): String =
        when (status) {
            VisitStatus.REQUESTED -> if (video) "Visite vidéo demandée" else "Visite demandée"
            VisitStatus.CONFIRMED -> if (video) "Visite vidéo confirmée" else "Visite confirmée"
            VisitStatus.CHECKED_IN -> if (video) "Visite vidéo en cours" else "Visite en cours"
            VisitStatus.COMPLETED -> if (video) "Visité en vidéo" else "Visité"
            else -> if (video) "Visite vidéo" else "Visite"
        }

    private fun whenText(visit: Visit, whenLabel: String, formatter: TenantFormatter): String =
        when (visit.status) {
            VisitStatus.REQUESTED -> "pour le $whenLabel"
            VisitStatus.CHECKED_IN -> visit.checkedInAt?.let { "depuis ${formatter.time(it)}" } ?: "en cours"
            else -> "le $whenLabel"
        }

    private fun whenLabel(visit: Visit, today: LocalDate, tenant: Tenant, formatter: TenantFormatter): String {
        if (visit.status == VisitStatus.CHECKED_IN) {
            val at = visit.checkedInAt
            if (at != null) return "En cours depuis ${formatter.time(at)}"
        }
        return VisitRules.whenLabel(visit.scheduledAt ?: visit.requestedAt, today, tenant, formatter)
    }

    private fun name(customer: CustomerSummary?): String =
        listOf(
            customer?.party?.firstName?.trim().orEmpty(),
            customer?.party?.lastName?.trim().orEmpty(),
        )
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { "Client" }

    private fun listing(
        visit: Visit,
        listings: Map<Long, ListingSummary>?,
        locations: Map<Long, Location>,
    ): String {
        val listing = listings?.get(visit.listingId)
        val title = listing?.let { line(it, locations) } ?: "Annonce n° ${visit.listingId}"
        return "${VisitRules.typeLabel(visit.type)} · $title"
    }

    private fun line(listing: ListingSummary, locations: Map<Long, Location>): String {
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        val rooms = listing.rooms?.let { MatchRules.rooms(it) }
        val place = listOfNotNull(listing.neighborhoodId, listing.cityId)
            .firstNotNullOfOrNull { locations[it]?.name }
        return listOfNotNull(type, rooms, place?.let { "· $it" }).joinToString(" ")
    }
}
