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
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.bot.ListingTelegramLabels
import com.wutsi.ndopify.listing.server.bot.ListingTelegramSender
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MatchAnsweredBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val sender = mock<ListingTelegramSender>()
    private val labels = ListingTelegramLabels(locationService)
    private val botlet = MatchAnsweredBotlet(tenantService, labels, sender)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighborhoodIds = listOf(1001L),
        propertyType = PropertyType.APARTMENT,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val listing = ListingEntity(
        id = 102L,
        title = "Small apartment",
        neighborhoodId = 1001L,
        price = 140000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(
        id = 2L,
        tenantId = 1L,
        mandate = mandate,
        listing = listing,
        agentId = 20L,
        status = MatchStatus.ACCEPTED,
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
    }

    @Test
    fun `an acceptance is one line naming the customer and the property`() {
        botlet.send(match, customer, agent, 1000L)

        val message = text()
        assertEquals(
            "✅ Ray Sponsible a accepté la proposition : Small apartment - 140K/mois",
            message,
        )
        assertFalse(message.contains("Prix :"), "a rental's line keeps its bare amount")
        assertEquals(
            listOf(TelegramButton("Voir le mandat", url = "https://partner.ndopify.com/mandates/1")),
            buttons(),
        )
    }

    @Test
    fun `a sale acceptance names the purchase and its price`() {
        val saleListing = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)
        val sale = match.copy(listing = saleListing, mandate = mandate.copy(listingType = ListingType.SALE))

        botlet.send(sale, customer, agent, 1000L)

        val message = text(saleListing)
        assertEquals(
            "✅ Ray Sponsible a accepté la proposition : Achat — Small apartment - 140K",
            message,
        )
        listOf("location", "loyer", "logement").forEach { word ->
            assertFalse(message.lowercase().contains(word), "no '$word' in a sale message")
        }
    }

    @Test
    fun `a sale rejection names the purchase and its price`() {
        val saleListing = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)
        val sale = match.copy(
            listing = saleListing,
            mandate = mandate.copy(listingType = ListingType.SALE),
            status = MatchStatus.REJECTED,
            rejectionReasonCode = MatchRejectionReason.PRICE,
        )

        botlet.send(sale, customer, agent, 1000L)

        assertEquals(
            """
            ❌ Ray Sponsible a refusé la proposition : Achat — Small apartment - 140K
            Motif : trop cher
            """.trimIndent(),
            text(saleListing),
        )
    }

    @Test
    fun `a rejection names its reason and comment`() {
        botlet.send(
            match.copy(
                status = MatchStatus.REJECTED,
                rejectionReasonCode = MatchRejectionReason.PRICE,
                rejectionReason = "Trop loin du travail.",
            ),
            customer,
            agent,
            1000L,
        )

        assertEquals(
            """
            ❌ Ray Sponsible a refusé la proposition : Small apartment - 140K/mois
            Motif : trop cher · « Trop loin du travail. »
            """.trimIndent(),
            text(),
        )
    }

    @Test
    fun `a rejection without a comment leaves no comment line`() {
        botlet.send(
            match.copy(
                status = MatchStatus.REJECTED,
                rejectionReasonCode = MatchRejectionReason.OTHER,
                rejectionReason = " ",
            ),
            customer,
            agent,
            1000L,
        )

        assertEquals(
            """
            ❌ Ray Sponsible a refusé la proposition : Small apartment - 140K/mois
            Motif : autre raison
            """.trimIndent(),
            text(),
        )
        assertFalse(text().contains("Commentaire"))
    }

    @Test
    fun `a blank title falls back to the usage and the neighbourhood`() {
        val withoutTitle = match.copy(listing = listing.copy(title = null))

        botlet.send(withoutTitle, customer, agent, 1000L)

        assertEquals(
            "✅ Ray Sponsible a accepté la proposition : Bien à louer (Bastos) - 140K/mois",
            text(withoutTitle.listing),
        )
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(match, customer, agent, 1000L)

        assertEquals(emptyList<TelegramButton>(), buttons())
    }

    private fun text(listing: ListingEntity = match.listing): String {
        val text = argumentCaptor<String>()
        verify(sender).send(eq(1000L), eq(listing), text.capture(), any())
        return text.firstValue
    }

    private fun buttons(listing: ListingEntity = match.listing): List<TelegramButton> {
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(sender).send(eq(1000L), eq(listing), any(), buttons.capture())
        return buttons.firstValue
    }
}
