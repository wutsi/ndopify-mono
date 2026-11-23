package com.wutsi.ndopify.match.server.bot

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
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
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

class MatchInterestBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()
    private val labels = ListingTelegramLabels(locationService)
    private val botlet = MatchInterestBotlet(tenantService, labels, sender)

    private val tenant = TenantEntity(
        id = 1L,
        partnerCentralUrl = "https://partner.ndopify.com/",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )
    private val mandate = MandateEntity(id = 5L, tenantId = 1L)
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
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `the interest opens with the event and the property line`() {
        botlet.send(agent, customer, mandate, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            "👀 Ray Sponsible s'intéresse à : Nice apartment (Bastos) — 150 000 FCFA / mois",
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/5")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a sale listing's line names its type and its price`() {
        val sale = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)

        botlet.send(agent, customer, mandate.copy(listingType = ListingType.SALE), sale, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(sale), text.capture(), any())
        assertEquals(
            "👀 Ray Sponsible s'intéresse à : Vente — Nice apartment (Bastos) — Prix : 150 000 FCFA",
            text.firstValue,
        )
        listOf("location", "loyer", "logement").forEach { word ->
            assertFalse(text.firstValue.lowercase().contains(word), "no '$word' in a sale message")
        }
    }

    @Test
    fun `a listing without a title is named by its id`() {
        val withoutTitle = listing.copy(title = null)

        botlet.send(agent, customer, mandate, withoutTitle, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(withoutTitle), text.capture(), any())
        assertEquals(
            "👀 Ray Sponsible s'intéresse à : Bien n° 101 (Bastos) — 150 000 FCFA / mois",
            text.firstValue,
        )
    }

    @Test
    fun `a customer with a WhatsApp number gets a contact button`() {
        botlet.send(agent, customer.copy(whatsappNumber = "+237600000001"), mandate, listing, 1000L)

        verify(sender).send(
            eq(1000L),
            eq(listing),
            any(),
            eq(
                listOf(
                    TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
                    TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/5"),
                ),
            ),
        )
    }

    @Test
    fun `no WhatsApp number, no WhatsApp button`() {
        botlet.send(agent, customer, mandate, listing, 1000L)

        verify(sender).send(
            eq(1000L),
            eq(listing),
            any(),
            eq(listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/5"))),
        )
    }

    @Test
    fun `no partner central, only the WhatsApp button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(agent, customer.copy(whatsappNumber = "+237600000001"), mandate, listing, 1000L)

        verify(sender).send(
            eq(1000L),
            eq(listing),
            any(),
            eq(listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"))),
        )
    }
}
