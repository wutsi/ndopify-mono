package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.github.mustachejava.resolver.ClasspathResolver
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
import com.wutsi.ndopify.customer.server.service.CustomerService
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

class CoBrokerageMatchMailetTest {
    private val tenantService = mock<TenantService>()
    private val customerService = mock<CustomerService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val imageService = mock<ListingImageService>()
    private val mailet = CoBrokerageMatchMailet(
        tenantService,
        customerService,
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
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )
    private val coAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
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
        id = 101L,
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
        agent = coAgent,
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
        id = 101L,
        mandate = search,
        listing = listing,
        agentId = 20L,
        coBrokerage = true,
        status = MatchStatus.ACTIVE,
        score = 0.8,
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(customer(10L, "Ray", "Sponsible")).whenever(customerService).findByIdOrNull(10L)
        doReturn(customer(11L, "Secret", "Owner", "owner@gmail.com")).whenever(customerService).findByIdOrNull(11L)
        doReturn("<html>matches</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(searchAgent, listOf(match))

        val context = context()
        val mandate = (context["mandates"] as List<*>).single() as Map<*, *>
        assertEquals("Ray Sponsible", mandate["customer_name"])
        assertEquals("Bastos (Yaounde)", mandate["place"])
        assertEquals("https://pc.ndopify.com/mandates/1", mandate["mandate_url"])

        val listing = (mandate["listings"] as List<*>).single() as Map<*, *>
        assertEquals("Villa Bastos", listing["title"])
        assertEquals("90 000 FCFA / mois", listing["price"])
        assertEquals(80, listing["score"])
        assertEquals("John Doe", listing["agent_full_name"])
        assertEquals("https://wa.me/237677889900", listing["agent_whatsapp_url"])
        assertNull(listing["agent_email"])
        assertTrue(listing.containsKey("agent_email"))
        assertTrue(listing.containsKey("agent_email_url"))

        // Anti-sautage: the search agent's own customer is fine, the listing customer is not
        val rendered = context.toString()
        assertFalse(rendered.contains("Secret Owner"))
        assertFalse(rendered.contains("owner@gmail.com"))
        verify(customerService, never()).findByIdOrNull(11L)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ndopify: 1 bien d'un autre agent correspond à vos recherches", email.firstValue.subject)
    }

    @Test
    fun `no WhatsApp - the email is the contact`() {
        val noWhatsApp = coAgent.copy(whatsappNumber = null)
        mailet.send(searchAgent, listOf(match.copy(listing = listing.copy(agent = noWhatsApp))))

        val card = listingCard()
        assertNull(card["agent_whatsapp_url"])
        assertEquals("john.doe@gmail.com", card["agent_email"])
        assertEquals("mailto:john.doe@gmail.com", card["agent_email_url"])
    }

    @Test
    fun `no WhatsApp and no email - no contact link`() {
        val noContact = coAgent.copy(
            whatsappNumber = null,
            party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = ""),
        )
        mailet.send(searchAgent, listOf(match.copy(listing = listing.copy(agent = noContact))))

        val card = listingCard()
        assertNull(card["agent_whatsapp_url"])
        assertNull(card["agent_email"])
        assertNull(card["agent_email_url"])
        assertEquals("John Doe", card["agent_full_name"])
    }

    @Test
    fun `no partner portal - no mandate link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(searchAgent, listOf(match))

        val mandate = (context()["mandates"] as List<*>).single() as Map<*, *>
        assertNull(mandate["mandate_url"])
    }

    @Test
    fun `no matches - no email`() {
        mailet.send(searchAgent, emptyList())

        verify(mailService, never()).send(any())
    }

    @Test
    fun `more than 5 listings`() {
        val matches = (1..6).map { i -> match.copy(id = i.toLong(), mandate = search) }
        mailet.send(searchAgent, matches)

        val mandate = (context()["mandates"] as List<*>).single() as Map<*, *>
        assertEquals(5, (mandate["listings"] as List<*>).size)
        assertEquals(1, mandate["more"])
    }

    @Test
    fun template() {
        val resolver = MailBodyResolver(
            renderer = MjmlRenderer.create(),
            mustache = DefaultMustacheFactory(ClasspathResolver()),
            filters = listOf(MailFilterNull()),
        )
        val html = resolver.resolve(
            CoBrokerageMatchMailet.TEMPLATE,
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "mandates" to listOf(
                    mapOf(
                        "customer_name" to "Ray Sponsible",
                        "listing_type" to "Location",
                        "property_type" to "Appartement",
                        "place" to "Bastos (Yaounde)",
                        "mandate_url" to "https://pc.ndopify.com/mandates/1",
                        "listings" to listOf(
                            mapOf(
                                "title" to "Villa Bastos",
                                "price" to "90 000 FCFA / mois",
                                "score" to 80,
                                "agent_full_name" to "John Doe",
                                "agent_photo_url" to "https://cdn.ndopify.com/john.jpg",
                                "agent_whatsapp" to "+237 677 889 900",
                                "agent_whatsapp_url" to "https://wa.me/237677889900",
                                "agent_email" to "john.doe@gmail.com",
                                "agent_email_url" to "mailto:john.doe@gmail.com",
                            ),
                        ),
                        "more" to null,
                    ),
                ),
            ),
        )
        assertTrue(html.contains("Villa Bastos"))
        assertTrue(html.contains("John Doe"))
        assertTrue(html.contains("https://wa.me/237677889900"))
        assertTrue(html.contains("https://cdn.ndopify.com/john.jpg"))
        assertTrue(html.contains("https://cdn.ndopify.com/logo.png"))
        assertTrue(html.contains("mailto:john.doe@gmail.com"))
        assertFalse(html.contains("Secret Owner"))
        assertFalse(html.contains("owner@gmail.com"))
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(CoBrokerageMatchMailet.TEMPLATE), context.capture())
        return context.firstValue
    }

    private fun listingCard(): Map<*, *> =
        (((context()["mandates"] as List<*>).single() as Map<*, *>)["listings"] as List<*>).single() as Map<*, *>

    private fun customer(id: Long, firstName: String, lastName: String, email: String = "") =
        CustomerEntity(id = id, party = PartyEntity(id = id * 10, firstName = firstName, lastName = lastName, email = email))
}
