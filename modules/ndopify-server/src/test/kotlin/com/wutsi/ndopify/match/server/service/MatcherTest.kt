package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MatcherTest {
    companion object {
        const val DELTA = 0.005
        const val AGENT_ID = 10L
        const val CITY_ID = 100L
        const val N1 = 1001L
        const val N2 = 1002L // 1.5 km from N1
        const val N9 = 1009L // Not nearby
        val TODAY: LocalDate = LocalDate.of(2026, 11, 1)
    }

    private val matcher = Matcher(0.10, 3.0)
    private val distances = mapOf(N1 to 0.0, N2 to 1.5)

    // The worked example of docs/superpowers/plans/2026-10-31-search-listing-matching.md
    private val search = MandateEntity(
        id = 1L,
        customerId = 500L,
        agentId = AGENT_ID,
        mandateType = MandateType.SEARCH,
        status = MandateStatus.APPROVED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.APARTMENT,
        cityId = CITY_ID,
        neighborhoodIds = listOf(N1),
        rooms = "3",
        price = 250_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        generatorSet = true,
        securedFenced = true,
        waterTank = true,
        movingPeriod = MovingPeriod.WITHIN_3_MONTHS,
    )

    private val listingA = ListingEntity(
        id = 11L,
        mandate = MandateEntity(id = 2L, customerId = 600L, mandateType = MandateType.LISTING),
        agent = AgentEntity(id = AGENT_ID),
        status = ListingStatus.PUBLISHED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        propertyType = PropertyType.APARTMENT,
        cityId = CITY_ID,
        neighborhoodId = N1,
        rooms = 3,
        price = 240_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        securedFenced = true,
        waterTank = true,
        availableFrom = LocalDate.of(2026, 12, 1),
    )
    private val listingB = listingA.copy(
        id = 12L,
        neighborhoodId = N2,
        price = 265_000,
        rooms = 4,
        generatorSet = true,
        availableFrom = null,
    )
    private val listingC = listingA.copy(
        id = 13L,
        price = 200_000,
        rooms = 2,
        waterTank = false,
        availableFrom = LocalDate.of(2027, 2, 15),
    )

    private val commercialSearch = search.copy(
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = PropertyType.OFFICE,
    )
    private val commercialListing = listingA.copy(
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = PropertyType.OFFICE,
    )

    private fun score(search: MandateEntity, listing: ListingEntity): MatchScore? =
        matcher.score(search, listing, distances, TODAY)

    private fun assertScore(expected: List<Double>, total: Double, actual: MatchScore?) {
        assertNotNull(actual)
        assertEquals(expected[0], actual.neighborhood, DELTA)
        assertEquals(expected[1], actual.price, DELTA)
        assertEquals(expected[2], actual.rooms, DELTA)
        assertEquals(expected[3], actual.amenities, DELTA)
        assertEquals(expected[4], actual.availability, DELTA)
        assertEquals(expected[5], actual.furniture, DELTA)
        assertEquals(total, actual.total, DELTA)
    }

    @Test
    fun `listing A`() {
        assertScore(listOf(1.0, 1.0, 1.0, 0.67, 1.0, 1.0), 0.95, score(search, listingA))
        assertEquals(1.0, score(search, listingA)?.type)
    }

    @Test
    fun `commercial sibling - retail`() {
        val result = score(commercialSearch, commercialListing.copy(propertyType = PropertyType.RETAIL))

        assertNotNull(result)
        assertEquals(0.9, result.type, DELTA)
        assertEquals(0.855, result.total, DELTA) // 0.95 * 0.9
    }

    @Test
    fun `commercial sibling - building`() {
        val result = score(commercialSearch, commercialListing.copy(propertyType = PropertyType.BUILDING))

        assertNotNull(result)
        assertEquals(0.85, result.type, DELTA)
        assertEquals(0.8075, result.total, DELTA)
    }

    @Test
    fun `commercial sibling - industrial`() {
        val result = score(commercialSearch, commercialListing.copy(propertyType = PropertyType.INDUSTRIAL))

        assertNotNull(result)
        assertEquals(0.8, result.type, DELTA)
        assertEquals(0.76, result.total, DELTA)
    }

    @Test
    fun `building matches a building of the other category`() {
        val result = score(
            search.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.BUILDING),
            listingA.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.BUILDING),
        )

        assertNotNull(result)
        assertEquals(1.0, result.type, DELTA)
        assertEquals(0.95, result.total, DELTA)
    }

    @Test
    fun `office matches a building stored residential`() {
        val result = score(
            commercialSearch,
            listingA.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.BUILDING),
        )

        assertNotNull(result)
        assertEquals(0.85, result.type, DELTA)
        assertEquals(0.8075, result.total, DELTA)
    }

    @Test
    fun `ruled out - land`() {
        assertNull(score(commercialSearch, commercialListing.copy(propertyType = PropertyType.LAND)))
        assertNull(score(commercialSearch.copy(propertyType = PropertyType.LAND), commercialListing))
    }

    @Test
    fun `ruled out - commercial against residential`() {
        assertNull(score(commercialSearch, commercialListing.copy(propertyType = PropertyType.APARTMENT)))
    }

    @Test
    fun `no property type searched - unknown`() {
        val result = score(
            search.copy(propertyType = PropertyType.UNKNOWN),
            listingA.copy(propertyType = PropertyType.HOUSE),
        )

        assertNotNull(result)
        assertEquals(1.0, result.type, DELTA)
    }

    @Test
    fun `ruled out - listing without property type`() {
        assertNull(score(search, listingA.copy(propertyType = null)))
    }

    @Test
    fun `no property type searched - the category decides`() {
        assertNull(
            score(
                search.copy(propertyType = null),
                listingA.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.LAND),
            )
        )
        assertNull(
            score(
                commercialSearch.copy(propertyType = null),
                listingA.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.BUILDING),
            )
        )

        val result = score(
            commercialSearch.copy(propertyType = null),
            commercialListing.copy(propertyType = PropertyType.LAND),
        )
        assertNotNull(result)
        assertEquals(1.0, result.type, DELTA)
    }

    @Test
    fun `ruled out - building search against a listing without property type`() {
        assertNull(score(search.copy(propertyType = PropertyType.BUILDING), listingA.copy(propertyType = null)))
    }

    @Test
    fun `industrial and building are siblings in both directions`() {
        val industrialSearch = commercialSearch.copy(propertyType = PropertyType.INDUSTRIAL)
        val buildingListing = commercialListing.copy(propertyType = PropertyType.BUILDING)
        val industrialListing = commercialListing.copy(propertyType = PropertyType.INDUSTRIAL)

        assertEquals(0.8, score(industrialSearch, buildingListing)?.type)
        assertEquals(0.8, score(commercialSearch.copy(propertyType = PropertyType.BUILDING), industrialListing)?.type)
    }

    @Test
    fun `listing C`() {
        assertScore(listOf(1.0, 1.0, 0.3, 0.33, 0.5, 1.0), 0.745, score(search, listingC))
    }

    @Test
    fun `listing B`() {
        assertScore(listOf(0.25, 0.4, 0.7, 1.0, 1.0, 1.0), 0.58, score(search, listingB))
    }

    @Test
    fun `ruled out - over budget`() {
        assertNull(score(search, listingA.copy(price = 290_000)))
    }

    @Test
    fun `housing sibling - house for an apartment`() {
        val result = score(search, listingA.copy(propertyType = PropertyType.HOUSE))

        assertNotNull(result)
        assertEquals(0.8, result.type, DELTA)
        assertEquals(0.76, result.total, DELTA) // 0.95 * 0.8
    }

    @Test
    fun `housing sibling - one more room`() {
        val result = score(search, listingA.copy(propertyType = PropertyType.HOUSE, rooms = 4))

        assertNotNull(result)
        assertEquals(0.7, result.rooms, DELTA)
        assertEquals(0.724, result.total, DELTA) // 0.905 * 0.8
    }

    @Test
    fun `ruled out - housing sibling with other rooms`() {
        val house = listingA.copy(propertyType = PropertyType.HOUSE)

        assertNull(score(search, house.copy(rooms = 2)))
        assertNull(score(search, house.copy(rooms = 5)))
        assertNull(score(search, house.copy(rooms = null)))
    }

    @Test
    fun `housing sibling - no rooms searched`() {
        val result = score(search.copy(rooms = null), listingA.copy(propertyType = PropertyType.VILLA, rooms = 6))

        assertNotNull(result)
        assertEquals(0.8, result.type, DELTA)
        assertEquals(0.76, result.total, DELTA)
    }

    @Test
    fun `exact type with other rooms is kept`() {
        assertNotNull(score(search, listingA.copy(rooms = 5)))
    }

    @Test
    fun `housing sibling - apartment for a studio`() {
        val studioSearch = search.copy(propertyType = PropertyType.STUDIO, rooms = null)

        assertEquals(0.9, score(studioSearch, listingA)?.type)
    }

    @Test
    fun `housing sibling - studio without rooms for a 1 room apartment`() {
        val result = score(
            search.copy(rooms = "1"),
            listingA.copy(propertyType = PropertyType.STUDIO, rooms = null),
        )

        assertNotNull(result)
        assertEquals(1.0, result.rooms, DELTA)
        assertEquals(0.9, result.type, DELTA)
    }

    @Test
    fun `housing siblings`() {
        val noRooms = search.copy(rooms = null)
        fun type(searched: PropertyType, listed: PropertyType): Double? =
            score(noRooms.copy(propertyType = searched), listingA.copy(propertyType = listed))?.type

        assertEquals(0.9, type(PropertyType.HOUSE, PropertyType.VILLA))
        assertEquals(0.9, type(PropertyType.VILLA, PropertyType.HOUSE))
        assertEquals(0.85, type(PropertyType.APARTMENT, PropertyType.DUPLEX))
        assertEquals(0.85, type(PropertyType.DUPLEX, PropertyType.HOUSE))
        assertEquals(0.85, type(PropertyType.VILLA, PropertyType.DUPLEX))
        assertEquals(0.8, type(PropertyType.VILLA, PropertyType.APARTMENT))
        assertEquals(0.8, type(PropertyType.ROOM, PropertyType.STUDIO))
        assertEquals(0.8, type(PropertyType.STUDIO, PropertyType.ROOM))
    }

    @Test
    fun `ruled out - room against other housing`() {
        val noRooms = search.copy(rooms = null)

        assertNull(score(noRooms.copy(propertyType = PropertyType.ROOM), listingA))
        assertNull(score(noRooms, listingA.copy(propertyType = PropertyType.ROOM)))
        assertNull(score(noRooms.copy(propertyType = PropertyType.STUDIO), listingA.copy(propertyType = PropertyType.HOUSE)))
    }

    @Test
    fun `ruled out - housing against building`() {
        assertNull(score(search, listingA.copy(propertyType = PropertyType.BUILDING)))
    }

    @Test
    fun `ruled out - other listing type`() {
        assertNull(score(search, listingA.copy(listingType = ListingType.SALE)))
    }

    @Test
    fun `ruled out - other city`() {
        assertNull(score(search, listingA.copy(cityId = 200L)))
    }

    @Test
    fun `ruled out - other property category`() {
        assertNull(score(search, listingA.copy(propertyCategory = PropertyCategory.COMMERCIAL)))
    }

    @Test
    fun `ruled out - other currency`() {
        assertNull(score(search, listingA.copy(currencyCode = "EUR")))
    }

    @Test
    fun `ruled out - other price periodicity`() {
        assertNull(score(search, listingA.copy(pricePeriodicityType = PeriodicityType.WEEKLY)))
    }

    @Test
    fun `ruled out - the customer's own property`() {
        assertNull(score(search, listingA.copy(mandate = listingA.mandate.copy(customerId = search.customerId))))
    }

    @Test
    fun `no budget`() {
        val result = score(search.copy(price = -1), listingA.copy(price = 1_000_000, currencyCode = "EUR"))

        assertNotNull(result)
        assertEquals(1.0, result.price, DELTA)
    }

    @Test
    fun `no property type searched`() {
        assertNotNull(score(search.copy(propertyType = null), listingA.copy(propertyType = PropertyType.HOUSE)))
    }

    @Test
    fun `no neighborhood searched`() {
        assertEquals(1.0, score(search.copy(neighborhoodIds = emptyList()), listingB)?.neighborhood)
    }

    @Test
    fun `listing without neighborhood`() {
        assertEquals(0.0, score(search, listingA.copy(neighborhoodId = null))?.neighborhood)
    }

    @Test
    fun `neighborhood not nearby`() {
        assertEquals(0.0, score(search, listingA.copy(neighborhoodId = N9))?.neighborhood)
    }

    @Test
    fun `rooms - 5+ with 7`() {
        assertEquals(1.0, score(search.copy(rooms = "5+"), listingA.copy(rooms = 7))?.rooms)
    }

    @Test
    fun `rooms - 5+ with 4`() {
        assertEquals(0.3, score(search.copy(rooms = "5+"), listingA.copy(rooms = 4))?.rooms)
    }

    @Test
    fun `rooms - studio without rooms`() {
        assertEquals(0.0, score(search, listingA.copy(rooms = null))?.rooms)
    }

    @Test
    fun `rooms - not searched`() {
        assertEquals(1.0, score(search.copy(rooms = null), listingA.copy(rooms = 6))?.rooms)
    }

    @Test
    fun `rooms - unparseable`() {
        assertEquals(1.0, score(search.copy(rooms = "beaucoup"), listingA)?.rooms)
    }

    @Test
    fun `rooms - 2 more`() {
        assertEquals(0.0, score(search, listingA.copy(rooms = 5))?.rooms)
    }

    @Test
    fun `no amenity requested`() {
        val result = score(search.copy(generatorSet = false, securedFenced = false, waterTank = false), listingA)
        assertEquals(1.0, result?.amenities)
    }

    @Test
    fun `trackInaccessibleByCar is not an amenity`() {
        val result = score(
            search.copy(generatorSet = false, securedFenced = false, waterTank = false, trackInaccessibleByCar = true),
            listingA,
        )
        assertEquals(1.0, result?.amenities)
    }

    @Test
    fun `availability - within 1 month`() {
        val result = score(search.copy(movingPeriod = MovingPeriod.WITHIN_1_MONTH), listingA)
        assertEquals(1.0, result?.availability)
    }

    @Test
    fun `availability - after 3 months`() {
        val result = score(
            search.copy(movingPeriod = MovingPeriod.AFTER_3_MONTHS),
            listingA.copy(availableFrom = LocalDate.of(2027, 6, 1)),
        )
        assertEquals(1.0, result?.availability)
    }

    @Test
    fun `availability - too late`() {
        val result = score(search, listingA.copy(availableFrom = LocalDate.of(2027, 6, 1)))
        assertEquals(0.0, result?.availability)
    }

    @Test
    fun `availability - available now`() {
        assertEquals(1.0, score(search, listingA.copy(availableFrom = null))?.availability)
    }

    @Test
    fun `furniture - other`() {
        val result = score(
            search.copy(furnitureType = FurnitureType.FULLY_FURNISHED),
            listingA.copy(furnitureType = FurnitureType.UNFURNISHED),
        )
        assertEquals(0.0, result?.furniture)
    }

    @Test
    fun `furniture - unknown searched`() {
        val result = score(
            search.copy(furnitureType = FurnitureType.UNKNOWN),
            listingA.copy(furnitureType = FurnitureType.UNFURNISHED),
        )
        assertEquals(1.0, result?.furniture)
    }

    @Test
    fun `distance - in a searched neighborhood`() {
        assertEquals(0.0, score(search, listingA)?.distanceKm)
    }

    @Test
    fun `distance - nearby`() {
        assertEquals(1.5, score(search, listingB)?.distanceKm)
    }

    @Test
    fun `distance - not nearby`() {
        assertNull(score(search, listingA.copy(neighborhoodId = N9))!!.distanceKm)
    }

    @Test
    fun `distance - no neighborhood searched`() {
        assertNull(score(search.copy(neighborhoodIds = emptyList()), listingA)!!.distanceKm)
    }

    @Test
    fun `distance - listing without neighborhood`() {
        assertNull(score(search, listingA.copy(neighborhoodId = null))!!.distanceKm)
    }

    @Test
    fun `missing amenities`() {
        assertEquals(listOf("generatorSet"), score(search, listingA)?.missingAmenities)
        assertEquals(listOf("waterTank", "generatorSet"), score(search, listingC)?.missingAmenities)
        assertEquals(emptyList(), score(search, listingB)?.missingAmenities)
    }

    @Test
    fun `missing amenities - none requested`() {
        val none = search.copy(generatorSet = false, securedFenced = false, waterTank = false)

        assertEquals(emptyList(), score(none, listingC)?.missingAmenities)
    }
}
