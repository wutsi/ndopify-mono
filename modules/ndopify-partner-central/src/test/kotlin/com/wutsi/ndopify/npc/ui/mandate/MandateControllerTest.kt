package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.npc.client.ndopify.NdAssignmentClient
import com.wutsi.ndopify.npc.client.ndopify.NdContractClient
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.CONTRACT
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.MANDATE
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.ACCEPTED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.PENDING
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MandateControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val contractClient = mock<NdContractClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val assignmentClient = mock<NdAssignmentClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(NOW).whenever(clock).millis()
        doReturn(PENDING).whenever(mandateClient).get(201)
        doReturn(CONTRACT).whenever(contractClient).get("c-201")
        doReturn(CUSTOMER).whenever(customerClient).get(555)
        doReturn(ACCEPTED).whenever(assignmentClient).accepted(201)

        val controller = MandateController(
            currentAgent,
            mandateClient,
            contractClient,
            customerClient,
            assignmentClient,
            locationClient,
            tenantContext,
            clock,
            7,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun mandate(result: MvcResult) = model(result)["mandate"] as MandateView

    @Test
    fun show() {
        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        val view = mandate(result)
        assertEquals(201L, view.id)
        assertEquals("22 octobre 2026", view.signatureDeadline)
        assertEquals("ray@gmail.com", view.contact?.email)
        assertEquals("10 octobre 2026", view.acceptedAt)
        assertEquals("Secteurs" to "Bonapriso, Akwa — Douala", view.keyCriteria[0])
        assertEquals("SN", model(result)["initials"])
        assertNull(model(result)["locked"])
        assertNull(model(result)["end"])
    }

    @Test
    fun `not found - locked`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)

        val result = mvc.perform(get("/mandates/201")).andReturn()

        assertEquals("mandate", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["mandate"])
    }

    @Test
    fun `malformed id - locked`() {
        val result = mvc.perform(get("/mandates/abc")).andReturn()

        assertEquals(true, model(result)["locked"])
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `pending assignment - locked`() {
        doReturn(MANDATE.copy(status = MandateStatus.PENDING_ASSIGNMENT)).whenever(mandateClient).get(201)

        assertEquals(true, model(mvc.perform(get("/mandates/201")).andReturn())["locked"])
    }

    @Test
    fun `contract unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(contractClient).get(any())

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.signatureDeadline)
        assertEquals("ray@gmail.com", view.contact?.email)
    }

    @Test
    fun `no contract - not fetched`() {
        doReturn(MANDATE).whenever(mandateClient).get(201)

        mvc.perform(get("/mandates/201")).andReturn()

        verify(contractClient, never()).get(any())
    }

    @Test
    fun `customer unavailable`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(customerClient).get(any())

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertNull(view.contact)
        assertEquals("22 octobre 2026", view.signatureDeadline)
    }

    @Test
    fun `accepted assignment unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(assignmentClient).accepted(any())

        assertNull(mandate(mvc.perform(get("/mandates/201")).andReturn()).acceptedAt)
    }

    @Test
    fun `no accepted assignment`() {
        doReturn(null).whenever(assignmentClient).accepted(any())

        assertNull(mandate(mvc.perform(get("/mandates/201")).andReturn()).acceptedAt)
    }

    @Test
    fun `location names unavailable`() {
        whenever(locationClient.search(any())).thenThrow(ResourceAccessException("timeout"))

        val view = mandate(mvc.perform(get("/mandates/201")).andReturn())

        assertEquals("Secteurs" to "—", view.keyCriteria[0])
    }

    @Test
    fun `end - cancel open`() {
        assertEquals("cancel", model(mvc.perform(get("/mandates/201?end=cancel")).andReturn())["end"])
    }

    @Test
    fun `end - close ignored before approval`() {
        assertNull(model(mvc.perform(get("/mandates/201?end=close")).andReturn())["end"])
    }

    @Test
    fun `end - close open once approved`() {
        doReturn(PENDING.copy(status = MandateStatus.APPROVED)).whenever(mandateClient).get(201)

        assertEquals("close", model(mvc.perform(get("/mandates/201?end=close")).andReturn())["end"])
    }

    @Test
    fun `end - ignored once closed`() {
        doReturn(PENDING.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        assertNull(model(mvc.perform(get("/mandates/201?end=cancel")).andReturn())["end"])
    }

    @Test
    fun `end - unknown ignored`() {
        assertNull(model(mvc.perform(get("/mandates/201?end=xyz")).andReturn())["end"])
    }

    @Test
    fun `not an agent - sign out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/logout", mvc.perform(get("/mandates/201")).andReturn().modelAndView?.viewName)
    }
}
