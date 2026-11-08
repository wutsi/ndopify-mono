package com.wutsi.ndopify.visit.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeast
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
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
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

class VisitNoShowMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val linkTokenService = mock<MatchLinkTokenService>()
    private val imageService = mock<ListingImageService>()
    private val mailet =
        VisitNoShowMailet(tenantService, mailService, mailBodyResolver, linkTokenService, imageService)

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
        status = VisitStatus.NO_SHOW,
        type = VisitType.ON_SITE,
        noShowParty = VisitParty.CUSTOMER,
        noShowReason = VisitNoShowReason.CUSTOMER_ABSENT,
        scheduledAt = Date.from(
            LocalDateTime.of(2026, 12, 2, 10, 0).atZone(ZoneId.of("Africa/Douala")).toInstant(),
        ),
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(imageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("xxx.yyy.zzz").whenever(linkTokenService).create(customer)
        doReturn("<html>no show</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `the customer caused it - only the customer is told`() {
        mailet.send(visit, customer, agent, listing)

        val email = argumentCaptor<Email>()
        verify(mailService, times(1)).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ndopify: la visite n'a pas eu lieu", email.firstValue.subject)
        assertEquals("Kim Possible", email.firstValue.sender?.displayName)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "other_party" to "Kim Possible",
                "listing_title" to "Nice apartment",
                "image_url" to "https://cdn.ndopify.com/7.jpg",
                "visit_date" to "02/12/2026",
                "visit_time" to "10:00",
                "reason_label" to "Le client ne s'est pas présenté.",
                "agent_recipient" to false,
                "visit_url" to "https://www.ndopify.com/visits/1?token=xxx.yyy.zzz",
                "agent_full_name" to "Kim Possible",
                "agent_photo_url" to null,
                "agent_whatsapp" to null,
                "agent_whatsapp_url" to null,
            ),
            contexts().single(),
        )
    }

    @Test
    fun `the agent caused it - both sides are told`() {
        mailet.send(
            visit.copy(noShowParty = VisitParty.AGENT, noShowReason = VisitNoShowReason.AGENT_ABSENT),
            customer,
            agent,
            listing,
        )

        val captor = argumentCaptor<Email>()
        verify(mailService, times(2)).send(captor.capture())
        assertEquals(
            setOf("ray.sponsible@gmail.com", "kim.possible@gmail.com"),
            captor.allValues.map { it.recipient.email }.toSet(),
        )
        assertEquals(
            "Kim Possible",
            captor.allValues.single { it.recipient.email == "ray.sponsible@gmail.com" }.sender?.displayName,
        )
        assertNull(captor.allValues.single { it.recipient.email == "kim.possible@gmail.com" }.sender)
        val contexts = contexts()
        assertEquals("L'agent ne s'est pas présenté.", contexts[0]["reason_label"])
        assertEquals("L'agent ne s'est pas présenté.", contexts[1]["reason_label"])
        assertEquals("Kim Possible", contexts[1]["recipient_name"])
        assertEquals("Ray Sponsible", contexts[1]["other_party"])
        assertEquals("https://partner.ndopify.com/visits/1", contexts[1]["visit_url"])
    }

    @Test
    fun `no public portal - no customer link`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(visit, customer, agent, listing)

        assertNull(contexts().single()["visit_url"])
        verify(linkTokenService, never()).create(any())
    }

    private fun contexts(): List<Map<*, *>> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver, atLeast(1)).resolve(eq(VisitNoShowMailet.TEMPLATE), captor.capture())
        return captor.allValues
    }
}
