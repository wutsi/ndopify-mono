package com.wutsi.ndopify.match.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchProposedMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val mailet = MatchProposedMailet(tenantService, locationService, mailService, mailBodyResolver, linkTokenService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        publicPortalUrl = "https://www.ndopify.com/",
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
        neighborhoodIds = listOf(111L),
    )
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
        url = "/yaounde/bastos/listing/101/nice-apartment",
    )
    private val match = MatchEntity(id = 1L, mandate = mandate, listing = listing, agentId = 20L, status = MatchStatus.PROPOSED)
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"))

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn("<html>proposed</html>").whenever(mailBodyResolver).resolve(any(), any())
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
    }

    @Test
    fun send() {
        mailet.send(listOf(match), customer, agent)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "agent_full_name" to "Kim Possible",
                "count" to null,
                "online" to true,
                "listings" to listOf(
                    mapOf(
                        "listing_title" to "Nice apartment",
                        "price_label" to "Loyer",
                        "price" to "150 000 FCFA / mois",
                        "listing_url" to "https://www.ndopify.com/match/1?token=xxx.yyy.zzz",
                    ),
                ),
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ray Sponsible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Kim Possible vous propose un bien à Bastos (Yaounde)", email.firstValue.subject)
        assertEquals("<html>proposed</html>", email.firstValue.body)
    }

    @Test
    fun `several listings - one e-mail`() {
        val other = match.copy(id = 2L, listing = listing.copy(id = 102L, title = "Big house", price = 200000))

        mailet.send(listOf(match, other), customer, agent)

        val context = context()
        assertEquals(2, context["count"])
        val listings = context["listings"] as List<*>
        assertEquals(
            listOf(
                "https://www.ndopify.com/match/1?token=xxx.yyy.zzz",
                "https://www.ndopify.com/match/2?token=xxx.yyy.zzz",
            ),
            listings.map { (it as Map<*, *>)["listing_url"] },
        )
        verify(linkTokenService).create(customer) // One token for all the links
        assertEquals("200 000 FCFA / mois", (listings[1] as Map<*, *>)["price"])

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Kim Possible vous propose 2 biens à Bastos (Yaounde)", email.firstValue.subject)
    }

    @Test
    fun `no public portal - no link`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(listOf(match), customer, agent)

        assertNull(context()["online"])
        assertNull(listing()["listing_url"])
        verify(linkTokenService, never()).create(any())
    }

    @Test
    fun `no token - the link without it`() {
        doReturn(null).whenever(linkTokenService).create(customer)

        mailet.send(listOf(match), customer, agent)

        assertEquals("https://www.ndopify.com/match/1", listing()["listing_url"])
    }

    @Test
    fun `listing without url - the proposal's link`() {
        mailet.send(listOf(match.copy(listing = listing.copy(url = null))), customer, agent)

        assertEquals("https://www.ndopify.com/match/1?token=xxx.yyy.zzz", listing()["listing_url"])
    }

    @Test
    fun `blank title`() {
        mailet.send(listOf(match.copy(listing = listing.copy(title = ""))), customer, agent)

        assertEquals("Bien n° 101", listing()["listing_title"])
    }

    private fun listing(): Map<*, *> = (context()["listings"] as List<*>).first() as Map<*, *>

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchProposedMailet.TEMPLATE), context.capture())
        return context.firstValue
    }
}
