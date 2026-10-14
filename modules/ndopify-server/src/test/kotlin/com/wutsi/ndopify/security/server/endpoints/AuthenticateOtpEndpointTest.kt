package com.wutsi.ndopify.security.server.endpoints

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.dao.AuthFactorRepository
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.OtpGenerator
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/security/AuthenticateOtpEndpoint.sql"])
@Sql(
    value = ["/db/test/security/AuthenticateOtpEndpointCleanup.sql"],
    executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD
)
class AuthenticateOtpEndpointTest : TenantAwareEndpointIntegrationTest() {
    companion object {
        const val CODE = "123456"
    }

    @MockitoBean
    private lateinit var passwordEncryptor: PasswordEncryptor

    @MockitoBean
    private lateinit var otpGenerator: OtpGenerator

    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    @Autowired
    private lateinit var authFactorRepository: AuthFactorRepository

    @BeforeEach
    override fun setUp() {
        super.setUp()

        // Only the right code matches
        doReturn(false).whenever(passwordEncryptor).matches(any(), any(), anyOrNull())
        doReturn(true).whenever(passwordEncryptor).matches(eq(CODE), any(), anyOrNull())
    }

    @Test
    fun login() {
        val now = System.currentTimeMillis()

        val response = login("ray.sponsible@gmail.com", CODE)

        assertEquals(HttpStatus.OK, response.statusCode)

        val principal = accessTokenService.decode(response.body!!.accessToken)
        assertEquals(1L, principal.getUserId())
        assertEquals("1", principal.getSubject())
        assertEquals(ApplicationCode.PARTNER_CENTRAL, principal.getApplication())
        assertEquals(TENANT_ID, principal.getTenantId())
        assertEquals(listOf("AGENT"), principal.getRoles().toList())

        val authFactor = authFactorRepository.findById(1L).get()
        assertNotNull(authFactor.lastLoggedInAt)
        assertTrue(authFactor.lastLoggedInAt.time >= now - 1000) // DATETIME has second precision

        // Consumed: the code expires as it's used
        assertTrue(authFactor.expiresAt!!.time < now)
    }

    @Test
    fun `email is case insensitive`() {
        val response = login("Ray.Sponsible@GMAIL.com", CODE)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1L, accessTokenService.decode(response.body!!.accessToken).getUserId())
    }

    @Test
    fun `wrong code`() {
        val response = loginError("ray.sponsible@gmail.com", "654321")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
        assertNull(authFactorRepository.findById(1L).get().lastLoggedInAt)
    }

    @Test
    fun `expired code`() {
        val response = loginError("expired-otp@gmail.com", CODE)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, response.body?.error?.code)
        assertNull(authFactorRepository.findById(2L).get().lastLoggedInAt)
    }

    @Test
    fun `expired code, unused since a previous login`() {
        val response = loginError("expired-after-login@gmail.com", CODE)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, response.body?.error?.code)
    }

    @Test
    fun `wrong code replayed after a login - still a wrong code`() {
        assertEquals(HttpStatus.OK, login("ray.sponsible@gmail.com", CODE).statusCode)

        val response = loginError("ray.sponsible@gmail.com", "654321")

        // Only the right code tells it was used: the wrong one must not reveal anything
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `unknown user gets the same error as a wrong code`() {
        val response = loginError("nobody@gmail.com", CODE)

        // Not "user not found": the response must not reveal which emails have an account
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `user who never requested a code`() {
        val response = loginError("no-otp@gmail.com", CODE)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `a password cannot be used as an OTP`() {
        val response = loginError("no-otp@gmail.com", "secret-password")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `used code cannot be reused`() {
        val first = login("ray.sponsible@gmail.com", CODE)
        assertEquals(HttpStatus.OK, first.statusCode)
        val lastLoggedInAt = authFactorRepository.findById(1L).get().lastLoggedInAt

        val response = loginError("ray.sponsible@gmail.com", CODE)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_CREDENTIALS_ALREADY_USED, response.body?.error?.code)
        assertEquals(lastLoggedInAt, authFactorRepository.findById(1L).get().lastLoggedInAt)
    }

    @Test
    fun `new code requested after a login can be used`() {
        doReturn(CODE).whenever(otpGenerator).generate()
        doReturn("hashed-code").whenever(passwordEncryptor).encrypt(any(), anyOrNull()) // mocked: would be null
        assertEquals(HttpStatus.OK, createOtp("flow@gmail.com").statusCode)
        assertEquals(HttpStatus.OK, login("flow@gmail.com", CODE).statusCode)

        assertEquals(HttpStatus.OK, createOtp("flow@gmail.com").statusCode)
        val response = login("flow@gmail.com", CODE)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(5L, accessTokenService.decode(response.body!!.accessToken).getUserId())
    }

    @Test
    fun `no access to the application`() {
        val response = loginError("no-access@gmail.com", CODE)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
        assertNull(authFactorRepository.findById(4L).get().lastLoggedInAt)
    }

    @Test
    fun `application that does not accept OTP`() {
        val response = loginError("ray.sponsible@gmail.com", CODE, ApplicationCode.PUBLIC_PORTAL)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_TYPE_NOT_SUPPORTED, response.body?.error?.code)
    }

    @Test
    fun `unknown application`() {
        val response = loginError("ray.sponsible@gmail.com", CODE, "invalid-app")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `no code`() {
        val response = loginError("ray.sponsible@gmail.com", null)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.AUTH_MISSING_SECRET, response.body?.error?.code)
    }

    private fun createOtp(email: String) =
        rest.postForEntity("/v1/otp", CreateOtpRequest(email = email), Void::class.java)

    private fun login(
        email: String,
        secret: String?,
        applicationCode: String = ApplicationCode.PARTNER_CENTRAL,
    ) = rest.postForEntity("/v1/auth", request(email, secret, applicationCode), AuthenticateResponse::class.java)

    private fun loginError(
        email: String,
        secret: String?,
        applicationCode: String = ApplicationCode.PARTNER_CENTRAL,
    ) = rest.postForEntity("/v1/auth", request(email, secret, applicationCode), ErrorResponse::class.java)

    private fun request(email: String, secret: String?, applicationCode: String) = AuthenticateRequest(
        email = email,
        secret = secret,
        applicationCode = applicationCode,
        authType = AuthType.OTP,
    )
}
