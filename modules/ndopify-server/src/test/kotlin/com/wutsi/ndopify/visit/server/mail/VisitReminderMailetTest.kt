package com.wutsi.ndopify.visit.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.mockMailImageService
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class VisitReminderMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val listingImageService = mock<ListingImageService>()
    private val imageService = mockMailImageService()
    private val mailet =
        VisitReminderMailet(tenantService, locationService, mailService, mailBodyResolver, linkTokenService, listingImageService, imageService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        publicPortalUrl = "https://www.ndopify.com/",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
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
    private val listing = ListingEntity(
        id = 101L,
        title = "Nice apartment",
        cityId = 2370201L,
        neighborhoodId = 1001L,
        landmark = "Rue 1.234",
        accessDirections = "Derrière la pharmacie",
    )
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = Date.from(
            LocalDateTime.of(2026, 12, 2, 10, 0).atZone(ZoneId.of("Africa/Douala")).toInstant(),
        ),
        code = "1234",
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(listingImageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn("<html>reminder</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `sendCustomer sends only the customer copy`() {
        mailet.sendCustomer(visit, customer, agent, listing)

        val email = email()
        assertEquals("ray.sponsible@gmail.com", email.recipient.email)
        assertEquals("Votre visite, c'est demain", email.subject)
        assertEquals("Kim Possible", email.sender?.displayName)

        val customerContext = context(VisitReminderMailet.TEMPLATE_CUSTOMER)
        val replyUrl = customerContext["agent_whatsapp_reply_url"] as String
        assertEquals(
            "Bonjour, je vous réponds au sujet de la visite du bien Nice apartment le 02/12/2026 à 10:00.",
            URLDecoder.decode(replyUrl.substringAfter("?text="), "UTF-8"),
        )
        assertEquals("02/12/2026", customerContext["visit_date"])
        assertEquals("10:00", customerContext["visit_time"])
        assertEquals("1234", customerContext["code"])
        assertEquals("Bastos, Yaounde", customerContext["place"])
        assertEquals("https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg", customerContext["image_url"])
        assertEquals("près de Rue 1.234", customerContext["landmark"])
        assertFalse(customerContext.containsKey("address"))
        assertEquals("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz", customerContext["visit_url"])
    }

    @Test
    fun `sendAgent sends only the agent copy`() {
        mailet.sendAgent(visit, customer, agent, listing)

        val email = email()
        assertEquals("kim.possible@gmail.com", email.recipient.email)
        assertEquals("Ndopify: rappel de la visite avec Ray Sponsible", email.subject)
        assertNull(email.sender)

        val agentContext = context(VisitReminderMailet.TEMPLATE_AGENT)
        assertEquals("+237 6 00 00 00 01", agentContext["customer_whatsapp"])
        assertEquals("https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg", agentContext["image_url"])
        assertEquals("https://partner.ndopify.com/visits/1", agentContext["visit_url"])
        assertFalse(agentContext.containsKey("agent_whatsapp_reply_url"), "the agent copy has no reply button")
    }

    @Test
    fun `a VIDEO reminder has no address in the e-mail`() {
        mailet.sendCustomer(visit.copy(type = VisitType.VIDEO, code = "5678"), customer, agent, listing)

        val context = context(VisitReminderMailet.TEMPLATE_CUSTOMER)
        assertEquals(false, context["on_site"])
        assertFalse(context.containsKey("address"))
    }

    @Test
    fun `blank title`() {
        mailet.sendCustomer(visit, customer, agent, listing.copy(title = " "))

        assertEquals("Bien n° 101", context(VisitReminderMailet.TEMPLATE_CUSTOMER)["listing_title"])
    }

    private fun email(): Email {
        val captor = argumentCaptor<Email>()
        verify(mailService).send(captor.capture())
        return captor.firstValue
    }

    private fun context(template: String): Map<*, *> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(template), captor.capture())
        return captor.firstValue
    }
}
