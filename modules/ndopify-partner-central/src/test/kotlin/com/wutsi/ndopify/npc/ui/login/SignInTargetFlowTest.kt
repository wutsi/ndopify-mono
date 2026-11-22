package com.wutsi.ndopify.npc.ui.login

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdAuthClient
import com.wutsi.ndopify.npc.client.ndopify.NdOtpClient
import com.wutsi.ndopify.npc.config.SignInTargetEntryPoint
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.web.core.service.security.AccessTokenService
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The full round trip a Telegram deep link takes: the entry point's interstitial (whose script forwards
 * `location.hash`, which a JVM test can't run) → `/login` → submit (which lifts the hash into the target, standing in
 * for the login page's one client-side step) → verify. It pins the fragment and the target end to end, not just per
 * unit.
 */
class SignInTargetFlowTest {
    private val otpClient = mock<NdOtpClient>()
    private val authClient = mock<NdAuthClient>()
    private val accessTokenService = mock<AccessTokenService>()
    private val entryPoint = SignInTargetEntryPoint("/login")

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(
            LoginController(
                otpClient,
                authClient,
                accessTokenService,
                Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC),
            ),
        )
        .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
        .build()

    @Test
    fun deepLinkReturnsToTheRequestedPageAtItsAnchor() {
        // 1. A signed-out agent opens /mandates/42#match-7. The entry point remembers the page without the fragment
        //    (it never reaches the server) and answers the interstitial, whose script forwards the hash to /login.
        val session = MockHttpSession()
        val request = MockHttpServletRequest("GET", "/mandates/42")
        request.setSession(session)
        val response = MockHttpServletResponse()
        entryPoint.commence(request, response, InsufficientAuthenticationException("sign in"))
        assertEquals(SignInTarget("/mandates/42"), session.getAttribute(SignInTarget.SESSION_ATTRIBUTE))
        assertTrue(response.contentAsString.contains("location.replace('/login' + location.hash)"))

        // 2. The interstitial's script landed on /login#match-7 and the login page's script lifted the hash into the
        //    hidden fragment field; submit the e-mail, which stores it on the target.
        mvc.perform(post("/login").param("email", "ray@gmail.com").param("fragment", "match-7").session(session))

        // 3. A valid code returns to the requested page, at the anchor.
        doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())
        val verify = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

        assertEquals("redirect:/mandates/42#match-7", verify.modelAndView?.viewName)
    }
}
