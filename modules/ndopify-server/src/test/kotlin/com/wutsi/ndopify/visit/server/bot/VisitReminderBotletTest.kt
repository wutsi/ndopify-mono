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
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
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

class VisitReminderBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()

    // 2026-12-01 10:00 in Douala
    private val clock = Clock.fixed(Instant.parse("2026-12-01T09:00:00Z"), ZoneId.of("UTC"))
    private val botlet = VisitReminderBotlet(tenantService, locationService, client, clock)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        timeZone = "Africa/Douala",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
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
    private val listing = ListingEntity(id = 101L, title = "Nice apartment", cityId = 2370201L, neighborhoodId = 1001L)
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 2, 10, 0),
        code = "1234",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `an on-site reminder carries the slot, the place and the customer's WhatsApp`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour Kim Possible,

            Petit rappel : la visite du bien Nice apartment avec Ray Sponsible a lieu demain, 02/12/2026 à 10:00.

            Type: Sur place
            Lieu: Bastos, Yaounde
            WhatsApp: +237 6 00 00 00 01
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a video reminder carries the WhatsApp call note`() {
        botlet.send(visit.copy(type = VisitType.VIDEO), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertEquals(
            """
            Bonjour Kim Possible,

            Petit rappel : la visite du bien Nice apartment avec Ray Sponsible a lieu demain, 02/12/2026 à 10:00.

            Type: Visite vidéo
            Lieu: Bastos, Yaounde
            WhatsApp: +237 6 00 00 00 01

            Depuis le bien, appelez Ray Sponsible en vidéo sur WhatsApp. Le client vous lira son code de visite pendant l'appel.
            """.trimIndent(),
            text.firstValue,
        )
        assertFalse(text.firstValue.contains("Type: Sur place"))
    }

    @Test
    fun `a visit later today says aujourd'hui`() {
        botlet.send(visit.copy(scheduledAt = date(2026, 12, 1, 20, 0)), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("a lieu aujourd'hui, 01/12/2026 à 20:00"))
    }

    @Test
    fun `the day follows the tenant's calendar, not UTC's`() {
        // 2026-12-02 00:30 in Douala: the visit is later that tenant day, though still 2026-12-01 in UTC
        val midnight = VisitReminderBotlet(
            tenantService,
            locationService,
            client,
            Clock.fixed(Instant.parse("2026-12-01T23:30:00Z"), ZoneId.of("UTC")),
        )

        midnight.send(visit.copy(scheduledAt = date(2026, 12, 2, 8, 0)), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("a lieu aujourd'hui, 02/12/2026 à 08:00"))
    }

    @Test
    fun `no WhatsApp number leaves no line`() {
        botlet.send(visit, customer.copy(whatsappNumber = null), agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("WhatsApp:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(visit, customer, agent, listing.copy(title = " "), 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("la visite du bien Bien n° 101 avec Ray Sponsible"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    @Test
    fun `no visit code and no exact address in the text`() {
        botlet.send(
            visit,
            customer,
            agent,
            listing.copy(landmark = "Rue 1.234", accessDirections = "Derrière la pharmacie"),
            1000L,
        )

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("1234"))
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
