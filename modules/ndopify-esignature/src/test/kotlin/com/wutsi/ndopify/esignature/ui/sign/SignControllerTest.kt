package com.wutsi.ndopify.esignature.ui.sign

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.dto.Contract
import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.dto.ContractSummary
import com.wutsi.ndopify.contract.dto.SearchContractRequest
import com.wutsi.ndopify.contract.dto.SignContractRequest
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.esignature.client.ContractContentClient
import com.wutsi.ndopify.esignature.client.ndopify.NdAuthClient
import com.wutsi.ndopify.esignature.client.ndopify.NdContractClient
import com.wutsi.ndopify.esignature.client.ndopify.NdOtpClient
import com.wutsi.ndopify.esignature.service.TenantContext
import com.wutsi.ndopify.esignature.service.TenantFixtures.TENANT
import com.wutsi.ndopify.esignature.service.security.AccessTokenService
import com.wutsi.ndopify.esignature.service.security.JwtAuthentication
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTENT_HTML
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTENT_URL
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTRACT
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.CONTRACT_PARTY_ID
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.NOW
import com.wutsi.ndopify.esignature.ui.sign.SignFixtures.signed
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.servlet.view.InternalResourceViewResolver
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignControllerTest {
    private val otpClient = mock<NdOtpClient>()
    private val authClient = mock<NdAuthClient>()
    private val contractClient = mock<NdContractClient>()
    private val contentClient = mock<ContractContentClient>()
    private val accessTokenService = mock<AccessTokenService>()
    private val tenantContext = mock<TenantContext> {
        on { getTenant() } doReturn TENANT
    }
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val pending = PendingSignature(
        contractPartyId = CONTRACT_PARTY_ID,
        email = "ray.sponsible@gmail.com",
        expiresAt = NOW.toEpochMilli() + 300_000,
    )
    private val path = "/sign/$CONTRACT_PARTY_ID"
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = SignController(
            otpClient,
            authClient,
            contractClient,
            contentClient,
            accessTokenService,
            SignViews(clock, tenantContext),
            clock,
        )
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
        doReturn(CONTENT_HTML).whenever(contentClient).fetch(CONTENT_URL)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `intro without token`() {
        val result = mvc.perform(get(path).session(session)).andReturn()

        assertEquals("sign", result.modelAndView?.viewName)
        assertEquals(CONTRACT_PARTY_ID, model(result)["contractPartyId"])
        assertEquals(SignView.STEP_INTRO, view(result).step)
        assertNull(model(result)["email"])
        verify(contractClient, never()).search(any())
    }

    @Test
    fun `code step needs pending`() {
        val result = mvc.perform(get(path).param("step", "code").session(session)).andReturn()

        assertEquals(SignView.STEP_INTRO, view(result).step)
    }

    @Test
    fun `code step`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending)

        val result = mvc.perform(get(path).param("step", "code").session(session)).andReturn()

        assertEquals(SignView.STEP_CODE, view(result).step)
        assertEquals("ra•••@gmail.com", view(result).maskedEmail)
        assertEquals("14:37", view(result).codeExpiresAt)
    }

    @Test
    fun `pending of another link shows intro`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending.copy(contractPartyId = "cp-other"))

        val result = mvc.perform(get(path).param("step", "code").session(session)).andReturn()

        assertEquals(SignView.STEP_INTRO, view(result).step)
    }

    @Test
    fun `email is normalized`() {
        val result = mvc.perform(post("$path/otp").param("email", " Ray.Sponsible@Gmail.com ").session(session))
            .andReturn()

        val request = argumentCaptor<CreateOtpRequest>()
        verify(otpClient).create(request.capture())
        assertEquals("ray.sponsible@gmail.com", request.firstValue.email)
        assertEquals(300, request.firstValue.ttl)

        assertEquals("redirect:$path?step=code", result.modelAndView?.viewName)
        assertEquals(pending, session.getAttribute(PendingSignature.SESSION_ATTRIBUTE))
        assertEquals(false, result.flashMap["resent"])
    }

    @Test
    fun `unknown email keeps it in the field`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending)
        doThrow(error(HttpStatus.NOT_FOUND, ErrorCode.USER_NOT_FOUND)).whenever(otpClient).create(any())

        val result = mvc.perform(post("$path/otp").param("email", "other@gmail.com").session(session)).andReturn()

        assertEquals("sign", result.modelAndView?.viewName)
        assertEquals(SignView.STEP_INTRO, view(result).step)
        assertEquals("other@gmail.com", model(result)["email"])
        assertEquals(SignController.EMAIL_UNKNOWN, model(result)["error"])
        assertEquals(CONTRACT_PARTY_ID, model(result)["contractPartyId"])
        assertNull(session.getAttribute(PendingSignature.SESSION_ATTRIBUTE))
    }

    @Test
    fun `code that can't be sent`() {
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(otpClient).create(any())

        val result = mvc.perform(post("$path/otp").param("email", "ray").session(session)).andReturn()

        assertEquals(SignView.STEP_INTRO, view(result).step)
        assertEquals("ray", model(result)["email"])
        assertEquals(
            "Le code n'a pas pu être envoyé. Vérifiez votre connexion, puis réessayez.",
            model(result)["error"],
        )
    }

    @Test
    fun `resend reuses pending email`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending.copy(expiresAt = 0))

        val result = mvc.perform(
            post("$path/otp").param("resend", "true").param("email", "other@gmail.com").session(session),
        ).andReturn()

        val request = argumentCaptor<CreateOtpRequest>()
        verify(otpClient).create(request.capture())
        assertEquals("ray.sponsible@gmail.com", request.firstValue.email)
        assertEquals("redirect:$path?step=code", result.modelAndView?.viewName)
        assertEquals(true, result.flashMap["resent"])
        assertEquals(pending, session.getAttribute(PendingSignature.SESSION_ATTRIBUTE))
    }

    @Test
    fun `resend without pending`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending.copy(contractPartyId = "cp-other"))

        val result = mvc.perform(post("$path/otp").param("resend", "true").session(session)).andReturn()

        assertEquals("redirect:$path", result.modelAndView?.viewName)
        verify(otpClient, never()).create(any())
    }

    @Test
    fun `verify sets cookie and opens sign`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending)
        doReturn(AuthenticateResponse("token-123")).whenever(authClient).authenticate(any())

        val result = mvc.perform(
            post("$path/verify").param("code", "123 456").param("email", "other@gmail.com").session(session),
        ).andReturn()

        val request = argumentCaptor<AuthenticateRequest>()
        verify(authClient).authenticate(request.capture())
        assertEquals("ray.sponsible@gmail.com", request.firstValue.email)
        assertEquals("e-signature", request.firstValue.applicationCode)
        assertEquals(AuthType.OTP, request.firstValue.authType)
        assertEquals("123456", request.firstValue.secret)

        assertEquals("redirect:$path?step=sign", result.modelAndView?.viewName)
        assertNull(session.getAttribute(PendingSignature.SESSION_ATTRIBUTE))
        verify(accessTokenService).set("token-123")
    }

    @Test
    fun `verify without pending`() {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending.copy(contractPartyId = "cp-other"))

        val result = mvc.perform(post("$path/verify").param("code", "123456").session(session)).andReturn()

        assertEquals("redirect:$path", result.modelAndView?.viewName)
        verify(authClient, never()).authenticate(any())
    }

    @Test
    fun `wrong code keeps code`() {
        assertVerifyError(ErrorCode.AUTH_INVALID_CREDENTIALS, "Code incorrect. Vérifiez les 6 chiffres reçus par e-mail.")
    }

    @Test
    fun `expired code`() {
        assertVerifyError(ErrorCode.AUTH_CREDENTIALS_EXPIRED, "Ce code a expiré. Demandez-en un nouveau ci-dessous.")
    }

    @Test
    fun `code already used`() {
        assertVerifyError(
            ErrorCode.AUTH_CREDENTIALS_ALREADY_USED,
            "Ce code a déjà été utilisé. Demandez-en un nouveau ci-dessous.",
        )
    }

    @Test
    fun `no e-signature access`() {
        assertVerifyError(ErrorCode.AUTH_ACCESS_DENIED, SignController.EMAIL_UNKNOWN)
    }

    @Test
    fun `verify unexpected error`() {
        assertVerifyError(null, "Connexion impossible pour le moment. Réessayez dans quelques instants.")
    }

    @Test
    fun `sign step shows content`() {
        signIn()
        givenContract(CONTRACT)

        val result = mvc.perform(get(path).param("step", "sign")).andReturn()

        assertEquals("sign", result.modelAndView?.viewName)
        assertEquals(CONTRACT_PARTY_ID, model(result)["contractPartyId"])
        assertEquals(SignView.STEP_SIGN, view(result).step)
        assertEquals(CONTENT_HTML, view(result).contentHtml)
        assertEquals("MNDT-2026-000042", view(result).contractNumber)
        assertTrue(view(result).canSign)

        val search = argumentCaptor<SearchContractRequest>()
        verify(contractClient).search(search.capture())
        assertEquals(listOf(CONTRACT_PARTY_ID), search.firstValue.contractPartyIds)
    }

    @Test
    fun `no sign button without content`() {
        signIn()
        givenContract(CONTRACT)
        doReturn(null).whenever(contentClient).fetch(CONTENT_URL)

        val result = mvc.perform(get(path).param("step", "sign")).andReturn()

        assertEquals(SignView.STEP_SIGN, view(result).step)
        assertNull(view(result).contentHtml)
        assertEquals(false, view(result).canSign)
    }

    @Test
    fun `other account sees not yours`() {
        signIn()
        doReturn(emptyList<ContractSummary>()).whenever(contractClient).search(any())

        val result = mvc.perform(get(path).param("step", "sign")).andReturn()

        assertEquals(SignView(step = SignView.STEP_BLOCKED, blocked = SignView.BLOCKED_NOT_YOURS), view(result))
        verify(contractClient, never()).get(any())
        verify(contentClient, never()).fetch(any())
    }

    @Test
    fun `blocked states never show contract content`() {
        signIn()
        listOf(
            CONTRACT.copy(status = ContractStatus.CANCELLED) to SignView.BLOCKED_CANCELLED,
            CONTRACT.copy(status = ContractStatus.EXPIRED) to SignView.BLOCKED_EXPIRED,
            CONTRACT.copy(status = ContractStatus.DRAFT) to SignView.BLOCKED_UNAVAILABLE,
            signed() to SignView.BLOCKED_SIGNED,
            CONTRACT.copy(parties = CONTRACT.parties.map { it.copy(signatureRequired = false) }) to
                SignView.BLOCKED_NOT_REQUIRED,
        ).forEach { (contract, reason) ->
            givenContract(contract)

            val result = mvc.perform(get(path).param("step", "sign")).andReturn()

            assertEquals(SignView.STEP_BLOCKED, view(result).step)
            assertEquals(reason, view(result).blocked)
            assertNull(view(result).contentHtml)
        }
        verify(contentClient, never()).fetch(any())
    }

    @Test
    fun `done after signing`() {
        signIn()
        givenContract(signed())

        val result = mvc.perform(get(path).param("step", "done")).andReturn()

        assertEquals(SignView.STEP_DONE, view(result).step)
        assertEquals("18 oct. 2026 à 14:32", view(result).signedAt)
        verify(contentClient, never()).fetch(any())
    }

    @Test
    fun `consent required`() {
        signIn()
        givenContract(CONTRACT)

        val result = mvc.perform(post(path).param("partyId", "cp-other")).andReturn()

        assertEquals("sign", result.modelAndView?.viewName)
        assertEquals(SignView.STEP_SIGN, view(result).step)
        assertEquals(CONTENT_HTML, view(result).contentHtml)
        assertEquals("Cochez la case pour signer le contrat.", model(result)["error"])
        verify(contractClient, never()).sign(any(), any())
    }

    @Test
    fun `signs with ip and user agent`() {
        signIn()
        givenContract(CONTRACT)

        val result = mvc.perform(
            post(path)
                .param("consent", "yes")
                .param("partyId", "cp-other")
                .header("X-Forwarded-For", "41.202.1.2, 10.0.0.1")
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0"),
        ).andReturn()

        val request = argumentCaptor<SignContractRequest>()
        verify(contractClient).sign(eq(CONTRACT.id), request.capture())
        assertEquals(SignContractRequest(CONTRACT_PARTY_ID, "41.202.1.2", "Mozilla/5.0"), request.firstValue)
        assertEquals("redirect:$path?step=done", result.modelAndView?.viewName)
    }

    @Test
    fun `signs with remote address without proxy`() {
        signIn()
        givenContract(CONTRACT)

        mvc.perform(post(path).param("consent", "yes").with { it.apply { remoteAddr = "41.202.1.3" } }).andReturn()

        val request = argumentCaptor<SignContractRequest>()
        verify(contractClient).sign(eq(CONTRACT.id), request.capture())
        assertEquals("41.202.1.3", request.firstValue.ipAddress)
    }

    @Test
    fun `second sign shows done`() {
        signIn()
        givenContract(CONTRACT)
        doThrow(error(HttpStatus.CONFLICT, ErrorCode.CONTRACT_PARTY_ALREADY_SIGNED))
            .whenever(contractClient).sign(any(), any())

        val result = mvc.perform(post(path).param("consent", "yes")).andReturn()

        assertEquals("redirect:$path?step=done", result.modelAndView?.viewName)

        givenContract(signed())
        val done = mvc.perform(get(path).param("step", "done")).andReturn()
        assertEquals(SignView.STEP_DONE, view(done).step)
    }

    @Test
    fun `sign failure`() {
        signIn()
        givenContract(CONTRACT)
        doThrow(HttpClientErrorException(HttpStatus.BAD_REQUEST)).whenever(contractClient).sign(any(), any())

        val result = mvc.perform(post(path).param("consent", "yes")).andReturn()

        assertEquals(SignView.STEP_SIGN, view(result).step)
        assertEquals(
            "Votre signature n'a pas pu être enregistrée. Vérifiez votre connexion, puis réessayez.",
            model(result)["error"],
        )
    }

    @Test
    fun `sign without token`() {
        val result = mvc.perform(post(path).param("consent", "yes")).andReturn()

        assertEquals("redirect:$path", result.modelAndView?.viewName)
        verify(contractClient, never()).sign(any(), any())
    }

    @Test
    fun `sign another's contract party`() {
        signIn()
        doReturn(emptyList<ContractSummary>()).whenever(contractClient).search(any())

        val result = mvc.perform(post(path).param("consent", "yes")).andReturn()

        assertEquals("redirect:$path", result.modelAndView?.viewName)
        verify(contractClient, never()).sign(any(), any())
    }

    @Test
    fun `logout clears cookie`() {
        signIn()
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending)

        val result = mvc.perform(post("$path/logout").session(session)).andReturn()

        assertEquals("redirect:$path", result.modelAndView?.viewName)
        verify(accessTokenService).remove()
        assertTrue(session.isInvalid)
    }

    private fun assertVerifyError(code: String?, message: String) {
        session.setAttribute(PendingSignature.SESSION_ATTRIBUTE, pending)
        doThrow(error(HttpStatus.CONFLICT, code)).whenever(authClient).authenticate(any())

        val result = mvc.perform(post("$path/verify").param("code", "123 45").session(session)).andReturn()

        assertEquals("sign", result.modelAndView?.viewName)
        assertEquals(SignView.STEP_CODE, view(result).step)
        assertEquals("ra•••@gmail.com", view(result).maskedEmail)
        assertEquals(message, model(result)["error"])
        assertEquals("12345", model(result)["code"])
        assertEquals(CONTRACT_PARTY_ID, model(result)["contractPartyId"])
        verify(accessTokenService, never()).set(any())
        // A wrong code keeps the sign-in pending, so the signer can try again or resend
        assertEquals(pending, session.getAttribute(PendingSignature.SESSION_ATTRIBUTE))
    }

    private fun signIn() {
        val principal = JwtPrincipal(mock<DecodedJWT>())
        SecurityContextHolder.setContext(SecurityContextImpl(JwtAuthentication(principal)))
    }

    private fun givenContract(contract: Contract) {
        doReturn(listOf(ContractSummary(id = contract.id))).whenever(contractClient).search(any())
        doReturn(contract).whenever(contractClient).get(contract.id)
    }

    private fun error(status: HttpStatus, code: String?): HttpClientErrorException {
        val ex = HttpClientErrorException(status)
        if (code != null) {
            ex.setBodyConvertFunction { ErrorResponse(Error(code = code)) }
        }
        return ex
    }

    private fun view(result: MvcResult): SignView = model(result)["view"] as SignView

    private fun model(result: MvcResult): Map<String, Any?> = result.modelAndView?.model ?: emptyMap()
}
