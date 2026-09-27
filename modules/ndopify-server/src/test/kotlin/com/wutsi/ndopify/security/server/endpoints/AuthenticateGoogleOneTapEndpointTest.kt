package com.wutsi.ndopify.security.server.endpoints

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.dto.GoogleOneTapPayload
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.AuthFactorService
import com.wutsi.ndopify.security.server.service.UserService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/security/AuthenticateGoogleOneTapEndpoint.sql"])
class AuthenticateGoogleOneTapEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var accessTokenService: AccessTokenService

    @Autowired
    private lateinit var authFactorService: AuthFactorService

    @Autowired
    protected lateinit var userService: UserService

    private var now = -1L

    @BeforeEach
    override fun setUp() {
        super.setUp()

        now = System.currentTimeMillis()
    }

    @Test
    fun login() {
        Thread.sleep(1000L)

        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = GoogleOneTapPayload(
                email = "ray.sponsible@gmail.com",
                sub = "sub-1234567890",
            )
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
        assertEquals(request.googleOneTapPayload?.sub, authFactor.data)
    }

    @Test
    fun `no password`() {
        val request = AuthenticateRequest(
            email = "no-password@gmail.com",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = GoogleOneTapPayload(
                email = "no-password@gmail.com",
                givenName = "Ray",
                familyName = "Sponsible",
                picture = "https://lh3.googleusercontent.com/a-/AOh14Gg0k1e2f3g4h5i6j7k8l9m0n1o2p3q4r5s6t7u8v9w0x1y2z3",
                sub = "sub-1234567890",
            )
        )
        val response = rest.postForEntity("/v1/auth", request, AuthenticateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val principal = accessTokenService.decode(response.body!!.accessToken)
        val user = userService.findByEmailOrNull(request.email)
        assertEquals(user?.id, principal.getUserId())
        assertEquals(user?.id.toString(), principal.getSubject())
        assertEquals(request.applicationCode, principal.getApplication())
        assertEquals(TENANT_ID, principal.getTenantId())
        assertEquals(listOf(), principal.getRoles().toList())

        val authFactor = authFactorService.findByUserAndAuthTypeOrNull(user!!, AuthType.GOOGLE_ONE_TAP)
        assertEquals(request.googleOneTapPayload?.sub, authFactor?.data)
    }

    @Test
    fun `user not found`() {
        val request = AuthenticateRequest(
            email = "user-not-found@gmail.com",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = GoogleOneTapPayload(
                email = "user-not-found@gmail.com",
                sub = "sub-5555555",
            )
        )
        val response = rest.postForEntity("/v1/auth", request, AuthenticateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val principal = accessTokenService.decode(response.body!!.accessToken)
        val user = userService.findByEmailOrNull(request.email)
        assertEquals(user?.id, principal.getUserId())
        assertEquals(user?.id.toString(), principal.getSubject())
        assertEquals(request.applicationCode, principal.getApplication())
        assertEquals(TENANT_ID, principal.getTenantId())
        assertEquals(listOf(), principal.getRoles().toList())

        val authFactor = authFactorService.findByUserAndAuthTypeOrNull(user!!, AuthType.GOOGLE_ONE_TAP)
        assertEquals(request.googleOneTapPayload?.sub, authFactor?.data)
    }

    @Test
    fun `no application access`() {
        val request = AuthenticateRequest(
            email = "no-app-access@gmail.com",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = GoogleOneTapPayload(
                email = "no-app-access@gmail.com",
                sub = "sub-5555555",
            )
        )
        val response = rest.postForEntity("/v1/auth", request, AuthenticateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val principal = accessTokenService.decode(response.body!!.accessToken)
        val user = userService.findByEmailOrNull(request.email)
        assertEquals(user?.id, principal.getUserId())
        assertEquals(user?.id.toString(), principal.getSubject())
        assertEquals(request.applicationCode, principal.getApplication())
        assertEquals(TENANT_ID, principal.getTenantId())
        assertEquals(listOf(), principal.getRoles().toList())

        val authFactor = authFactorService.findByUserAndAuthTypeOrNull(user!!, AuthType.GOOGLE_ONE_TAP)
        assertEquals(request.googleOneTapPayload?.sub, authFactor?.data)
    }

    @Test
    fun `auth-type not supported`() {
        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            applicationCode = ApplicationCode.ADMIN_CONSOLE,
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = GoogleOneTapPayload(
                email = "user-not-found@gmail.com",
                sub = "sub-5555555",
            )
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_TYPE_NOT_SUPPORTED, response.body?.error?.code)
    }

    @Test
    fun `invalid app`() {
        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            applicationCode = "invalid-app",
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = GoogleOneTapPayload(
                email = "user-not-found@gmail.com",
                sub = "sub-5555555",
            )
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `no payload`() {
        val request = AuthenticateRequest(
            email = "ray.sponsible@gmail.com",
            applicationCode = ApplicationCode.PUBLIC_PORTAL,
            authType = AuthType.GOOGLE_ONE_TAP,
            googleOneTapPayload = null
        )
        val response = rest.postForEntity("/v1/auth", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        assertEquals(ErrorCode.AUTH_MISSING_PAYLOAD, response.body?.error?.code)
    }
}
