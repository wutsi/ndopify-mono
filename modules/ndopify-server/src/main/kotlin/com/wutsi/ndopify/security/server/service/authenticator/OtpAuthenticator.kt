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

    override fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity {
        val authFactor = super.checkCredentials(request, user)
        ensureNotUsed(authFactor)
        return authFactor
    }

    private fun ensureNotUsed(authFactor: AuthFactorEntity) {
        if (authFactor.lastLoggedInAt != null) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_CREDENTIALS_ALREADY_USED
                )
            )
        }
    }
}
