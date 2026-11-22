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
    fun remembersAGetTargetAndAnswersTheInterstitial() {
        val request = MockHttpServletRequest("GET", "/mandates/42").apply { queryString = "match=7" }
        val response = MockHttpServletResponse()

        entryPoint.commence(request, response, error)

        assertEquals(
            SignInTarget("/mandates/42", "match=7"),
            request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE),
        )
        assertEquals(200, response.status)
        assertNull(response.redirectedUrl)
        assertTrue(response.contentAsString.contains("location.replace('/login' + location.hash)"))
    }

    @Test
    fun interstitialFallsBackToThePlainRedirectWithoutJavaScript() {
        val request = MockHttpServletRequest("GET", "/mandates/42")
        val response = MockHttpServletResponse()

        entryPoint.commence(request, response, error)

        assertTrue(response.contentAsString.contains("http-equiv=\"refresh\""))
        assertTrue(response.contentAsString.contains("url=/login"))
    }

    @Test
    fun remembersAHeadTarget() {
        val request = MockHttpServletRequest("HEAD", "/listings/42")
        val response = MockHttpServletResponse()
        entryPoint.commence(request, response, error)
        assertEquals(SignInTarget("/listings/42"), request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
        assertTrue(response.redirectedUrl!!.endsWith("/login"))
    }

    @Test
    fun ignoresAPost() {
        val request = MockHttpServletRequest("POST", "/mandates/42/close")
        val response = MockHttpServletResponse()
        entryPoint.commence(request, response, error)
        assertNull(request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
        assertTrue(response.redirectedUrl!!.endsWith("/login"))
    }

    @Test
    fun ignoresAnExcludedPath() {
        val request = MockHttpServletRequest("GET", "/login")
        val response = MockHttpServletResponse()
        entryPoint.commence(request, response, error)
        assertNull(request.session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
        assertTrue(response.redirectedUrl!!.endsWith("/login"))
    }
}
