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
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
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
import kotlin.test.assertTrue

class VisitFeedbackBotletTest {
    private val tenantService = mock<TenantService>()
    private val sender = mock<ListingTelegramSender>()
    private val botlet = VisitFeedbackBotlet(tenantService, sender)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
    )
    private val customer = CustomerEntity(id = 10L, party = PartyEntity(firstName = "Ray", lastName = "Sponsible"))
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L, title = "Nice apartment")
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
    }

    @Test
    fun `a not-interested answer carries the reason and the comment`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            L'avis de Ray Sponsible sur la visite

            Bien: Nice apartment
            Date: 01/12/2026 à 10:00
            Client: Ray Sponsible
            Avis: Pas intéressé
            Raison: trop cher
            Commentaire: Trop cher pour moi
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
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
        assertTrue(text.firstValue.contains("Avis: Intéressé"))
        assertFalse(text.firstValue.contains("Raison:"))
        assertFalse(text.firstValue.contains("Commentaire:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(visit, customer, agent, listing.copy(title = null), 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing.copy(title = null)), text.capture(), any())
        assertTrue(text.firstValue.contains("Bien: Bien n° 101"))
    }

    @Test
    fun `an unknown reason leaves no Raison line`() {
        botlet.send(visit.copy(feedbackReasonCode = MatchRejectionReason.UNKNOWN), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertFalse(text.firstValue.contains("Raison:"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(sender).send(eq(1000L), eq(listing), any(), eq(emptyList<TelegramButton>()))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Africa/Douala")).toInstant())
}
