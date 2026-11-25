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
    private val sender = mock<ListingTelegramSender>()

    // 2026-12-01 10:00 in Douala
    private val clock = Clock.fixed(Instant.parse("2026-12-01T09:00:00Z"), ZoneId.of("UTC"))
    private val labels = ListingTelegramLabels(locationService)
    private val visitLabels = VisitTelegramLabels(labels)
    private val botlet = VisitReminderBotlet(tenantService, visitLabels, sender, clock)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        timeZone = "Africa/Douala",
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
    fun `an on-site reminder carries the day, the property and a WhatsApp button`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ⏰ Rappel de visite : demain à 10:00
            Nice apartment - 150K/mois - Sur place
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
    fun `a video reminder says so and carries no call note`() {
        botlet.send(visit.copy(type = VisitType.VIDEO), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertEquals(
            """
            ⏰ Rappel de visite : demain à 10:00
            Nice apartment - 150K/mois - Vidéo
            """.trimIndent(),
            text.firstValue,
        )
        assertFalse(text.firstValue.contains("code de visite"))
    }

    @Test
    fun `a co-brokerage reminder names the listing agent and contacts it`() {
        val coBrokerage = listing.copy(agent = listingAgent)

        botlet.send(visit, customer, agent, coBrokerage, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(coBrokerage), text.capture(), buttons.capture())
        assertEquals(
            """
            ⏰ Rappel de visite : demain à 10:00
            Nice apartment - 150K/mois - Sur place
            Agent : Clarisse Mbarga
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
    fun `a visit later today says aujourd'hui`() {
        botlet.send(visit.copy(scheduledAt = date(2026, 12, 1, 20, 0)), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertTrue(text.firstValue.startsWith("⏰ Rappel de visite : aujourd'hui à 20:00"))
    }

    @Test
    fun `the day follows the tenant's calendar, not UTC's`() {
        // 2026-12-02 00:30 in Douala: the visit is later that tenant day, though still 2026-12-01 in UTC
        val midnight = VisitReminderBotlet(
            tenantService,
            visitLabels,
            sender,
            Clock.fixed(Instant.parse("2026-12-01T23:30:00Z"), ZoneId.of("UTC")),
        )

        midnight.send(visit.copy(scheduledAt = date(2026, 12, 2, 8, 0)), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        assertTrue(text.firstValue.startsWith("⏰ Rappel de visite : aujourd'hui à 08:00"))
    }

    @Test
    fun `no whatsapp number - the visit button only`() {
        botlet.send(visit, customer.copy(whatsappNumber = null), agent, listing, 1000L)

        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), any(), buttons.capture())
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a blank title falls back to the usage and the place`() {
        botlet.send(visit, customer, agent, listing.copy(title = " "), 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing.copy(title = " ")), text.capture(), any())
        assertEquals("Bien à louer, Yaounde (Bastos) - 150K/mois - Sur place", text.firstValue.lines()[1])
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

    @Test
    fun `no visit code and no exact address in the text`() {
        val withAddress = listing.copy(landmark = "Rue 1.234", accessDirections = "Derrière la pharmacie")

        botlet.send(visit, customer, agent, withAddress, 1000L)

        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(withAddress), text.capture(), any())
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
