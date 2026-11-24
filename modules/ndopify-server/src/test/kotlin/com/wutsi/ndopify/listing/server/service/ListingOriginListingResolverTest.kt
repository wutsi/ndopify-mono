package com.wutsi.ndopify.listing.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.dto.FurnitureType
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.MovingPeriod
import com.wutsi.ndopify.listing.dto.PropertyCategory
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingOriginListingResolverTest {
    private val dao = mock<ListingRepository>()
    private val resolver = ListingOriginListingResolver(dao)

    private val listing = ListingEntity(
        id = 7,
        tenantId = 1,
        agent = AgentEntity(id = 70, party = PartyEntity(id = 700)),
        status = ListingStatus.PUBLISHED,
        listingType = ListingType.RENTAL,
        propertyCategory = PropertyCategory.RESIDENTIAL,
        cityId = 2370201,
        neighborhoodId = 11,
        propertyType = PropertyType.APARTMENT,
        furnitureType = FurnitureType.FULLY_FURNISHED,
        rooms = 2,
        parkings = 1,
        area = 75,
        waterTank = true,
        price = 175_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )

    @Test
    fun `resolves a published listing into search criteria`() {
        doReturn(Optional.of(listing)).whenever(dao).findById(7)

        val origin = resolver.resolve(7)!!

        assertEquals(7L, origin.id)
        assertEquals(70L, origin.agentId)
        assertTrue(origin.published)
        assertEquals(MandateType.SEARCH, origin.criteria.mandateType)
        assertEquals(ListingType.RENTAL, origin.criteria.listingType)
        assertEquals(PropertyCategory.RESIDENTIAL, origin.criteria.propertyCategory)
        assertEquals(2370201L, origin.criteria.cityId)
        assertEquals(listOf(11L), origin.criteria.neighbourhoodIds)
        assertEquals(PropertyType.APARTMENT, origin.criteria.propertyType)
        assertEquals(FurnitureType.FULLY_FURNISHED, origin.criteria.furnitureType)
        assertEquals("2", origin.criteria.rooms)
        assertEquals("1", origin.criteria.parkings)
        assertEquals(75, origin.criteria.area)
        assertEquals(true, origin.criteria.waterTank)
        assertEquals(Money(175_000, "XAF", PeriodicityType.MONTHLY), origin.criteria.price)
        assertEquals("", origin.criteria.email)
        // The visitor's own fields are never taken from the listing
        assertEquals(MovingPeriod.UNKNOWN, origin.criteria.movingPeriod)
        assertNull(origin.criteria.availableFrom)
        assertNull(origin.criteria.securityDeposit)
    }

    @Test
    fun `a building listing seeds the search with its counts`() {
        doReturn(
            Optional.of(
                listing.copy(
                    listingType = ListingType.SALE,
                    propertyType = PropertyType.BUILDING,
                    rooms = null,
                    parkings = null,
                    levels = 3,
                    units = 12,
                ),
            ),
        ).whenever(dao).findById(7)

        val criteria = resolver.resolve(7)!!.criteria

        assertEquals(3, criteria.levels)
        assertEquals(12, criteria.units)
    }

    @Test
    fun `a sold listing resolves, not published`() {
        doReturn(Optional.of(listing.copy(status = ListingStatus.SOLD))).whenever(dao).findById(7)

        val origin = resolver.resolve(7)!!

        assertFalse(origin.published)
        assertEquals(2370201L, origin.criteria.cityId)
        assertEquals(175_000L, origin.criteria.price.amount)
    }

    @Test
    fun `an unknown listing resolves to null`() {
        doReturn(Optional.empty<ListingEntity>()).whenever(dao).findById(7)

        assertNull(resolver.resolve(7))
    }
}
