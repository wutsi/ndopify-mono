package com.wutsi.ndopify.mandate.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
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

class MandateAssignedMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = MandateAssignedMailet(tenantService, locationService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
    )
    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L, 222L),
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(
            id = 200L,
            firstName = "Kim",
            lastName = "Possible",
            email = "kim.possible@gmail.com",
            photoUrl = "https://cdn.ndopify.com/agents/200.jpg",
        ),
        whatsappNumber = "+237670000020",
        biography = "Agent immobilier à Yaoundé",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
        doReturn("<html>assigned</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `send assigned email to the customer`() {
        mailet.send(mandate, customer, agent)

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/mandate/assigned.mjml"), context.capture())
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Ray Sponsible",
                "agent_full_name" to "Kim Possible",
                "agent_photo_url" to "https://cdn.ndopify.com/agents/200.jpg",
                "agent_presentation" to "Agent immobilier à Yaoundé",
            ),
            context.firstValue,
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("ray.sponsible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Ray Sponsible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Un agent a été assigné à votre mandat à Bastos, Mvan (Yaounde)", email.firstValue.subject)
        assertEquals("<html>assigned</html>", email.firstValue.body)
    }

    @Test
    fun `no neighbourhoods - the city in the subject`() {
        mailet.send(mandate.copy(neighborhoodIds = emptyList()), customer, agent)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Un agent a été assigné à votre mandat à Yaounde", email.firstValue.subject)
    }

    @Test
    fun `unknown city - the neighbourhoods only`() {
        doReturn(null).whenever(locationService).findByIdOrNull(2370201L)

        mailet.send(mandate, customer, agent)

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Un agent a été assigné à votre mandat à Bastos, Mvan", email.firstValue.subject)
    }

    @Test
    fun `a listing mandate - its template`() {
        mailet.send(mandate.copy(mandateType = MandateType.LISTING), customer, agent)

        verify(mailBodyResolver).resolve(eq("/mail/mandate/assigned_listing.mjml"), any())
    }

    @Test
    fun `blank biography is not presented`() {
        mailet.send(mandate, customer, agent.copy(biography = "  "))

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertNull(context.firstValue["agent_presentation"])
    }

    @Test
    fun `no photo is shown without one`() {
        mailet.send(mandate, customer, agent.copy(party = agent.party.copy(photoUrl = " ")))

        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(any(), context.capture())
        assertNull(context.firstValue["agent_photo_url"])
    }
}
