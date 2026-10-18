package com.wutsi.ndopify.npc.ui.home

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.ASSIGNMENT
import com.wutsi.ndopify.npc.ui.home.AssignmentViewsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardView
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
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
    private val contractClient = mock<NdContractClient>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(AssignmentViewsTest.NOW).whenever(clock).millis()
        doReturn(emptyList<AssignmentSummary>()).whenever(assignmentClient).pending(any())
        doReturn(emptyList<MandateSummary>()).whenever(mandateClient).active(any())

        val controller = HomeController(
            currentAgent,
            kycClient,
            assignmentClient,
            mandateClient,
            contractClient,
            locationClient,
            tenantContext,
            clock,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Test
    fun show() {
        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals("Serge Ngono", model(result)["fullName"])
        assertEquals("SN", model(result)["initials"])
        assertEquals("https://cdn.ndopify.com/party/100/photo/a.jpg", model(result)["photoUrl"])
        assertEquals("https://cdn.ndopify.com/party/100/photo/a.jpg", model(result)["userPhotoUrl"])
        assertEquals("Démarcheur à Douala depuis 6 ans.", model(result)["biography"])
        assertEquals("Bonapriso, Akwa — Douala", model(result)["area"])
        assertNull(model(result)["kyc"])
        verify(kycClient, never()).current(any())
    }

    @Test
    fun `incomplete profile`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(photoUrl = " "), biography = "  ")).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["photoUrl"])
        assertNull(model(result)["userPhotoUrl"])
        assertNull(model(result)["biography"])
    }

    @Test
    fun `no area`() {
        doReturn(AGENT.copy(cityId = null, neighborhoodIds = emptyList())).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertNull(model(result)["area"])
    }

    @Test
    fun `location names unavailable - page shown without the area`() {
        whenever(locationClient.search(any())).thenThrow(ResourceAccessException("timeout"))

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertNull(model(result)["area"])
    }

    private fun notVerified(case: KycCase?): MvcResult {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()
        doReturn(case).whenever(kycClient).current(100L)
        return mvc.perform(get("/")).andReturn()
    }

    @ParameterizedTest
    @EnumSource(value = KycStatus::class, names = ["PENDING", "CANCELLED"])
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
    @EnumSource(value = KycStatus::class, names = ["SUBMITTED", "IN_PROGRESS", "REQUIRES_MANUAL_REVIEW"])
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
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()
        whenever(kycClient.current(any())).thenThrow(ResourceAccessException("timeout"))

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("index", result.modelAndView?.viewName)
        assertEquals(HomeController.KYC_TODO, model(result)["kyc"])
    }

    @Test
    fun `not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("redirect:/logout", result.modelAndView?.viewName)
    }

    // Mandats proposés

    @Test
    fun `offers shown`() {
        val other = ASSIGNMENT.copy(id = 8, mandateId = 102)
        doReturn(listOf(ASSIGNMENT, other)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)
        doReturn(MANDATE.copy(id = 102)).whenever(mandateClient).get(102)

        val result = mvc.perform(get("/")).andReturn()

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
    }

    @Test
    fun `expired offer left out`() {
        doReturn(listOf(ASSIGNMENT.copy(expiresAt = Date(AssignmentViewsTest.NOW - 1))))
            .whenever(assignmentClient).pending(AGENT.id)

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(emptyList(), assignments(result))
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `one mandate unavailable - the other offers still show`() {
        doReturn(listOf(ASSIGNMENT, ASSIGNMENT.copy(id = 8, mandateId = 102)))
            .whenever(assignmentClient).pending(AGENT.id)
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(101)
        doReturn(MANDATE.copy(id = 102)).whenever(mandateClient).get(102)

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
    fun `location names unavailable - offers still shown`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)
        whenever(locationClient.search(any())).thenThrow(ResourceAccessException("timeout"))

        val result = mvc.perform(get("/")).andReturn()

        assertNull(assignments(result).single().sector)
    }

    @Test
    fun `not verified - no offers section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["assignments"])
        verify(assignmentClient, never()).pending(any())
    }

    @Test
    fun `decline confirmation`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)

        val result = mvc.perform(get("/").param("decline", "7")).andReturn()

        assertEquals(7L, model(result)["declining"])
    }

    @Test
    fun `decline confirmation - unknown offer ignored`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)

        val result = mvc.perform(get("/").param("decline", "999")).andReturn()

        assertNull(model(result)["declining"])
    }

    @Test
    fun `decline confirmation - malformed id ignored`() {
        doReturn(listOf(ASSIGNMENT)).whenever(assignmentClient).pending(AGENT.id)
        doReturn(MANDATE).whenever(mandateClient).get(101)

        val result = mvc.perform(get("/").param("decline", "abc")).andReturn()

        assertEquals(200, result.response.status)
        assertEquals("index", result.modelAndView?.viewName)
        assertNull(model(result)["declining"])
    }

    // Mes mandats

    private val assigned = MandateCardsTest.MANDATE
    private val pending = assigned.copy(id = 202, status = MandateStatus.PENDING_APPROVAL, contractId = "c-202")
    private val approved = assigned.copy(id = 203, status = MandateStatus.APPROVED, contractId = "c-203")

    private fun active(vararg ids: Long) {
        doReturn(ids.map { MandateSummary(id = it) }).whenever(mandateClient).active(AGENT.id)
    }

    @Test
    fun `mandates shown`() {
        active(203, 201, 202)
        doReturn(assigned).whenever(mandateClient).get(201)
        doReturn(pending).whenever(mandateClient).get(202)
        doReturn(approved).whenever(mandateClient).get(203)
        doReturn(listOf(MandateCardsTest.CONTRACT_SUMMARY.copy(id = "c-203"), MandateCardsTest.CONTRACT_2027.copy(id = "c-202")))
            .whenever(contractClient).search(any())

        val result = mvc.perform(get("/")).andReturn()

        // One search for every contract, in the mandates' order
        verify(contractClient).search(SearchContractRequest(ids = listOf("c-203", "c-202"), limit = 2))
        verify(contractClient, never()).get(any())
        val mandates = mandates(result)
        assertEquals(listOf(203L, 201L, 202L), mandates.map { it.id })
        assertEquals("Secteurs" to "Bonapriso, Akwa", mandates[1].facts[0])
        assertEquals("Mandat signé, valable jusqu'au 28 mars 2027.", mandates[0].nextStep)
        assertEquals("Le client a jusqu'au 19 janv. 2027 pour signer le mandat.", mandates[2].nextStep)
        assertEquals(false, model(result)["mandatesUnavailable"])
    }

    @Test
    fun `contract only fetched for mandates sent to the customer`() {
        active(201)
        doReturn(assigned.copy(contractId = "c-201")).whenever(mandateClient).get(201)

        mvc.perform(get("/")).andReturn()

        verify(contractClient, never()).search(any())
    }

    @Test
    fun `one mandate unavailable - the others still show`() {
        active(201, 202)
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)
        doReturn(pending).whenever(mandateClient).get(202)
        doReturn(listOf(MandateCardsTest.CONTRACT_2027.copy(id = "c-202"))).whenever(contractClient).search(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals(listOf(202L), mandates(result).map { it.id })
        verify(contractClient).search(SearchContractRequest(ids = listOf("c-202"), limit = 1))
    }

    @Test
    fun `contract unavailable - card kept without the date`() {
        active(203)
        doReturn(approved).whenever(mandateClient).get(203)
        doThrow(ResourceAccessException("timeout")).whenever(contractClient).search(any())

        val result = mvc.perform(get("/")).andReturn()

        assertEquals("Mandat signé.", mandates(result).single().nextStep)
    }

    @Test
    fun `contract not returned - card kept without the date`() {
        active(202, 203)
        doReturn(pending).whenever(mandateClient).get(202)
        doReturn(approved).whenever(mandateClient).get(203)
        doReturn(listOf(MandateCardsTest.CONTRACT_SUMMARY.copy(id = "c-203"))).whenever(contractClient).search(any())

        val result = mvc.perform(get("/")).andReturn()

        val mandates = mandates(result)
        assertEquals("Le client doit signer le mandat.", mandates[0].nextStep)
        assertEquals("Mandat signé, valable jusqu'au 28 mars 2027.", mandates[1].nextStep)
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
        doReturn(MANDATE).whenever(mandateClient).get(101)
        active(201)
        doReturn(assigned).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/")).andReturn()

        // One for the profile's area, one for the cards.
        verify(locationClient, times(2)).search(any())
        assertEquals("Bonapriso, Akwa", assignments(result).single().sector)
        assertEquals("Bonapriso, Akwa", mandates(result).single().facts[0].second)
    }

    @Test
    fun `not verified - no mandates section`() {
        doReturn(AGENT.copy(party = AGENT.party.copy(kycStatus = KycStatus.PENDING))).whenever(currentAgent).get()

        val result = mvc.perform(get("/")).andReturn()

        assertNull(model(result)["mandates"])
        verify(mandateClient, never()).active(any())
    }

    @Suppress("UNCHECKED_CAST")
    private fun mandates(result: MvcResult) = model(result)["mandates"] as List<MandateCardView>

    @Suppress("UNCHECKED_CAST")
    private fun assignments(result: MvcResult) = model(result)["assignments"] as List<AssignmentView>
}
