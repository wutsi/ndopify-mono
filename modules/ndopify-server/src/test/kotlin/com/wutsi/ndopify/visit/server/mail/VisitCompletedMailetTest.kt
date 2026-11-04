package com.wutsi.ndopify.visit.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import kotlin.test.assertNull

class VisitCompletedMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val mailet = VisitCompletedMailet(tenantService, mailService, mailBodyResolver, linkTokenService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        publicPortalUrl = "https://www.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val listing = ListingEntity(id = 101L, title = "Nice apartment")
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        customerId = 10L,
        status = VisitStatus.COMPLETED,
        type = VisitType.ON_SITE,
        scheduledAt = Date.from(
            LocalDateTime.of(2026, 12, 2, 10, 0).atZone(ZoneId.of("Africa/Douala")).toInstant(),
        ),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn("<html>completed</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(visit, customer, listing)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "listing_title" to "Nice apartment",
                "visit_date" to "02/12/2026",
                "visit_time" to "10:00",
                "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
            ),
            context(),
        )
        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ndopify: votre visite est terminée", email.firstValue.subject)
        assertEquals("<html>completed</html>", email.firstValue.body)
    }

    @Test
    fun `no public portal - no link`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(visit, customer, listing)

        assertNull(context()["visit_url"])
    }

    private fun context(): Map<String, Any?> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(VisitCompletedMailet.TEMPLATE), captor.capture())
        return captor.firstValue
    }
}
