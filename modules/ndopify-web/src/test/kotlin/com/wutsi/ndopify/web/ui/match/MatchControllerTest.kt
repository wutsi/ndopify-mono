package com.wutsi.ndopify.web.ui.match

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.lang.reflect.Proxy
import java.time.Clock
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchControllerTest {
    private val matchClient = mock<NdMatchClient> { on { get(12) } doReturn MATCH }
    private val accessTokenService = mock<AccessTokenService>()
    private val context = mock<TenantContext> { on { getTenant() } doReturn TENANT }

    @AfterEach
    fun tearDown() = MatchFixtures.signOut()

    private fun request(path: String, tokens: AccessTokenService = accessTokenService): MvcResult =
        MockMvcBuilders.standaloneSetup(MatchController(matchClient, tokens))
            .setControllerAdvice(TenantAdvice(context), HttpClientErrorHandler())
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
            .perform(get(path))
            .andReturn()

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
        assertEquals(MATCH, result.modelAndView?.model?.get("match"))
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
