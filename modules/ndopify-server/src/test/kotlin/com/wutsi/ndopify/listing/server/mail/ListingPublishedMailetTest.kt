package com.wutsi.ndopify.listing.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingPublishedMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = ListingPublishedMailet(tenantService, locationService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        publicPortalUrl = "https://www.ndopify.com/",
    )
    private val listing = ListingEntity(
        id = 1L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodId = 111L,
        title = "Appartement à louer, 3 chambres, Yaounde (Bastos)",
        url = "/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn("<html>published</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    private fun context(template: String, listing: ListingEntity = this.listing): Map<String, Any?> {
        mailet.send(listing, customer, agent)
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(template), context.capture())
        return context.firstValue
    }

    @Test
    fun `send to the customer and the agent`() {
        mailet.send(listing, customer, agent)

        val email = argumentCaptor<Email>()
        verify(mailService, times(2)).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ray Sponsible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Votre bien à Yaounde est en ligne", email.firstValue.subject)
        assertEquals("<html>published</html>", email.firstValue.body)
        assertEquals("kim.possible@gmail.com", email.secondValue.recipient.email)
        assertEquals("Kim Possible", email.secondValue.recipient.displayName)
        assertEquals("Ndopify: Votre annonce à Yaounde est publiée", email.secondValue.subject)
    }

    @Test
    fun `customer e-mail`() {
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "customer_name" to "Ray Sponsible",
                "agent_name" to "Kim Possible",
                "title" to "Appartement à louer, 3 chambres, Yaounde (Bastos)",
                "city" to "Yaounde",
                "neighbourhood" to "Bastos",
                "listing_url" to "https://www.ndopify.com/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
                "recipient_name" to "Ray Sponsible",
            ),
            context(ListingPublishedMailet.TEMPLATE_CUSTOMER),
        )
    }

    @Test
    fun `agent e-mail`() {
        assertEquals("Kim Possible", context(ListingPublishedMailet.TEMPLATE_AGENT)["recipient_name"])
    }

    @Test
    fun `no neighbourhood nor title`() {
        val context = context(ListingPublishedMailet.TEMPLATE_CUSTOMER, listing.copy(neighborhoodId = null, title = null))

        assertNull(context["neighbourhood"])
        assertNull(context["title"])
    }

    @Test
    fun `no public portal - no link`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)

        assertNull(context(ListingPublishedMailet.TEMPLATE_CUSTOMER)["listing_url"])
    }

    @Test
    fun `no url - no link`() {
        assertNull(context(ListingPublishedMailet.TEMPLATE_CUSTOMER, listing.copy(url = null))["listing_url"])
    }
}
