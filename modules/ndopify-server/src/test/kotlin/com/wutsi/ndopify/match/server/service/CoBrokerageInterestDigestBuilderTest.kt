package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CoBrokerageInterestDigestBuilderTest {
    private val tenantService = mock<TenantService>()
    private val agentService = mock<AgentService>()
    private val locationService = mock<LocationService>()
    private val imageService = mock<ListingImageService>()
    private val builder = CoBrokerageInterestDigestBuilder(tenantService, agentService, locationService, imageService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://pc.ndopify.com",
        countryCode = "CM",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )

    // The search's customer: the digest must never read it (anti-sautage)
    private val customer = PartyEntity(
        id = 100L,
        firstName = "Secret",
        lastName = "Client",
        email = "secret@example.com",
    )
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
        whatsappNumber = "+237677889900",
    )
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
        whatsappNumber = "+237699000001",
    )
    private val search = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = customer.id!!,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L),
        rooms = "2",
        price = 100000L,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val listingMandate = MandateEntity(
        id = 11L,
        tenantId = 1L,
        customerId = 11L,
        agentId = 30L,
        mandateType = MandateType.LISTING,
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
    )
    private val listing = ListingEntity(
        id = 101L,
        tenantId = 1L,
        mandate = listingMandate,
        agent = listingAgent,
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        cityId = 2370201L,
        neighborhoodId = 111L,
        title = "Villa Bastos",
        price = 90000L,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(
        id = 1L,
        mandate = search,
        listing = listing,
        agentId = 20L,
        coBrokerage = true,
        status = MatchStatus.SHORTLISTED,
        score = 0.8,
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(searchAgent).whenever(agentService).findByIdOrNull(20L)
    }

    @Test
    fun `one listing - header, row and contact`() {
        val digest = builder.build(listOf(match))

        assertEquals(1, digest.agentCount)
        val interest = digest.listings.single()
        assertEquals(101L, interest.listingId)
        assertEquals("Villa Bastos", interest.title)
        assertEquals("90 000 FCFA / mois", interest.price)
        assertEquals("Bastos (Yaounde)", interest.place)
        assertEquals("https://pc.ndopify.com/mandates/11", interest.mandateUrl)
        assertNull(interest.imageUrl)
        assertNull(interest.more)
        val agent = interest.agents.single()
        assertEquals("Kim Possible", agent.fullName)
        assertEquals("+237 6 99 00 00 01", agent.whatsapp)
        assertEquals("https://wa.me/237699000001", agent.whatsappUrl)
        assertNull(agent.email)
        assertNull(agent.emailUrl)
        assertEquals("Location", agent.listingType)
        assertEquals("Appartement", agent.propertyType)
        assertEquals("Bastos (Yaounde)", agent.place)
        assertEquals("2 pièces", agent.roomsLabel)
        assertEquals("100 000 FCFA / mois", agent.budget)
        assertNull(agent.searchCount)
    }

    @Test
    fun `listings best score first, agents best score first`() {
        val otherListing = listing.copy(
            id = 102L,
            mandate = listingMandate.copy(id = 12L),
            title = "Studio Mvan",
            price = 60000L,
        )
        val agent21 = searchAgent.copy(
            id = 21L,
            party = PartyEntity(id = 210L, firstName = "Sam", lastName = "Sung", email = "sam@example.com"),
        )
        doReturn(agent21).whenever(agentService).findByIdOrNull(21L)

        val digest = builder.build(
            listOf(
                match.copy(id = 1L, listing = otherListing, score = 0.4),
                match.copy(id = 2L, listing = listing, score = 0.9),
                match.copy(id = 3L, mandate = search.copy(id = 2L, agentId = 21L), score = 0.5),
                match.copy(id = 4L, mandate = search.copy(id = 3L), score = 0.85),
            ),
        )

        assertEquals(listOf("Villa Bastos", "Studio Mvan"), digest.listings.map { it.title })
        assertEquals(listOf("Kim Possible", "Sam Sung"), digest.listings.first().agents.map { it.fullName })
    }

    @Test
    fun `more than 5 agents - 5 rows and the count`() {
        val interested = (20L..25L).map { id ->
            AgentEntity(id = id, party = PartyEntity(id = id * 10, firstName = "Agent", lastName = "$id"))
        }
        interested.forEach { agent ->
            doReturn(agent).whenever(agentService).findByIdOrNull(agent.id!!)
        }
        val matches = interested.mapIndexed { i, agent ->
            match.copy(
                id = i + 1L,
                mandate = search.copy(id = i + 1L, agentId = agent.id),
                score = 0.5 + i * 0.01,
            )
        }

        val digest = builder.build(matches)

        val interest = digest.listings.single()
        assertEquals(5, interest.agents.size)
        assertEquals(1, interest.more)
        assertEquals(6, digest.agentCount)
    }

    @Test
    fun `several searches of one agent - one row with the count`() {
        val other = match.copy(id = 2L, mandate = search.copy(id = 2L, price = 200000), score = 0.9)

        val agent = builder.build(listOf(match, other)).listings.single().agents.single()

        assertEquals(2, agent.searchCount)
        assertEquals("200 000 FCFA / mois", agent.budget)
    }

    @Test
    fun `no WhatsApp - the email is the contact`() {
        doReturn(searchAgent.copy(whatsappNumber = null)).whenever(agentService).findByIdOrNull(20L)

        val agent = builder.build(listOf(match)).listings.single().agents.single()

        assertNull(agent.whatsapp)
        assertNull(agent.whatsappUrl)
        assertEquals("kim.possible@gmail.com", agent.email)
        assertEquals("mailto:kim.possible@gmail.com", agent.emailUrl)
    }

    @Test
    fun `no WhatsApp and no email - no contact`() {
        doReturn(
            searchAgent.copy(
                whatsappNumber = null,
                party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = ""),
            )
        ).whenever(agentService).findByIdOrNull(20L)

        val agent = builder.build(listOf(match)).listings.single().agents.single()

        assertNull(agent.whatsapp)
        assertNull(agent.whatsappUrl)
        assertNull(agent.email)
        assertNull(agent.emailUrl)
        assertEquals("Kim Possible", agent.fullName)
    }

    @Test
    fun `no partner portal - no mandate url`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        assertNull(builder.build(listOf(match)).listings.single().mandateUrl)
    }

    @Test
    fun `no title - null`() {
        val digest = builder.build(listOf(match.copy(listing = listing.copy(title = " "))))

        assertNull(digest.listings.single().title)
    }

    @Test
    fun `cover image`() {
        doReturn(ListingImageEntity(id = 1L, url = "https://cdn.ndopify.com/101.png", status = ImageStatus.VERIFIED))
            .whenever(imageService).cover(listing)

        assertEquals("https://cdn.ndopify.com/101.png", builder.build(listOf(match)).listings.single().imageUrl)
    }

    @Test
    fun `an unknown search agent is dropped`() {
        val unknown = match.copy(id = 2L, mandate = search.copy(id = 2L, agentId = 99L))

        val digest = builder.build(listOf(match, unknown))

        val interest = digest.listings.single()
        assertEquals(1, interest.agents.size)
        assertEquals("Kim Possible", interest.agents.single().fullName)
        assertEquals(1, digest.agentCount)
    }

    @Test
    fun `no customer in the digest`() {
        val rendered = builder.build(listOf(match)).toString()

        assertFalse(rendered.contains("Secret Client"))
        assertFalse(rendered.contains("secret@example.com"))
    }
}
