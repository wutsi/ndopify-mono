package com.wutsi.ndopify.security.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.AlgorithmMismatchException
import com.auth0.jwt.exceptions.SignatureVerificationException
import com.auth0.jwt.exceptions.TokenExpiredException
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.UnauthorizedException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.dto.JwtPrincipal
import jakarta.servlet.http.HttpServletRequest
import org.mockito.Mockito.mock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AccessTokenServiceTest {
    private val clock = mock<Clock>()
    private val request = mock<HttpServletRequest>()
    private val service = AccessTokenService(clock, request, SECRET, PROPERTIES)

    companion object {
        private const val SECRET = "test-secret-not-for-prod-0123456789abcdef"
        private val PROPERTIES = AccessTokenProperties(
            ttl = mapOf(ApplicationCode.PARTNER_CENTRAL to Duration.ofDays(1)),
            defaultTtl = Duration.ofHours(2),
        )
    }

    @Test
    fun create() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 3600,
            partyId = 333L,
        )

        val principal = service.decode(token)
        assertEquals(333L, principal.getPartyId())
        assertEquals(ApplicationCode.ADMIN_CONSOLE, principal.getApplication())
        assertEquals("111", principal.getSubject())
        assertEquals(111L, principal.getUserId())
        assertEquals(222L, principal.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT_MANAGER"), principal.getRoles().toList())
    }

    @Test
    fun `token with no tenant`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = null,
            ttlSeconds = 3600
        )

        val principal = service.decode(token)
        assertNull(principal.getTenantId())
        assertNull(principal.getPartyId())
    }

    @Test
    fun `ttl of the application`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(ApplicationCode.PARTNER_CENTRAL, userId = 111L, roles = emptyArray(), tenantId = 1L)

        assertEquals(Duration.ofDays(1), lifetime(token))
    }

    @Test
    fun `default ttl for an application not configured`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(ApplicationCode.E_SIGNATURE, userId = 111L, roles = emptyArray(), tenantId = 1L)

        assertEquals(Duration.ofHours(2), lifetime(token))
    }

    @Test
    fun `a ttl over Int seconds`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.PUBLIC_PORTAL,
            userId = 111L,
            roles = emptyArray(),
            tenantId = 1L,
            ttlSeconds = Duration.ofDays(365).seconds.toInt(),
        )

        assertEquals(Duration.ofDays(365), lifetime(token))
    }

    @Test
    fun `expired token`() {
        val now = 1000L
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 1
        )

        assertFailsWith<TokenExpiredException> { service.decode(token) }
    }

    @Test
    fun `unsigned token`() {
        val token = forgedToken().sign(Algorithm.none())

        assertFailsWith<AlgorithmMismatchException> { service.decode(token) }
    }

    @Test
    fun `token signed with another secret`() {
        val token = forgedToken().sign(Algorithm.HMAC256("another-secret-not-for-prod-0123456789abc"))

        assertFailsWith<SignatureVerificationException> { service.decode(token) }
    }

    @Test
    fun `secret too short`() {
        assertFailsWith<IllegalArgumentException> { AccessTokenService(clock, request, "short-secret", PROPERTIES) }
    }

    @Test
    fun `create with an issue date and a session start`() {
        val issuedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
        val sessionStartedAt = issuedAt.minus(Duration.ofDays(6))

        val token = service.create(
            application = ApplicationCode.PARTNER_CENTRAL,
            userId = 111L,
            roles = emptyArray(),
            tenantId = 1L,
            ttlSeconds = Duration.ofDays(30).seconds.toInt(),
            issuedAt = issuedAt,
            sessionStartedAt = sessionStartedAt,
        )

        val jwt = JWT.decode(token)
        assertEquals(issuedAt, jwt.issuedAtAsInstant)
        assertEquals(issuedAt.plus(Duration.ofDays(30)), jwt.expiresAtAsInstant)
        assertEquals(sessionStartedAt.toEpochMilli(), jwt.getClaim(AccessTokenService.CLAIM_SESSION_STARTED_AT).asLong())
    }

    @Test
    fun `getAccessToken - valid token`() {
        val issuedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
        val sessionStartedAt = issuedAt.minus(Duration.ofDays(6))
        val token = service.create(
            application = ApplicationCode.PARTNER_CENTRAL,
            userId = 111L,
            roles = arrayOf("ROLE_USER"),
            tenantId = 222L,
            partyId = 333L,
            ttlSeconds = Duration.ofDays(30).seconds.toInt(),
            issuedAt = issuedAt,
            sessionStartedAt = sessionStartedAt,
        )
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        val accessToken = service.getAccessToken()

        assertEquals(111L, accessToken.principal.getUserId())
        assertEquals(222L, accessToken.principal.getTenantId())
        assertEquals(333L, accessToken.principal.getPartyId())
        assertEquals(issuedAt, accessToken.issuedAt)
        assertEquals(issuedAt.plus(Duration.ofDays(30)), accessToken.expiresAt)
        assertEquals(sessionStartedAt, accessToken.sessionStartedAt)
    }

    @Test
    fun `getAccessToken - a token without sessionStartedAt reads it from iat`() {
        val issuedAt = Instant.now().minus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS)
        val token = JWT.create()
            .withIssuer(AccessTokenService.ISSUER)
            .withIssuedAt(issuedAt)
            .withExpiresAt(issuedAt.plus(Duration.ofDays(30)))
            .withClaim(JwtPrincipal.CLAIM_USER_ID, 111L)
            .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 222L)
            .sign(Algorithm.HMAC256(SECRET))
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        assertEquals(issuedAt, service.getAccessToken().sessionStartedAt)
    }

    @Test
    fun `getAccessToken - no authorization header`() {
        doReturn(null).whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<UnauthorizedException> { service.getAccessToken() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getAccessToken - invalid token`() {
        doReturn("Bearer invalid-token").whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<ForbiddenException> { service.getAccessToken() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getPrincipalOrNull - no authorization header`() {
        doReturn(null).whenever(request).getHeader("Authorization")

        assertNull(service.getPrincipalOrNull())
    }

    @Test
    fun `getPrincipalOrNull - valid token`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 3600
        )
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        val principal = service.getPrincipalOrNull()

        assertEquals(ApplicationCode.ADMIN_CONSOLE, principal?.getApplication())
        assertEquals(111L, principal?.getUserId())
        assertEquals(222L, principal?.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT_MANAGER"), principal?.getRoles()?.toList())
    }

    @Test
    fun `getPrincipalOrNull - invalid token`() {
        doReturn("Bearer invalid-token").whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<ForbiddenException> { service.getPrincipalOrNull() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getPrincipal - no authorization header`() {
        doReturn(null).whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<UnauthorizedException> { service.getPrincipal() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    @Test
    fun `getPrincipal - valid token`() {
        val now = System.currentTimeMillis()
        doReturn(now).whenever(clock).millis()

        val token = service.create(
            application = ApplicationCode.ADMIN_CONSOLE,
            userId = 111L,
            roles = arrayOf("ADMIN", "SUPPORT_MANAGER"),
            tenantId = 222L,
            ttlSeconds = 3600
        )
        doReturn("Bearer $token").whenever(request).getHeader("Authorization")

        val principal = service.getPrincipal()

        assertEquals(ApplicationCode.ADMIN_CONSOLE, principal.getApplication())
        assertEquals(111L, principal.getUserId())
        assertEquals(222L, principal.getTenantId())
        assertEquals(listOf("ADMIN", "SUPPORT_MANAGER"), principal.getRoles().toList())
    }

    @Test
    fun `getPrincipal - invalid token`() {
        doReturn("Bearer invalid-token").whenever(request).getHeader("Authorization")

        val ex = assertFailsWith<ForbiddenException> { service.getPrincipal() }

        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, ex.error.code)
    }

    private fun forgedToken() = JWT.create()
        .withIssuer(AccessTokenService.ISSUER)
        .withClaim(JwtPrincipal.CLAIM_USER_ID, 111L)
        .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 222L)
        .withArrayClaim(JwtPrincipal.CLAIM_ROLE, arrayOf("admin"))

    private fun lifetime(token: String): Duration {
        val jwt = JWT.decode(token)
        return Duration.between(jwt.issuedAtAsInstant, jwt.expiresAtAsInstant)
    }
}
