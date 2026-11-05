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
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.time.LocalDate
import java.util.Date

/**
 * Builds the *Visites* section of the Espace agent (see [VisitCardsView]) after the prototype's `renderVisits`: the
 * requests still within their 48 h, soonest expiry first, and the visits to come. A requested visit past its 48 h and
 * a confirmed one past its `+ 4 h` window are dropped even when ndopify-server's job hasn't moved them yet.
 * [listings] and [customers] are secondary: null or a missing entry only leaves that line out.
 */
object VisitCards {
    private const val URGENT = 12 * 3_600_000L
    private const val WINDOW_AFTER = 4 * 3_600_000L

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
        val requested = visits
            .map { it.asVisit() }
            .filter { it.status == VisitStatus.REQUESTED && VisitRules.expiresAt(it) > now }
            .sortedBy { VisitRules.expiresAt(it) }
            .map { visit ->
                val expiresAt = VisitRules.expiresAt(visit)
                val left = expiresAt - now
                VisitCardView(
                    id = visit.id,
                    customerName = name(customers[visit.customerId]),
                    tag = null,
                    whenLabel = "Demandée pour le ${VisitSlots.dateTimeLabel(visit.requestedAt, tenant, formatter)}",
                    listing = listing(visit, listings, locations),
                    expiresAt = expiresAt,
                    expiresIn = VisitRules.expiresIn(left),
                    urgent = left < URGENT,
                    nextStep = VisitRules.nextStep(visit, now).orEmpty(),
                )
            }

        val upcoming = visits
            .filter { it.status == VisitStatus.CHECKED_IN || isUpcoming(it, now) }
            .sortedWith(compareBy(nullsLast()) { it.scheduledAt?.time })
            .map { summary ->
                val visit = summary.asVisit()
                VisitCardView(
                    id = visit.id,
                    customerName = name(customers[visit.customerId]),
                    tag = VisitRules.tag(VisitRules.panel(visit, now)),
                    whenLabel = whenLabel(visit, today, tenant, formatter),
                    listing = listing(visit, listings, locations),
                    expiresAt = null,
                    expiresIn = null,
                    urgent = false,
                    nextStep = VisitRules.nextStep(visit, now).orEmpty(),
                )
            }

        return VisitCardsView(requested, upcoming)
    }

    private fun isUpcoming(visit: VisitSummary, now: Long): Boolean =
        visit.status == VisitStatus.CONFIRMED &&
            visit.scheduledAt?.let { it.time + WINDOW_AFTER >= now } == true

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

    /** The visit rules take a [Visit]: a summary has every field they read. */
    private fun VisitSummary.asVisit(): Visit =
        Visit(
            id = id,
            matchId = matchId,
            mandateId = mandateId,
            listingId = listingId,
            agentId = agentId,
            customerId = customerId,
            status = status,
            type = type,
            requestedAt = requestedAt,
            note = note,
            scheduledAt = scheduledAt,
            checkedInAt = checkedInAt,
            checkInDistanceMeters = checkInDistanceMeters,
            checkInFlagged = checkInFlagged,
            checkedOutAt = checkedOutAt,
            noShowParty = noShowParty,
            noShowReason = noShowReason,
            cancelledBy = cancelledBy,
            cancellationReason = cancellationReason,
            cancelledAt = cancelledAt,
            createdAt = createdAt,
            version = version,
        )
}
