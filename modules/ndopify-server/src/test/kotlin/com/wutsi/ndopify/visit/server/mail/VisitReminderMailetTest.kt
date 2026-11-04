package com.wutsi.ndopify.visit.server.mail

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

class VisitReminderMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val mailet = VisitReminderMailet(tenantService, mailService, mailBodyResolver, linkTokenService)

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
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn("<html>reminder</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(visit, customer, agent, listing)

        val customerEmail = email("ray.sponsible@gmail.com")
        assertEquals("Ndopify: rappel de votre visite", customerEmail.subject)
        val agentEmail = email("kim.possible@gmail.com")
        assertEquals("Ndopify: rappel de la visite avec Ray Sponsible", agentEmail.subject)

        val customerContext = context(VisitReminderMailet.TEMPLATE_CUSTOMER)
        assertEquals("02/12/2026", customerContext["visit_date"])
        assertEquals("10:00", customerContext["visit_time"])
        assertEquals("1234", customerContext["code"])
        assertEquals("Rue 1.234, Derrière la pharmacie", customerContext["address"])
        assertEquals("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz", customerContext["visit_url"])

        val agentContext = context(VisitReminderMailet.TEMPLATE_AGENT)
        assertEquals("+237600000001", agentContext["customer_whatsapp"])
        assertEquals("https://partner.ndopify.com/visits/1", agentContext["visit_url"])
    }

    @Test
    fun `VIDEO reminder has no address`() {
        mailet.send(visit.copy(type = VisitType.VIDEO, code = "5678"), customer, agent, listing)

        val context = context(VisitReminderMailet.TEMPLATE_CUSTOMER)
        assertEquals(false, context["on_site"])
        assertEquals(null, context["address"])
    }

    @Test
    fun `blank title`() {
        mailet.send(visit, customer, agent, listing.copy(title = " "))

        assertEquals("Bien n° 101", context(VisitReminderMailet.TEMPLATE_CUSTOMER)["listing_title"])
    }

    private fun email(address: String): Email {
        val captor = argumentCaptor<Email>()
        verify(mailService, times(2)).send(captor.capture())
        return captor.allValues.single { it.recipient.email == address }
    }

    private fun context(template: String): Map<*, *> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(template), captor.capture())
        return captor.firstValue
    }
}
