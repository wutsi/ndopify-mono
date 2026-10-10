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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginControllerTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val otpClient = mock<NdOtpClient>()
    private val authClient = mock<NdAuthClient>()
    private val accessTokenService = mock<AccessTokenService>()
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = LoginController(otpClient, authClient, accessTokenService, Clock.fixed(now, ZoneOffset.UTC))
        mvc = MockMvcBuilders.standaloneSetup(controller).build()
    }

    @Test
    fun show() {
        val result = mvc.perform(get("/login")).andReturn()

        assertEquals("login/index", result.modelAndView?.viewName)
    }

    @Test
    fun submit() {
        val result = mvc.perform(post("/login").param("email", " Ray@Gmail.com ")).andReturn()

        val request = argumentCaptor<CreateOtpRequest>()
        verify(otpClient).create(request.capture())
        assertEquals("ray@gmail.com", request.firstValue.email)
        assertEquals(300, request.firstValue.ttl)

        assertEquals("redirect:/login/connection", result.modelAndView?.viewName)
        assertEquals("ray@gmail.com", result.flashMap["email"])
        assertEquals(now.toEpochMilli() + 300_000, result.flashMap["expiresAt"])
        assertEquals(false, result.flashMap["resent"])
    }

    @Test
    fun resend() {
        val result = mvc.perform(post("/login").param("email", "ray@gmail.com").param("resend", "true")).andReturn()

        assertEquals("redirect:/login/connection", result.modelAndView?.viewName)
        assertEquals(true, result.flashMap["resent"])
    }

    @Test
    fun submitRejectedByServer() {
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(otpClient).create(any())

        val result = mvc.perform(post("/login").param("email", "ray")).andReturn()

        assertEquals("login/index", result.modelAndView?.viewName)
        assertEquals("ray", result.modelAndView?.model?.get("email"))
        assertTrue(result.modelAndView?.model?.get("error") is String)
        assertTrue(result.flashMap.isEmpty())
    }

    @Test
    fun connection() {
        val result = mvc.perform(get("/login/connection").flashAttr("email", "ray@gmail.com")).andReturn()

        assertEquals("login/connection", result.modelAndView?.viewName)
        assertEquals("ra•••@gmail.com", result.modelAndView?.model?.get("maskedEmail"))
    }

    @Test
    fun connectionWithoutEmail() {
        val result = mvc.perform(get("/login/connection")).andReturn()

        assertEquals("login/connection", result.modelAndView?.viewName)
        assertNull(result.modelAndView?.model?.get("maskedEmail"))
    }

    @Test
    fun verify() {
        doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())

        val result = mvc.perform(post("/login/verify").param("email", "ray@gmail.com").param("code", "123 456"))
            .andReturn()

        val request = argumentCaptor<AuthenticateRequest>()
        verify(authClient).authenticate(request.capture())
        assertEquals("ray@gmail.com", request.firstValue.email)
        assertEquals("partner-central", request.firstValue.applicationCode)
        assertEquals(AuthType.OTP, request.firstValue.authType)
        assertEquals("123456", request.firstValue.secret)

        assertEquals("redirect:/kyc", result.modelAndView?.viewName)

        verify(accessTokenService).set("token-123")
    }

    @Test
    fun verifyWithoutEmail() {
        val result = mvc.perform(post("/login/verify").param("code", "123456")).andReturn()

        assertEquals("login/connection", result.modelAndView?.viewName)
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
    fun verifyNotAPartner() {
        assertVerifyError(
            ErrorCode.AUTH_ACCESS_DENIED,
            "Aucun compte partenaire n'utilise cette adresse. Vérifiez-la, ou rejoignez Ndopify.",
        )
    }

    @Test
    fun verifyUnexpectedError() {
        doThrow(HttpClientErrorException(HttpStatus.CONFLICT)).whenever(authClient).authenticate(any())

        val result = mvc.perform(post("/login/verify").param("email", "ray@gmail.com").param("code", "123456"))
            .andReturn()

        assertEquals(
            "Connexion impossible pour le moment. Réessayez dans quelques instants.",
            result.modelAndView?.model?.get("error"),
        )
    }

    private fun assertVerifyError(code: String, message: String) {
        val ex = HttpClientErrorException(HttpStatus.CONFLICT)
        ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        doThrow(ex).whenever(authClient).authenticate(any())

        val result = mvc.perform(post("/login/verify").param("email", "ray@gmail.com").param("code", "123456"))
            .andReturn()

        assertEquals("login/connection", result.modelAndView?.viewName)
        assertEquals("ray@gmail.com", result.modelAndView?.model?.get("email"))
        assertEquals("ra•••@gmail.com", result.modelAndView?.model?.get("maskedEmail"))
        assertEquals(message, result.modelAndView?.model?.get("error"))
        assertNull(result.response.getHeader(HttpHeaders.SET_COOKIE))
    }
}
