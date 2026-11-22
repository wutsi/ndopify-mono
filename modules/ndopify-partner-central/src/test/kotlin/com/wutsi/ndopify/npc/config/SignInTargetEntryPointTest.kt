package com.wutsi.ndopify.npc.config

import com.wutsi.ndopify.npc.ui.login.SignInTarget
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.InsufficientAuthenticationException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignInTargetEntryPointTest {
    private val entryPoint = SignInTargetEntryPoint("/login")
    private val error = InsufficientAuthenticationException("sign in")

    @Test
    fun remembersAGetTarget() {
        val request = MockHttpServletRequest("GET", "/mandates/42").apply { queryString = "match=7" }
        val response = MockHttpServletResponse()

        entryPoint.commence(request, response, error)

        assertEquals(
            SignInTarget("/mandates/42", "match=7"),
            request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE),
        )
        assertTrue(response.redirectedUrl!!.endsWith("/login"))
    }

    @Test
    fun remembersAHeadTarget() {
        val request = MockHttpServletRequest("HEAD", "/listings/42")
        entryPoint.commence(request, MockHttpServletResponse(), error)
        assertEquals(SignInTarget("/listings/42"), request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
    }

    @Test
    fun ignoresAPost() {
        val request = MockHttpServletRequest("POST", "/mandates/42/close")
        entryPoint.commence(request, MockHttpServletResponse(), error)
        assertNull(request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
    }

    @Test
    fun ignoresAnExcludedPath() {
        val request = MockHttpServletRequest("GET", "/login")
        entryPoint.commence(request, MockHttpServletResponse(), error)
        assertNull(request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
    }
}
