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
    fun `ruled out - other property type`() {
        assertNull(score(search, listingA.copy(propertyType = PropertyType.HOUSE)))
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
}
