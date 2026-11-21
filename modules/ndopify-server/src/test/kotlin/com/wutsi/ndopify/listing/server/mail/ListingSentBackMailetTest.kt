package com.wutsi.ndopify.listing.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingSentBackMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = ListingSentBackMailet(tenantService, locationService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com/",
    )
    private val listing = ListingEntity(
        id = 101L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodId = 111L,
        errorCodes = listOf(ErrorCode.LISTING_NOT_ENOUGH_IMAGES, ErrorCode.LISTING_OUTSIDE_NEIGHBORHOOD),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaoundé")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn("<html>sent back</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `send to the agent only`() {
        mailet.send(listing, agent)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Votre annonce a été renvoyée pour correction", email.firstValue.subject)
        assertEquals("<html>sent back</html>", email.firstValue.body)
    }

    @Test
    fun `the e-mail context carries the property and the link`() {
        mailet.send(listing, agent)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(ListingSentBackMailet.TEMPLATE_AGENT), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "property" to "n° 101 — Bastos, Yaoundé",
                "fix_url" to "https://partner.ndopify.com/listings/101",
            ),
            context.firstValue,
        )
    }

    @Test
    fun `no partner central URL means no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(listing, agent)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(ListingSentBackMailet.TEMPLATE_AGENT), context.capture())
        assertNull(context.firstValue["fix_url"])
    }
}
