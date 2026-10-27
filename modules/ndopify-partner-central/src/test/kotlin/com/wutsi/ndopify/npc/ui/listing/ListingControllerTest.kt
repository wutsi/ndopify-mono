package com.wutsi.ndopify.npc.ui.listing

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
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.COMPLETE
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.IMAGES
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest.Companion.LISTING
import com.wutsi.ndopify.npc.ui.mandate.MandateController
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val listingClient = mock<NdListingClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var mvc: MockMvc

    private val mandate = Mandate(
        id = 203,
        agentId = AGENT.id,
        status = MandateStatus.APPROVED,
        mandateType = MandateType.LISTING,
    )

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(AGENT).whenever(currentAgent).get()
        doReturn(LISTING).whenever(listingClient).get(501)
        doReturn(IMAGES).whenever(listingClient).images(501)
        doReturn(mandate).whenever(mandateClient).get(203)

        val controller = ListingController(currentAgent, listingClient, mandateClient, locationClient, tenantContext)
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    private fun flash(result: MvcResult) = result.flashMap[ListingController.OUTCOME]

    private fun conflict(code: String): HttpClientErrorException {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        return ex
    }

    @Test
    fun show() {
        val result = mvc.perform(get("/listings/501")).andReturn()

        assertEquals("listing", result.modelAndView?.viewName)
        val view = model(result)["listing"] as ListingView
        assertEquals(501L, view.id)
        assertEquals("Annonce · Appartement, Bonapriso", view.title)
        assertEquals("2 photos · 5 au moins", view.rows.first { it.url.endsWith("/photos") }.summary)
        assertEquals("SN", model(result)["initials"])
        assertNull(model(result)["locked"])
    }

    @Test
    fun `show - images unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).images(501)

        val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

        assertEquals("", view.rows.first { it.url.endsWith("/photos") }.summary)
    }

    @Test
    fun `show - another agent's listing is locked`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(get("/listings/501")).andReturn()

        assertEquals("listing", result.modelAndView?.viewName)
        assertEquals(true, model(result)["locked"])
        assertNull(model(result)["listing"])
    }

    @Test
    fun `show - not found is locked`() {
        doThrow(HttpClientErrorException(HttpStatus.NOT_FOUND)).whenever(listingClient).get(501)

        assertEquals(true, model(mvc.perform(get("/listings/501")).andReturn())["locked"])
    }

    @Test
    fun `show - server error is unavailable, not locked`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(listingClient).get(501)

        val result = mvc.perform(get("/listings/501")).andReturn()

        assertEquals("listing", result.modelAndView?.viewName)
        assertEquals(true, model(result)["unavailable"])
        assertNull(model(result)["locked"])
    }

    @Test
    fun `show - network error is unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(501)

        assertEquals(true, model(mvc.perform(get("/listings/501")).andReturn())["unavailable"])
    }

    @Test
    fun `show - unknown status is locked`() {
        doReturn(LISTING.copy(status = ListingStatus.UNKNOWN)).whenever(listingClient).get(501)

        assertEquals(true, model(mvc.perform(get("/listings/501")).andReturn())["locked"])
    }

    @Test
    fun `show - outcome on the locked view`() {
        doReturn(LISTING.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(get("/listings/501").flashAttr(ListingController.OUTCOME, ListingController.CHANGED))
            .andReturn()

        assertEquals(true, model(result)["locked"])
        assertEquals("warning", model(result)["outcomeStyle"])
    }

    @Test
    fun `show - malformed id is locked`() {
        val result = mvc.perform(get("/listings/abc")).andReturn()

        assertEquals(true, model(result)["locked"])
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `show - signed out`() {
        doReturn(null).whenever(currentAgent).get()

        assertEquals("redirect:/logout", mvc.perform(get("/listings/501")).andReturn().modelAndView?.viewName)
    }

    @Test
    fun `show - outcome`() {
        val result = mvc.perform(get("/listings/501").flashAttr(ListingController.OUTCOME, ListingController.SENT))
            .andReturn()

        assertEquals("Annonce envoyée. Nous vous préviendrons dès sa publication.", model(result)["outcomeText"])
        assertEquals("success", model(result)["outcomeStyle"])
    }

    @Test
    fun `show - section saved`() {
        val result = mvc.perform(get("/listings/501").flashAttr(ListingController.OUTCOME, "saved-amenities")).andReturn()

        assertEquals("Équipements enregistrés.", model(result)["outcomeText"])
        assertEquals("success", model(result)["outcomeStyle"])
    }

    @Test
    fun `show - unknown outcome`() {
        val result = mvc.perform(get("/listings/501").flashAttr(ListingController.OUTCOME, "saved-garden")).andReturn()

        assertNull(model(result)["outcomeText"])
    }

    @Test
    fun send() {
        doReturn(COMPLETE).whenever(listingClient).get(501)

        val result = mvc.perform(post("/listings/501/verify")).andReturn()

        verify(listingClient).verify(501)
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals(ListingController.SENT, flash(result))
    }

    @Test
    fun `send - incomplete`() {
        val result = mvc.perform(post("/listings/501/verify")).andReturn()

        verify(listingClient, never()).verify(any())
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
        assertEquals(ListingController.INCOMPLETE, flash(result))
    }

    @Test
    fun `send - already sent`() {
        doReturn(COMPLETE.copy(status = ListingStatus.UNDER_VERIFICATION)).whenever(listingClient).get(501)

        val result = mvc.perform(post("/listings/501/verify")).andReturn()

        verify(listingClient, never()).verify(any())
        assertEquals(ListingController.CHANGED, flash(result))
    }

    @Test
    fun `send - another agent's listing`() {
        doReturn(COMPLETE.copy(agentId = 999)).whenever(listingClient).get(501)

        val result = mvc.perform(post("/listings/501/verify")).andReturn()

        verify(listingClient, never()).verify(any())
        assertEquals(ListingController.CHANGED, flash(result))
    }

    @Test
    fun `send - conflict`() {
        doReturn(COMPLETE).whenever(listingClient).get(501)
        doThrow(conflict(ErrorCode.LISTING_NOT_DRAFT)).whenever(listingClient).verify(501)

        assertEquals(ListingController.CHANGED, flash(mvc.perform(post("/listings/501/verify")).andReturn()))
    }

    @Test
    fun `send - incomplete for the server`() {
        doReturn(COMPLETE).whenever(listingClient).get(501)
        doThrow(conflict(ErrorCode.LISTING_INCOMPLETE)).whenever(listingClient).verify(501)

        assertEquals(ListingController.INCOMPLETE, flash(mvc.perform(post("/listings/501/verify")).andReturn()))
    }

    @Test
    fun `send - malformed id`() {
        val result = mvc.perform(post("/listings/abc/verify")).andReturn()

        verify(listingClient, never()).get(any())
        assertEquals("redirect:/", result.modelAndView?.viewName)
    }

    @Test
    fun `send - failure`() {
        doReturn(COMPLETE).whenever(listingClient).get(501)
        doThrow(ResourceAccessException("down")).whenever(listingClient).verify(501)

        assertEquals(ListingController.FAILED, flash(mvc.perform(post("/listings/501/verify")).andReturn()))
    }

    @Test
    fun create() {
        doReturn(501L).whenever(listingClient).create(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        verify(listingClient).create(203)
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
    }

    @Test
    fun `create - already created`() {
        doReturn(mandate.copy(listingId = 501)).whenever(mandateClient).get(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        verify(listingClient, never()).create(any())
        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
    }

    @Test
    fun `create - created meanwhile`() {
        doReturn(mandate).doReturn(mandate.copy(listingId = 501)).whenever(mandateClient).get(203)
        doThrow(conflict(ErrorCode.LISTING_ALREADY_EXISTS)).whenever(listingClient).create(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        assertEquals("redirect:/listings/501", result.modelAndView?.viewName)
    }

    @Test
    fun `create - not approved`() {
        doReturn(mandate.copy(status = MandateStatus.PENDING_APPROVAL)).whenever(mandateClient).get(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        verify(listingClient, never()).create(any())
        assertEquals("redirect:/mandates/203", result.modelAndView?.viewName)
        assertEquals(MandateController.CHANGED, result.flashMap[MandateController.OUTCOME])
    }

    @Test
    fun `create - a search mandate`() {
        doReturn(mandate.copy(mandateType = MandateType.SEARCH)).whenever(mandateClient).get(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        verify(listingClient, never()).create(any())
        assertEquals(MandateController.CHANGED, result.flashMap[MandateController.OUTCOME])
    }

    @Test
    fun `create - another agent's mandate`() {
        doReturn(mandate.copy(agentId = 999)).whenever(mandateClient).get(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        verify(listingClient, never()).create(any())
        assertEquals(MandateController.CHANGED, result.flashMap[MandateController.OUTCOME])
    }

    @Test
    fun `create - mandate not the agent's for the server`() {
        doThrow(HttpClientErrorException(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        verify(listingClient, never()).create(any())
        assertEquals("redirect:/mandates/203", result.modelAndView?.viewName)
        assertEquals(MandateController.CHANGED, result.flashMap[MandateController.OUTCOME])
    }

    @Test
    fun `create - no listing id returned`() {
        doReturn(-1L).whenever(listingClient).create(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        assertEquals("redirect:/mandates/203", result.modelAndView?.viewName)
        assertEquals(MandateController.FAILED, result.flashMap[MandateController.OUTCOME])
    }

    @Test
    fun `create - malformed id`() {
        val result = mvc.perform(post("/mandates/abc/listing")).andReturn()

        verify(mandateClient, never()).get(any())
        assertEquals("redirect:/", result.modelAndView?.viewName)
    }

    @Test
    fun `create - failure`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).create(203)

        val result = mvc.perform(post("/mandates/203/listing")).andReturn()

        assertEquals("redirect:/mandates/203", result.modelAndView?.viewName)
        assertEquals(MandateController.FAILED, result.flashMap[MandateController.OUTCOME])
    }
}
