package com.wutsi.ndopify.mandate.server.bot

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MandateTelegramLabelsTest {
    private val locationService = mock<LocationService>()
    private val labels = MandateTelegramLabels(locationService)
    private val formatter = TenantFormatter(TenantEntity(currencyCode = "XAF", monetaryFormat = "#,###,##0 FCFA"))

    @BeforeTest
    fun setUp() {
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
    }

    private val search = MandateEntity(
        id = 1L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L),
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        mandateType = MandateType.SEARCH,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )

    @Test
    fun `a rental search reads its criteria, its neighbourhoods and its compact price`() {
        assertEquals("Location - Appartement (Bastos) - 150K/mois", labels.line(search, formatter))
    }

    @Test
    fun `a sale search reads achat and its compact price`() {
        val sale = search.copy(
            listingType = ListingType.SALE,
            price = 250_000_000,
            pricePeriodicityType = PeriodicityType.NONE,
        )

        assertEquals("Achat - Appartement (Bastos) - 250M", labels.line(sale, formatter))
    }

    @Test
    fun `a sale listing reads vente`() {
        val sale = search.copy(
            listingType = ListingType.SALE,
            mandateType = MandateType.LISTING,
            price = 25_000_000,
            pricePeriodicityType = PeriodicityType.NONE,
        )

        assertEquals("Vente - Appartement (Bastos) - 25M", labels.line(sale, formatter))
    }

    @Test
    fun `no property type - the criteria alone`() {
        assertEquals("Location (Bastos) - 150K/mois", labels.line(search.copy(propertyType = null), formatter))
    }

    @Test
    fun `no neighbourhood - no parenthesis, no dangling place`() {
        val text = labels.line(search.copy(neighborhoodIds = emptyList()), formatter)

        assertEquals("Location - Appartement - 150K/mois", text)
        assertFalse(text.contains("("))
    }

    @Test
    fun `no price - no trailing dash`() {
        val text = labels.line(search.copy(price = -1), formatter)

        assertEquals("Location - Appartement (Bastos)", text)
        assertFalse(text.contains(" - 150"))
    }

    @Test
    fun `a blank currency leaves the price out`() {
        assertEquals("Location - Appartement (Bastos)", labels.line(search.copy(currencyCode = " "), formatter))
    }
}
