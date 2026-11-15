package com.wutsi.ndopify.security.server.service.authenticator

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.AuthFactorService
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.OtpProperties
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Service
import java.time.Clock

@Service
@EnableConfigurationProperties(OtpProperties::class)
class OtpAuthenticator(
    userApplicationService: UserApplicationService,
    applicationService: ApplicationService,
    tokenService: AccessTokenService,
    authFactorService: AuthFactorService,
    membershipService: MembershipService,
    clock: Clock,
    userService: UserService,
    passwordEncryptor: PasswordEncryptor,
    private val properties: OtpProperties,
) : AbstractPasswordAuthenticator(
    userApplicationService,
    applicationService,
    tokenService,
    authFactorService,
    membershipService,
    clock,
    userService,
    passwordEncryptor
) {
    override fun getAuthType(): AuthType {
        return AuthType.OTP
    }

    /**
     * Rejects every code while the account is locked (a right one included: it isn't consumed). Rejects a code already
     * used to log in, checked here once the code is known to match and before the expiry check: a consumed code is also
     * expired, but that's not what the user must be told. The row is read under a write lock, so that codes sent in
     * parallel each count (`AuthFactorService.recordFailedAttempt`).
     */
    override fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity {
        val authFactor = authFactorService.findByUserAndAuthTypeForUpdateOrNull(user, AuthType.OTP)
            ?: invalidCredentials(null)
        if (authFactor.isLocked(clock)) {
            throw ConflictException(error = lockedError(authFactor))
        }

        val credential = super.checkCredentials(request, user)
        if (credential.isConsumed()) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_CREDENTIALS_ALREADY_USED
                )
            )
        }
        return credential
    }

    /**
     * A wrong code is counted even though the call fails: the last one allowed locks the account, and a lock that
     * expired restarts the count. The count is written in the caller's transaction, which must not roll it back with
     * the 409 (`AuthenticationService.authenticate`).
     */
    override fun invalidCredentials(authFactor: AuthFactorEntity?): Nothing {
        if (authFactor == null || authFactor.authType != AuthType.OTP) {
            super.invalidCredentials(authFactor)
        }

        val saved = authFactorService.recordFailedAttempt(
            authFactor,
            properties.maxFailedAttempts,
            properties.lockDuration,
        )
        if (saved.isLocked(clock)) {
            throw ConflictException(error = lockedError(saved))
        }
        throw ConflictException(
            error = Error(
                code = ErrorCode.AUTH_INVALID_CREDENTIALS,
                data = mapOf("remainingAttempts" to properties.maxFailedAttempts - saved.failedAttempts),
            )
        )
    }

    /** Single use: the code expires as it's used. */
    override fun onAuthenticated(authFactor: AuthFactorEntity) {
        authFactorService.consume(authFactor)
    }

    private fun lockedError(authFactor: AuthFactorEntity) = Error(
        code = ErrorCode.AUTH_ACCOUNT_LOCKED,
        data = mapOf("lockedUntil" to authFactor.lockedUntil!!.toInstant().toString()),
    )
}
