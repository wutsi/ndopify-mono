package com.wutsi.ndopify.match.server.mail

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
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
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

class MatchVisitAgentNudgeMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val listingImageService = mock<ListingImageService>()
    private val imageService = mockMailImageService()
    private val mailet = MatchVisitAgentNudgeMailet(
        tenantService,
        mailService,
        mailBodyResolver,
        listingImageService,
        imageService,
    )

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com/",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val mandate = MandateEntity(
        id = 204L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
    )
    private val listing = ListingEntity(
        id = 501L,
        title = "Nice apartment",
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(
        id = 9001L,
        mandate = mandate,
        listing = listing,
        agentId = 20L,
        status = MatchStatus.ACCEPTED,
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
        whatsappNumber = "+237699000001",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>nudge</html>").whenever(mailBodyResolver).resolve(any(), any())
        doReturn(ListingImageEntity(id = 7L, listingId = 501L, url = "https://cdn.ndopify.com/7.jpg"))
            .whenever(listingImageService).cover(any())
    }

    @Test
    fun send() {
        mailet.send(match, customer, agent)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "listing_title" to "Nice apartment",
                "image_url" to "https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg",
                "price_label" to "Loyer",
                "price" to "150 000 FCFA / mois",
                "whatsapp_url" to "https://wa.me/237600000001",
                "visit_url" to "https://partner.ndopify.com/mandates/204#match-9001",
                "agent_full_name" to "Kim Possible",
                "agent_photo_url" to null,
                "agent_whatsapp" to "+237 6 99 00 00 01",
                "agent_whatsapp_url" to "https://wa.me/237699000001",
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Ray Sponsible attend une visite", email.firstValue.subject)
        assertEquals("<html>nudge</html>", email.firstValue.body)
    }

    @Test
    fun `no customer whatsapp - no WhatsApp button`() {
        mailet.send(match, customer.copy(whatsappNumber = null), agent)

        assertNull(context()["whatsapp_url"])
    }

    @Test
    fun `no Partner Central - no visit button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(match, customer, agent)

        assertNull(context()["visit_url"])
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

        assertEquals("Bien n° 501", context()["listing_title"])
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchVisitAgentNudgeMailet.TEMPLATE), context.capture())
        return context.firstValue
    }
}
