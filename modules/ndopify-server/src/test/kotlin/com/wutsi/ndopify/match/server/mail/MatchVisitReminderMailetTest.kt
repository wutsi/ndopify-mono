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
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.mockMailImageService
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchVisitReminderMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val listingImageService = mock<ListingImageService>()
    private val imageService = mockMailImageService()
    private val mailet = MatchVisitReminderMailet(
        tenantService,
        mailService,
        mailBodyResolver,
        linkTokenService,
        listingImageService,
        imageService,
    )

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
    )
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(id = 1L, mandate = mandate, listing = listing, agentId = 20L, status = MatchStatus.ACCEPTED)
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"))

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>reminder</html>").whenever(mailBodyResolver).resolve(any(), any())
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn(ListingImageEntity(id = 7L, listingId = 101L, url = "https://cdn.ndopify.com/7.jpg"))
            .whenever(listingImageService).cover(any())
    }

    @Test
    fun send() {
        mailet.send(match, customer, agent)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "agent_full_name" to "Kim Possible",
                "agent_photo_url" to null,
                "agent_whatsapp" to null,
                "agent_whatsapp_url" to null,
                "listing_title" to "Nice apartment",
                "image_url" to "https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg",
                "price_label" to "Loyer",
                "price" to "150 000 FCFA / mois",
                "online" to true,
                "listing_url" to "https://www.ndopify.com/match/1?token=xxx.yyy.zzz",
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ray Sponsible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: n'oubliez pas de demander votre visite", email.firstValue.subject)
        assertEquals("<html>reminder</html>", email.firstValue.body)
        assertEquals("Kim Possible", email.firstValue.sender?.displayName)
        assertEquals("", email.firstValue.sender?.email)
    }

    @Test
    fun `no public portal - no link`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(match, customer, agent)

        assertNull(context()["online"])
        assertNull(context()["listing_url"])
        verify(linkTokenService, never()).create(any())
    }

    @Test
    fun `no token - the link without it`() {
        doReturn(null).whenever(linkTokenService).create(customer)

        mailet.send(match, customer, agent)

        assertEquals("https://www.ndopify.com/match/1", context()["listing_url"])
    }

    @Test
    fun `no photo - no image`() {
        doReturn(null).whenever(listingImageService).cover(any())

        mailet.send(match, customer, agent)

        assertNull(context()["image_url"])
    }

    @Test
    fun `blank title`() {
        mailet.send(match.copy(listing = listing.copy(title = "")), customer, agent)

        assertEquals("Bien n° 101", context()["listing_title"])
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchVisitReminderMailet.TEMPLATE), context.capture())
        return context.firstValue
    }
}
