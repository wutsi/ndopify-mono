package com.wutsi.ndopify.security.server.service.authenticator

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.AuthFactorService
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.OtpProperties
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OtpAuthenticatorTest {
    private val userApplicationService = mock<UserApplicationService>()
    private val applicationService = mock<ApplicationService>()
    private val tokenService = mock<AccessTokenService>()
    private val authFactorService = mock<AuthFactorService>()
    private val membershipService = mock<MembershipService>()
    private val userService = mock<UserService>()
    private val passwordEncryptor = PasswordEncryptor()
    private val authenticator = OtpAuthenticator(
        userApplicationService,
        applicationService,
        tokenService,
        authFactorService,
        membershipService,
        Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneOffset.UTC),
        userService,
        passwordEncryptor,
        OtpProperties(maxFailedAttempts = 5, lockDuration = Duration.ofHours(1)),
    )

    private val user = UserEntity(id = 1, email = "ray.sponsible@gmail.com")
    private val membership = MembershipEntity(id = 1, tenantId = 1, user = user)
    private val application = ApplicationEntity(
        id = 3,
        code = ApplicationCode.PARTNER_CENTRAL,
        supportedAuthTypes = listOf(AuthType.OTP),
    )
    private val request = AuthenticateRequest(
        email = user.email,
        secret = "654321",
        applicationCode = ApplicationCode.PARTNER_CENTRAL,
        authType = AuthType.OTP,
    )

    @BeforeTest
    fun setUp() {
        doReturn(1L).whenever(membershipService).currentTenantId()
        doReturn(user).whenever(userService).findByEmailOrNull(user.email)
        doReturn(membership).whenever(membershipService).findByUserOrNull(user)
        doReturn(application).whenever(applicationService).findByCodeOrNull(ApplicationCode.PARTNER_CENTRAL)
        doReturn(UserApplicationEntity(membership = membership, application = application))
            .whenever(userApplicationService)
            .findByMembershipAndApplicationOrNull(membership, application)
    }

    @Test
    fun `a wrong code counts on the row read under the write lock`() {
        val factor = AuthFactorEntity(
            id = 1,
            authType = AuthType.OTP,
            data = passwordEncryptor.encrypt("123456", "salt"),
            salt = "salt",
            failedAttempts = 1,
        )
        doReturn(factor).whenever(authFactorService).findByUserAndAuthTypeForUpdateOrNull(user, AuthType.OTP)
        doReturn(factor).whenever(authFactorService).findByUserAndAuthTypeOrNull(user, AuthType.OTP)
        doReturn(factor.copy(failedAttempts = 2)).whenever(authFactorService)
            .recordFailedAttempt(factor, 5, Duration.ofHours(1))

        val ex = assertFailsWith<ConflictException> { authenticator.authenticate(request) }

        assertEquals(ErrorCode.AUTH_INVALID_CREDENTIALS, ex.error.code)
        assertEquals(3, ex.error.data?.get("remainingAttempts"))
        // The count runs on the locked row, not on a separate unlocked read: parallel codes then each count
        verify(authFactorService).findByUserAndAuthTypeForUpdateOrNull(user, AuthType.OTP)
        verify(authFactorService).recordFailedAttempt(factor, 5, Duration.ofHours(1))
    }
}
