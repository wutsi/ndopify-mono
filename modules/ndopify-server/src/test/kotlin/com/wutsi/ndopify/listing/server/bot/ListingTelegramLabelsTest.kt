package com.wutsi.ndopify.listing.server.bot

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ListingTelegramLabelsTest {
    private val locationService = mock<LocationService>()
    private val labels = ListingTelegramLabels(locationService)
    private val formatter = TenantFormatter(
        TenantEntity(currencyCode = "XAF", monetaryFormat = "#,###,##0 FCFA"),
    )
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        neighborhoodId = 1001L,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )

    @BeforeTest
    fun setUp() {
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `title is the listing title`() {
        assertEquals("Nice apartment", labels.title(listing))
    }

    @Test
    fun `the property line is the title and the compact price`() {
        assertEquals("Nice apartment - 150K/mois", labels.titleAndPrice(listing, formatter))
    }

    @Test
    fun `a sale reads its compact price without a currency or a label`() {
        val sale = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)

        assertEquals("Nice apartment - 150K", labels.titleAndPrice(sale, formatter))
    }

    @Test
    fun `a blank title falls back to the type, the usage and the place`() {
        doReturn(LocationEntity(id = 1002L, name = "Yaounde")).whenever(locationService).findByIdOrNull(1002L)
        val titled = listing.copy(
            title = null,
            propertyType = PropertyType.APARTMENT,
            cityId = 1002L,
            listingType = ListingType.RENTAL,
        )

        assertEquals(
            "Appartement à louer, Yaounde (Bastos) - 150K/mois",
            labels.titleAndPrice(titled, formatter),
        )
        assertEquals(
            "Bien à louer, Yaounde (Bastos) - 150K/mois",
            labels.titleAndPrice(titled.copy(propertyType = null), formatter),
        )
        assertEquals(
            "Appartement à louer, Yaounde - 150K/mois",
            labels.titleAndPrice(titled.copy(neighborhoodId = null), formatter),
        )
        assertEquals(
            "Appartement à louer - 150K/mois",
            labels.titleAndPrice(titled.copy(neighborhoodId = null, cityId = -1), formatter),
        )
    }

    @Test
    fun `a sale's blank title says a vendre`() {
        doReturn(LocationEntity(id = 1002L, name = "Yaounde")).whenever(locationService).findByIdOrNull(1002L)
        val titled = listing.copy(
            title = null,
            propertyType = PropertyType.LAND,
            listingType = ListingType.SALE,
            pricePeriodicityType = PeriodicityType.NONE,
            cityId = 1002L,
        )

        assertEquals("Terrain à vendre, Yaounde (Bastos) - 150K", labels.titleAndPrice(titled, formatter))
    }

    @Test
    fun `no price - no trailing dash`() {
        val text = labels.titleAndPrice(listing.copy(price = -1), formatter)

        assertEquals("Nice apartment", text)
        assertFalse(text.contains(" - "))
    }

    @Test
    fun `a blank currency leaves the price out`() {
        val text = labels.titleAndPrice(listing.copy(currencyCode = " "), formatter)

        assertEquals("Nice apartment", text)
        assertFalse(text.contains(" - "))
    }

    @Test
    fun `a blank title is not kept as is`() {
        assertEquals("Bien à louer (Bastos) - 150K/mois", labels.titleAndPrice(listing.copy(title = " "), formatter))
    }
}
