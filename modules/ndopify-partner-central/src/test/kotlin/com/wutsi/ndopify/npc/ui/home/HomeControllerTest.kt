package com.wutsi.ndopify.npc.ui.home

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.ASSIGNMENT
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.mandate.MandateCardView
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest
import com.wutsi.ndopify.npc.ui.mandate.MandateSummariesTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.visit.VisitCardsView
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.ui.ExtendedModelMap
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val kycClient = mock<NdKycClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val listingClient = mock<NdListingClient>()
    private val visitClient = mock<NdVisitClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val clock = mock<Clock>()
    private val parallel = Parallel()
    private lateinit var mvc: MockMvc
    private lateinit var controller: HomeController

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(AssignmentViewsTest.NOW).whenever(clock).millis()
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(any())
        doReturn(emptyList<MandateSummary>()).whenever(mandateClient).active(any())
        doReturn(emptyList<VisitSummary>()).whenever(visitClient).search(any(), any(), any(), anyOrNull())
        doReturn(emptyList<CustomerSummary>()).whenever(customerClient).search(any())

        controller = HomeController(
            currentAgent,
            kycClient,
            assignmentClient,
            mandateClient,
            listingClient,
            visitClient,
            customerClient,
            locationClient,
            tenantContext,
            clock,
            parallel,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    @AfterEach
    fun closeParallel() = parallel.close()

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Test
    fun show() {
        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals("SN", model(result)["initials"])
        assertEquals("https://cdn.ndopify.com/party/100/photo/a.jpg", model(result)["userPhotoUrl"])
        assertEquals(false, model(result)["photoMissing"])
        assertEquals(false, model(result)["biographyMissing"])
        assertNull(model(result)["kyc"])
        verify(kycClient, never()).current(any())
    }

    @Test
    fun `incomplete profile`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = " "), biography = "  ")).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(true, model(result)["photoMissing"])
        assertNull(model(result)["userPhotoUrl"])
        assertEquals(true, model(result)["biographyMissing"])
    }

    private fun notVerified(case: KycCase?): MvcResult {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent).get()
        doReturn(case).whenever(kycClient).current(100L)
        return mvc.perform(get("/")).andReturn()
    }

    @ParameterizedTest
    @EnumSource(value = KycStatus::class, names = ["PENDING_VERIFICATION", "CANCELLED"])
    fun `not verified - documents to send`(status: KycStatus) {
        val result = notVerified(KycCase(id = "case-1", status = status))

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(HomeController.KYC_TODO, model(result)["kyc"])
    }

    @Test
    fun `not verified - no case yet`() {
        assertEquals(HomeController.KYC_TODO, model(notVerified(null))["kyc"])
    }

    @ParameterizedTest
    @EnumSource(value = KycStatus::class, names = ["SUBMITTED", "UNDER_VERIFICATION", "REQUIRES_MANUAL_VERIFICATION"])
    fun `not verified - under review`(status: KycStatus) {
        assertEquals(HomeController.KYC_PENDING, model(notVerified(KycCase(id = "case-1", status = status)))["kyc"])
    }

    @Test
    fun `not verified - rejected`() {
        val result = notVerified(KycCase(id = "case-1", status = KycStatus.REJECTED))

        assertEquals(HomeController.KYC_REJECTED, model(result)["kyc"])
    }

    @Test
    fun `not verified - case unavailable - banner still shown`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent).get()
        whenever(kycClient.current(any())).thenThrow(ResourceAccessException("timeout"))

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(HomeController.KYC_TODO, model(result)["kyc"])
    }

    @Test
    fun `not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("redirect:/login", result.modelAndView?.viewName)
    }

    // Mandats proposés

    private fun offered(vararg mandates: Mandate) {
        doReturn(mandates.map { MandateSummariesTest.summary(it) }).whenever(mandateClient).offered(AGENT.id)
    }

    @Test
    fun `offers shown`() {
        val other = ASSIGNMENT.copy(id = 8, mandateId = 102)
        doReturn(listOf(ASSIGNMENT, other)).whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE.copy(id = 102), MANDATE)

        val result = mvc.perform(get("/")).andReturn()

        // Their mandates in one search, whatever its order
        verify(mandateClient).offered(AGENT.id)
        verify(mandateClient, never()).get(any())
        val assignments = assignments(result)
        assertEquals(listOf(7L, 8L), assignments.map { it.id })
        assertEquals("Bonapriso, Akwa", assignments[0].sector)
        assertEquals(false, model(result)["assignmentsUnavailable"])
        assertNull(model(result)["declining"])
    }

    @Test
    fun `no offer`() {
        val result = mvc.perform(get("/")).andReturn()

        assertEquals(emptyList(), assignments(result))
        verify(mandateClient, never()).offered(any())
    }

    @Test
    fun `expired offer left out`() {
        doReturn(listOf(ASSIGNMENT.copy(expiresAt = Date(AssignmentViewsTest.NOW - 1))))
            .whenever(assignmentClient).pending(AGENT.id)

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(emptyList(), assignments(result))
        verify(mandateClient, never()).offered(any())
    }

    @Test
    fun `mandate not returned - the other offers still show`() {
        doReturn(listOf(ASSIGNMENT, ASSIGNMENT.copy(id = 8, mandateId = 102)))
            .whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE.copy(id = 102))

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(listOf(8L), assignments(result).map { it.id })
    }

    @Test
    fun `offers unavailable - page still shown`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).pending(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(emptyList(), assignments(result))
        assertEquals(true, model(result)["assignmentsUnavailable"])
    }

    @Test
    fun `offered mandates unavailable - page still shown`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doThrow(ResourceAccessException("timeout")).whenever(mandateClient).offered(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(emptyList(), assignments(result))
        assertEquals(true, model(result)["assignmentsUnavailable"])
    }

    @Test
    fun `location names unavailable - offers still shown`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE)
        whenever(locationClient.search(any())).thenThrow(ResourceAccessException("timeout"))

        val result = mvc.perform(get("/")).andReturn()

        assertNull(assignments(result).single().sector)
    }

    @Test
    fun `not verified - no offers section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["assignments"])
        verify(assignmentClient, never()).pending(any())
    }

    @Test
    fun `decline confirmation`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE)

        val result = mvc.perform(get("/").param("decline", "7")).andReturn()

        assertEquals(7L, model(result)["declining"])
    }

    @Test
    fun `decline confirmation - unknown offer ignored`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE)

        val result = mvc.perform(get("/").param("decline", "999")).andReturn()

        assertNull(model(result)["declining"])
    }

    @Test
    fun `decline confirmation - malformed id ignored`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE)

        val result = mvc.perform(get("/").param("decline", "abc")).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("index", result.modelAndView?.viewName)
        assertNull(model(result)["declining"])
    }

    // Mes mandats

    private val assigned = MandateCardsTest.MANDATE
    private val pending = assigned.copy(id = 202, status = MandateStatus.PENDING_APPROVAL, contractId = "c-202")
    private val approved = assigned.copy(id = 203, status = MandateStatus.APPROVED, contractId = "c-203")

    private fun active(vararg mandates: Mandate) {
        doReturn(mandates.map { MandateSummariesTest.summary(it) }).whenever(mandateClient).active(AGENT.id)
    }

    @Test
    fun `mandates shown`() {
        active(approved, assigned, pending)

        val result = mvc.perform(get("/")).andReturn()

        // The mandates aren't loaded one by one
        verify(mandateClient, never()).get(any())
        val mandates = mandates(result)
        assertEquals(listOf(203L, 201L, 202L), mandates.map { it.id })
        assertEquals("Secteurs" to "Bonapriso, Akwa", mandates[1].facts[0])
        assertEquals(false, model(result)["mandatesUnavailable"])
    }

    @Test
    fun `mandate stats - one call for the approved searches`() {
        active(approved, landlord, assigned.copy(id = 204, status = MandateStatus.APPROVED))
        doReturn(mapOf(203L to MandateStats(203, 2, 1, 0, 2), 204L to MandateStats(204, 0, 0, 0, 0)))
            .whenever(mandateClient).stats(any())

        val result = mvc.perform(get("/")).andReturn()

        verify(mandateClient).stats(listOf(203L, 204L))
        assertEquals("2 biens · 1 intéressé · 2 visites", mandates(result)[0].searchStats?.label)
        assertEquals("Aucun bien correspondant pour l'instant", mandates(result)[2].searchStats?.label)
    }

    @Test
    fun `mandate stats unavailable - cards kept without the line`() {
        active(approved)
        doThrow(ResourceAccessException("timeout")).whenever(mandateClient).stats(any())

        val result = mvc.perform(get("/")).andReturn()

        assertNull(mandates(result).single().searchStats)
        assertEquals(false, model(result)["mandatesUnavailable"])
    }

    @Test
    fun `no approved search - no mandate stats call`() {
        active(assigned)

        mvc.perform(get("/")).andReturn()

        verify(mandateClient, never()).stats(any())
    }

    @Test
    fun `mandates unavailable - page still shown`() {
        doThrow(ResourceAccessException("timeout")).whenever(mandateClient).active(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(emptyList(), mandates(result))
        assertEquals(true, model(result)["mandatesUnavailable"])
    }

    @Test
    fun `location names fetched once for offers and mandates`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        offered(MANDATE)
        active(assigned)

        val result = mvc.perform(get("/")).andReturn()

        verify(locationClient).search(any())
        assertEquals("Bonapriso, Akwa", assignments(result).single().sector)
        assertEquals("Bonapriso, Akwa", mandates(result).single().facts[0].second)
    }

    private val landlord = approved.copy(id = 205, mandateType = MandateType.LISTING, contractId = null)

    @Test
    fun `listings of the landlords' signed mandates - one search`() {
        active(approved, landlord, landlord.copy(id = 206))
        doReturn(listOf(ListingSummary(id = 501, mandateId = 205, status = ListingStatus.DRAFT)))
            .whenever(listingClient).searchByMandates(any(), any())

        val result = mvc.perform(get("/")).andReturn()

        verify(listingClient).searchByMandates(listOf(205L, 206L), AGENT.id)
        val mandates = mandates(result)
        assertNull(mandates[0].listing)
        assertEquals("/listings/501", mandates[1].listing?.url)
        assertEquals("/mandates/206", mandates[2].listing?.url)
    }

    @Test
    fun `no landlord's signed mandate - no listing search`() {
        active(approved, landlord.copy(status = MandateStatus.ASSIGNED))

        mvc.perform(get("/")).andReturn()

        verify(listingClient, never()).searchByMandates(any(), any())
    }

    @Test
    fun `listings unavailable - cards kept without the listing line`() {
        active(landlord)
        doThrow(ResourceAccessException("timeout")).whenever(listingClient).searchByMandates(any(), any())

        val result = mvc.perform(get("/")).andReturn()

        val card = mandates(result).single()
        assertNull(card.listing)
        assertEquals(false, model(result)["mandatesUnavailable"])
    }

    @Test
    fun `published listings - one stats search`() {
        active(landlord, landlord.copy(id = 206), landlord.copy(id = 207))
        doReturn(
            listOf(
                ListingSummary(id = 501, mandateId = 205, status = ListingStatus.PUBLISHED),
                ListingSummary(id = 502, mandateId = 206, status = ListingStatus.PUBLISHED),
                ListingSummary(id = 503, mandateId = 207, status = ListingStatus.DRAFT),
            ),
        ).whenever(listingClient).searchByMandates(any(), any())
        doReturn(
            mapOf(501L to ListingStats(501, 2, 1, 2), 502L to ListingStats(502, 0, 0, 0)),
        ).whenever(listingClient).stats(listOf(501L, 502L))

        val result = mvc.perform(get("/")).andReturn()

        verify(listingClient).stats(listOf(501L, 502L))
        val mandates = mandates(result)
        assertEquals("2 personnes intéressées · 3 visites", mandates[0].stats)
        assertEquals("Aucune personne intéressée pour l'instant · Aucune visite", mandates[1].stats)
        assertNull(mandates[2].stats)
    }

    @Test
    fun `no published listing - no stats search`() {
        active(landlord)
        doReturn(listOf(ListingSummary(id = 501, mandateId = 205, status = ListingStatus.DRAFT)))
            .whenever(listingClient).searchByMandates(any(), any())

        val result = mvc.perform(get("/")).andReturn()

        verify(listingClient, never()).stats(any())
        assertNull(mandates(result).single().stats)
    }

    @Test
    fun `stats unavailable - cards kept without the line`() {
        active(landlord)
        doReturn(listOf(ListingSummary(id = 501, mandateId = 205, status = ListingStatus.PUBLISHED)))
            .whenever(listingClient).searchByMandates(any(), any())
        doThrow(ResourceAccessException("timeout")).whenever(listingClient).stats(any())

        val result = mvc.perform(get("/")).andReturn()

        assertNull(mandates(result).single().stats)
        assertEquals("/listings/501", mandates(result).single().listing?.url)
    }

    @Test
    fun `not verified - no mandates section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["mandates"])
        verify(mandateClient, never()).active(any())
    }

    // Visites

    private fun visitCards(result: MvcResult) = model(result)["visits"] as VisitCardsView

    @Test
    fun `visits`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        doReturn(
            listOf(
                VisitFixtures.summary(VisitFixtures.requested()),
                VisitFixtures.summary(VisitFixtures.confirmed()),
            ),
        ).whenever(visitClient).search(AGENT.id, VisitRules.OPEN)
        doReturn(listOf(VisitFixtures.listingSummary())).whenever(listingClient).searchByIds(listOf(3012L))
        doReturn(listOf(VisitFixtures.customerSummary())).whenever(customerClient).search(listOf(300L))

        val result = mvc.perform(get("/")).andReturn()

        verify(visitClient).search(AGENT.id, VisitRules.OPEN)
        val visits = visitCards(result)
        assertEquals(1, visits.requested.size)
        assertEquals(1, visits.upcoming.size)
        assertEquals("Sandrine Eboa", visits.requested.single().customerName)
        assertEquals("Sur place · Appartement 3 chambres · Bonapriso", visits.requested.single().listing)
        assertEquals("Confirmée", visits.upcoming.single().tag?.label)
        assertEquals(false, visits.unavailable)
    }

    @Test
    fun `visits unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(any(), any(), any(), anyOrNull())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(true, visitCards(result).unavailable)
    }

    @Test
    fun `not verified - no visits`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING_VERIFICATION))).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["visits"])
        verify(visitClient, never()).search(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `no customers`() {
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        doReturn(listOf(VisitFixtures.summary(VisitFixtures.requested()))).whenever(visitClient).search(AGENT.id, VisitRules.OPEN)
        doThrow(ResourceAccessException("timeout")).whenever(customerClient).search(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("Client", visitCards(result).requested.single().customerName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun mandates(result: MvcResult) = model(result)["mandates"] as List<MandateCardView>

    @Suppress("UNCHECKED_CAST")
    private fun assignments(result: MvcResult) = model(result)["assignments"] as List<AssignmentView>

    @Test
    fun `model is written on the request thread`() {
        val model = RequestThreadModel(Thread.currentThread())

        controller.show(null, model)

        assertEquals("SN", model["initials"])
    }

    private class RequestThreadModel(private val requestThread: Thread) : ExtendedModelMap() {
        override fun addAttribute(attributeName: String, attributeValue: Any?): ExtendedModelMap {
            check(Thread.currentThread() === requestThread) { "Model written from ${Thread.currentThread()}" }
            return super.addAttribute(attributeName, attributeValue)
        }

        override fun addAttribute(attributeValue: Any): ExtendedModelMap {
            check(Thread.currentThread() === requestThread) { "Model written from ${Thread.currentThread()}" }
            return super.addAttribute(attributeValue)
        }
    }
}
