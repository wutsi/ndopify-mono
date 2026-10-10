package com.wutsi.ndopify.npc.service.security

import com.wutsi.ndopify.npc.service.security.AccessTokenService.Companion.ACCESS_TOKEN_COOKIE
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessTokenServiceTest {
    private val request = MockHttpServletRequest()
    private val response = MockHttpServletResponse()
    private val service = AccessTokenService(request, response)

    @Test
    fun set() {
        service.set("token-123")

        val cookie = setCookie()
        assertTrue(cookie.startsWith("$ACCESS_TOKEN_COOKIE=token-123;"), cookie)
        assertTrue(cookie.contains("Path=/"), cookie)
        assertTrue(cookie.contains("HttpOnly"), cookie)
        assertTrue(cookie.contains("SameSite=Lax"), cookie)
        assertFalse(cookie.contains("Max-Age"), cookie) // session cookie
        assertFalse(cookie.contains("Secure"), cookie)
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
    fun `get without the cookie`() {
        request.setCookies(Cookie("other", "value"))

        assertNull(service.get())
    }

    @Test
    fun `get without any cookie`() {
        assertNull(service.get())
    }

    private fun setCookie(): String = response.getHeader(HttpHeaders.SET_COOKIE) ?: ""
}
