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
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.match.server.service.MatchLinkTokenService
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitParty
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

class VisitCancelledMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val imageService = mock<ListingImageService>()
    private val mailet =
        VisitCancelledMailet(tenantService, mailService, mailBodyResolver, linkTokenService, imageService)

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
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )
    private val listing = ListingEntity(id = 101L, title = "Nice apartment")
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CANCELLED,
        type = VisitType.ON_SITE,
        scheduledAt = Date.from(
            LocalDateTime.of(2026, 12, 2, 10, 0).atZone(ZoneId.of("Africa/Douala")).toInstant(),
        ),
        cancelledBy = VisitParty.CUSTOMER,
        cancellationReason = "Empêchement",
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(imageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn("<html>cancelled</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `the customer cancelled - the agent is told`() {
        mailet.send(visit, customer, agent, listing)

        val email = argumentCaptor<Email>()
        verify(mailService, times(1)).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ndopify: la visite a été annulée", email.firstValue.subject)
        assertNull(email.firstValue.sender)
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "other_party" to "Ray Sponsible",
                "listing_title" to "Nice apartment",
                "image_url" to "https://cdn.ndopify.com/7.jpg",
                "visit_date" to "02/12/2026",
                "visit_time" to "10:00",
                "cancelled_by_label" to "Le client a annulé la visite.",
                "cancellation_reason" to "Empêchement",
                "visit_url" to "https://partner.ndopify.com/visits/1",
            ),
            context(),
        )
    }

    @Test
    fun `the agent cancelled - the customer is told`() {
        mailet.send(visit.copy(cancelledBy = VisitParty.AGENT), customer, agent, listing)

        val email = argumentCaptor<Email>()
        verify(mailService, times(1)).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.sender?.displayName)
        assertEquals("J'ai annulé la visite.", context()["cancelled_by_label"])
        assertEquals("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz", context()["visit_url"])
    }

    @Test
    fun `no reason`() {
        mailet.send(visit.copy(cancellationReason = null), customer, agent, listing)

        assertNull(context()["cancellation_reason"])
    }

    private fun context(): Map<String, Any?> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(VisitCancelledMailet.TEMPLATE), captor.capture())
        return captor.firstValue
    }
}
