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
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.TenantFixtures
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.mandate.MandateCardsTest.Companion.NOW
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.APPROVED
import com.wutsi.ndopify.npc.ui.mandate.MandateViewsTest.Companion.CUSTOMER
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures
import com.wutsi.ndopify.npc.ui.visit.VisitRules
import com.wutsi.ndopify.visit.dto.CreateMatchVisitRequest
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MandateVisitControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val mandateClient = mock<NdMandateClient>()
    private val matchClient = mock<NdMatchClient>()
    private val visitClient = mock<NdVisitClient>()
    private val listingClient = mock<NdListingClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    private val match = MatchFixtures.MATCH_A.copy(mandateId = 201, status = MatchStatus.ACCEPTED)

    @BeforeEach
    fun setUp() {
        doReturn(TenantFixtures.TENANT).whenever(tenantContext).getTenant()
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(NOW).whenever(clock).millis()
        doReturn(APPROVED).whenever(mandateClient).get(201)
        doReturn(listOf(match)).whenever(matchClient).search(listOf(201), listOf(MatchStatus.ACCEPTED))
        doReturn(emptyList<VisitSummary>())
            .whenever(visitClient).searchByMatches(AGENT.id, VisitRules.OPEN, listOf(match.id))
        doReturn(listOf(MatchFixtures.LISTING_A.copy(title = "Nice apartment"))).whenever(listingClient).searchByIds(any())
        doReturn(CUSTOMER).whenever(customerClient).get(555)

        val controller = MandateVisitController(
            currentAgent,
            mandateClient,
            matchClient,
            visitClient,
            listingClient,
            customerClient,
            tenantContext,
            clock,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun outcome(result: MvcResult) = result.flashMap[MandateController.VISIT_OUTCOME]

    @Suppress("UNCHECKED_CAST")
    private fun errors(result: MvcResult) = model(result)["errors"] as Map<String, String>

    private fun form(vararg params: Pair<String, String>): MockHttpServletRequestBuilder {
        val builder = post("/mandates/201/visits")
        params.forEach { (name, value) -> builder.param(name, value) }
        return builder
    }

    private fun validForm(type: String = "ON_SITE") = form(
        "matchId" to "9001",
        "date" to "2026-10-22",
        "time" to "10:30",
        "type" to type,
    )

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    // Show

    @Test
    fun `show - the accepted matches, the slots and the empty form`() {
        val result = mvc.perform(get("/mandates/201/visits")).andReturn()

        assertEquals("mandate/visit", result.modelAndView?.viewName)
        assertEquals(APPROVED, model(result)["mandate"])
        assertEquals(listOf(MandateVisitProperty(9001, "Nice apartment — annonce n° 3012")), model(result)["properties"])
        assertEquals("Ray Sponsible", model(result)["clientName"])
        assertEquals(7, (model(result)["days"] as List<*>).size)
        assertEquals(25, (model(result)["times"] as List<*>).size)
        assertEquals(MandateVisitForm(), model(result)["form"])
        assertTrue(errors(result).isEmpty())
    }

    @Test
    fun `show - a mandate that isn't approved goes back to its page`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        assertEquals(
            "redirect:/mandates/201",
            mvc.perform(get("/mandates/201/visits")).andReturn().modelAndView?.viewName,
        )
    }

    @Test
    fun `show - no accepted match left goes back to the mandate`() {
        doReturn(emptyList<com.wutsi.ndopify.match.dto.Match>())
            .whenever(matchClient).search(listOf(201), listOf(MatchStatus.ACCEPTED))

        assertEquals(
            "redirect:/mandates/201",
            mvc.perform(get("/mandates/201/visits")).andReturn().modelAndView?.viewName,
        )
    }

    @Test
    fun `show - every accepted match has a visit, nothing to book`() {
        doReturn(listOf(VisitFixtures.summary(VisitFixtures.requested().copy(matchId = 9001))))
            .whenever(visitClient).searchByMatches(AGENT.id, VisitRules.OPEN, listOf(9001L))

        assertEquals(
            "redirect:/mandates/201",
            mvc.perform(get("/mandates/201/visits")).andReturn().modelAndView?.viewName,
        )
    }

    @Test
    fun `show - an unknown, another agent's or a non-numeric mandate goes to the home page`() {
        assertEquals("redirect:/", mvc.perform(get("/mandates/abc/visits")).andReturn().modelAndView?.viewName)

        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(202)
        assertEquals("redirect:/", mvc.perform(get("/mandates/202/visits")).andReturn().modelAndView?.viewName)

        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(203)
        assertEquals("redirect:/", mvc.perform(get("/mandates/203/visits")).andReturn().modelAndView?.viewName)
    }

    @Test
    fun `show - not signed in`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(get("/mandates/201/visits")).andReturn().modelAndView?.viewName)
    }

    // Create

    @Test
    fun `create - books the visit and goes back to the client's contact`() {
        val result = mvc.perform(validForm("VIDEO")).andReturn()

        assertEquals("redirect:/mandates/201#contact-title", result.modelAndView?.viewName)
        assertEquals(MandateController.VISIT_SCHEDULED, outcome(result))
        verify(visitClient).createForMatch(
            9001,
            CreateMatchVisitRequest(
                VisitType.VIDEO,
                Date.from(LocalDateTime.of(2026, 10, 22, 10, 30).atZone(ZoneId.of("Africa/Douala")).toInstant()),
            ),
        )
    }

    @Test
    fun `create - an invalid form shows the page again, nothing is sent`() {
        val result = mvc.perform(form("matchId" to "9999", "date" to "", "time" to "", "type" to "")).andReturn()

        assertEquals("mandate/visit", result.modelAndView?.viewName)
        assertTrue(errors(result).containsKey("matchId"))
        assertTrue(errors(result).containsKey("date"))
        assertTrue(errors(result).containsKey("time"))
        assertTrue(errors(result).containsKey("type"))
        verify(visitClient, never()).createForMatch(any(), any())
    }

    @Test
    fun `create - a slot the server refused`() {
        doThrow(conflict(ErrorCode.VISIT_INVALID_SLOT)).whenever(visitClient).createForMatch(any(), any())

        assertEquals(MandateController.VISIT_INVALID_SLOT, outcome(mvc.perform(validForm()).andReturn()))
    }

    @Test
    fun `create - a video visit without whatsapp`() {
        doThrow(conflict(ErrorCode.VISIT_WHATSAPP_MISSING)).whenever(visitClient).createForMatch(any(), any())

        assertEquals(MandateController.VISIT_WHATSAPP_MISSING, outcome(mvc.perform(validForm("VIDEO")).andReturn()))
    }

    @Test
    fun `create - a stale page is changed`() {
        listOf(
            HttpClientErrorException(HttpStatus.FORBIDDEN),
            HttpClientErrorException(HttpStatus.NOT_FOUND),
            conflict(ErrorCode.VISIT_ALREADY_EXISTS),
        ).forEach { ex ->
            doThrow(ex).whenever(visitClient).createForMatch(any(), any())

            assertEquals(MandateController.CHANGED, outcome(mvc.perform(validForm()).andReturn()))
        }
    }

    @Test
    fun `create - a server error is an error`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
            .whenever(visitClient).createForMatch(any(), any())
        assertEquals(MandateController.FAILED, outcome(mvc.perform(validForm()).andReturn()))

        doThrow(ResourceAccessException("down")).whenever(visitClient).createForMatch(any(), any())
        assertEquals(MandateController.FAILED, outcome(mvc.perform(validForm()).andReturn()))
    }

    @Test
    fun `create - the mandate moved on or is gone`() {
        doReturn(APPROVED.copy(status = MandateStatus.CLOSED)).whenever(mandateClient).get(201)

        val result = mvc.perform(validForm()).andReturn()

        assertEquals("redirect:/mandates/201#contact-title", result.modelAndView?.viewName)
        assertEquals(MandateController.CHANGED, outcome(result))
        verify(visitClient, never()).createForMatch(any(), any())

        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(mandateClient).get(201)
        assertEquals(MandateController.CHANGED, outcome(mvc.perform(validForm()).andReturn()))
    }

    @Test
    fun `create - not signed in`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/login", mvc.perform(validForm()).andReturn().modelAndView?.viewName)
    }
}
