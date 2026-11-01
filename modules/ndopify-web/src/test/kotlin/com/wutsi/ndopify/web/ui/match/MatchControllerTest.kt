package com.wutsi.ndopify.web.ui.match

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
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.web.client.ndopify.NdAgentClient
import com.wutsi.ndopify.web.client.ndopify.NdListingClient
import com.wutsi.ndopify.web.client.ndopify.NdLocationClient
import com.wutsi.ndopify.web.client.ndopify.NdMandateClient
import com.wutsi.ndopify.web.client.ndopify.NdMatchClient
import com.wutsi.ndopify.web.core.service.TenantContext
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import com.wutsi.ndopify.web.service.TenantFixtures.TENANT
import com.wutsi.ndopify.web.ui.layout.HttpClientErrorHandler
import com.wutsi.ndopify.web.ui.layout.TenantAdvice
import com.wutsi.ndopify.web.ui.match.MatchFixtures.MATCH
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchControllerTest {
    private val matchClient = mock<NdMatchClient> { on { get(12) } doReturn MATCH }
    private val mandateClient = mock<NdMandateClient> { on { get(7) } doReturn Mandate(id = 7, agentId = 3, cityId = 1) }
    private val agentClient = mock<NdAgentClient> {
        on { get(3) } doReturn Agent(id = 3, party = Party(firstName = "Serge", lastName = "Ngono"))
    }
    private val listingClient = mock<NdListingClient> {
        on { get(123) } doReturn Listing(id = 123, status = ListingStatus.PUBLISHED, cityId = 1, neighborhoodId = 5, title = "Appartement")
        on { images(123) } doReturn listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"))
    }
    private val locationClient = mock<NdLocationClient> {
        on { search(any()) } doReturn listOf(Location(id = 1, name = "Douala"), Location(id = 5, name = "Bonapriso"))
    }
    private val clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC)
    private val accessTokenService = mock<AccessTokenService>()
    private val context = mock<TenantContext> { on { getTenant() } doReturn TENANT }

    @AfterEach
    fun tearDown() = MatchFixtures.signOut()

    private fun request(path: String, tokens: AccessTokenService = accessTokenService): MvcResult = perform(get(path), tokens)

    private fun perform(request: RequestBuilder, tokens: AccessTokenService = accessTokenService): MvcResult =
        MockMvcBuilders.standaloneSetup(
            MatchController(matchClient, mandateClient, agentClient, listingClient, locationClient, tokens, clock),
        )
            .setControllerAdvice(TenantAdvice(context), HttpClientErrorHandler())
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(request)
            .andReturn()

    private fun page(result: MvcResult) = result.modelAndView?.model?.get("page") as MatchPage

    private fun failure(status: HttpStatus) =
        HttpClientErrorException.create(status, "", HttpHeaders.EMPTY, null, null)

    @Test
    fun tokenIsStoredAndStripped() {
        val result = request("/match/12?token=abc.def&utm=mail")

        verify(accessTokenService).set("abc.def")
        assertEquals(302, result.response.status)
        assertEquals("/match/12?utm=mail", result.response.getHeader(HttpHeaders.LOCATION))
        verify(matchClient, never()).get(12)
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
    fun tokenIsSetInTheCookie() {
        val token = JWT.create().withExpiresAt(Date(System.currentTimeMillis() + 3_600_000)).sign(Algorithm.HMAC256("secret"))

        val result = request("/match/12?token=$token", realTokens())

        assertEquals(302, result.response.status)
        assertEquals("/match/12", result.response.getHeader(HttpHeaders.LOCATION))
        val cookie = result.response.getHeader(HttpHeaders.SET_COOKIE)!!
        assertTrue(cookie.startsWith("__nweb_access_token=$token;"), cookie)
        assertTrue(cookie.contains("HttpOnly"), cookie)
        assertTrue(Regex("Max-Age=\\d+").containsMatchIn(cookie), cookie)
    }

    @Test
    fun tokenIsTheOnlyParameterStripped() {
        val result = request("/match/12?token=abc.def")

        assertEquals("/match/12", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun signedIn() {
        MatchFixtures.signIn()

        val result = request("/match/12")

        assertEquals(MatchController.VIEW, result.modelAndView?.viewName)
        val page = page(result)
        assertEquals(AnswerState.PENDING, page.state)
        assertEquals("Serge vous propose ce logement", page.title)
        assertEquals("Appartement", page.listing?.title)
        assertEquals(1, page.listing?.photos?.size)
        assertNull(page.flash)
        verify(locationClient).search(listOf<Long>(1, 5))
    }

    @Test
    fun flash() {
        MatchFixtures.signIn()
        doReturn(MATCH.copy(status = MatchStatus.ACCEPTED)).whenever(matchClient).get(12)

        val result = request("/match/12?answer=accepted")

        assertEquals("C'est noté : ce logement vous intéresse.", page(result).flash)
    }

    @Test
    fun `listing no longer readable`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(listingClient).get(123)

        val result = request("/match/12")

        assertEquals(MatchController.VIEW, result.modelAndView?.viewName)
        assertEquals(AnswerState.UNAVAILABLE, page(result).state)
        assertNull(page(result).listing)
        verify(listingClient, never()).images(123)
    }

    @Test
    fun `listing forbidden`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(listingClient).get(123)

        val result = request("/match/12")

        assertEquals(MatchController.VIEW, result.modelAndView?.viewName)
        assertEquals(AnswerState.UNAVAILABLE, page(result).state)
    }

    @Test
    fun `listing no longer published`() {
        MatchFixtures.signIn()
        doReturn(Listing(id = 123, status = ListingStatus.RENTED)).whenever(listingClient).get(123)

        val result = request("/match/12")

        assertEquals(AnswerState.UNAVAILABLE, page(result).state)
        verify(listingClient, never()).images(123)
    }

    @Test
    fun `mandate without agent`() {
        MatchFixtures.signIn()
        doReturn(Mandate(id = 7, agentId = null, cityId = 1)).whenever(mandateClient).get(7)

        val result = request("/match/12")

        assertEquals("Votre agent vous propose ce logement", page(result).title)
        verify(agentClient, never()).get(any())
    }

    @Test
    fun accept() {
        MatchFixtures.signIn()

        val result = perform(post("/match/12/accept"))

        verify(matchClient).accept(12)
        assertEquals(302, result.response.status)
        assertEquals("/match/12?answer=accepted", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun reject() {
        MatchFixtures.signIn()

        val result = perform(post("/match/12/reject").param("reason", "PRICE").param("comment", "  Trop cher pour moi "))

        verify(matchClient).reject(12, RejectMatchRequest(MatchRejectionReason.PRICE, "Trop cher pour moi"))
        assertEquals(302, result.response.status)
        assertEquals("/match/12?answer=rejected", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun `reject - blank comment`() {
        MatchFixtures.signIn()

        perform(post("/match/12/reject").param("reason", "LOCATION").param("comment", "   "))

        verify(matchClient).reject(12, RejectMatchRequest(MatchRejectionReason.LOCATION, null))
    }

    @Test
    fun `reject - missing reason`() {
        MatchFixtures.signIn()

        perform(post("/match/12/reject"))

        verify(matchClient).reject(12, RejectMatchRequest(MatchRejectionReason.OTHER, null))
    }

    @Test
    fun `reject - UNKNOWN reason`() {
        MatchFixtures.signIn()

        perform(post("/match/12/reject").param("reason", "UNKNOWN"))

        verify(matchClient).reject(12, RejectMatchRequest(MatchRejectionReason.OTHER, null))
    }

    @Test
    fun `reject - comment cut to what ndopify-server takes`() {
        MatchFixtures.signIn()

        perform(post("/match/12/reject").param("reason", "OTHER").param("comment", "x".repeat(1200)))

        verify(matchClient).reject(12, RejectMatchRequest(MatchRejectionReason.OTHER, "x".repeat(1000)))
    }

    @Test
    fun answerTwice() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.CONFLICT)).whenever(matchClient).accept(12)

        val result = perform(post("/match/12/accept"))

        assertEquals(302, result.response.status)
        assertEquals("/match/12", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun rejectTwice() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.CONFLICT)).whenever(matchClient).reject(any(), any())

        val result = perform(post("/match/12/reject").param("reason", "PRICE"))

        assertEquals(302, result.response.status)
        assertEquals("/match/12", result.response.getHeader(HttpHeaders.LOCATION))
    }

    @Test
    fun `answer signed out`() {
        val result = perform(post("/match/12/accept"))

        verify(matchClient, never()).accept(any())
        assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `answer with a token ndopify-server rejects`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.UNAUTHORIZED)).whenever(matchClient).reject(any(), any())

        val result = perform(post("/match/12/reject").param("reason", "PRICE"))

        verify(accessTokenService).remove()
        assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun `answer another's match`() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(matchClient).accept(12)

        val result = perform(post("/match/12/accept"))

        assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("not-yours", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun signedOut() {
        val result = request("/match/12")

        assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
        assertFalse(result.modelAndView!!.model.containsKey("view"))
        verify(matchClient, never()).get(12)
    }

    @Test
    fun serverUnauthorized() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.UNAUTHORIZED)).whenever(matchClient).get(12)

        val result = request("/match/12")

        verify(accessTokenService).remove()
        assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("expired", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun notYours() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.FORBIDDEN)).whenever(matchClient).get(12)

        val result = request("/match/12")

        verify(accessTokenService, never()).remove()
        assertEquals(MatchController.VIEW_LINK, result.modelAndView?.viewName)
        assertEquals("not-yours", result.modelAndView?.model?.get("reason"))
    }

    @Test
    fun notFound() {
        MatchFixtures.signIn()
        doThrow(failure(HttpStatus.NOT_FOUND)).whenever(matchClient).get(12)

        val result = request("/match/12")

        assertEquals(404, result.response.status)
        assertNull(result.modelAndView?.viewName)
    }
}
