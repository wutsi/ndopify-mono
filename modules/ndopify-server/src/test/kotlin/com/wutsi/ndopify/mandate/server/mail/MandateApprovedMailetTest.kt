package com.wutsi.ndopify.mandate.server.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.mandate.dto.MandateType
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
import kotlin.test.assertNull

class MandateApprovedMailetTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val mailService = mock<MailService>()
    private val mailBodyResolver = mock<MailBodyResolver>()
    private val mailet = MandateApprovedMailet(tenantService, locationService, mailService, mailBodyResolver)

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
        customerId = 10L,
        agentId = 20L,
        contractId = "c-1",
        mandateType = MandateType.SEARCH,
        cityId = 2370201L,
        neighborhoodIds = listOf(111L, 222L),
        listingType = ListingType.RENTAL,
        propertyType = PropertyType.APARTMENT,
        price = 1500000,
        currencyCode = "XAF",
        pricePeriodicityType = PeriodicityType.MONTHLY,
    )
    private val contract = ContractEntity(
        id = "c-1",
        number = "MNDT-2026-000001",
        // 23:30 in Douala: the dates are the tenant's
        validFrom = date("2026-10-15T23:30"),
        validUntil = date("2027-04-15T23:30"),
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible", email = "kim.possible@gmail.com"),
    )

    private fun date(local: String): Date =
        Date.from(LocalDateTime.parse(local).atZone(ZoneId.of("Africa/Douala")).toInstant())

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(LocationEntity(id = 222L, name = "Mvan")).whenever(locationService).findByIdOrNull(222L)
        doReturn("<html>approved</html>").whenever(mailBodyResolver).resolve(any(), any())
    }

    private fun context(mandate: MandateEntity = this.mandate, contract: ContractEntity = this.contract): Map<String, Any?> {
        mailet.send(mandate, contract, customer, agent)
        val context = argumentCaptor<Map<String, Any?>>()
        verify(mailBodyResolver).resolve(eq("/mail/mandate/approved.mjml"), context.capture())
        return context.firstValue
    }

    @Test
    fun `send the approval to the agent`() {
        assertEquals(
            mapOf(
                "tenant_logo_url" to "https://cdn.ndopify.com/logo.png",
                "tenant_name" to "Ndopify",
                "recipient_name" to "Kim Possible",
                "customer_name" to "Ray Sponsible",
                "contract_number" to "MNDT-2026-000001",
                "criteria_label" to "Recherche",
                "listing_type" to "Location",
                "property_type" to "Appartement",
                "city" to "Yaounde",
                "neighbourhoods" to "Bastos, Mvan",
                "budget" to "1 500 000 XAF / mois",
                "valid_from" to "15/10/2026",
                "valid_until" to "15/04/2027",
                "next_step" to "Vous pouvez maintenant démarrer la recherche et organiser les visites avec votre client.",
                "mandate_url" to "https://partner.ndopify.com/mandates/1",
            ),
            context(),
        )

        val email = argumentCaptor<Email>()
        verify(mailService).send(email.capture())
        assertEquals("kim.possible@gmail.com", email.firstValue.recipient.email)
        assertEquals("Kim Possible", email.firstValue.recipient.displayName)
        assertEquals("Ndopify: Votre mandat à Yaounde est approuvé", email.firstValue.subject)
        assertEquals("<html>approved</html>", email.firstValue.body)
    }

    @Test
    fun `a listing mandate`() {
        val context = context(mandate.copy(mandateType = MandateType.LISTING))

        assertEquals("Bien", context["criteria_label"])
        assertEquals(
            "Vous pouvez maintenant commercialiser le bien et organiser les visites avec les candidats.",
            context["next_step"],
        )
    }

    @Test
    fun `contract without validity - no dates`() {
        val context = context(contract = contract.copy(validFrom = null, validUntil = null))

        assertNull(context["valid_from"])
        assertNull(context["valid_until"])
    }

    @Test
    fun `no partner central - no link`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        assertNull(context()["mandate_url"])
    }
}
