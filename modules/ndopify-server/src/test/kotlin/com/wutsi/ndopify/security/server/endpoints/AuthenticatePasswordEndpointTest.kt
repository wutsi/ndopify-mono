package com.wutsi.ndopify.security.server.endpoints

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.AuthFactorService
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/security/AuthenticatePasswordEndpoint.sql"])
class AuthenticatePasswordEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var passwordEncryptor: PasswordEncryptor

    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    @Autowired
    private lateinit var authFactorService: AuthFactorService

    private var now = -1L

    @BeforeEach
    override fun setUp() {
        super.setUp()

        doReturn(true).whenever(passwordEncryptor).matches(any(), any(), anyOrNull())

        now = System.currentTimeMillis()
    }

    @Test
    fun login() {
        Thread.sleep(1000L)

        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            secret = "secret",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, AuthenticateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val principal = accessTokenService.decode(response.body!!.accessToken)
        assertEquals(1L, principal.getUserId())
        assertEquals("1", principal.getSubject())
        assertEquals(request.applicationCode, principal.getApplication())
        assertEquals(TENANT_ID, principal.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT"), principal.getRoles().toList())

        val authFactor = authFactorService.findByIdOrNull(1L)
        assertTrue(authFactor!!.lastLoggedInAt!!.time >= now)
    }

    @Test
    fun `password mismatch`() {
        doReturn(false).whenever(passwordEncryptor).matches(any(), any(), anyOrNull())

        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            secret = "secret",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `no password`() {
        val request = AuthenticateRequest(
            email = "no-password@gmail.com",
            secret = "secret",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `user not found`() {
        val request = AuthenticateRequest(
            email = "invalid-user@gmail.com",
            secret = "secret",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, response.body?.error?.code)
    }

    @Test
    fun `expired password`() {
        val request = AuthenticateRequest(
            email = "expired-password@gmail.com",
            secret = "secret",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, response.body?.error?.code)
    }

    @Test
    fun `no access to app`() {
        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            secret = "secret",
            applicationCode = ApplicationCode.ADMIN_CONSOLE,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `invalid app`() {
        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            secret = "secret",
            applicationCode = "invalid-app",
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `no secret`() {
        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            secret = null,
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.PASSWORD
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        assertEquals(ErrorCode.AUTH_MISSING_SECRET, response.body?.error?.code)
    }
}
