package com.wutsi.ndopify.web.core.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.web.core.client.ndopify.NdTokenClient
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.client.RestClientException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessTokenRenewalFilterTest {
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val chain = MockFilterChain()

    // Relative to the real now: JwtDecoder checks the token's exp against the system clock, not the fixed one.
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val tokenClient = mock<NdTokenClient>()
    private val service = AccessTokenService(request, response, "__npc_access_token", clock = clock)
    private val filter = AccessTokenRenewalFilter(service, JwtDecoder("partner-central"), tokenClient, clock)

    @Test
    fun `renews a token older than the interval`() {
        val token = token(issuedAt = now.minus(Duration.ofDays(2)), expiresAt = now.plus(Duration.ofDays(28)))
        doReturn("new-token").whenever(tokenClient).refresh(token)
        request.setCookies(Cookie("__npc_access_token", token))

        filter.doFilter(request, response, chain)

        verify(tokenClient).refresh(token)
        assertTrue(setCookie().startsWith("__npc_access_token=new-token;"), setCookie())
        assertNotNull(chain.request) // the page renders
    }

    @Test
    fun `leaves a younger token untouched`() {
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofHours(1)), now.plus(Duration.ofDays(30)))))

        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `renews a token that expires within the interval`() {
        doReturn("new-token").whenever(tokenClient).refresh(any())
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(12)))))

        filter.doFilter(request, response, chain)

        verify(tokenClient).refresh(any())
        assertTrue(setCookie().startsWith("__npc_access_token=new-token;"), setCookie())
    }

    @Test
    fun `keeps the cookie when the renewal fails`() {
        doThrow(RestClientException("boom")).whenever(tokenClient).refresh(any())
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)))))

        filter.doFilter(request, response, chain)

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
        assertNotNull(chain.request)
    }

    @Test
    fun `never renews a token it cannot decode`() {
        request.setCookies(Cookie("__npc_access_token", "not-a-token"))

        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
        assertNotNull(chain.request)
    }

    @Test
    fun `never renews another application's token`() {
        request.setCookies(
            Cookie(
                "__npc_access_token",
                token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)), application = "public-portal"),
            ),
        )

        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
    }

    @Test
    fun `skips static assets and the error page`() {
        doReturn("new-token").whenever(tokenClient).refresh(any())
        for (path in listOf("/assets/dist/app.min.js", "/sw.js", "/offline.html", "/manifest.json", "/favicon.ico", "/error")) {
            request.servletPath = path
            request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)))))

            filter.doFilter(request, response, MockFilterChain())
        }

        verify(tokenClient, never()).refresh(any())
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    @Test
    fun `does nothing without a cookie`() {
        filter.doFilter(request, response, chain)

        verify(tokenClient, never()).refresh(any())
        assertNotNull(chain.request)
    }

    @Test
    fun `never sets an empty cookie`() {
        doReturn("").whenever(tokenClient).refresh(any())
        request.setCookies(Cookie("__npc_access_token", token(now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(28)))))

        filter.doFilter(request, response, chain)

        assertNull(response.getHeader(HttpHeaders.SET_COOKIE))
    }

    // The same claims as ndopify-server's AccessTokenService, signed with any secret (we never check it).
    private fun token(
        issuedAt: Instant,
        expiresAt: Instant,
        application: String = "partner-central",
    ): String = JWT.create()
        .withIssuer(JwtDecoder.ISSUER)
        .withIssuedAt(issuedAt)
        .withExpiresAt(expiresAt)
        .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
        .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 1L)
        .withClaim(JwtPrincipal.CLAIM_APPLICATION, application)
        .withArrayClaim(JwtPrincipal.CLAIM_ROLE, emptyArray<String>())
        .sign(Algorithm.HMAC256("any-secret"))

    private fun setCookie(): String = response.getHeader(HttpHeaders.SET_COOKIE) ?: ""
}
