package com.wutsi.ndopify.listing.server.bot

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
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
    fun `a blank or absent title falls back to the id`() {
        assertEquals("Bien n° 101", labels.title(listing.copy(title = " ")))
        assertEquals("Bien n° 101", labels.title(listing.copy(title = null)))
    }

    @Test
    fun `the property line names the title, the neighbourhood and the price`() {
        assertEquals("Nice apartment (Bastos) — 150 000 FCFA / mois", labels.titleAndPrice(listing, formatter))
    }

    @Test
    fun `no neighbourhood - no empty parenthesis`() {
        doReturn(null).whenever(locationService).findByIdOrNull(1001L)

        val text = labels.titleAndPrice(listing, formatter)

        assertEquals("Nice apartment — 150 000 FCFA / mois", text)
        assertFalse(text.contains("()"))
    }

    @Test
    fun `a sale listing's line names its price`() {
        val sale = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)

        assertEquals("Nice apartment (Bastos) — Prix : 150 000 FCFA", labels.titleAndPrice(sale, formatter))
    }

    @Test
    fun `no price - no trailing dash`() {
        val text = labels.titleAndPrice(listing.copy(price = -1), formatter)

        assertEquals("Nice apartment (Bastos)", text)
        assertFalse(text.contains(" — "))
    }

    @Test
    fun `a blank currency leaves the price out`() {
        val text = labels.titleAndPrice(listing.copy(currencyCode = " "), formatter)

        assertEquals("Nice apartment (Bastos)", text)
        assertFalse(text.contains(" — "))
    }

    @Test
    fun `a blank title uses the id`() {
        assertEquals(
            "Bien n° 101 (Bastos) — 150 000 FCFA / mois",
            labels.titleAndPrice(listing.copy(title = " "), formatter),
        )
    }
}
