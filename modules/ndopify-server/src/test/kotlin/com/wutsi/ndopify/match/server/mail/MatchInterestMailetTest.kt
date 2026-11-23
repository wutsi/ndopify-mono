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
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.mockMailImageService
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchInterestMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val listingImageService = mock<ListingImageService>()
    private val imageService = mockMailImageService()
    private val locationService = mock<LocationService>()
    private val mailet = MatchInterestMailet(
        tenantService,
        mailService,
        mailBodyResolver,
        listingImageService,
        imageService,
        locationService,
    )

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com/",
        currencyCode = "XAF",
        monetaryFormat = "#,##0 FCFA",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )
    private val mandate = MandateEntity(id = 1L, tenantId = 1L, agentId = 20L, listingType = ListingType.RENTAL)
    private val listing = ListingEntity(
        id = 101L,
        cityId = 100L,
        neighborhoodId = 200L,
        title = "Nice apartment",
        price = 175_000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(listingImageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 100L, name = "Douala")).whenever(locationService).findByIdOrNull(100L)
        doReturn(LocationEntity(id = 200L, name = "Bonapriso")).whenever(locationService).findByIdOrNull(200L)
        doReturn("<html>interest</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(agent, customer, mandate, listing)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "listing_title" to "Nice apartment",
                "image_url" to "https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg",
                "price" to "175 000 FCFA / mois",
                "place" to "Bonapriso, Douala",
                "mandate_url" to "https://partner.ndopify.com/mandates/1",
                "whatsapp_url" to "https://wa.me/237600000001",
                "asset" to "un logement",
                "price_label" to "Loyer",
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Ray Sponsible s'intéresse à un logement", email.firstValue.subject)
        assertEquals("<html>interest</html>", email.firstValue.body)
    }

    @Test
    fun `a sale listing's interest mail speaks of a bien and its price`() {
        val sale = listing.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)

        mailet.send(agent, customer, mandate.copy(listingType = ListingType.SALE), sale)

        val context = context()
        assertEquals("un bien", context["asset"])
        assertEquals("Prix", context["price_label"])
        assertEquals("175 000 FCFA", context["price"])

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Ray Sponsible s'intéresse à un bien", email.firstValue.subject)
    }

    @Test
    fun `listing without title`() {
        mailet.send(agent, customer, mandate, listing.copy(title = null))

        assertEquals("Bien n° 101", context()["listing_title"])
    }

    @Test
    fun `no partner central - no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(agent, customer, mandate, listing)

        assertNull(context()["mandate_url"])
    }

    @Test
    fun `no whatsapp number - no WhatsApp link`() {
        mailet.send(agent, customer.copy(whatsappNumber = null), mandate, listing)

        assertNull(context()["whatsapp_url"])
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchInterestMailet.TEMPLATE), context.capture())
        return context.firstValue
    }
}
