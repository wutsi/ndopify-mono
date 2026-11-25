package com.wutsi.ndopify.mandate.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MandateApprovedBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val labels = MandateTelegramLabels(locationService)
    private val botlet = MandateApprovedBotlet(tenantService, labels, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com",
        timeZone = "Africa/Douala",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
    }

    @Test
    fun `a search approval carries its criteria and the two buttons`() {
        val mandate = MandateEntity(
            id = 1L,
            tenantId = 1L,
            customerId = 10L,
            agentId = 20L,
            contractId = "c-1",
            status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH,
            cityId = 2370201L,
            neighborhoodIds = listOf(111L, 222L),
            listingType = ListingType.RENTAL,
            propertyType = PropertyType.APARTMENT,
            price = 1500000,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, customer, agent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            ✅ Le client a signé le mandat.
            Recherche : Location - Appartement (Bastos, Mvan) - 1,5M/mois
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
                TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1"),
            ),
            buttons.firstValue,
        )

        // Summary only: the page has the customer, the contract and its dates
        assertFalse(text.firstValue.contains("Sponsible"))
        assertFalse(text.firstValue.contains("MNDT-"))
        assertFalse(text.firstValue.contains("15/10/2026"))
        assertFalse(text.firstValue.contains("Yaounde"), "the city leaves the first line")
    }

    @Test
    fun `a listing approval carries its price and no search wording`() {
        val mandate = MandateEntity(
            id = 2L,
            tenantId = 1L,
            agentId = 20L,
            status = MandateStatus.APPROVED,
            mandateType = MandateType.LISTING,
            cityId = 2370201L,
            neighborhoodIds = listOf(111L),
            listingType = ListingType.SALE,
            propertyType = PropertyType.VILLA,
            price = 25_000_000,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.UNKNOWN,
        )

        botlet.send(mandate, customer, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            ✅ Le client a signé le mandat.
            Bien : Vente - Villa (Bastos) - 25M
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `a sale search approval names its budget and never a rental`() {
        val mandate = MandateEntity(
            id = 3L,
            tenantId = 1L,
            agentId = 20L,
            status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH,
            cityId = 2370201L,
            neighborhoodIds = listOf(111L, 222L),
            listingType = ListingType.SALE,
            propertyType = PropertyType.HOUSE,
            price = 250_000_000,
            currencyCode = "XAF",
        )

        botlet.send(mandate, customer, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            ✅ Le client a signé le mandat.
            Recherche : Achat - Maison (Bastos, Mvan) - 250M
            """.trimIndent(),
            text.firstValue,
        )
        listOf("location", "loyer", "logement").forEach { word ->
            assertFalse(text.firstValue.lowercase().contains(word), "no '$word' in a sale message")
        }
    }

    @Test
    fun `a customer without a WhatsApp number gets the mandate button only`() {
        val mandate = MandateEntity(
            id = 1L, tenantId = 1L, agentId = 20L, status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH, cityId = 2370201L, listingType = ListingType.RENTAL,
            price = 1500000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, customer.copy(whatsappNumber = null), agent, 1000L)

        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), any(), buttons.capture())
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a tenant without a Partner Central URL keeps only the WhatsApp button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)
        val mandate = MandateEntity(
            id = 1L, tenantId = 1L, agentId = 20L, status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH, cityId = 2370201L, listingType = ListingType.RENTAL,
            price = 1500000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, customer, agent, 1000L)

        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), any(), buttons.capture())
        assertEquals(
            listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a mandate without neighbourhoods or a property type leaves no dangling place`() {
        val mandate = MandateEntity(
            id = 1L,
            tenantId = 1L,
            agentId = 20L,
            status = MandateStatus.APPROVED,
            mandateType = MandateType.SEARCH,
            cityId = -1L,
            listingType = ListingType.RENTAL,
            price = 1500000,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        )

        botlet.send(mandate, customer, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            ✅ Le client a signé le mandat.
            Recherche : Location - 1,5M/mois
            """.trimIndent(),
            text.firstValue,
        )
    }
}
