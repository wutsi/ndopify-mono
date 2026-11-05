package com.wutsi.ndopify.web.ui.visit

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingImage
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.web.client.ndopify.NdAgentClient
import com.wutsi.ndopify.web.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.client.ndopify.NdVisitClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.web.ui.layout.TenantAdvice
import com.wutsi.ndopify.web.ui.match.MatchFixtures
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.lang.reflect.Proxy
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VisitControllerTest {
    private val visitClient = mock<NdVisitClient>()
    private val listingClient = mock<NdListingClient> {
        on { get(123) } doReturn Listing(id = 123, status = ListingStatus.PUBLISHED, cityId = 1, neighborhoodId = 5, title = "Appartement")
        on { images(123) } doReturn listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"))
    }
    private val locationClient = mock<NdLocationClient> {
        on { search(any()) } doReturn listOf(Location(id = 1, name = "Douala"), Location(id = 5, name = "Bonapriso"))
    }
    private val agentClient = mock<NdAgentClient> {
        on { get(3) } doReturn Agent(id = 3, party = Party(firstName = "Serge", lastName = "Ngono"))
    }
    private val customerClient = mock<NdCustomerClient>()
    private val clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC)
    private val accessTokenService = mock<AccessTokenService>()
    private val context = mock<TenantContext> { on { getTenant() } doReturn TENANT }

    @AfterEach
    fun tearDown() = MatchFixtures.signOut()

    private fun request(path: String, tokens: AccessTokenService = accessTokenService): MvcResult = perform(get(path), tokens)

    private fun perform(request: RequestBuilder, tokens: AccessTokenService = accessTokenService): MvcResult =
        MockMvcBuilders.standaloneSetup(
            VisitController(visitClient, listingClient, locationClient, agentClient, customerClient, tokens, clock),
        )
            .setControllerAdvice(TenantAdvice(context), HttpClientErrorHandler(accessTokenService))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(request)
            .andReturn()

    private fun page(result: MvcResult) = result.modelAndView?.model?.get("page") as VisitPage

    private fun failure(status: HttpStatus) =
        HttpClientErrorException.create(status, "", HttpHeaders.EMPTY, null, null)

    @Test
    fun `token is stored and stripped`() {
        val result = request("/visits/9?token=aaa.bbb.ccc&cancel=done")
        verify(accessTokenService).set("aaa.bbb.ccc")
        assertEquals(302, result.response.status)
        assertEquals("/visits/9?cancel=done", result.response.getHeader(HttpHeaders.LOCATION))
        verify(visitClient, never()).get(9)
    }

    @Test
    fun `invalid token shape`() {
        val result = request("/visits/9?token=abc")
        assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals(VisitController.REASON_EXPIRED, result.modelAndView?.model?.get("reason"))
        verify(accessTokenService, never()).set(any())
    }

    @Test
    fun `signed out`() {
        val result = request("/visits/9")
        assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `server unauthorized`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.UNAUTHORIZED)).whenever(visitClient).get(9)
        val result = request("/visits/9")
        verify(accessTokenService).remove()
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `another customer`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(visitClient).get(9)
        val result = request("/visits/9")
        assertEquals("not-yours", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `not found`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(visitClient).get(9)
        val result = request("/visits/9")
        assertEquals(404, result.response.status)
    }

    /** A real [AccessTokenService] on the request and response MockMvc is handling. */
    private fun realTokens(): AccessTokenService {
        val attributes = { RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes }
        val loader = javaClass.classLoader
        val request = Proxy.newProxyInstance(loader, arrayOf(HttpServletRequest::class.java)) { _, method, args ->
            method.invoke(attributes().request, *(args ?: emptyArray()))
        } as HttpServletRequest
        val response = Proxy.newProxyInstance(loader, arrayOf(HttpServletResponse::class.java)) { _, method, args ->
            method.invoke(attributes().response, *(args ?: emptyArray()))
        } as HttpServletResponse
        return AccessTokenService(request, response, "__nweb_access_token", true, Clock.systemUTC())
    }

    @Test
    fun `token is set in the cookie`() {
        val token = JWT.create().withExpiresAt(Date(System.currentTimeMillis() + 3_600_000)).sign(Algorithm.HMAC256("secret"))

        val result = request("/visits/9?token=$token", realTokens())

        assertEquals(302, result.response.status)
        assertEquals("/visits/9", result.response.getHeader(HttpHeaders.LOCATION))
        val cookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        assertTrue(cookie.startsWith("__nweb_access_token=$token;"), cookie)
        assertTrue(cookie.contains("HttpOnly"), cookie)
        assertTrue(Regex("Max-Age=\\d+").containsMatchIn(cookie), cookie)
    }

    @Test
    fun page() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, matchId = 12, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE))
            .whenever(visitClient).get(9)

        val result = request("/visits/9")

        assertEquals(VisitController.VIEW, result.modelAndView?.viewName)
        val page = result.modelAndView?.model?.get("page") as VisitPage
        assertEquals(9L, page.id)
        assertEquals("Demandée", page.statusLabel)
        assertEquals("/match/12", page.matchUrl)
        verify(listingClient).get(123)
        verify(agentClient).get(3)
        verify(customerClient).get(42)
    }

    @Test
    fun `listing gone is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.CONFIRMED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(listingClient).get(123)

        val page = page(request("/visits/9"))
        assertNull(page.listing)
        verify(listingClient, never()).images(123)
    }

    @Test
    fun `listing gone skips the location search`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.CONFIRMED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(listingClient).get(123)

        val page = page(request("/visits/9"))
        assertNull(page.listing)
        verify(locationClient, never()).search(any())
    }

    @Test
    fun `listing forbidden is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(listingClient).get(123)

        val page = page(request("/visits/9"))
        assertNull(page.listing)
        verify(listingClient, never()).images(123)
    }

    @Test
    fun `listing no longer published is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doReturn(Listing(id = 123, status = ListingStatus.RENTED)).whenever(listingClient).get(123)

        val page = page(request("/visits/9"))
        assertNull(page.listing)
        verify(listingClient, never()).images(123)
    }

    @Test
    fun `images gone is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(listingClient).images(123)

        val page = page(request("/visits/9"))
        assertNotNull(page.listing)
        assertTrue(page.listing!!.photos.isEmpty())
    }

    @Test
    fun `images forbidden is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(listingClient).images(123)

        val page = page(request("/visits/9"))
        assertNotNull(page.listing)
        assertTrue(page.listing!!.photos.isEmpty())
    }

    @Test
    fun `agent gone is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(agentClient).get(3)

        val page = page(request("/visits/9"))
        assertEquals("votre agent", page.agent?.name)
    }

    @Test
    fun `visit without agent`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)

        val page = page(request("/visits/9"))
        assertNull(page.agent)
        verify(agentClient, never()).get(any())
    }

    @Test
    fun `customer gone is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(customerClient).get(42)

        val page = page(request("/visits/9"))
        assertNull(page.videoNumber)
    }

    @Test
    fun `customer forbidden is tolerated`() {
        MatchFixtures.signIn()
        doReturn(Visit(id = 9, listingId = 123, agentId = 3, customerId = 42, status = VisitStatus.REQUESTED, type = VisitType.ON_SITE)).whenever(visitClient).get(9)
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(customerClient).get(42)

        val page = page(request("/visits/9"))
        assertNull(page.videoNumber)
    }

    @Test
    fun cancel() {
        MatchFixtures.signIn()
        val result = perform(post("/visits/9/cancel").param("reason", "  Empêchement  "))
        verify(visitClient).cancel(9, CancelVisitRequest("Empêchement"))
        assertEquals("/visits/9?cancel=done", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun `cancel - blank reason`() {
        MatchFixtures.signIn()
        perform(post("/visits/9/cancel").param("reason", "  "))
        verify(visitClient).cancel(9, CancelVisitRequest(null))
    }

    @Test
    fun `cancel - reason cut to what ndopify-server takes`() {
        MatchFixtures.signIn()
        perform(post("/visits/9/cancel").param("reason", "x".repeat(600)))
        verify(visitClient).cancel(9, CancelVisitRequest("x".repeat(500)))
    }

    @Test
    fun `cancel - conflict`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.CONFLICT)).whenever(visitClient).cancel(any(), any())
        val result = perform(post("/visits/9/cancel"))
        assertEquals("/visits/9?cancel=conflict", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun `cancel - server unauthorized`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.UNAUTHORIZED)).whenever(visitClient).cancel(any(), any())
        val result = perform(post("/visits/9/cancel"))
        verify(accessTokenService).remove()
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `cancel - another customer`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(visitClient).cancel(any(), any())
        val result = perform(post("/visits/9/cancel"))
        assertEquals("not-yours", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `cancel - signed out`() {
        val result = perform(post("/visits/9/cancel"))
        assertEquals(VisitController.VIEW_LINK, result.modelAndView?.viewName)
        verify(visitClient, never()).cancel(any(), any())
    }
}
