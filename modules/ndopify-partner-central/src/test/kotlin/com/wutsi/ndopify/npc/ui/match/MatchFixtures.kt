package com.wutsi.ndopify.npc.ui.match

import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate

/** The worked example of `2026-10-31-search-listing-matching.md`: search 204, listings A (3012), C (3027), B (3041). */
object MatchFixtures {
    val TODAY: LocalDate = LocalDate.of(2026, 11, 1)

    val MANDATE = Mandate(
        id = 204,
        customerId = 300,
        agentId = 20,
        mandateType = MandateType.SEARCH,
        status = MandateStatus.APPROVED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.APARTMENT,
        cityId = 100,
        neighborhoodIds = listOf(1001),
        rooms = "3",
        price = Money(250000, "XAF", PeriodicityType.MONTHLY),
        generatorSet = true,
        securedFenced = true,
        waterTank = true,
        movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
        furnitureType = FurnitureType.UNFURNISHED,
    )

    val LISTING_A = listing(3012, 1001, 240000, 3, LocalDate.of(2026, 12, 1))
    val LISTING_C = listing(3027, 1001, 200000, 2, LocalDate.of(2027, 2, 15))
    val LISTING_B = listing(3041, 1002, 265000, 4, null)

    val MATCH_A = Match(
        id = 9001,
        mandateId = 204,
        listingId = 3012,
        status = MatchStatus.ACTIVE,
        score = 0.95,
        neighborhoodScore = 1.0,
        priceScore = 1.0,
        roomsScore = 1.0,
        amenitiesScore = 0.67,
        availabilityScore = 1.0,
        furnitureScore = 1.0,
    )
    val MATCH_C = Match(
        id = 9002,
        mandateId = 204,
        listingId = 3027,
        status = MatchStatus.ACTIVE,
        score = 0.745,
        neighborhoodScore = 1.0,
        priceScore = 1.0,
        roomsScore = 0.3,
        amenitiesScore = 0.33,
        availabilityScore = 0.5,
        furnitureScore = 1.0,
    )
    val MATCH_B = Match(
        id = 9003,
        mandateId = 204,
        listingId = 3041,
        status = MatchStatus.ACTIVE,
        score = 0.58,
        neighborhoodScore = 0.25,
        priceScore = 0.4,
        roomsScore = 0.7,
        amenitiesScore = 1.0,
        availabilityScore = 1.0,
        furnitureScore = 1.0,
    )

    val LISTINGS: Map<Long, ListingSummary> = listOf(LISTING_A, LISTING_C, LISTING_B).associateBy { it.id }

    val LOCATIONS: Map<Long, Location> = listOf(
        Location(id = 100, name = "Douala"),
        Location(id = 1001, name = "Bonapriso"),
        Location(id = 1002, name = "Bonanjo"),
    ).associateBy { it.id }

    private fun listing(id: Long, neighborhoodId: Long, price: Long, rooms: Int, availableFrom: LocalDate?) =
        ListingSummary(
            id = id,
            agentId = 20,
            status = ListingStatus.PUBLISHED,
            listingType = ListingType.RENTAL,
            propertyCategory = PropertyCategory.RESIDENTIAL,
            propertyType = PropertyType.APARTMENT,
            furnitureType = FurnitureType.UNFURNISHED,
            cityId = 100,
            neighborhoodId = neighborhoodId,
            rooms = rooms,
            price = price,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
            availableFrom = availableFrom,
        )
}
