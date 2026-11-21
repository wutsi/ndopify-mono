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
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.mockMailImageService
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.VisitInterest
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

class VisitFeedbackMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val listingImageService = mock<ListingImageService>()
    private val imageService = mockMailImageService()
    private val mailet =
        VisitFeedbackMailet(tenantService, mailService, mailBodyResolver, listingImageService, imageService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
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
        status = VisitStatus.COMPLETED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 1, 10, 0),
        interest = VisitInterest.NOT_INTERESTED,
        feedbackReasonCode = MatchRejectionReason.PRICE,
        feedbackComment = "Trop cher pour moi",
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(listingImageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>feedback</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun send() {
        mailet.send(visit, customer, agent, listing)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "listing_title" to "Nice apartment",
                "image_url" to "https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg",
                "visit_date" to "01/12/2026",
                "visit_time" to "10:00",
                "interest" to "Pas intéressé",
                "reason" to "trop cher",
                "comment" to "Trop cher pour moi",
                "visit_url" to "https://partner.ndopify.com/visits/1",
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: l'avis de Ray Sponsible sur la visite", email.firstValue.subject)
        assertEquals("<html>feedback</html>", email.firstValue.body)
    }

    @Test
    fun `an interested answer has no reason and no comment`() {
        mailet.send(
            visit.copy(interest = VisitInterest.INTERESTED, feedbackReasonCode = null, feedbackComment = null),
            customer, agent, listing,
        )

        assertEquals("Intéressé", context()["interest"])
        assertNull(context()["reason"])
        assertNull(context()["comment"])
    }

    @Test
    fun `no partner central - no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(visit, customer, agent, listing)

        assertNull(context()["visit_url"])
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(VisitFeedbackMailet.TEMPLATE), context.capture())
        return context.firstValue
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
