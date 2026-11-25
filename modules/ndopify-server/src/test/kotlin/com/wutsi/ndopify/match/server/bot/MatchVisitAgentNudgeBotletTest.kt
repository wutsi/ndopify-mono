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
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.server.domain.MatchEntity
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
import kotlin.test.assertTrue

class MatchVisitAgentNudgeBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()
    private val labels = ListingTelegramLabels(locationService)
    private val botlet = MatchVisitAgentNudgeBotlet(tenantService, labels, sender)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val match = MatchEntity(
        id = 9001L,
        tenantId = 1L,
        mandate = MandateEntity(id = 204L),
        listing = ListingEntity(
            id = 501L,
            title = "Nice apartment",
            neighborhoodId = 1001L,
            price = 150000,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        ),
        agentId = 20L,
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237 6 99 00 00 01",
    )
    private val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"))

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `the nudge names the customer, the property and the two next steps`() {
        botlet.send(match, customer, agent, 1000L)

        assertEquals(
            """
            ⏰ Appelez Ray Sponsible pour planifier la visite
            Nice apartment - 150K/mois
            Proposition acceptée il y a plus de 48 h.
            """.trimIndent(),
            text(),
        )
        assertEquals(
            listOf(
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001"),
                TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/204#match-9001"),
            ),
            buttons(),
        )
    }

    @Test
    fun `no partner central - the WhatsApp button only`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(match, customer, agent, 1000L)

        assertEquals(
            listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001")),
            buttons(),
        )
    }

    @Test
    fun `no whatsapp number - the mandate button only`() {
        botlet.send(match, customer.copy(whatsappNumber = null), agent, 1000L)

        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/204#match-9001")),
            buttons(),
        )
    }

    @Test
    fun `no button at all`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(match, customer.copy(whatsappNumber = " "), agent, 1000L)

        assertTrue(buttons().isEmpty())
    }

    @Test
    fun `a blank title falls back to the usage and the neighbourhood`() {
        val withoutTitle = match.copy(listing = match.listing.copy(title = ""))

        botlet.send(withoutTitle, customer, agent, 1000L)

        assertEquals(
            "Bien à louer (Bastos) - 150K/mois",
            text(withoutTitle.listing).lines()[1],
        )
    }

    private fun text(listing: ListingEntity = match.listing): String {
        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        return text.firstValue
    }

    private fun buttons(listing: ListingEntity = match.listing): List<TelegramButton> {
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), any(), buttons.capture())
        return buttons.firstValue
    }
}
