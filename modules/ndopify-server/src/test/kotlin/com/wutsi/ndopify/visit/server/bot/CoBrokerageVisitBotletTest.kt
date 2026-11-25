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
    private val labels = ListingTelegramLabels(locationService)
    private val botlet = CoBrokerageVisitBotlet(tenantService, labels, sender)

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
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
    )

    // A, the search agent: the organizer and the voice of the message
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    private val listing = ListingEntity(
        id = 101L,
        title = "Villa Bastos",
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
        scheduledAt = date(2026, 12, 2, 10, 0),
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
    fun `confirmed - B is told why, A signs, one listing button`() {
        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ✅ Visite confirmée : Villa Bastos - 150K/mois, 02/12/2026 à 10:00
            Sur place — je vous y retrouve avec mon client.
            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir l'annonce", url = "https://partner.ndopify.com/listings/101#visits-title")),
            buttons.firstValue,
        )
    }

    @Test
    fun `confirmed - a video visit says the customer is remote`() {
        botlet.sendConfirmed(visit.copy(type = VisitType.VIDEO), listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals(
            "Visite vidéo — je serai sur place pour vous ouvrir ; le client suivra en visio.",
            text.firstValue.lines()[1],
        )
        assertFalse(text.firstValue.contains("je vous y retrouve avec mon client"))
    }

    @Test
    fun `reminder - the reminder wording`() {
        botlet.sendReminder(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals(
            """
            ⏰ Rappel : Villa Bastos - 150K/mois, 02/12/2026 à 10:00
            Sur place — je vous y retrouve avec mon client.
            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
    }

    @Test
    fun `cancelled - the actor's label, never the mandate reason`() {
        botlet.sendCancelled(
            visit.copy(cancelledBy = VisitParty.AGENT, cancellationReason = "Mandat clôturé"),
            listing,
            searchAgent,
            1000L,
        )

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals(
            """
            ❌ Visite annulée : Villa Bastos - 150K/mois, 02/12/2026 à 10:00
            J'ai annulé la visite.
            Visite organisée par Kim Possible.
            """.trimIndent(),
            text.firstValue,
        )
        assertFalse(text.firstValue.contains("Mandat clôturé"))
    }

    @Test
    fun `cancelled - the customer and the system labels`() {
        val text = argumentCaptor<String>()
        botlet.sendCancelled(visit.copy(cancelledBy = VisitParty.CUSTOMER), listing, searchAgent, 1000L)
        botlet.sendCancelled(visit.copy(cancelledBy = VisitParty.SYSTEM), listing, searchAgent, 1000L)

        verify(sender, times(2)).send(eq(1000L), eq(listing), text.capture(), any())
        assertTrue(text.allValues[0].lines()[1].contains("Le client a annulé la visite."))
        assertTrue(text.allValues[1].lines()[1].contains("La visite est annulée."))
    }

    @Test
    fun `no-show - both customer-caused labels`() {
        val text = argumentCaptor<String>()
        botlet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CUSTOMER_ABSENT), listing, searchAgent, 1000L)
        botlet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CODE_REFUSED), listing, searchAgent, 1000L)

        verify(sender, times(2)).send(eq(1000L), eq(listing), text.capture(), any())
        assertTrue(
            text.allValues[0].lines().first()
                .startsWith("⚠️ Visite non honorée : Villa Bastos - 150K/mois, "),
        )
        assertTrue(text.allValues[0].lines()[1].contains("Le client ne s'est pas présenté à la visite."))
        assertTrue(text.allValues[1].lines()[1].contains("Le client a refusé de donner le code de visite."))
    }

    @Test
    fun `a blank title falls back to the usage and the place`() {
        val withoutTitle = listing.copy(title = null)

        botlet.sendConfirmed(visit, withoutTitle, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(withoutTitle), text.capture(), any())
        assertTrue(text.firstValue.startsWith("✅ Visite confirmée : Bien à louer, Yaounde (Bastos) - 150K/mois, "))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        verify(sender).send(eq(1000L), eq(listing), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `the text never carries the customer nor its data`() {
        botlet.sendConfirmed(visit, listing, searchAgent, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertFalse(text.firstValue.contains("Secret Client"))
        assertFalse(text.firstValue.contains("secret@example.com"))
        assertFalse(text.firstValue.contains("1234"))
        assertFalse(text.firstValue.contains("Client pressé"))
        assertTrue(text.firstValue.contains("mon client"))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
