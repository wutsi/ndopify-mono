package com.wutsi.ndopify.match.server.mail

import com.github.mustachejava.DefaultMustacheFactory
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
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

class MatchNotificationMailetTest {
    private val tenantService = mock<TenantService>()
    private val customerService = mock<CustomerService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = MatchNotificationMailet(
        tenantService,
        customerService,
        locationService,
        mailService,
        mailBodyResolver,
    )

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://pc.ndopify.com",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )
    private val search1 = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L),
    )
    private val search2 = search1.copy(id = 2L, customerId = 11L, propertyType = null, neighborhoodIds = emptyList())

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(customer(10L, "Ray", "Sponsible")).whenever(customerService).findByIdOrNull(10L)
        doReturn(customer(11L, "Sam", "Sung")).whenever(customerService).findByIdOrNull(11L)
        doReturn("<html>matches</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(
            agent,
            listOf(
                match(search1, listing(301L, "Studio Bastos", 150000), 0.72),
                match(search2, listing(302L, "Villa Etoudi", 400000), 0.91),
                match(search1, listing(303L, "Appartement Mvan", 240000), 0.85),
            ),
        )

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchNotificationMailet.TEMPLATE), context.capture())
        assertEquals("https://cdn.ndopify.com/logo.png", context.firstValue["tenant_logo_url"])
        assertEquals("Ndopify", context.firstValue["tenant_name"])
        assertEquals("Kim Possible", context.firstValue["recipient_name"])
        assertEquals(
            listOf(
                mapOf(
                    "customer_name" to "Sam Sung",
                    "listing_type" to "Location",
                    "property_type" to null,
                    "place" to "Yaounde",
                    "mandate_url" to "https://pc.ndopify.com/mandates/2",
                    "listings" to listOf(
                        mapOf("title" to "Villa Etoudi", "price" to "400 000 FCFA / mois", "score" to 91),
                    ),
                    "more" to null,
                ),
                mapOf(
                    "customer_name" to "Ray Sponsible",
                    "listing_type" to "Location",
                    "property_type" to "Appartement",
                    "place" to "Bastos (Yaounde)",
                    "mandate_url" to "https://pc.ndopify.com/mandates/1",
                    "listings" to listOf(
                        mapOf("title" to "Appartement Mvan", "price" to "240 000 FCFA / mois", "score" to 85),
                        mapOf("title" to "Studio Bastos", "price" to "150 000 FCFA / mois", "score" to 72),
                    ),
                    "more" to null,
                ),
            ),
            context.firstValue["mandates"],
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: 3 nouveaux biens correspondent à vos recherches", email.firstValue.subject)
        assertEquals("<html>matches</html>", email.firstValue.body)
    }

    @Test
    fun `one match`() {
        mailet.send(agent, listOf(match(search1, listing(301L, "Studio Bastos", 150000), 0.72)))

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: 1 nouveau bien correspond à vos recherches", email.firstValue.subject)
    }

    @Test
    fun `more than 5 listings`() {
        val matches = (1..7).map { i -> match(search1, listing(300L + i, "Bien $i", 100000), 0.5 + i * 0.05) }

        mailet.send(agent, matches)

        val mandate = mandates().single()
        assertEquals(
            listOf("Bien 7", "Bien 6", "Bien 5", "Bien 4", "Bien 3"),
            (mandate["listings"] as List<*>).map { listing -> (listing as Map<*, *>)["title"] },
        )
        assertEquals(2, mandate["more"])
    }

    @Test
    fun `no mandate_url`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(agent, listOf(match(search1, listing(301L, "Studio Bastos", 150000), 0.72)))

        assertNull(mandates().single()["mandate_url"])
        verify(mailService).send(any())
    }

    @Test
    fun `blank title`() {
        mailet.send(agent, listOf(match(search1, listing(300L, " ", 150000), 0.72)))

        val listing = (mandates().single()["listings"] as List<*>).single() as Map<*, *>
        assertEquals("Bien n° 300", listing["title"])
    }

    @Test
    fun template() {
        val resolver = MailBodyResolver(
            renderer = MjmlRenderer.create(),
            mustache = DefaultMustacheFactory(),
            filters = listOf(MailFilterNull()),
        )
        val mandate = mapOf(
            "customer_name" to "Ray Sponsible",
            "listing_type" to "Location",
            "property_type" to "Appartement",
            "place" to "Bastos (Yaounde)",
            "mandate_url" to "https://pc.ndopify.com/mandates/1",
            "listings" to listOf(mapOf("title" to "Studio Bastos", "price" to "150 000 FCFA / mois", "score" to 72)),
            "more" to 2,
        )

        val html = resolver.resolve(
            MatchNotificationMailet.TEMPLATE,
            mapOf("tenant_name" to "Ndopify", "recipient_name" to "Kim Possible", "mandates" to listOf(mandate)),
        )
        assertTrue(html.contains("Ray Sponsible"))
        assertTrue(html.contains("Location - Appartement à Bastos (Yaounde)"))
        assertTrue(html.contains("Studio Bastos"))
        assertTrue(html.contains("150 000 FCFA / mois (72 %)"))
        assertTrue(html.contains("et 2 autre(s) bien(s)"))
        assertTrue(html.contains("https://pc.ndopify.com/mandates/1"))

        val bare = resolver.resolve(
            MatchNotificationMailet.TEMPLATE,
            mapOf(
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "mandates" to listOf(mandate - "more" - "mandate_url"),
            ),
        )
        assertFalse(bare.contains("autre(s) bien(s)"))
        assertFalse(bare.contains("Voir le mandat"))
    }

    @Suppress("UNCHECKED_CAST")
    private fun mandates(): List<Map<String, Any?>> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        return context.firstValue["mandates"] as List<Map<String, Any?>>
    }

    private fun customer(id: Long, firstName: String, lastName: String) =
        CustomerEntity(id = id, party = PartyEntity(id = id * 10, firstName = firstName, lastName = lastName))

    private fun listing(id: Long, title: String, price: Long) =
        ListingEntity(
            id = id,
            title = title,
            price = price,
            currencyCode = "XAF",
            pricePeriodicityType = PeriodicityType.MONTHLY,
        )

    private fun match(mandate: MandateEntity, listing: ListingEntity, score: Double) =
        MatchEntity(
            id = listing.id,
            mandate = mandate,
            listing = listing,
            agentId = 20L,
            status = MatchStatus.ACTIVE,
            score = score,
        )
}
