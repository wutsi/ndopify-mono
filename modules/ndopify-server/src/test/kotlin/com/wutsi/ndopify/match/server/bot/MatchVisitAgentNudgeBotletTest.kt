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
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchVisitAgentNudgeBotletTest {
    private val tenantService = mock<TenantService>()
    private val client = mock<TelegramClient>()
    private val botlet = MatchVisitAgentNudgeBotlet(tenantService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com",
    )
    private val match = MatchEntity(
        id = 9001L,
        tenantId = 1L,
        mandate = MandateEntity(id = 204L),
        listing = ListingEntity(id = 501L, title = "Nice apartment"),
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
    }

    @Test
    fun send() {
        botlet.send(match, customer, agent, 1000L)

        assertEquals(
            """
            Bonjour Kim Possible,

            Ray Sponsible a accepté votre proposition pour Nice apartment il y a plus de 48 h, mais aucune visite n'est planifiée.

            Appelez-le sur WhatsApp pour convenir d'un créneau, puis planifiez la visite.

            — Ndopify
            """.trimIndent(),
            text(),
        )
        assertEquals(
            listOf(
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001"),
                TelegramButton("Planifier la visite", url = "https://partner.ndopify.com/mandates/204#match-9001"),
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
    fun `no whatsapp number - the visit button only`() {
        botlet.send(match, customer.copy(whatsappNumber = null), agent, 1000L)

        assertEquals(
            listOf(TelegramButton("Planifier la visite", url = "https://partner.ndopify.com/mandates/204#match-9001")),
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
    fun `a listing without a title is named by its id`() {
        botlet.send(match.copy(listing = match.listing.copy(title = "")), customer, agent, 1000L)

        assertTrue(text().contains("pour Bien n° 501 il y a plus de 48 h"))
    }

    private fun text(): String {
        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        return text.firstValue
    }

    private fun buttons(): List<TelegramButton> {
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), any(), buttons.capture())
        return buttons.firstValue
    }
}
