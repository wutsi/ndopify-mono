package com.wutsi.ndopify.npc.ui.login

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdAuthClient
import com.wutsi.ndopify.npc.client.ndopify.NdOtpClient
import com.wutsi.ndopify.npc.service.security.AccessTokenService
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginControllerTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val otpClient = mock<NdOtpClient>()
    private val authClient = mock<NdAuthClient>()
    private val accessTokenService = mock<AccessTokenService>()
    private val pending = PendingLogin(email = "ray@gmail.com", expiresAt = now.toEpochMilli() + 300_000)
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = LoginController(otpClient, authClient, accessTokenService, Clock.fixed(now, ZoneOffset.UTC))
        // The view is named after its URL ("login" at /login); the default resolver would take that for a loop.
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    @Test
    fun show() {
        val result = mvc.perform(get("/login").session(session)).andReturn()

        assertEquals("login", result.modelAndView?.viewName)
        assertEquals("email", model(result)["step"])
        assertNull(model(result)["email"])
    }

    @Test
    fun showPrefillsPendingEmail() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)

        val result = mvc.perform(get("/login").session(session)).andReturn()

        assertEquals("email", model(result)["step"])
        assertEquals("ray@gmail.com", model(result)["email"])
    }

    @Test
    fun showCodeStep() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)

        val result = mvc.perform(get("/login").param("step", "code").session(session)).andReturn()

        assertEquals("login", result.modelAndView?.viewName)
        assertEquals("code", model(result)["step"])
        assertEquals("ra•••@gmail.com", model(result)["maskedEmail"])
        assertEquals(pending.expiresAt, model(result)["expiresAt"])
        assertNull(model(result)["email"])
    }

    @Test
    fun showCodeStepWithoutPendingLogin() {
        val result = mvc.perform(get("/login").param("step", "code").session(session)).andReturn()

        assertEquals("login", result.modelAndView?.viewName)
        assertEquals("email", model(result)["step"])
        assertNull(model(result)["maskedEmail"])
    }

    @Test
    fun connectionRedirectsToCodeStep() {
        val result = mvc.perform(get("/login/connection")).andReturn()

        assertEquals("redirect:/login?step=code", result.modelAndView?.viewName)
    }

    @Test
    fun submit() {
        val result = mvc.perform(post("/login").param("email", " Ray@Gmail.com ").session(session)).andReturn()

        val request = argumentCaptor<CreateOtpRequest>()
        verify(otpClient).create(request.capture())
        assertEquals("ray@gmail.com", request.firstValue.email)
        assertEquals(300, request.firstValue.ttl)

        assertEquals("redirect:/login?step=code", result.modelAndView?.viewName)
        assertEquals(pending, session.getAttribute(PendingLogin.SESSION_ATTRIBUTE))
        assertEquals(false, result.flashMap["resent"])
        assertNull(result.flashMap["email"])
    }

    @Test
    fun resend() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending.copy(expiresAt = 0))

        val result = mvc.perform(post("/login").param("resend", "true").session(session)).andReturn()

        val request = argumentCaptor<CreateOtpRequest>()
        verify(otpClient).create(request.capture())
        assertEquals("ray@gmail.com", request.firstValue.email)

        assertEquals("redirect:/login?step=code", result.modelAndView?.viewName)
        assertEquals(true, result.flashMap["resent"])
        assertEquals(pending, session.getAttribute(PendingLogin.SESSION_ATTRIBUTE))
    }

    @Test
    fun resendIgnoresPostedEmail() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)

        mvc.perform(post("/login").param("resend", "true").param("email", "other@gmail.com").session(session))
            .andReturn()

        val request = argumentCaptor<CreateOtpRequest>()
        verify(otpClient).create(request.capture())
        assertEquals("ray@gmail.com", request.firstValue.email)
    }

    @Test
    fun resendWithoutPendingLogin() {
        val result = mvc.perform(post("/login").param("resend", "true").session(session)).andReturn()

        assertEquals("redirect:/login", result.modelAndView?.viewName)
        verify(otpClient, never()).create(any())
    }

    @Test
    fun submitRejectedByServer() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(otpClient).create(any())

        val result = mvc.perform(post("/login").param("email", "ray").session(session)).andReturn()

        assertEquals("login", result.modelAndView?.viewName)
        assertEquals("email", model(result)["step"])
        assertEquals("ray", model(result)["email"])
        assertTrue(model(result)["error"] is String)
        assertTrue(result.flashMap.isEmpty())
        assertNull(session.getAttribute(PendingLogin.SESSION_ATTRIBUTE))
    }

    @Test
    fun verify() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
        doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())
        val sessionId = session.id

        val result = mvc.perform(post("/login/verify").param("code", "123 456").session(session)).andReturn()

        val request = argumentCaptor<AuthenticateRequest>()
        verify(authClient).authenticate(request.capture())
        assertEquals("ray@gmail.com", request.firstValue.email)
        assertEquals("partner-central", request.firstValue.applicationCode)
        assertEquals(AuthType.OTP, request.firstValue.authType)
        assertEquals("123456", request.firstValue.secret)

        assertEquals("redirect:/", result.modelAndView?.viewName)
        assertNull(session.getAttribute(PendingLogin.SESSION_ATTRIBUTE))
        assertNotEquals(sessionId, session.id)

        verify(accessTokenService).set("token-123")
    }

    @Test
    fun verifyWithoutPendingLogin() {
        val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

        assertEquals("redirect:/login", result.modelAndView?.viewName)
        verify(authClient, never()).authenticate(any())
    }

    @Test
    fun verifyInvalidCode() {
        assertVerifyError(
            ErrorCode.AUTH_INVALID_CREDENTIALS,
            "Code incorrect. Vérifiez les 6 chiffres reçus par email."
        )
    }

    @Test
    fun verifyExpiredCode() {
        assertVerifyError(ErrorCode.AUTH_CREDENTIALS_EXPIRED, "Ce code a expiré. Demandez un nouveau code ci-dessous.")
    }

    @Test
    fun verifyCodeAlreadyUsed() {
        assertVerifyError(
            ErrorCode.AUTH_CREDENTIALS_ALREADY_USED,
            "Ce code a déjà été utilisé. Demandez un nouveau code ci-dessous."
        )
    }

    @Test
    fun verifyNotAPartner() {
        assertVerifyError(
            ErrorCode.AUTH_ACCESS_DENIED,
            "Aucun compte partenaire n'utilise cette adresse. Vérifiez-la, ou rejoignez Ndopify.",
        )
    }

    @Test
    fun verifyUnexpectedError() {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(authClient).authenticate(any())

        val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

        assertEquals(
            "Connexion impossible pour le moment. Réessayez dans quelques instants.",
            model(result)["error"],
        )
    }

    private fun assertVerifyError(code: String, message: String) {
        session.setAttribute(PendingLogin.SESSION_ATTRIBUTE, pending)
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        doThrow(ex).whenever(authClient).authenticate(any())

        val result = mvc.perform(post("/login/verify").param("code", "123456").session(session)).andReturn()

        assertEquals("login", result.modelAndView?.viewName)
        assertEquals("code", model(result)["step"])
        assertEquals("ra•••@gmail.com", model(result)["maskedEmail"])
        assertEquals(message, model(result)["error"])
        assertNull(model(result)["email"])
        assertNull(result.response.getHeader(HttpHeaders.SET_COOKIE))
        // A wrong code keeps the sign-in pending, so the agent can try again or resend.
        assertEquals(pending, session.getAttribute(PendingLogin.SESSION_ATTRIBUTE))
    }

    private fun model(result: MvcResult): Map<String, Any?> = result.modelAndView?.model ?: emptyMap()
}
