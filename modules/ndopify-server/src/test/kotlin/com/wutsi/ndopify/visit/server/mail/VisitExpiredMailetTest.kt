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
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VisitExpiredMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val imageService = mock<ListingImageService>()
    private val mailet =
        VisitExpiredMailet(tenantService, mailService, mailBodyResolver, linkTokenService, imageService)

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
        status = VisitStatus.EXPIRED,
        type = VisitType.ON_SITE,
        requestedAt = Date.from(
            LocalDateTime.of(2026, 12, 2, 10, 0).atZone(ZoneId.of("Africa/Douala")).toInstant(),
        ),
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(imageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn("<html>expired</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(visit, customer, agent, listing)

        val captor = argumentCaptor<Email>()
        verify(mailService, times(2)).send(captor.capture())
        assertEquals("Ndopify: votre demande de visite a expiré", captor.allValues[0].subject)
        assertEquals("Ndopify: demande de visite expirée avec Ray Sponsible", captor.allValues[1].subject)
        assertEquals("Kim Possible", captor.allValues[0].sender?.displayName)
        assertEquals(null, captor.allValues[1].sender)

        val customerContext = context("Ray Sponsible")
        assertEquals("02/12/2026", customerContext["requested_date"])
        assertEquals("10:00", customerContext["requested_time"])
        assertEquals("Kim Possible", customerContext["other_party"])
        assertEquals("https://www.ndopify.com/visits/1?token=xxx.yyy.zzz", customerContext["visit_url"])
    }

    private fun context(recipient: String): Map<*, *> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver, times(2)).resolve(eq(VisitExpiredMailet.TEMPLATE), captor.capture())
        return captor.allValues.single { it["recipient_name"] == recipient }
    }
}
