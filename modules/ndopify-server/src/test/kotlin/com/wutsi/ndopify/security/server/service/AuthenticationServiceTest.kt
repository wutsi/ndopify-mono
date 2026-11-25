package com.wutsi.ndopify.security.server.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.domain.RoleEntity
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.junit.jupiter.api.BeforeEach
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuthenticationServiceTest {
    private val provider = mock<AuthenticatorProvider>()
    private val tokenService = mock<AccessTokenService>()
    private val userService = mock<UserService>()
    private val membershipService = mock<MembershipService>()
    private val applicationService = mock<ApplicationService>()
    private val userApplicationService = mock<UserApplicationService>()
    private val properties = AccessTokenProperties(ttl = mapOf(ApplicationCode.PARTNER_CENTRAL to Duration.ofDays(30)))
    private val clock = Clock.fixed(Instant.now(), ZoneOffset.UTC)
    private val now = clock.instant()
    private val service = AuthenticationService(
        provider,
        tokenService,
        userService,
        membershipService,
        applicationService,
        userApplicationService,
        properties,
        clock,
    )

    private val user = UserEntity(id = 11, email = "ray.sponsible@gmail.com")
    private val membership = MembershipEntity(id = 11, tenantId = 1, user = user, party = PartyEntity(id = 100))
    private val application = ApplicationEntity(id = 3, code = ApplicationCode.PARTNER_CENTRAL)
    private val access = UserApplicationEntity(
        id = 11,
        membership = membership,
        application = application,
        roles = listOf(RoleEntity(id = 1, code = "admin")),
    )

    @BeforeEach
    fun setUp() {
        doReturn(user).whenever(userService).findByIdOrNull(11L)
        doReturn(membership).whenever(membershipService).findByUserOrNull(user)
        doReturn(application).whenever(applicationService).findByCodeOrNull(any())
        doReturn(access).whenever(userApplicationService).findByMembershipAndApplicationOrNull(membership, application)
        doReturn("new-token").whenever(tokenService).create(any(), any(), any(), any(), any(), any(), any(), any())
        doReturn(token(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(2))))
            .whenever(tokenService).getAccessToken()
    }

    @Test
    fun `refresh gives the same claims, the current party and a new life`() {
        // issued 20 days ago, 30-day lifetime, session started 40 days ago: 30 more days, not the 10 left
        doReturn(token(now.minus(Duration.ofDays(20)), now.minus(Duration.ofDays(40)))).whenever(tokenService).getAccessToken()

        assertEquals("new-token", service.refresh())

        verify(tokenService).create(
            application = ApplicationCode.PARTNER_CENTRAL,
            userId = 11L,
            roles = arrayOf("admin"),
            tenantId = 1L,
            ttlSeconds = Duration.ofDays(30).seconds.toInt(),
            partyId = 100L,
            issuedAt = now,
            sessionStartedAt = now.minus(Duration.ofDays(40)),
        )
    }

    @Test
    fun `refresh near the cap gives only what the cap leaves`() {
        // issued 1 day ago, 30-day lifetime, session started 85 days ago: 5 days to the cap, not 30
        doReturn(token(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(85)))).whenever(tokenService).getAccessToken()

        service.refresh()

        val captor = argumentCaptor<Int>()
        verify(tokenService).create(any(), any(), any(), any(), captor.capture(), any(), any(), any())
        assertEquals(Duration.ofDays(5).seconds.toInt(), captor.firstValue)
    }

    @Test
    fun `refresh of a match link keeps its original expiry`() {
        // a 365-day public-portal token, 10 days old, session started with it
        doReturn(
            token(
                issuedAt = now.minus(Duration.ofDays(10)),
                sessionStartedAt = now.minus(Duration.ofDays(10)),
                lifetime = Duration.ofDays(365),
                application = ApplicationCode.PUBLIC_PORTAL,
            ),
        ).whenever(tokenService).getAccessToken()

        service.refresh()

        val captor = argumentCaptor<Int>()
        verify(tokenService).create(any(), any(), any(), any(), captor.capture(), any(), any(), any())
        assertEquals(Duration.ofDays(355).seconds.toInt(), captor.firstValue) // now + 355 days == its original exp
    }

    @Test
    fun `refresh past the cap`() {
        doReturn(token(now.minus(Duration.ofDays(1)), now.minus(Duration.ofDays(90))))
            .whenever(tokenService).getAccessToken()

        val ex = assertFailsWith<ConflictException> { service.refresh() }

        assertEquals(ErrorCode.AUTH_CREDENTIALS_EXPIRED, ex.error.code)
        verify(tokenService, never()).create(any(), any(), any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `refresh for a user who is no longer a member`() {
        doReturn(null).whenever(userService).findByIdOrNull(11L)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, assertFailsWith<ConflictException> { service.refresh() }.error.code)
    }

    @Test
    fun `refresh for a membership that is gone`() {
        doReturn(null).whenever(membershipService).findByUserOrNull(user)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, assertFailsWith<ConflictException> { service.refresh() }.error.code)
    }

    @Test
    fun `refresh when the application access was removed`() {
        doReturn(null).whenever(userApplicationService).findByMembershipAndApplicationOrNull(membership, application)

        assertEquals(ErrorCode.AUTH_ACCESS_DENIED, assertFailsWith<ConflictException> { service.refresh() }.error.code)
    }

    private fun token(
        issuedAt: Instant,
        sessionStartedAt: Instant,
        lifetime: Duration = Duration.ofDays(30),
        application: String = ApplicationCode.PARTNER_CENTRAL,
    ): AccessToken = AccessToken(
        principal = JwtPrincipal(
            JWT.decode(
                JWT.create()
                    .withIssuer(AccessTokenService.ISSUER)
                    .withIssuedAt(issuedAt)
                    .withExpiresAt(issuedAt.plus(lifetime))
                    .withClaim(JwtPrincipal.CLAIM_USER_ID, 11L)
                    .withClaim(JwtPrincipal.CLAIM_TENANT_ID, 1L)
                    .withClaim(JwtPrincipal.CLAIM_APPLICATION, application)
                    .sign(Algorithm.HMAC256("test-secret-not-for-prod-0123456789abcdef")),
            ),
        ),
        issuedAt = issuedAt,
        expiresAt = issuedAt.plus(lifetime),
        sessionStartedAt = sessionStartedAt,
    )
}
