package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.customer.dto.Customer
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import java.time.Instant
import java.util.Date

/** The visits of `2026-11-05-partner-central-visits.md`: a requested one (7001) and its variants. */
object VisitFixtures {
    val NOW: Long = Instant.parse("2026-11-05T10:00:00Z").toEpochMilli()
    val TENANT = TenantFixtures.TENANT
    val FORMATTER = TenantFixtures.FORMATTER
    val AGENT_ID = 20L
    val CUSTOMER_ID = 300L

    val CUSTOMER = Customer(
        id = CUSTOMER_ID,
        party = Party(firstName = "Sandrine", lastName = "Eboa", email = "sandrine@gmail.com"),
        whatsappNumber = "+237699887766",
    )

    val LISTING = Listing(
        id = 3012,
        agentId = AGENT_ID,
        status = ListingStatus.PUBLISHED,
        cityId = 2232593,
        neighborhoodId = 3001,
        propertyType = PropertyType.APARTMENT,
        rooms = 3,
        price = 240_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        landmark = "Rue Njo-Njo, portail bleu",
        accessDirections = "Derrière la pharmacie",
        latitude = 4.0247,
        longitude = 9.6943,
    )

    fun requested() = Visit(
        id = 7001,
        matchId = 9001,
        mandateId = 204,
        listingId = 3012,
        agentId = AGENT_ID,
        customerId = CUSTOMER_ID,
        status = VisitStatus.REQUESTED,
        type = VisitType.ON_SITE,
        requestedAt = date("2026-11-07T09:30:00Z"),
        note = "Je viendrai avec mon mari.",
        createdAt = date("2026-11-05T04:00:00Z"),
    )

    fun confirmed() = requested().copy(status = VisitStatus.CONFIRMED, scheduledAt = date("2026-11-07T09:30:00Z"))

    fun window() = requested().copy(status = VisitStatus.CONFIRMED, scheduledAt = date("2026-11-05T09:30:00Z"))

    fun checkedIn() = requested().copy(
        status = VisitStatus.CHECKED_IN,
        scheduledAt = date("2026-11-05T09:30:00Z"),
        checkedInAt = date("2026-11-05T09:05:00Z"),
        checkInDistanceMeters = 820,
        checkInFlagged = true,
    )

    fun completed() = checkedIn().copy(
        status = VisitStatus.COMPLETED,
        checkedOutAt = date("2026-11-05T09:35:00Z"),
        agentComment = "Client intéressé, reviendra avec son épouse.",
        interest = VisitInterest.NOT_INTERESTED,
        feedbackReasonCode = MatchRejectionReason.PRICE,
        feedbackComment = "Un peu cher pour le quartier.",
    )

    fun date(iso: String): Date = Date.from(Instant.parse(iso))

    fun summary(visit: Visit) = VisitSummary(
        id = visit.id,
        matchId = visit.matchId,
        mandateId = visit.mandateId,
        listingId = visit.listingId,
        agentId = visit.agentId,
        customerId = visit.customerId,
        status = visit.status,
        type = visit.type,
        requestedAt = visit.requestedAt,
        note = visit.note,
        scheduledAt = visit.scheduledAt,
        checkedInAt = visit.checkedInAt,
        checkInDistanceMeters = visit.checkInDistanceMeters,
        checkInFlagged = visit.checkInFlagged,
        checkedOutAt = visit.checkedOutAt,
        noShowParty = visit.noShowParty,
        noShowReason = visit.noShowReason,
        cancelledBy = visit.cancelledBy,
        cancellationReason = visit.cancellationReason,
        cancelledAt = visit.cancelledAt,
        createdAt = visit.createdAt,
        version = visit.version,
    )

    fun listingSummary() = ListingSummary(
        id = LISTING.id,
        mandateId = 204,
        agentId = AGENT_ID,
        status = ListingStatus.PUBLISHED,
        cityId = LISTING.cityId,
        neighborhoodId = LISTING.neighborhoodId,
        propertyType = LISTING.propertyType,
        rooms = LISTING.rooms,
        price = LISTING.price,
        currencyCode = LISTING.currencyCode,
        pricePeriodicityType = LISTING.pricePeriodicityType,
    )

    fun customerSummary() = CustomerSummary(
        id = CUSTOMER_ID,
        party = CUSTOMER.party,
        whatsappNumber = CUSTOMER.whatsappNumber,
    )

    val LOCATIONS: Map<Long, Location> = listOf(
        Location(id = 2232593, name = "Douala"),
        Location(id = 3001, name = "Bonapriso"),
    ).associateBy { it.id }
}
