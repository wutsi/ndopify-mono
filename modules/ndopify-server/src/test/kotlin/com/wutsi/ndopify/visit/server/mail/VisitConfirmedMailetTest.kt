package com.wutsi.ndopify.visit.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
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
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitConfirmedMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val imageService = mock<ListingImageService>()
    private val mailet =
        VisitConfirmedMailet(tenantService, mailService, mailBodyResolver, linkTokenService, imageService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com/",
        publicPortalUrl = "https://www.ndopify.com/",
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
        landmark = "Rue 1.234, Bonapriso",
        accessDirections = "Derrière la pharmacie",
    )
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 2, 10, 0),
        code = "1234",
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(imageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>confirmed</html>").whenever(mailBodyResolver).resolve(any(), any())
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
    }

    @Test
    fun `the customer gets the code and the address, the agent neither`() {
        mailet.send(visit, customer, agent, listing)

        val customerEmail = email("ray.sponsible@gmail.com")
        assertEquals("Ray Sponsible", customerEmail.recipient.displayName)
        assertEquals("Ndopify: votre visite est confirmée", customerEmail.subject)
        assertEquals("<html>confirmed</html>", customerEmail.body)

        val agentEmail = email("kim.possible@gmail.com")
        assertEquals("Kim Possible", agentEmail.recipient.displayName)
        assertEquals("Ndopify: visite confirmée avec Ray Sponsible", agentEmail.subject)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "listing_title" to "Nice apartment",
                "image_url" to "https://cdn.ndopify.com/7.jpg",
                "agent_full_name" to "Kim Possible",
                "agent_whatsapp" to "+237 6 99 00 00 01",
                "visit_date" to "02/12/2026",
                "visit_time" to "10:00",
                "on_site" to true,
                "address" to "Rue 1.234, Bonapriso, Derrière la pharmacie",
                "code" to "1234",
                "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
            ),
            customerContext(),
        )
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "customer_whatsapp" to "+237 6 00 00 00 01",
                "listing_title" to "Nice apartment",
                "image_url" to "https://cdn.ndopify.com/7.jpg",
                "visit_date" to "02/12/2026",
                "visit_time" to "10:00",
                "on_site" to true,
                "visit_url" to "https://partner.ndopify.com/visits/1",
            ),
            agentContext(),
        )
    }

    @Test
    fun `a VIDEO visit has no address`() {
        mailet.send(visit.copy(type = VisitType.VIDEO), customer, agent, listing)

        val context = customerContext()
        assertEquals(false, context["on_site"])
        assertNull(context["address"])
        assertTrue((context["agent_whatsapp"] as String).isNotBlank())
    }

    @Test
    fun `no public portal - no customer link`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(visit, customer, agent, listing)

        assertNull(customerContext()["visit_url"])
        verify(linkTokenService, never()).create(any())
    }

    private fun email(address: String): Email {
        val captor = argumentCaptor<Email>()
        verify(mailService, times(2)).send(captor.capture())
        return captor.allValues.single { it.recipient.email == address }
    }

    private fun customerContext(): Map<*, *> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(VisitConfirmedMailet.TEMPLATE_CUSTOMER), captor.capture())
        return captor.firstValue
    }

    private fun agentContext(): Map<*, *> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(VisitConfirmedMailet.TEMPLATE_AGENT), captor.capture())
        return captor.firstValue
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
