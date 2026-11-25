package com.wutsi.ndopify.visit.server.bot

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
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
import kotlin.test.assertNull

class VisitTelegramLabelsTest {
    private val locationService = mock<LocationService>()
    private val labels = ListingTelegramLabels(locationService)
    private val visitLabels = VisitTelegramLabels(labels)

    // 2026-11-26 10:00 in Douala
    private val clock = Clock.fixed(Instant.parse("2026-11-26T09:00:00Z"), ZoneId.of("UTC"))
    private val tenant = TenantEntity(id = 1L, timeZone = "Africa/Douala", currencyCode = "XAF")
    private val formatter = TenantFormatter(tenant)

    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )

    // B, the listing agent of a co-brokerage visit: the recipient of B's messages
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
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 11, 26, 10, 30),
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Jean", lastName = "Dupont"),
        whatsappNumber = "+237600000001",
    )

    @BeforeTest
    fun setUp() {
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `a non co-brokerage visit has two lines and no organizer`() {
        assertNull(visitLabels.organizer(visit, listing))
        assertEquals(
            """
            ⏰ Rappel de visite : 26/11/2026 à 10:30
            Nice apartment - 150K/mois - Sur place
            """.trimIndent(),
            visitLabels.body(
                "⏰ Rappel de visite",
                visitLabels.slot(visit.scheduledAt!!, formatter),
                listing,
                visit,
                visitLabels.organizer(visit, listing),
                formatter,
            ),
        )
    }

    @Test
    fun `a co-brokerage visit names the listing agent on its third line`() {
        val coBrokerage = listing.copy(agent = listingAgent)

        assertEquals(listingAgent, visitLabels.organizer(visit, coBrokerage))
        assertEquals(
            """
            ✅ Visite confirmée : 26/11/2026 à 10:30
            Nice apartment - 150K/mois - Sur place
            Agent : Clarisse Mbarga
            """.trimIndent(),
            visitLabels.body(
                "✅ Visite confirmée",
                visitLabels.slot(visit.scheduledAt!!, formatter),
                coBrokerage,
                visit,
                visitLabels.organizer(visit, coBrokerage),
                formatter,
            ),
        )
    }

    @Test
    fun `the WhatsApp number is the customer's, or the listing agent's on a co-brokerage visit`() {
        assertEquals("+237600000001", visitLabels.whatsappNumber(visit, listing, customer))
        assertEquals("+237699123456", visitLabels.whatsappNumber(visit, listing.copy(agent = listingAgent), customer))
    }

    @Test
    fun `a co-brokerage visit never falls back to the customer's number`() {
        val withoutNumber = listing.copy(agent = listingAgent.copy(whatsappNumber = null))

        assertNull(visitLabels.whatsappNumber(visit, withoutNumber, customer))
    }

    @Test
    fun `a blank number has no WhatsApp link`() {
        assertNull(visitLabels.whatsappNumber(visit, listing, customer.copy(whatsappNumber = " ")))
        assertNull(visitLabels.whatsappNumber(visit, listing, customer.copy(whatsappNumber = null)))
    }

    @Test
    fun `the type reads sur place or video`() {
        assertEquals("Sur place", visitLabels.typeLabel(VisitType.ON_SITE))
        assertEquals("Vidéo", visitLabels.typeLabel(VisitType.VIDEO))
    }

    @Test
    fun `the property line carries the compact price and the type`() {
        assertEquals(
            "Nice apartment - 150K/mois - Vidéo",
            visitLabels.property(visit.copy(type = VisitType.VIDEO), listing, formatter),
        )
    }

    @Test
    fun `the cancellation names its actor`() {
        assertEquals("Annulée par le client", visitLabels.cancelledBy(VisitParty.CUSTOMER))
        assertEquals("Annulée par l'agent", visitLabels.cancelledBy(VisitParty.AGENT))
        assertEquals("Annulée", visitLabels.cancelledBy(VisitParty.SYSTEM))
    }

    @Test
    fun `a date and a time read like a slot`() {
        assertEquals("26/11/2026 à 10:30", visitLabels.slot(visit.scheduledAt!!, formatter))
        assertFalse(visitLabels.slot(visit.scheduledAt!!, formatter).contains("12:30"))
    }

    @Test
    fun `the day word follows the tenant's calendar`() {
        assertEquals(
            "aujourd'hui",
            visitLabels.dayWord(visit.copy(scheduledAt = date(2026, 11, 26, 20, 0)), tenant, clock),
        )
        assertEquals(
            "demain",
            visitLabels.dayWord(visit.copy(scheduledAt = date(2026, 11, 27, 8, 0)), tenant, clock),
        )
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
