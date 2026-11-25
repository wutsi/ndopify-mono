package com.wutsi.ndopify.security.server.endpoints

import com.auth0.jwt.JWT
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import com.wutsi.ndopify.security.server.service.AccessTokenService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Sql(value = ["/db/test/clean.sql", "/db/test/security/RefreshAuthenticationEndpoint.sql"])
class RefreshAuthenticationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application: String = ApplicationCode.PARTNER_CENTRAL

    @Autowired
    private lateinit var tokenService: AccessTokenService

    private var issuedAt: Instant = Instant.now()
    private var sessionStartedAt: Instant = issuedAt
    private var ttlSeconds: Int = 3600

    override fun createAccessToken(application: String): String = tokenService.create(
        application = application,
        userId = USER_ID,
        roles = roles,
        tenantId = TENANT_ID,
        ttlSeconds = ttlSeconds,
        issuedAt = issuedAt,
        sessionStartedAt = sessionStartedAt,
    )

    @Test
    fun refresh() {
        val startedAt = Instant.now()

        val response = rest.postForEntity("/v1/auth/refresh", null, AuthenticateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val token = tokenService.decode(response.body!!.accessToken)
        assertEquals(11L, token.getUserId())
        assertEquals(1L, token.getTenantId())
        assertEquals(100L, token.getPartyId()) // re-read from the membership; the request's token carries none
        assertEquals(ApplicationCode.PARTNER_CENTRAL, token.getApplication())

        val jwt = JWT.decode(response.body!!.accessToken)
        assertEquals(ttlSeconds.toLong(), Duration.between(jwt.issuedAtAsInstant, jwt.expiresAtAsInstant).seconds)
        assertFalse(jwt.expiresAtAsInstant.isBefore(startedAt.plusSeconds(ttlSeconds - 60L)))
        assertEquals(sessionStartedAt.toEpochMilli(), jwt.getClaim(AccessTokenService.CLAIM_SESSION_STARTED_AT).asLong())
    }

    @Test
    fun `past the absolute cap`() {
        issuedAt = Instant.now().minus(Duration.ofDays(10))
        sessionStartedAt = Instant.now().minus(Duration.ofDays(91))
        ttlSeconds = Duration.ofDays(30).seconds.toInt()

        val response = rest.postForEntity("/v1/auth/refresh", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, response.body?.error?.code)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/security/RefreshAuthenticationEndpoint.sql"],
        statements = ["UPDATE T_MEMBERSHIP SET deleted = true WHERE id = 11"],
    )
    fun `membership removed`() {
        assertDenied()
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/security/RefreshAuthenticationEndpoint.sql"],
        statements = ["DELETE FROM T_USER_APPLICATION WHERE membership_id = 11"],
    )
    fun `application access removed`() {
        assertDenied()
    }

    @Test
    fun `no token`() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/auth/refresh", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    private fun assertDenied() {
        val response = rest.postForEntity("/v1/auth/refresh", null, ErrorResponse::class.java)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, response.body?.error?.code)
    }
}
