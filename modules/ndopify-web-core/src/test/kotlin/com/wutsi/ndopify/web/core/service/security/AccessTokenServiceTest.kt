package com.wutsi.ndopify.web.core.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessTokenServiceTest {
    companion object {
        private const val ACCESS_TOKEN_COOKIE = "__test_access_token"
        private val NOW = Instant.parse("2026-10-30T12:00:00Z")
    }

    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val service = AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, clock = clock)
    private val token = token(expiresAt = NOW.plus(Duration.ofDays(1)))

    @Test
    fun set() {
        service.set(token)

        val cookie = setCookie()
        assertTrue(cookie.startsWith("$ACCESS_TOKEN_COOKIE=$token;"), cookie)
        assertTrue(cookie.contains("Path=/"), cookie)
        assertTrue(cookie.contains("HttpOnly"), cookie)
        assertTrue(cookie.contains("SameSite=Lax"), cookie)
        assertTrue(cookie.contains("Max-Age=86400"), cookie) // until the token's exp
        assertFalse(cookie.contains("Secure"), cookie)
    }

    @Test
    fun `set a token that lived part of its TTL`() {
        service.set(token(expiresAt = NOW.plus(Duration.ofMinutes(30))))

        assertTrue(setCookie().contains("Max-Age=1800"), setCookie())
    }

    @Test
    fun `set an expired token`() {
        service.set(token(expiresAt = NOW.minus(Duration.ofMinutes(1))))

        assertTrue(setCookie().contains("Max-Age=0"), setCookie()) // dropped by the browser at once
    }

    @Test
    fun `set a session cookie when not persistent`() {
        AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, persistent = false, clock = clock).set(token)

        assertSessionCookie(token)
    }

    @Test
    fun `set a session cookie for a token without exp`() {
        val token = JWT.create().withSubject("1").sign(Algorithm.HMAC256("secret"))

        service.set(token)

        assertSessionCookie(token)
    }

    @Test
    fun `set a session cookie for a token that does not decode`() {
        service.set("token-123")

        assertSessionCookie("token-123")
    }

    @Test
    fun `set over HTTPS`() {
        request.isSecure = true

        service.set(token)

        assertTrue(setCookie().contains("Secure"), setCookie())
    }

    @Test
    fun remove() {
        service.remove()

        val cookie = setCookie()
        assertTrue(cookie.startsWith("$ACCESS_TOKEN_COOKIE=;"), cookie)
        assertTrue(cookie.contains("Max-Age=0"), cookie)
        // Same path and flags as set(), otherwise the browser keeps the original cookie.
        assertTrue(cookie.contains("Path=/"), cookie)
        assertTrue(cookie.contains("HttpOnly"), cookie)
        assertTrue(cookie.contains("SameSite=Lax"), cookie)
    }

    @Test
    fun `remove over HTTPS`() {
        request.isSecure = true

        service.remove()

        assertTrue(setCookie().contains("Secure"), setCookie())
    }

    @Test
    fun get() {
        request.setCookies(Cookie("other", "value"), Cookie(ACCESS_TOKEN_COOKIE, "token-123"))

        assertEquals("token-123", service.get())
    }

    @Test
    fun `get ignores the cookie of another web app`() {
        request.setCookies(Cookie("__npc_access_token", "token-123"))

        assertNull(service.get())
    }

    @Test
    fun `get without the cookie`() {
        request.setCookies(Cookie("other", "value"))

        assertNull(service.get())
    }

    @Test
    fun `get without any cookie`() {
        assertNull(service.get())
    }

    @Test
    fun cookieName() {
        assertEquals(ACCESS_TOKEN_COOKIE, service.cookieName)
    }

    private fun assertSessionCookie(token: String) {
        val cookie = setCookie()
        assertTrue(cookie.startsWith("$ACCESS_TOKEN_COOKIE=$token;"), cookie)
        assertFalse(cookie.contains("Max-Age"), cookie)
        assertFalse(cookie.contains("Expires"), cookie)
    }

    private fun token(expiresAt: Instant): String =
        JWT.create().withSubject("1").withExpiresAt(expiresAt).sign(Algorithm.HMAC256("secret"))

    private fun setCookie(): String = response.getHeader(HttpHeaders.SET_COOKIE) ?: ""
}
