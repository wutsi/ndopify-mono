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
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class OtpAuthenticator(
    userApplicationService: UserApplicationService,
    applicationService: ApplicationService,
    tokenService: AccessTokenService,
    authFactorService: AuthFactorService,
    clock: Clock,
    userService: UserService,
    passwordEncryptor: PasswordEncryptor,
) : AbstractPasswordAuthenticator(
    userApplicationService,
    applicationService,
    tokenService,
    authFactorService,
    clock,
    userService,
    passwordEncryptor
) {
    override fun getAuthType(): AuthType {
        return AuthType.OTP
    }

    /**
     * Rejects a code already used to log in. Checked here, once the code is known to match, and before the expiry
     * check: a consumed code is also expired, but that's not what the user must be told.
     */
    override fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity {
        val authFactor = super.checkCredentials(request, user)
        if (authFactor.isConsumed()) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_CREDENTIALS_ALREADY_USED
                )
            )
        }
        return authFactor
    }

    /** Single use: the code expires as it's used. */
    override fun onAuthenticated(authFactor: AuthFactorEntity) {
        authFactorService.consume(authFactor)
    }
}
