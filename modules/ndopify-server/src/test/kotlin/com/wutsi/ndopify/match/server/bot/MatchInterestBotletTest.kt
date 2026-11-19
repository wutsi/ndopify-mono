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
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatchInterestBotletTest {
    private val tenantService = mock<TenantService>()
    private val client = mock<TelegramClient>()
    private val botlet = MatchInterestBotlet(tenantService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
    )
    private val mandate = MandateEntity(id = 1L, tenantId = 1L, listingType = ListingType.RENTAL)
    private val listing = ListingEntity(id = 102L, title = "Small apartment")
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
    }

    @Test
    fun `tells the agent to contact the customer, with WhatsApp and the mandate`() {
        botlet.send(agent, customer, mandate, listing, 99L)

        val captor = argumentCaptor<String>()
        verify(client).sendMessage(
            eq(99L),
            captor.capture(),
            eq(
                listOf(
                    TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
                    TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1"),
                ),
            ),
        )
        assertTrue(captor.firstValue.contains("Contactez-le pour organiser la visite."))
        assertTrue(captor.firstValue.contains("Ray Sponsible"))
        assertTrue(captor.firstValue.contains("Kim Possible"))
        assertTrue(captor.firstValue.contains("Small apartment"))
    }

    @Test
    fun `no WhatsApp number, no WhatsApp button`() {
        botlet.send(agent, customer.copy(whatsappNumber = null), mandate, listing, 99L)

        verify(client).sendMessage(
            eq(99L),
            any(),
            eq(listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1"))),
        )
    }

    @Test
    fun `no partner central, only the WhatsApp button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(agent, customer, mandate, listing, 99L)

        verify(client).sendMessage(
            eq(99L),
            any(),
            eq(listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"))),
        )
    }
}
