package com.wutsi.ndopify.match.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.refdata.dto.PeriodicityType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchAnsweredMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = MatchAnsweredMailet(tenantService, locationService, mailService, mailBodyResolver)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        logoUrl = "https://cdn.ndopify.com/logo.png",
        partnerCentralUrl = "https://partner.ndopify.com",
        currencyCode = "XAF",
        monetaryFormat = "#,###,##0 FCFA",
    )
    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        mandateType = MandateType.SEARCH,
        listingType = ListingType.RENTAL,
        cityId = 2370201L,
        neighborhoodIds = listOf(1001L),
        propertyType = PropertyType.APARTMENT,
        price = 150000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val listing = ListingEntity(
        id = 102L,
        title = "Small apartment",
        price = 140000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val match = MatchEntity(
        id = 2L,
        tenantId = 1L,
        mandate = mandate,
        listing = listing,
        agentId = 20L,
        status = MatchStatus.ACCEPTED,
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 1001L, name = "Bastos")).whenever(locationService).findByIdOrNull(1001L)
        doReturn("<html>answered</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    @Test
    fun `an acceptance`() {
        mailet.send(match, customer, agent)

        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "accepted" to true,
                "rejection_reason" to null,
                "comment" to null,
                "listing_title" to "Small apartment",
                "price_label" to "Loyer",
                "price" to "140 000 FCFA / mois",
                "listing_type" to "Location",
                "property_type" to "Appartement",
                "city" to "Yaounde",
                "neighbourhoods" to "Bastos",
                "budget" to "150 000 FCFA / mois",
                "mandate_url" to "https://partner.ndopify.com/mandates/1",
            ),
            context(),
        )
        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Ray Sponsible a accepté la proposition", email.firstValue.subject)
        assertEquals("<html>answered</html>", email.firstValue.body)
    }

    @Test
    fun `a rejection carries its reason and comment`() {
        mailet.send(
            match.copy(
                status = MatchStatus.REJECTED,
                rejectionReasonCode = MatchRejectionReason.PRICE,
                rejectionReason = "Trop loin du travail.",
            ),
            customer,
            agent,
        )

        assertEquals(false, context()["accepted"])
        assertEquals("trop cher", context()["rejection_reason"])
        assertEquals("Trop loin du travail.", context()["comment"])
        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("Ndopify: Ray Sponsible a refusé la proposition", email.firstValue.subject)
    }

    @Test
    fun `a blank comment is left out`() {
        mailet.send(
            match.copy(
                status = MatchStatus.REJECTED,
                rejectionReasonCode = MatchRejectionReason.OTHER,
                rejectionReason = " ",
            ),
            customer,
            agent,
        )

        assertEquals("autre raison", context()["rejection_reason"])
        assertNull(context()["comment"])
    }

    @Test
    fun `a listing without a title`() {
        mailet.send(match.copy(listing = listing.copy(title = null)), customer, agent)

        assertEquals("Bien n° 102", context()["listing_title"])
    }

    @Test
    fun `no Partner Central - no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        mailet.send(match, customer, agent)

        assertNull(context()["mandate_url"])
    }

    private fun context(): Map<String, Any?> {
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq(MatchAnsweredMailet.TEMPLATE), context.capture())
        return context.firstValue
    }
}
