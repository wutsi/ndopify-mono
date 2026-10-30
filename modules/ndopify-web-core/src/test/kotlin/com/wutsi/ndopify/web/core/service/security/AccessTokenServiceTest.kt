package com.wutsi.ndopify.web.core.service.security

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessTokenServiceTest {
    companion object {
        private const val ACCESS_TOKEN_COOKIE = "__test_access_token"
    }

    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val service = AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, Duration.ofHours(1))

    @Test
    fun set() {
        service.set("token-123")

        val cookie = setCookie()
        assertTrue(cookie.startsWith("$ACCESS_TOKEN_COOKIE=token-123;"), cookie)
        assertTrue(cookie.contains("Path=/"), cookie)
        assertTrue(cookie.contains("HttpOnly"), cookie)
        assertTrue(cookie.contains("SameSite=Lax"), cookie)
        assertTrue(cookie.contains("Max-Age=3600"), cookie) // the TTL
        assertFalse(cookie.contains("Secure"), cookie)
    }

    @Test
    fun `set a session cookie without TTL`() {
        AccessTokenService(request, response, ACCESS_TOKEN_COOKIE, null).set("token-123")

        val cookie = setCookie()
        assertTrue(cookie.startsWith("$ACCESS_TOKEN_COOKIE=token-123;"), cookie)
        assertFalse(cookie.contains("Max-Age"), cookie)
        assertFalse(cookie.contains("Expires"), cookie)
    }

    @Test
    fun `set over HTTPS`() {
        request.isSecure = true

        service.set("token-123")

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

    private fun setCookie(): String = response.getHeader(HttpHeaders.SET_COOKIE) ?: ""
}
