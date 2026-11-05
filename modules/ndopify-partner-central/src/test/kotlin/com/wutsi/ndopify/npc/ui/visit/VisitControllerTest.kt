package com.wutsi.ndopify.npc.ui.visit

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdVisitClient
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.layout.SignedInAdvice
import com.wutsi.ndopify.npc.ui.layout.SignedInAdviceTest
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.CUSTOMER
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.LISTING
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.checkedIn
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.confirmed
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.requested
import com.wutsi.ndopify.npc.ui.visit.VisitFixtures.window
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val visitClient = mock<NdVisitClient>()
    private val listingClient = mock<NdListingClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private val clock = mock<Clock>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        SignedInAdviceTest.signIn()
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(VisitFixtures.NOW).whenever(clock).millis()
        doReturn(requested()).whenever(visitClient).get(7001)
        doReturn(LISTING).whenever(listingClient).get(3012)
        doReturn(CUSTOMER).whenever(customerClient).get(300)

        mvc = MockMvcBuilders
            .standaloneSetup(
                VisitController(
                    currentAgent,
                    visitClient,
                    listingClient,
                    customerClient,
                    locationClient,
                    tenantContext,
                    clock,
                ),
            )
            .setControllerAdvice(SignedInAdvice())
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun outcome(result: MvcResult) = result.flashMap[VisitController.OUTCOME]

    private fun error(status: HttpStatus, code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(status)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    @Test
    fun page() {
        val result = mvc.perform(get("/visits/7001")).andReturn()

        assertEquals("visit", result.modelAndView?.viewName)
        val page = model(result)["page"] as VisitPage
        assertEquals(7001L, page.id)
        assertEquals("Sandrine Eboa", page.customerName)
        assertEquals("Appartement 3 chambres · Bonapriso", page.listingTitle)
        verify(listingClient).get(3012)
        verify(customerClient).get(300)
    }

    @Test
    fun `another agent`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(visitClient).get(7001)

        val result = mvc.perform(get("/visits/7001")).andReturn()

        assertEquals(VisitController.LOCKED_NOT_YOURS, model(result)["locked"])
        assertNull(model(result)["page"])
        verify(listingClient, never()).get(any())
        verify(customerClient, never()).get(any())
    }

    @Test
    fun `visit of another agent`() {
        doReturn(requested().copy(agentId = 99)).whenever(visitClient).get(7001)

        val result = mvc.perform(get("/visits/7001")).andReturn()

        assertEquals(VisitController.LOCKED_NOT_YOURS, model(result)["locked"])
        assertNull(model(result)["page"])
    }

    @Test
    fun `listing gone`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(listingClient).get(3012)

        val result = mvc.perform(get("/visits/7001")).andReturn()

        val page = model(result)["page"] as VisitPage
        assertNull(page.listingTitle)
        assertNull(page.address)
        assertNull(page.mapUrl)
        verify(customerClient).get(300)
    }

    @Test
    fun `customer gone`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(customerClient).get(300)

        val result = mvc.perform(get("/visits/7001")).andReturn()

        val page = model(result)["page"] as VisitPage
        assertNull(page.phone)
        assertEquals("Client", page.customerName)
    }

    @Test
    fun `server unavailable`() {
        doThrow(ResourceAccessException("timeout")).whenever(visitClient).get(7001)

        val result = mvc.perform(get("/visits/7001")).andReturn()

        assertEquals(VisitController.LOCKED_UNAVAILABLE, model(result)["locked"])
    }

    @Test
    fun `malformed id`() {
        val result = mvc.perform(get("/visits/abc")).andReturn()

        assertEquals(VisitController.LOCKED_UNAVAILABLE, model(result)["locked"])
        verify(visitClient, never()).get(any())
    }

    @Test
    fun flash() {
        doReturn(confirmed()).whenever(visitClient).get(7001)

        val result = mvc.perform(get("/visits/7001").flashAttr(VisitController.OUTCOME, VisitController.CONFIRMED)).andReturn()

        assertTrue((model(result)[VisitController.FLASH_TEXT] as String).startsWith("Visite confirmée pour le "))
        assertEquals("success", model(result)[VisitController.FLASH_STYLE])
    }

    @Test
    fun `code locked flash`() {
        val result = mvc.perform(get("/visits/7001").flashAttr(VisitController.OUTCOME, VisitController.CODE_LOCKED)).andReturn()

        assertEquals(true, model(result)[VisitController.CHECK_IN_LOCKED])
        assertEquals("warning", model(result)[VisitController.FLASH_STYLE])
    }

    @Test
    fun schedule() {
        val result = mvc.perform(
            post("/visits/7001/schedule")
                .param("date", "2026-11-07").param("time", "10:30").param("type", "ON_SITE"),
        ).andReturn()

        verify(visitClient).schedule(
            7001,
            ScheduleVisitRequest(Date.from(Instant.parse("2026-11-07T09:30:00Z")), VisitType.ON_SITE),
        )
        assertEquals("/visits/7001", result.response.redirectedUrl)
        assertEquals(VisitController.CONFIRMED, outcome(result))
    }

    @Test
    fun `schedule - stale slot`() {
        val result = mvc.perform(
            post("/visits/7001/schedule").param("date", "x").param("time", "10:30").param("type", "ON_SITE"),
        ).andReturn()

        assertEquals(VisitController.INVALID_SLOT, outcome(result))
        verify(visitClient, never()).schedule(any(), any())
    }

    @Test
    fun `schedule - status moved`() {
        doReturn(confirmed()).whenever(visitClient).get(7001)

        val result = mvc.perform(
            post("/visits/7001/schedule").param("date", "2026-11-07").param("time", "10:30").param("type", "ON_SITE"),
        ).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
        verify(visitClient, never()).schedule(any(), any())
    }

    @Test
    fun `schedule - server slot`() {
        doThrow(error(HttpStatus.CONFLICT, "VISIT_INVALID_SLOT")).whenever(visitClient).schedule(eq(7001), any())

        val result = mvc.perform(
            post("/visits/7001/schedule").param("date", "2026-11-07").param("time", "10:30").param("type", "ON_SITE"),
        ).andReturn()

        assertEquals(VisitController.INVALID_SLOT, outcome(result))
    }

    @Test
    fun `schedule - video without whatsapp`() {
        doThrow(error(HttpStatus.CONFLICT, "VISIT_WHATSAPP_MISSING")).whenever(visitClient).schedule(eq(7001), any())

        val result = mvc.perform(
            post("/visits/7001/schedule").param("date", "2026-11-07").param("time", "10:30").param("type", "VIDEO"),
        ).andReturn()

        assertEquals(VisitController.WHATSAPP_MISSING, outcome(result))
    }

    @Test
    fun `schedule - server error`() {
        doThrow(HttpServerErrorException(HttpStatus.BAD_GATEWAY)).whenever(visitClient).schedule(eq(7001), any())

        val result = mvc.perform(
            post("/visits/7001/schedule").param("date", "2026-11-07").param("time", "10:30").param("type", "ON_SITE"),
        ).andReturn()

        assertEquals(VisitController.FAILED, outcome(result))
    }

    @Test
    fun `cancel - requested`() {
        val result = mvc.perform(post("/visits/7001/cancel").param("reason", "  Empêchement  ")).andReturn()

        verify(visitClient).cancel(7001, CancelVisitRequest("Empêchement"))
        assertEquals(VisitController.REFUSED, outcome(result))
    }

    @Test
    fun `cancel - confirmed`() {
        doReturn(confirmed()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/cancel")).andReturn()

        assertEquals(VisitController.CANCELLED, outcome(result))
    }

    @Test
    fun `cancel - blank reason`() {
        val result = mvc.perform(post("/visits/7001/cancel").param("reason", "  ")).andReturn()

        verify(visitClient).cancel(7001, CancelVisitRequest(null))
        assertEquals(VisitController.REFUSED, outcome(result))
    }

    @Test
    fun `cancel - checked in`() {
        doReturn(checkedIn()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/cancel")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
        verify(visitClient, never()).cancel(any(), any())
    }

    @Test
    fun `cancel - conflict`() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(visitClient).cancel(eq(7001), any())

        val result = mvc.perform(post("/visits/7001/cancel")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
    }

    @Test
    fun `check in`() {
        doReturn(window()).whenever(visitClient).get(7001)

        val result = mvc.perform(
            post("/visits/7001/check-in")
                .param("code", "4827")
                .param("latitude", "4.0247")
                .param("longitude", "9.6943")
                .param("accuracyMeters", "12"),
        ).andReturn()

        verify(visitClient).checkIn(7001, CheckInVisitRequest("4827", 4.0247, 9.6943, 12.0))
        assertEquals(VisitController.CHECKED_IN, outcome(result))
    }

    @Test
    fun `check in - without position`() {
        doReturn(window()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-in").param("code", "4827")).andReturn()

        verify(visitClient).checkIn(7001, CheckInVisitRequest("4827", null, null, null))
        assertEquals(VisitController.CHECKED_IN, outcome(result))
    }

    @Test
    fun `check in - short code`() {
        doReturn(window()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-in").param("code", "12")).andReturn()

        assertEquals(VisitController.CODE_MISSING, outcome(result))
        verify(visitClient, never()).checkIn(any(), any())
    }

    @Test
    fun `check in - invalid`() {
        doReturn(window()).whenever(visitClient).get(7001)
        doThrow(error(HttpStatus.CONFLICT, "VISIT_CODE_INVALID")).whenever(visitClient).checkIn(eq(7001), any())

        val result = mvc.perform(post("/visits/7001/check-in").param("code", "4827")).andReturn()

        assertEquals(VisitController.CODE_INVALID, outcome(result))
    }

    @Test
    fun `check in - locked`() {
        doReturn(window()).whenever(visitClient).get(7001)
        doThrow(error(HttpStatus.CONFLICT, "VISIT_CODE_LOCKED")).whenever(visitClient).checkIn(eq(7001), any())

        val result = mvc.perform(post("/visits/7001/check-in").param("code", "4827")).andReturn()

        assertEquals(VisitController.CODE_LOCKED, outcome(result))
        assertEquals(true, result.request.getSession().getAttribute("npc.visit.code-locked.7001"))
    }

    @Test
    fun `check in - locked again`() {
        doReturn(window()).whenever(visitClient).get(7001)
        doThrow(error(HttpStatus.CONFLICT, "VISIT_CODE_LOCKED")).whenever(visitClient).checkIn(eq(7001), any())
        val session = MockHttpSession()

        mvc.perform(post("/visits/7001/check-in").param("code", "4827").session(session)).andReturn()
        val result = mvc.perform(post("/visits/7001/check-in").param("code", "4827").session(session)).andReturn()

        assertEquals(VisitController.CODE_LOCKED, outcome(result))
        verify(visitClient, times(1)).checkIn(eq(7001), any())
    }

    @Test
    fun `check in - locked survives a reload`() {
        doReturn(window()).whenever(visitClient).get(7001)
        doThrow(error(HttpStatus.CONFLICT, "VISIT_CODE_LOCKED")).whenever(visitClient).checkIn(eq(7001), any())
        val session = MockHttpSession()

        mvc.perform(post("/visits/7001/check-in").param("code", "4827").session(session)).andReturn()
        val result = mvc.perform(get("/visits/7001").session(session)).andReturn()

        assertEquals(true, model(result)[VisitController.CHECK_IN_LOCKED])
    }

    @Test
    fun `check in - outside the window`() {
        doReturn(window()).whenever(visitClient).get(7001)
        doThrow(error(HttpStatus.CONFLICT, "VISIT_OUTSIDE_WINDOW")).whenever(visitClient).checkIn(eq(7001), any())

        val result = mvc.perform(post("/visits/7001/check-in").param("code", "4827")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
    }

    @Test
    fun `no show`() {
        doReturn(window()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/no-show").param("reason", "CUSTOMER_ABSENT")).andReturn()

        verify(visitClient).noShow(7001, NoShowVisitRequest(VisitNoShowReason.CUSTOMER_ABSENT, null, null, null))
        assertEquals(VisitController.NO_SHOW, outcome(result))
    }

    @Test
    fun `no show - no reason`() {
        doReturn(window()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/no-show")).andReturn()

        assertEquals(VisitController.REASON_MISSING, outcome(result))
        verify(visitClient, never()).noShow(any(), any())
    }

    @Test
    fun `no show - too early`() {
        doReturn(window().copy(scheduledAt = Date(VisitFixtures.NOW - 5 * 60_000))).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/no-show").param("reason", "CUSTOMER_ABSENT")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
        verify(visitClient, never()).noShow(any(), any())
    }

    @Test
    fun `check out`() {
        doReturn(checkedIn().copy(checkedInAt = Date(VisitFixtures.NOW - 20 * 60_000))).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-out").param("comment", "  Client intéressé.  ")).andReturn()

        verify(visitClient).checkOut(7001, CheckOutVisitRequest("Client intéressé.", null, null, null))
        assertEquals(VisitController.COMPLETED, outcome(result))
    }

    @Test
    fun `check out - blank`() {
        doReturn(checkedIn()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-out").param("comment", "  ")).andReturn()

        assertEquals(VisitController.COMMENT_MISSING, outcome(result))
        verify(visitClient, never()).checkOut(any(), any())
    }

    @Test
    fun `check out - too long`() {
        doReturn(checkedIn()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-out").param("comment", "a".repeat(2001))).andReturn()

        assertEquals(VisitController.COMMENT_TOO_LONG, outcome(result))
        verify(visitClient, never()).checkOut(any(), any())
    }

    @Test
    fun `check out - too early`() {
        doReturn(checkedIn().copy(checkedInAt = Date(VisitFixtures.NOW - 5 * 60_000))).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-out").param("comment", "Client intéressé.")).andReturn()

        assertEquals(VisitController.TOO_EARLY, outcome(result))
        verify(visitClient, never()).checkOut(any(), any())
    }

    @Test
    fun `check out - not checked in`() {
        doReturn(confirmed()).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/check-out").param("comment", "Client intéressé.")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
        verify(visitClient, never()).checkOut(any(), any())
    }

    @Test
    fun `unknown visit`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/cancel")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
    }

    @Test
    fun `another agent's visit`() {
        doReturn(requested().copy(agentId = 99)).whenever(visitClient).get(7001)

        val result = mvc.perform(post("/visits/7001/cancel")).andReturn()

        assertEquals(VisitController.CHANGED, outcome(result))
        verify(visitClient, never()).cancel(any(), any())
    }

    @Test
    fun `malformed id action`() {
        val result = mvc.perform(post("/visits/abc/cancel")).andReturn()

        assertEquals("/", result.response.redirectedUrl)
        verify(visitClient, never()).cancel(any(), any())
    }
}
