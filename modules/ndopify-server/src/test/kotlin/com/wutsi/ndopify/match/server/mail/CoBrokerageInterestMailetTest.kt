package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.filter.MailFilterNull
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import dev.jcputney.mjml.MjmlRenderer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoBrokerageInterestMailetTest {
    private val tenantService = mock<TenantService>()
    private val agentService = mock<AgentService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val imageService = mock<ListingImageService>()
    private val mailet = CoBrokerageInterestMailet(
        tenantService,
        agentService,
        locationService,
        mailService,
        mailBodyResolver,
        imageService,
    )

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://pc.ndopify.com",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
        whatsappNumber = "+237677889900",
    )
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
        whatsappNumber = "+237677889900",
    )
    private val search = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
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
        doReturn("<html>interest</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(listingAgent, listOf(match))

        val context = context()
        val listing = (context["listings"] as List<*>).single() as Map<*, *>
        assertEquals("Villa Bastos", listing["title"])
        val agent = (listing["agents"] as List<*>).single() as Map<*, *>
        assertEquals("Kim Possible", agent["agent_full_name"])
        assertEquals("Appartement", agent["property_type"])
        assertEquals("Bastos (Yaounde)", agent["place"])
        assertNull(agent["search_count"])

        // Anti-sautage: the search customer never appears
        val rendered = context.toString()
        assertFalse(rendered.contains("Secret Client"))
        assertFalse(rendered.contains("secret@example.com"))

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("john.doe@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ndopify: 1 agent s'intéresse à votre bien", email.firstValue.subject)
    }

    @Test
    fun `several searches of the same agent - one row`() {
        val other = match.copy(id = 2L, mandate = search.copy(id = 2L, price = 200000), score = 0.9)
        mailet.send(listingAgent, listOf(match, other))

        val rows = agents()
        assertEquals(1, rows.size)
        val row = rows.single() as Map<*, *>
        assertEquals(2, row["search_count"])
        assertEquals("200 000 FCFA / mois", row["budget"])
    }

    @Test
    fun `several agents - subject counts agents`() {
        val second = searchAgent.copy(
            id = 21L,
            party = PartyEntity(id = 210L, firstName = "Sam", lastName = "Sung", email = "sam@example.com"),
        )
        doReturn(second).whenever(agentService).findByIdOrNull(21L)
        val other = match.copy(id = 2L, mandate = search.copy(id = 2L, agentId = 21L))

        mailet.send(listingAgent, listOf(match, other))

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: 2 agents s'intéressent à vos biens", email.firstValue.subject)
    }

    @Test
    fun `no WhatsApp - the email is the contact`() {
        doReturn(searchAgent.copy(whatsappNumber = null)).whenever(agentService).findByIdOrNull(20L)

        mailet.send(listingAgent, listOf(match))

        val row = agents().single() as Map<*, *>
        assertNull(row["agent_whatsapp_url"])
        assertEquals("kim.possible@gmail.com", row["agent_email"])
        assertEquals("mailto:kim.possible@gmail.com", row["agent_email_url"])
    }

    @Test
    fun `no WhatsApp and no email - no contact link`() {
        doReturn(
            searchAgent.copy(
                whatsappNumber = null,
                party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = ""),
            )
        ).whenever(agentService).findByIdOrNull(20L)

        mailet.send(listingAgent, listOf(match))

        val row = agents().single() as Map<*, *>
        assertNull(row["agent_whatsapp_url"])
        assertNull(row["agent_email"])
        assertNull(row["agent_email_url"])
        assertEquals("Kim Possible", row["agent_full_name"])
    }

    @Test
    fun `no partner portal - no mandate link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(listingAgent, listOf(match))

        assertNull(listing()["mandate_url"])
    }

    @Test
    fun template() {
        val resolver = MailBodyResolver(
            renderer = MjmlRenderer.create(),
            mustache = DefaultMustacheFactory(ClasspathResolver()),
            filters = listOf(MailFilterNull()),
        )
        val html = resolver.resolve(
            CoBrokerageInterestMailet.TEMPLATE,
            mapOf(
                "tenant_name" to "Ndopify",
                "recipient_name" to "John Doe",
                "listings" to listOf(
                    mapOf(
                        "title" to "Villa Bastos",
                        "price" to "90 000 FCFA / mois",
                        "mandate_url" to "https://pc.ndopify.com/mandates/11",
                        "agents" to listOf(
                            mapOf(
                                "agent_full_name" to "Kim Possible",
                                "property_type" to "Appartement",
                                "place" to "Bastos (Yaounde)",
                                "budget" to "100 000 FCFA / mois",
                                "agent_whatsapp_url" to "https://wa.me/237677889900",
                            ),
                        ),
                        "more" to null,
                    ),
                ),
            ),
        )
        assertTrue(html.contains("Villa Bastos"))
        assertTrue(html.contains("Kim Possible"))
        assertTrue(html.contains("https://wa.me/237677889900"))
        assertFalse(html.contains("Secret Client"))
        assertFalse(html.contains("secret@example.com"))
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(CoBrokerageInterestMailet.TEMPLATE), context.capture())
        return context.firstValue
    }

    private fun listing(): Map<*, *> = (context()["listings"] as List<*>).single() as Map<*, *>

    private fun agents(): List<*> = listing()["agents"] as List<*>
}
