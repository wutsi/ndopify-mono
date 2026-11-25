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
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitInterest
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

class VisitFeedbackBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()
    private val labels = ListingTelegramLabels(locationService)
    private val visitLabels = VisitTelegramLabels(labels)
    private val botlet = VisitFeedbackBotlet(tenantService, visitLabels, sender)

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
        party = PartyEntity(firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

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
        neighborhoodId = 1001L,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.COMPLETED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 1, 10, 0),
        interest = VisitInterest.NOT_INTERESTED,
        feedbackReasonCode = MatchRejectionReason.PRICE,
        feedbackComment = "Trop cher pour moi",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `a not-interested answer carries the reason and the comment`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ⭐ Avis de visite : 01/12/2026 à 10:00
            Nice apartment - 150K/mois - Sur place
            Pas intéressé · Raison : Trop cher
            Commentaire : « Trop cher pour moi »
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
    fun `a co-brokerage feedback names the listing agent and contacts it`() {
        val coBrokerage = listing.copy(agent = listingAgent)

        botlet.send(visit, customer, agent, coBrokerage, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(coBrokerage), text.capture(), buttons.capture())
        assertEquals(
            """
            ⭐ Avis de visite : 01/12/2026 à 10:00
            Nice apartment - 150K/mois - Sur place
            Agent : Clarisse Mbarga
            Pas intéressé · Raison : Trop cher
            Commentaire : « Trop cher pour moi »
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
    fun `an interested answer carries no reason and no comment`() {
        botlet.send(
            visit.copy(interest = VisitInterest.INTERESTED, feedbackReasonCode = null, feedbackComment = null),
            customer, agent, listing, 1000L,
        )

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals(
            "Intéressé",
            text.firstValue.lines()[2],
        )
        assertFalse(text.firstValue.contains("Raison"))
        assertFalse(text.firstValue.contains("Commentaire"))
    }

    @Test
    fun `a blank title falls back to the usage and the neighbourhood`() {
        botlet.send(visit, customer, agent, listing.copy(title = null), 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing.copy(title = null)), text.capture(), any())
        assertEquals(
            "Bien à louer (Bastos) - 150K/mois - Sur place",
            text.firstValue.lines()[1],
        )
    }

    @Test
    fun `an unknown reason leaves no Raison`() {
        botlet.send(visit.copy(feedbackReasonCode = MatchRejectionReason.UNKNOWN), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals("Pas intéressé", text.firstValue.lines()[2])
        assertFalse(text.firstValue.contains("Raison"))
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

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Africa/Douala")).toInstant())
}
