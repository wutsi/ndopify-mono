package com.wutsi.ndopify.visit.server.bot

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
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VisitCancelledBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()
    private val labels = ListingTelegramLabels(locationService)
    private val visitLabels = VisitTelegramLabels(labels)
    private val botlet = VisitCancelledBotlet(tenantService, visitLabels, sender)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )

    // B, the listing agent: the co-agent of a co-brokerage visit
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "Clarisse", lastName = "Mbarga"),
        whatsappNumber = "+237699123456",
    )
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        agent = agent,
        cityId = 2370201L,
        neighborhoodId = 1001L,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CANCELLED,
        type = VisitType.ON_SITE,
        requestedAt = date(2026, 11, 28, 9, 0),
        scheduledAt = date(2026, 12, 2, 10, 0),
        cancelledBy = VisitParty.CUSTOMER,
        cancellationReason = "Empêchement",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `a customer cancellation carries the slot, the property and the reason`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ❌ Visite annulée : 02/12/2026 à 10:00
            Nice apartment - 150K/mois - Sur place
            Annulée par le client · Motif : Empêchement
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(
                TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1"),
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001"),
            ),
            buttons.firstValue,
        )
    }

    @Test
    fun `a co-brokerage cancellation names the listing agent and contacts it`() {
        val coBrokerage = listing.copy(agent = listingAgent)

        botlet.send(visit.copy(cancelledBy = VisitParty.AGENT), customer, agent, coBrokerage, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(coBrokerage), text.capture(), buttons.capture())
        assertEquals(
            """
            ❌ Visite annulée : 02/12/2026 à 10:00
            Nice apartment - 150K/mois - Sur place
            Agent : Clarisse Mbarga
            Annulée par l'agent · Motif : Empêchement
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(
                TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1"),
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699123456"),
            ),
            buttons.firstValue,
        )
    }

    @Test
    fun `no reason - the actor alone`() {
        botlet.send(visit.copy(cancellationReason = " "), customer, agent, listing, 1000L)

        assertEquals(
            """
            ❌ Visite annulée : 02/12/2026 à 10:00
            Nice apartment - 150K/mois - Sur place
            Annulée par le client
            """.trimIndent(),
            text(listing),
        )
    }

    @Test
    fun `no neighbourhood - no empty parenthesis`() {
        botlet.send(visit, customer, agent, listing.copy(neighborhoodId = null), 1000L)

        assertFalse(text(listing.copy(neighborhoodId = null)).contains("("))
    }

    @Test
    fun `a blank title falls back to the usage and the place`() {
        val withoutTitle = listing.copy(title = null)

        botlet.send(visit, customer, agent, withoutTitle, 1000L)

        assertEquals(
            "Bien à louer, Yaounde (Bastos) - 150K/mois - Sur place",
            text(withoutTitle).lines()[1],
        )
    }

    @Test
    fun `a never-scheduled visit shows its requested slot`() {
        botlet.send(visit.copy(scheduledAt = null), customer, agent, listing, 1000L)

        assertEquals(
            """
            ❌ Visite annulée : 28/11/2026 à 09:00
            Nice apartment - 150K/mois - Sur place
            Annulée par le client · Motif : Empêchement
            """.trimIndent(),
            text(listing),
        )
    }

    @Test
    fun `a tenant without Partner Central keeps only the WhatsApp button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), any(), buttons.capture())
        assertEquals(
            listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237600000001")),
            buttons.firstValue,
        )
    }

    private fun text(listing: ListingEntity): String {
        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        return text.firstValue
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
