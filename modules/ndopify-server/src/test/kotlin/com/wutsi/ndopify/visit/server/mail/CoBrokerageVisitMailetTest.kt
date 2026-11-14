package com.wutsi.ndopify.visit.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

class CoBrokerageVisitMailetTest {
    private val tenantService = mock<TenantService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val imageService = mock<ListingImageService>()
    private val mailet = CoBrokerageVisitMailet(tenantService, mailService, mailBodyResolver, imageService)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
        publicPortalUrl = "https://www.ndopify.com",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
        timeZone = "Africa/Douala",
    )

    // The listing agent, B: the recipient, never the sender
    private val listingAgent = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe", email = "john.doe@gmail.com"),
        whatsappNumber = "+237677889900",
    )

    // The search agent, A: the sender, whose voice and contact the e-mail carries
    private val searchAgent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
        whatsappNumber = "+237699000001",
    )

    private val listing = ListingEntity(id = 101L, title = "Villa Bastos", agent = listingAgent)
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.CONFIRMED,
        type = VisitType.ON_SITE,
        scheduledAt = date(2026, 12, 2, 10, 0),
        code = "1234",
        note = "Client pressé",
    )

    @BeforeTest
    fun setUp() {
        doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(imageService).cover(any())
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("<html>co-brokerage</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `confirmed - the listing agent is the recipient, A is the sender`() {
        mailet.sendConfirmed(visit, listing, searchAgent)

        val email = email("john.doe@gmail.com")
        assertEquals("John Doe", email.recipient.displayName)
        assertEquals("Kim Possible", email.sender?.displayName)
        assertEquals("Ndopify: visite de votre bien confirmée", email.subject)

        val context = context(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED)
        assertEquals("Villa Bastos", context["listing_title"])
        assertEquals("02/12/2026", context["visit_date"])
        assertEquals("10:00", context["visit_time"])
        assertEquals("Sur place", context["type_label"])
        assertEquals(true, context["on_site"])
        assertEquals("Kim Possible", context["agent_full_name"])
        assertEquals("https://partner.ndopify.com/agent?id=20", context["agent_profile_url"])
        assertEquals("https://wa.me/237699000001", context["agent_whatsapp_url"])
        assertEquals("https://partner.ndopify.com/listings/101#visits-title", context["listing_url"])
        assertEquals("https://cdn.ndopify.com/7.jpg", context["image_url"])
        assertFalse(context.toString().contains("Secret Client"))
        assertFalse(context.toString().contains("secret@example.com"))
        assertFalse(context.toString().contains("1234"))
        assertFalse(context.toString().contains("Client pressé"))
    }

    @Test
    fun `confirmed - VIDEO tells B the customer is remote`() {
        mailet.sendConfirmed(visit.copy(type = VisitType.VIDEO), listing, searchAgent)

        val context = context(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED)
        assertEquals("Visite vidéo", context["type_label"])
        assertEquals(false, context["on_site"])
    }

    @Test
    fun `reminder - the same facts, the reminder template`() {
        mailet.sendReminder(visit, listing, searchAgent)

        assertEquals("Ndopify: rappel de la visite de votre bien", email("john.doe@gmail.com").subject)
        assertEquals("Villa Bastos", context(CoBrokerageVisitMailet.TEMPLATE_REMINDER)["listing_title"])
    }

    @Test
    fun `cancelled - the actor's neutral label, never the mandate reason`() {
        mailet.sendCancelled(
            visit.copy(cancelledBy = VisitParty.AGENT, cancellationReason = "Mandat clôturé"),
            listing,
            searchAgent,
        )

        assertEquals("Ndopify: la visite de votre bien est annulée", email("john.doe@gmail.com").subject)
        val context = context(CoBrokerageVisitMailet.TEMPLATE_CANCELLED)
        assertEquals("J'ai annulé la visite.", context["cancelled_by_label"])
        assertNull(context["cancellation_reason"])
    }

    @Test
    fun `cancelled - the customer and the system labels`() {
        mailet.sendCancelled(visit.copy(cancelledBy = VisitParty.CUSTOMER), listing, searchAgent)
        assertEquals("Le client a annulé la visite.", context(CoBrokerageVisitMailet.TEMPLATE_CANCELLED)["cancelled_by_label"])

        mailet.sendCancelled(visit.copy(cancelledBy = VisitParty.SYSTEM), listing, searchAgent)
        assertEquals("La visite est annulée.", context(CoBrokerageVisitMailet.TEMPLATE_CANCELLED)["cancelled_by_label"])
    }

    @Test
    fun `no-show - both customer-caused labels`() {
        mailet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CUSTOMER_ABSENT), listing, searchAgent)
        assertEquals("Le client ne s'est pas présenté à la visite.", context(CoBrokerageVisitMailet.TEMPLATE_NO_SHOW)["reason_label"])

        mailet.sendNoShow(visit.copy(noShowReason = VisitNoShowReason.CODE_REFUSED), listing, searchAgent)
        assertEquals("Le client a refusé de donner le code de visite.", context(CoBrokerageVisitMailet.TEMPLATE_NO_SHOW)["reason_label"])
    }

    @Test
    fun `no WhatsApp - no link, the name stays`() {
        mailet.sendConfirmed(visit, listing, searchAgent.copy(whatsappNumber = null))

        val context = context(CoBrokerageVisitMailet.TEMPLATE_CONFIRMED)
        assertNull(context["agent_whatsapp_url"])
        assertEquals("Kim Possible", context["agent_full_name"])
    }

    private fun email(recipient: String): Email {
        val captor = argumentCaptor<Email>()
        verify(mailService, atLeastOnce()).send(captor.capture())
        return captor.allValues.single { it.recipient.email == recipient }
    }

    private fun context(template: String): Map<String, Any?> {
        val captor = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver, atLeastOnce()).resolve(eq(template), captor.capture())
        return captor.lastValue
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
