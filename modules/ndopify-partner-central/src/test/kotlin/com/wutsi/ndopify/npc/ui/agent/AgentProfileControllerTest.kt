package com.wutsi.ndopify.npc.ui.agent

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.SearchListingResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.npc.service.security.CurrentAgent
import com.wutsi.ndopify.npc.service.security.SignInRequiredException
import com.wutsi.ndopify.npc.ui.agent.AgentProfileFixtures.LISTINGS
import com.wutsi.ndopify.npc.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.web.core.service.TenantContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.ui.ExtendedModelMap
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentProfileControllerTest {
    private val currentAgent = mock<CurrentAgent>()
    private val agentClient = mock<NdAgentClient>()
    private val listingClient = mock<NdListingClient>()
    private val locationClient = mock<NdLocationClient>()
    private val tenantContext = mock<TenantContext>()
    private lateinit var parallel: Parallel
    private lateinit var controller: AgentProfileController
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        ProfileFixtures.mockLocations(locationClient, tenantContext)
        doReturn(ProfileFixtures.AGENT).whenever(currentAgent).get()
        doReturn(AgentProfileFixtures.AGENT).whenever(agentClient).get(30)
        doReturn(SearchListingResponse(listings = LISTINGS, total = 6)).whenever(listingClient).searchByAgent(any(), any(), any())
        doReturn(emptyList<ListingImage>()).whenever(listingClient).images(any())
        parallel = Parallel()

        controller = AgentProfileController(
            currentAgent,
            agentClient,
            listingClient,
            locationClient,
            tenantContext,
            parallel,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(HttpClientErrorHandler(mock()))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
    }

    @AfterEach
    fun tearDown() {
        parallel.close()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Test
    fun show() {
        val result = mvc.perform(get("/agent?id=30")).andReturn()

        assertEquals("agent", result.modelAndView?.viewName)
        val view = model(result)["profile"] as AgentProfileView
        assertEquals("Clarisse Mbarga", view.name)
        assertEquals(1, view.page)
        assertEquals(2, view.pages)
        assertEquals(false, model(result)["missing"])
        verify(listingClient).searchByAgent(30, 3, 0)
        verify(listingClient).images(5001)
    }

    @Test
    fun `show - the viewer's header, not the viewed agent's`() {
        val result = mvc.perform(get("/agent?id=30")).andReturn()

        assertEquals("https://cdn.ndopify.com/party/100/photo/a.jpg", model(result)["userPhotoUrl"])
        assertEquals("SN", model(result)["initials"])
    }

    @Test
    fun `missing agent - 404 and the panel`() {
        doReturn(null).whenever(agentClient).get(99)

        val result = mvc.perform(get("/agent?id=99")).andReturn()

        assertEquals(404, result.response.status)
        assertEquals(true, model(result)["missing"])
        assertNull(model(result)["profile"])
    }

    @Test
    fun `missing id - 404 and the panel`() {
        for (url in listOf("/agent", "/agent?id=abc")) {
            val result = mvc.perform(get(url)).andReturn()

            assertEquals(404, result.response.status, url)
            assertEquals(true, model(result)["missing"], url)
            assertNull(model(result)["profile"], url)
        }
    }

    @Test
    fun `page 99 clamps to the last page`() {
        doReturn(SearchListingResponse(emptyList(), total = 6)).whenever(listingClient).searchByAgent(30, 3, 294)
        doReturn(SearchListingResponse(LISTINGS, total = 6)).whenever(listingClient).searchByAgent(30, 3, 3)

        val view = model(mvc.perform(get("/agent?id=30&page=99")).andReturn())["profile"] as AgentProfileView

        assertEquals(2, view.page)
        assertEquals(2, view.pages)
        assertEquals("/agent?id=30&page=1", view.prevUrl)
        verify(listingClient).searchByAgent(30, 3, 294)
        verify(listingClient).searchByAgent(30, 3, 3)
    }

    @Test
    fun `page 0 clamps to the first page`() {
        val view = model(mvc.perform(get("/agent?id=30&page=0")).andReturn())["profile"] as AgentProfileView

        assertEquals(1, view.page)
        verify(listingClient).searchByAgent(30, 3, 0)
    }

    @Test
    fun `unsafe back is ignored`() {
        val result = mvc.perform(get("/agent?id=30&back=javascript:alert(1)")).andReturn()

        assertEquals("/", model(result)["backUrl"])
        assertEquals("← Retour à l'espace agent", model(result)["backLabel"])
    }

    @Test
    fun `valid back is kept`() {
        val result = mvc.perform(get("/agent?id=30&back=/listings/501")).andReturn()

        assertEquals("/listings/501", model(result)["backUrl"])
        assertEquals("← Retour", model(result)["backLabel"])
        val view = model(result)["profile"] as AgentProfileView
        assertEquals("/agent?id=30&page=2&back=/listings/501", view.nextUrl)
    }

    @Test
    fun `listings unavailable - the profile still renders`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).searchByAgent(any(), any(), any())

        val result = mvc.perform(get("/agent?id=30")).andReturn()

        val view = model(result)["profile"] as AgentProfileView
        assertTrue(view.listingsUnavailable)
        assertEquals(emptyList(), view.listings)
        assertEquals("Clarisse Mbarga", view.name)
    }

    @Test
    fun `agent read failure propagates`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(agentClient).get(30)

        val error = runCatching {
            mvc.perform(get("/agent?id=30"))
        }.exceptionOrNull()

        assertTrue(error != null)
        assertTrue(generateSequence(error) { it.cause }.any { it is HttpServerErrorException })
    }

    @Test
    fun `not an agent - sign in`() {
        doReturn(null).whenever(currentAgent).get()

        mvc.perform(get("/agent?id=30")).andExpect(redirectedUrl("/login"))
    }

    @Test
    fun `not an agent - the controller throws`() {
        doReturn(null).whenever(currentAgent).get()

        val error = runCatching {
            controller.show("30", null, null, ExtendedModelMap(), MockHttpServletResponse())
        }.exceptionOrNull()

        assertTrue(error is SignInRequiredException)
    }
}
