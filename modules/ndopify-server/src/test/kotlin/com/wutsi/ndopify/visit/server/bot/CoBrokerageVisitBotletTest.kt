package com.wutsi.ndopify.visit.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
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
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoBrokerageVisitBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()

    // 2026-11-25 10:00 in Douala: the visit is tomorrow
    private val clock = Clock.fixed(Instant.parse("2026-11-25T09:00:00Z"), ZoneId.of("UTC"))
    private val labels = ListingTelegramLabels(locationService)
    private val visitLabels = VisitTelegramLabels(labels)
    private val botlet = CoBrokerageVisitBotlet(tenantService, visitLabels, sender, clock)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
        timeZone = "Africa/Douala",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )

    // B, the listing agent: the recipient
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "Clarisse", lastName = "Mbarga"),
        whatsappNumber = "+237699123456",
    )

    // A, the search agent: the organizer and the voice of the message
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237699000001",
    )

    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        agent = listingAgent,
        cityId = 2370201L,
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
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 11, 26, 10, 30),
        code = "1234",
        note = "Client pressé",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `confirmed - B is told the slot, the property and who organizes it`() {
        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ✅ Visite confirmée : 26/11/2026 à 10:30
            Nice apartment - 150K/mois - Sur place
            Agent : Ray Sponsible
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(
                TelegramButton("Voir la visite", url = "https://partner.ndopify.com/#co-visit-1"),
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001"),
            ),
            buttons.firstValue,
        )
    }

    @Test
    fun `confirmed - a video visit says video`() {
        botlet.sendConfirmed(visit.copy(type = VisitType.VIDEO), listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals(
            "Nice apartment - 150K/mois - Vidéo",
            text.firstValue.lines()[1],
        )
        assertFalse(text.firstValue.contains("Sur place"))
    }

    @Test
    fun `reminder - the day and the time`() {
        botlet.sendReminder(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ⏰ Rappel de visite : demain à 10:30
            Nice apartment - 150K/mois - Sur place
            Agent : Ray Sponsible
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(
                TelegramButton("Voir la visite", url = "https://partner.ndopify.com/#co-visit-1"),
                TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001"),
            ),
            buttons.firstValue,
        )
    }

    @Test
    fun `cancelled - the actor's label, never the cancellation reason`() {
        botlet.sendCancelled(
            visit.copy(cancelledBy = VisitParty.AGENT, cancellationReason = "Mandat clôturé"),
            listing,
            searchAgent,
            1000L,
        )

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ❌ Visite annulée : 26/11/2026 à 10:30
            Nice apartment - 150K/mois - Sur place
            Agent : Ray Sponsible
            Annulée par l'agent
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001")),
            buttons.firstValue,
        )
        assertFalse(text.firstValue.contains("Mandat clôturé"))
    }

    @Test
    fun `cancelled - an organizer without a number has no button`() {
        botlet.sendCancelled(visit, listing, searchAgent.copy(whatsappNumber = null), 1000L)

        verify(sender).send(eq(1000L), eq(listing), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `cancelled - the customer and the system labels`() {
        val text = argumentCaptor<String>()
        botlet.sendCancelled(visit.copy(cancelledBy = VisitParty.CUSTOMER), listing, searchAgent, 1000L)
        botlet.sendCancelled(visit.copy(cancelledBy = VisitParty.SYSTEM), listing, searchAgent, 1000L)

        verify(sender, times(2)).send(eq(1000L), eq(listing), text.capture(), any())
        assertTrue(text.allValues[0].endsWith("Annulée par le client"))
        assertTrue(text.allValues[1].endsWith("Annulée"))
    }

    @Test
    fun `no-show - the customer did not come`() {
        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        botlet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CUSTOMER_ABSENT), listing, searchAgent, 1000L)
        botlet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CODE_REFUSED), listing, searchAgent, 1000L)

        verify(sender, times(2)).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertTrue(text.allValues[0].startsWith("⚠️ Visite non honorée : 26/11/2026 à 10:30"))
        assertTrue(text.allValues[0].endsWith("Le client ne s'est pas présenté"))
        assertTrue(text.allValues[1].endsWith("Le client ne s'est pas présenté"))
        assertEquals(
            listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001")),
            buttons.allValues[0],
        )
        assertEquals(
            listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001")),
            buttons.allValues[1],
        )
    }

    @Test
    fun `a blank title falls back to the usage and the place`() {
        val withoutTitle = listing.copy(title = null)

        botlet.sendConfirmed(visit, withoutTitle, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(withoutTitle), text.capture(), any())
        assertEquals(
            "Bien à louer, Yaounde (Bastos) - 150K/mois - Sur place",
            text.firstValue.lines()[1],
        )
    }

    @Test
    fun `a tenant without Partner Central keeps only the WhatsApp button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        verify(sender).send(
            eq(1000L),
            eq(listing),
            any(),
            eq(listOf(TelegramButton("Contacter sur WhatsApp", url = "https://wa.me/237699000001"))),
        )
    }

    @Test
    fun `an organizer without a number keeps only the visit button`() {
        botlet.sendConfirmed(visit, listing, searchAgent.copy(whatsappNumber = null), 1000L)

        verify(sender).send(
            eq(1000L),
            eq(listing),
            any(),
            eq(listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/#co-visit-1"))),
        )
    }

    @Test
    fun `no Partner Central and no number - no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.sendConfirmed(visit, listing, searchAgent.copy(whatsappNumber = " "), 1000L)

        verify(sender).send(eq(1000L), eq(listing), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `the text never carries the customer nor its data`() {
        val withAddress = listing.copy(landmark = "Rue 1.234", accessDirections = "Derrière la pharmacie")

        botlet.sendConfirmed(visit.copy(feedbackComment = "Trop bruyant"), withAddress, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(withAddress), text.capture(), any())
        assertFalse(text.firstValue.contains("Secret Client"))
        assertFalse(text.firstValue.contains("secret@example.com"))
        assertFalse(text.firstValue.contains("1234"))
        assertFalse(text.firstValue.contains("Client pressé"))
        assertFalse(text.firstValue.contains("Trop bruyant"))
        assertFalse(text.firstValue.contains("Rue 1.234"))
        assertFalse(text.firstValue.contains("Derrière la pharmacie"))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
