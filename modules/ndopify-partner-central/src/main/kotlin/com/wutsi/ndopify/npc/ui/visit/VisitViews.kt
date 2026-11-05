package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.npc.ui.mandate.MandateLabels
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.Tenant
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantFormatter
import java.util.Date

/**
 * Builds the visit page's [VisitPage] after the prototype's `renderVisit`. The address comes from the listing (its
 * landmark and access directions), never from `Visit.address`, which is the customer's only; the no-show distance is
 * not shown because the API doesn't expose it.
 */
object VisitViews {
    private val CAMEROON_PHONE = Regex("""^\+237(\d)(\d{2})(\d{2})(\d{2})(\d{2})$""")

    fun of(
        visit: Visit,
        customer: Customer?,
        listing: Listing?,
        locations: Map<Long, Location>,
        now: Long,
        tenant: Tenant,
        formatter: TenantFormatter,
    ): VisitPage {
        val panel = VisitRules.panel(visit, now)
        val first = customer?.party?.firstName?.trim().orEmpty()
        val last = customer?.party?.lastName?.trim().orEmpty()
        val name = listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "Client" }
        val customerFirstName = first.ifEmpty { "Le client" }
        val phone = customer?.whatsappNumber?.trim()?.ifEmpty { null }
        val whatsapp = customer?.whatsappNumber
        val videoPossible = !whatsapp.isNullOrBlank() || visit.type == VisitType.VIDEO

        val scheduledAt = visit.scheduledAt
        val plannedAt = VisitSlots.dateTimeLabel(scheduledAt ?: visit.requestedAt, tenant, formatter)
        val address = address(listing)
        val cancelledAt = visit.cancelledAt
        val wasRequest = scheduledAt == null
        val byAgent = visit.cancelledBy == VisitParty.AGENT

        val days = VisitSlots.days(VisitSlots.localDate(Date(now), tenant), formatter)
        val times = VisitSlots.times()
        val selected = scheduledAt ?: visit.requestedAt
        val selectedDay = VisitSlots.localDate(selected, tenant).toString()
            .takeIf { value -> days.any { it.value == value } }
            ?: days.first().value
        val selectedTime = VisitSlots.localTime(selected, tenant).toString()
            .takeIf { value -> times.any { it.first == value } }
            ?: times.first().first

        val checkedInAt = visit.checkedInAt
        val checkedOutAt = visit.checkedOutAt

        return VisitPage(
            id = visit.id,
            panel = panel,
            tag = VisitRules.tag(panel),
            type = visit.type,
            typeLabel = VisitRules.typeLabel(visit.type),
            customerName = name,
            customerFirstName = customerFirstName,
            phone = phone?.let { displayPhone(it) },
            phoneDigits = phone?.filter { it.isDigit() },
            mandateId = visit.mandateId,
            listingId = visit.listingId,
            listingTitle = title(listing, locations),
            listingMeta = listing?.let { meta(it, formatter) },
            listingUrl = "/listings/${visit.listingId}",
            address = address,
            mapUrl = mapUrl(listing),
            requestedAt = VisitSlots.dateTimeLabel(visit.requestedAt, tenant, formatter),
            scheduledAt = scheduledAt?.let { VisitSlots.dateTimeLabel(it, tenant, formatter) },
            windowFrom = VisitRules.checkInFrom(visit)
                ?.let { VisitSlots.dateTimeLabel(Date(it), tenant, formatter) },
            expiresAt = VisitSlots.dateTimeLabel(Date(VisitRules.expiresAt(visit)), tenant, formatter),
            expiresIn = VisitRules.expiresIn(VisitRules.expiresAt(visit) - now),
            note = visit.note,
            checkedInAt = checkedInAt?.let { formatter.time(it) },
            checkOutFrom = VisitRules.checkOutFrom(visit)?.let { formatter.time(Date(it)) },
            checkOutReady = VisitRules.checkOutReady(visit, now),
            checkedOutAt = checkedOutAt?.let { VisitSlots.dateTimeLabel(it, tenant, formatter) },
            duration = VisitRules.duration(checkedInAt, checkedOutAt),
            startDistance = startDistance(visit),
            checkInFlagged = visit.checkInFlagged,
            distance = VisitRules.meters(visit.checkInDistanceMeters),
            distanceKnown = visit.checkInDistanceMeters != null,
            agentComment = visit.agentComment,
            interest = visit.interest,
            feedbackTag = feedbackTag(visit.interest),
            feedbackTone = feedbackTone(visit.interest),
            feedbackReason = if (visit.interest == VisitInterest.NOT_INTERESTED) {
                VisitRules.REJECTION_LABELS[visit.feedbackReasonCode]
            } else {
                null
            },
            feedbackComment = visit.feedbackComment,
            cancelledTitle = if (panel == VisitRules.Panel.CANCELLED || visit.cancelledAt != null) {
                when {
                    wasRequest && byAgent -> "Demande refusée"
                    wasRequest -> "Demande annulée"
                    else -> "Visite annulée"
                }
            } else {
                null
            },
            cancelledText = cancelledAt?.let {
                when {
                    byAgent && wasRequest -> "Vous avez refusé la demande le ${date(it, tenant, formatter)}."
                    byAgent -> "Vous avez annulé la visite le ${date(it, tenant, formatter)}."
                    wasRequest -> "$customerFirstName a annulé sa demande le ${date(it, tenant, formatter)}."
                    else -> "$customerFirstName a annulé la visite le ${date(it, tenant, formatter)}."
                }
            },
            cancellationReason = visit.cancellationReason?.trim()?.ifEmpty { null }?.let { "« $it »" },
            plannedAt = plannedAt,
            expiredText = if (panel == VisitRules.Panel.EXPIRED) {
                "Vous n'avez pas confirmé la demande sous 48 h : elle a expiré le " +
                    "${date(Date(VisitRules.expiresAt(visit)), tenant, formatter)}. " +
                    "$customerFirstName peut en faire une nouvelle."
            } else {
                null
            },
            noShowTitle = if (panel == VisitRules.Panel.NO_SHOW) noShowTitle(visit) else null,
            noShowText = if (panel == VisitRules.Panel.NO_SHOW) noShowText(visit) else null,
            canCancel = VisitRules.canCancel(visit),
            canCheckIn = VisitRules.canCheckIn(visit, now),
            canNoShow = VisitRules.canNoShow(visit, now),
            days = days,
            times = times,
            selectedDay = selectedDay,
            selectedTime = selectedTime,
            videoPossible = videoPossible,
        )
    }

    /** The listing's "type rooms" and its neighbourhood, else its city; null without a listing. */
    private fun title(listing: Listing?, locations: Map<Long, Location>): String? {
        if (listing == null) return null
        val type = MandateLabels.shortPropertyType(listing.propertyType) ?: "Bien"
        val rooms = listing.rooms?.let { MatchRules.rooms(it) }
        val place = listOfNotNull(listing.neighborhoodId, listing.cityId)
            .firstNotNullOfOrNull { locations[it]?.name }
        return listOfNotNull(type, rooms, place.takeIf { it != null }?.let { "· $it" })
            .joinToString(" ")
    }

    private fun meta(listing: Listing, formatter: TenantFormatter): String =
        "Annonce n° ${listing.id} · " +
            MandateLabels.price(
                Money(listing.price, listing.currencyCode, listing.pricePeriodicityType),
                formatter,
                compact = true,
            )

    private fun address(listing: Listing?): String? =
        listOfNotNull(listing?.landmark, listing?.accessDirections)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")
            .ifEmpty { null }

    private fun mapUrl(listing: Listing?): String? {
        val latitude = listing?.latitude ?: return null
        val longitude = listing.longitude ?: return null
        return "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=18/$latitude/$longitude"
    }

    private fun startDistance(visit: Visit): String {
        val meters = VisitRules.meters(visit.checkInDistanceMeters) ?: return "Non relevée"
        val flagged = if (visit.checkInFlagged) " (à vérifier par l'équipe Ndopify)" else ""
        return "À $meters du bien$flagged"
    }

    private fun feedbackTag(interest: VisitInterest?): String? =
        when (interest) {
            VisitInterest.INTERESTED -> "Intéressé"
            VisitInterest.NOT_INTERESTED -> "Pas intéressé"
            else -> null
        }

    private fun feedbackTone(interest: VisitInterest?): String? =
        when (interest) {
            VisitInterest.INTERESTED -> "success"
            VisitInterest.NOT_INTERESTED -> "neutral"
            else -> null
        }

    private fun noShowTitle(visit: Visit): String =
        when {
            visit.noShowParty == VisitParty.AGENT -> "Visite non démarrée"
            visit.noShowParty == VisitParty.CUSTOMER && visit.noShowReason == VisitNoShowReason.CODE_REFUSED ->
                "Code refusé par le client"

            visit.noShowParty == VisitParty.CUSTOMER &&
                visit.noShowReason == VisitNoShowReason.CUSTOMER_ABSENT &&
                visit.type == VisitType.VIDEO -> "Le client ne répond pas"

            else -> "Client absent"
        }

    private fun noShowText(visit: Visit): String =
        if (visit.noShowParty == VisitParty.AGENT) {
            "Vous n'avez pas démarré la visite : elle est comptée comme une absence de votre part."
        } else {
            "Déclaré par vous : l'absence du client est enregistrée."
        }

    private fun date(at: Date, tenant: Tenant, formatter: TenantFormatter): String =
        VisitSlots.dateTimeLabel(at, tenant, formatter)

    /** "+237699123456" → "+237 6 99 12 34 56" (Cameroon numbers; others are shown as they are). */
    private fun displayPhone(e164: String): String =
        CAMEROON_PHONE.matchEntire(e164)?.groupValues?.drop(1)?.joinToString(" ", prefix = "+237 ") ?: e164
}
