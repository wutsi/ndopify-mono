package com.wutsi.ndopify.npc.ui.home

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val kycClient = mock<NdKycClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()

        mvc = MockMvcBuilders.standaloneSetup(HomeController(currentAgent, kycClient, locationClient, tenantContext))
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
        assertEquals("serge@gmail.com", model(result)["email"])
        assertEquals("https://cdn.ndopify.com/party/100/photo/a.jpg", model(result)["photoUrl"])
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
}
