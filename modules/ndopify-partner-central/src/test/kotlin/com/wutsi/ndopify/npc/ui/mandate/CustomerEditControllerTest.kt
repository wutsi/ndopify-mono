package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.customer.dto.UpdateCustomerRequest
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
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
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomerEditControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(mock(), tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(NOW).whenever(clock).millis()
        doReturn(MANDATE).whenever(mandateClient).get(201)
        doReturn(CUSTOMER).whenever(customerClient).get(555)

        val controller = CustomerEditController(currentAgent, mandateClient, customerClient, tenantContext, clock)
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    @Test
    fun `show - pre-filled`() {
        val result = mvc.perform(get("/mandates/201/customer/edit")).andReturn()

        assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
        assertEquals(
            CustomerEditForm("Ray", "Sponsible", "ray@gmail.com", "+237699123456"),
            model(result)["answers"],
        )
        assertNull(model(result)["locked"])
        assertEquals(201L, model(result)["mandateId"])
    }

    @Test
    fun `show - not found`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201/customer/edit")).andReturn()

        assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
        assertEquals(CustomerEditController.LOCKED_UNAVAILABLE, model(result)["locked"])
        assertNull(model(result)["answers"])
        verify(customerClient, never()).get(any())
    }

    @Test
    fun `show - another agent's mandate is not fetched any further`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201/customer/edit")).andReturn()

        assertEquals(CustomerEditController.LOCKED_UNAVAILABLE, model(result)["locked"])
        verify(customerClient, never()).get(any())
    }

    @Test
    fun `show - customer unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(customerClient).get(555)

        val result = mvc.perform(get("/mandates/201/customer/edit")).andReturn()

        assertEquals(CustomerEditController.LOCKED_UNAVAILABLE, model(result)["locked"])
        assertNull(model(result)["answers"])
    }

    @Test
    fun `show - closed`() {
        doReturn(MANDATE.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201/customer/edit")).andReturn()

        assertEquals(CustomerEditController.LOCKED_CLOSED, model(result)["locked"])
        assertNull(model(result)["answers"])
    }

    @Test
    fun save() {
        val result = mvc.perform(
            post("/mandates/201/customer/edit")
                .param("firstName", "Raymond")
                .param("lastName", "Sponsible")
                .param("email", "raymond@example.com")
                .param("whatsappNumber", "+237699000111"),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("customer-saved", result.flashMap["outcome"])
        verify(customerClient).update(
            201,
            UpdateCustomerRequest("Raymond", "Sponsible", "raymond@example.com", "+237699000111"),
        )
    }

    @Test
    fun `save - pending approval, e-mail changed`() {
        doReturn(PENDING).whenever(mandateClient).get(201)

        val result = mvc.perform(
            post("/mandates/201/customer/edit")
                .param("firstName", "Raymond")
                .param("lastName", "Sponsible")
                .param("email", "raymond@example.com")
                .param("whatsappNumber", "+237699000111"),
        ).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("customer-saved-unsent", result.flashMap["outcome"])
    }

    @Test
    fun `save - pending approval, same e-mail in another case`() {
        doReturn(PENDING).whenever(mandateClient).get(201)

        val result = mvc.perform(
            post("/mandates/201/customer/edit")
                .param("firstName", "Raymond")
                .param("lastName", "Sponsible")
                .param("email", "RAY@GMAIL.COM")
                .param("whatsappNumber", "+237699000111"),
        ).andReturn()

        assertEquals("customer-saved", result.flashMap["outcome"])
    }

    @Test
    fun `save - invalid`() {
        val result = mvc.perform(
            post("/mandates/201/customer/edit")
                .param("firstName", "")
                .param("lastName", "Sponsible")
                .param("email", "ray@")
                .param("whatsappNumber", ""),
        ).andReturn()

        assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
        val errors = errors(result)
        assertTrue(errors.isNotEmpty())
        assertNotNull(errors["firstName"])
        assertNotNull(errors["email"])
        assertNotNull(errors["whatsappNumber"])
        // The agent's answers are kept
        assertEquals("", (model(result)["answers"] as CustomerEditForm).firstName)
        verify(customerClient, never()).update(any(), any())
    }

    @Test
    fun `save - e-mail conflict`() {
        doThrow(conflict(ErrorCode.PARTY_EMAIL_ALREADY_EXISTS)).whenever(customerClient).update(any(), any())

        val result = mvc.perform(valid("taken@example.com")).andReturn()

        assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
        assertNotNull(errors(result)["email"])
        assertEquals("taken@example.com", (model(result)["answers"] as CustomerEditForm).email)
    }

    @Test
    fun `save - moved on`() {
        doThrow(conflict(ErrorCode.MANDATE_ALREADY_CLOSED)).whenever(customerClient).update(any(), any())

        val result = mvc.perform(valid()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", result.flashMap["outcome"])
    }

    @Test
    fun `save - mandate became another agent's`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(201)

        val result = mvc.perform(valid()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", result.flashMap["outcome"])
        verify(customerClient, never()).update(any(), any())
    }

    @Test
    fun `save - mandate closed meanwhile`() {
        doReturn(MANDATE.copy(status = MandateStatus.CANCELLED)).whenever(mandateClient).get(201)

        val result = mvc.perform(valid()).andReturn()

        assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
        assertEquals("changed", result.flashMap["outcome"])
        verify(customerClient, never()).update(any(), any())
    }

    @Test
    fun `save - server failure`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(customerClient).update(any(), any())

        val result = mvc.perform(valid()).andReturn()

        assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
        assertNotNull(model(result)["error"])
        assertEquals("Raymond", (model(result)["answers"] as CustomerEditForm).firstName)
    }

    @Test
    fun `save - network failure`() {
        doThrow(ResourceAccessException("boom")).whenever(customerClient).update(any(), any())

        val result = mvc.perform(valid()).andReturn()

        assertEquals("mandate/customer-edit", result.modelAndView?.viewName)
        assertNotNull(model(result)["error"])
    }

    private fun valid(email: String = "raymond@example.com"): MockHttpServletRequestBuilder =
        post("/mandates/201/customer/edit")
            .param("firstName", "Raymond")
            .param("lastName", "Sponsible")
            .param("email", email)
            .param("whatsappNumber", "+237699000111")

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }
}
