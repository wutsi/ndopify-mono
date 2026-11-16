package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.RequestMandateApprovalRequest
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.refdata.dto.Money
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateContractControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(MANDATE).whenever(mandateClient).get(201)
        doReturn(CUSTOMER).whenever(customerClient).get(CUSTOMER.id!!)

        val controller = MandateContractController(
            currentAgent,
            mandateClient,
            contractClient,
            customerClient,
            tenantContext,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun outcome(result: MvcResult) = result.flashMap[MandateController.OUTCOME]

    private fun form(vararg params: Pair<String, String>): MockHttpServletRequestBuilder {
        val builder = post("/mandates/201/contract")
        params.forEach { (name, value) -> builder.param(name, value) }
        return builder
    }

    private fun conflict(code: String? = null): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code ?: ErrorCode.MANDATE_VERSION_CONFLICT)) }
        return ex
    }

    // Show

    @Test
    fun `show - pre-fills from the contract`() {
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT.copy(durationMonths = 3, visitFee = Money(5000, "XAF"))).whenever(contractClient).get("c-201")

        val result = mvc.perform(get("/mandates/201/contract")).andReturn()

        assertEquals("mandate/contract", result.modelAndView?.viewName)
        assertEquals(MandateContractForm("3", "5000"), model(result)["form"])
        assertEquals(true, model(result)["resend"])
        assertEquals("ray@gmail.com", model(result)["email"])
    }

    @Test
    fun `show - 6 months and no fee without a contract`() {
        val result = mvc.perform(get("/mandates/201/contract")).andReturn()

        assertEquals("mandate/contract", result.modelAndView?.viewName)
        assertEquals(MandateContractForm("6", "0"), model(result)["form"])
        assertEquals(false, model(result)["resend"])
        assertNull(model(result)["signatureTtlDays"])
    }

    @Test
    fun `show - a contract from before the visit fee`() {
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT).whenever(contractClient).get("c-201")

        val result = mvc.perform(get("/mandates/201/contract")).andReturn()

        assertEquals(MandateContractForm("6", "0"), model(result)["form"])
    }

    @Test
    fun `show - redirects a mandate that can't be sent`() {
        doReturn(PENDING.copy(status = MandateStatus.APPROVED)).whenever(mandateClient).get(201)

        assertEquals(
            "redirect:/mandates/201",
            mvc.perform(get("/mandates/201/contract")).andReturn().modelAndView?.viewName,
        )
    }

    @Test
    fun `show - an unknown or non-numeric id goes to the home page`() {
        assertEquals("redirect:/", mvc.perform(get("/mandates/abc/contract")).andReturn().modelAndView?.viewName)

        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(202)
        assertEquals("redirect:/", mvc.perform(get("/mandates/202/contract")).andReturn().modelAndView?.viewName)
    }

    // Send

    @Test
    fun `send - posts the terms and redirects to the mandate`() {
        val result = mvc.perform(form("durationMonths" to "3", "visitFee" to "5 000")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("sent", outcome(result))
        verify(mandateClient).requestApproval(201, RequestMandateApprovalRequest(3, Money(5000, "XAF")))
    }

    @Test
    fun `send - sending again says resent`() {
        doReturn(PENDING).whenever(mandateClient).get(201)

        val result = mvc.perform(form("durationMonths" to "6", "visitFee" to "0")).andReturn()

        assertEquals("resent", outcome(result))
    }

    @Test
    fun `send - a blank duration is a field error`() {
        mvc.perform(form("durationMonths" to "", "visitFee" to "")).andReturn()

        verify(mandateClient, never()).requestApproval(any(), any())
    }

    @Test
    fun `send - invalid terms show the page again`() {
        val result = mvc.perform(form("durationMonths" to "13", "visitFee" to "abc")).andReturn()

        assertEquals("mandate/contract", result.modelAndView?.viewName)
        assertEquals(MandateContractForm("13", "abc"), model(result)["form"])
        assertTrue(errors(result).containsKey("durationMonths"))
        assertTrue(errors(result).containsKey("visitFee"))
        verify(mandateClient, never()).requestApproval(any(), any())
    }

    @Test
    fun `send - a mandate gone while the form was shown is changed`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(form("durationMonths" to "13", "visitFee" to "0")).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", outcome(result))
    }

    @Test
    fun `send - conflict, server error and e-mail conflict`() {
        doThrow(conflict()).whenever(mandateClient).requestApproval(any(), any())
        assertEquals("changed", outcome(mvc.perform(form("durationMonths" to "6", "visitFee" to "0")).andReturn()))

        doThrow(conflict(ErrorCode.CONTRACT_PARTY_MEMBERSHIP_CONFLICT))
            .whenever(mandateClient).requestApproval(any(), any())
        assertEquals(
            "email-conflict",
            outcome(mvc.perform(form("durationMonths" to "6", "visitFee" to "0")).andReturn()),
        )

        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(mandateClient).requestApproval(any(), any())
        assertEquals("error", outcome(mvc.perform(form("durationMonths" to "6", "visitFee" to "0")).andReturn()))

        doThrow(ResourceAccessException("down")).whenever(mandateClient).requestApproval(any(), any())
        assertEquals("error", outcome(mvc.perform(form("durationMonths" to "6", "visitFee" to "0")).andReturn()))
    }

    @Test
    fun `send - not signed in`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals(
            "redirect:/login",
            mvc.perform(form("durationMonths" to "6", "visitFee" to "0")).andReturn().modelAndView?.viewName,
        )
    }

    @Test
    fun `show - not signed in`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201/contract")).andReturn().modelAndView?.viewName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>
}
