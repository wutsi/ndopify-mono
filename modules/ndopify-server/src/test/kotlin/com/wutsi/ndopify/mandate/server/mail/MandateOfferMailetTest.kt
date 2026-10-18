package com.wutsi.ndopify.mandate.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MandateOfferMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = MandateOfferMailet(tenantService, locationService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
        timeZone = "Africa/Douala",
    )
    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L, 222L),
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        rooms = "2",
        price = 1500000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val assignment = AssignmentEntity(
        id = 99L,
        mandate = mandate,
        agentId = 20L,
        // 13:00 in Douala
        expiresAt = Date.from(LocalDateTime.parse("2026-10-15T13:00").atZone(ZoneId.of("Africa/Douala")).toInstant()),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
        doReturn("<html>offer</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    private fun context(assignment: AssignmentEntity = this.assignment): Map<String, Any?> {
        mailet.send(assignment, agent)
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/mandate/offer.mjml"), context.capture())
        return context.firstValue
    }

    @Test
    fun `send the offer to the agent`() {
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "listing_type" to "Location",
                "property_type" to "Appartement",
                "rooms" to "2",
                "city" to "Yaounde",
                "neighbourhoods" to "Bastos, Mvan",
                "budget" to "1 500 000 XAF / mois",
                "deadline_date" to "15/10/2026",
                "deadline_time" to "13:00",
                "offer_url" to "https://partner.ndopify.com",
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Un nouveau mandat vous est proposé à Yaounde", email.firstValue.subject)
        assertEquals("<html>offer</html>", email.firstValue.body)
    }

    @Test
    fun `the deadline is in the tenant's time zone`() {
        doReturn(tenant.copy(timeZone = "America/Toronto")).whenever(tenantService).findById(1L)

        val context = context()
        assertEquals("15/10/2026", context["deadline_date"])
        assertEquals("08:00", context["deadline_time"])
    }

    @Test
    fun `the tenant's formats`() {
        doReturn(
            tenant.copy(
                locales = listOf("fr-CM"),
                currencyCode = "XAF",
                monetaryFormat = "#,###,##0 FCFA",
                dateFormat = "dd MMM yyyy",
                timeFormat = "HH'h'mm",
            )
        ).whenever(tenantService).findById(1L)

        val context = context()

        assertEquals("1 500 000 FCFA / mois", context["budget"])
        assertEquals("15 oct. 2026", context["deadline_date"])
        assertEquals("13h00", context["deadline_time"])
    }

    @Test
    fun `a sale - no periodicity`() {
        val sale = mandate.copy(listingType = ListingType.SALE, pricePeriodicityType = PeriodicityType.NONE)

        val context = context(assignment.copy(mandate = sale))

        assertEquals("Achat", context["listing_type"])
        assertEquals("1 500 000 XAF", context["budget"])
    }

    @Test
    fun `optional details left out`() {
        val bare = mandate.copy(neighborhoodIds = emptyList(), propertyType = null, rooms = null)

        val context = context(assignment.copy(mandate = bare))

        assertEquals(null, context["neighbourhoods"])
        assertEquals(null, context["property_type"])
        assertEquals(null, context["rooms"])
    }

    @Test
    fun `no partner central - no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        assertEquals(null, context()["offer_url"])
    }
}
