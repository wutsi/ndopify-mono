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
import kotlin.test.assertTrue

class VisitCancelledBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()
    private val labels = ListingTelegramLabels(locationService)
    private val botlet = VisitCancelledBotlet(tenantService, labels, sender)

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
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        cityId = 2370201,
        neighborhoodId = 1001,
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
        scheduledAt = date(2026, 12, 2, 10, 0),
        cancelledBy = VisitParty.CUSTOMER,
        cancellationReason = "Empêchement",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201)
        doReturn(LocationEntity(id = 1001, name = "Bastos")).whenever(locationService).findByIdOrNull(1001)
    }

    @Test
    fun `a customer cancellation carries the slot, the listing, the customer and the reason`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), buttons.capture())
        assertEquals(
            """
            ❌ Ray Sponsible a annulé la visite : Nice apartment - 150K/mois
            02/12/2026 à 10:00 · Motif : Empêchement
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `no reason - the slot alone`() {
        botlet.send(
            visit.copy(cancellationReason = " "),
            customer,
            agent,
            listing,
            1000L,
        )

        assertEquals(
            """
            ❌ Ray Sponsible a annulé la visite : Nice apartment - 150K/mois
            02/12/2026 à 10:00
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
            "❌ Ray Sponsible a annulé la visite : Bien à louer, Yaounde (Bastos) - 150K/mois",
            text(withoutTitle).lines().first(),
        )
    }

    @Test
    fun `a never-scheduled visit keeps only its reason`() {
        botlet.send(visit.copy(scheduledAt = null), customer, agent, listing, 1000L)

        assertEquals(
            """
            ❌ Ray Sponsible a annulé la visite : Nice apartment - 150K/mois
            Motif : Empêchement
            """.trimIndent(),
            text(listing),
        )
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(sender).send(eq(1000L), eq(listing), any(), eq(emptyList<TelegramButton>()))
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
