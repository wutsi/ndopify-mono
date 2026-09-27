package com.wutsi.ndopify.security.server.service.authenticator

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.server.domain.ApplicationEntity
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserApplicationEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.AuthFactorService
import com.wutsi.ndopify.security.server.service.PasswordEncryptor
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class PasswordAuthenticator(
    userApplicationService: UserApplicationService,
    applicationService: ApplicationService,
    tokenService: AccessTokenService,
    authFactorService: AuthFactorService,
    clock: Clock,

    private val userService: UserService,
    private val passwordEncryptor: PasswordEncryptor,
) : AbstractAuthenticator(userApplicationService, applicationService, tokenService, authFactorService, clock) {
    override fun getAuthType(): AuthType {
        return AuthType.PASSWORD
    }

    override fun validateRequest(request: AuthenticateRequest) {
        if (request.secret == null) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.AUTH_MISSING_SECRET
                )
            )
        }
    }

    override fun findUser(email: String): UserEntity {
        return userService.findByEmailOrNull(email)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_INVALID_CREDENTIALS
                )
            )
    }

    override fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity {
        val authFactor = authFactorService.findByUserAndAuthTypeOrNull(user, request.authType)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_INVALID_CREDENTIALS
                )
            )

        if (!passwordEncryptor.matches(request.secret!!, authFactor.data, authFactor.salt)) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_INVALID_CREDENTIALS
                )
            )
        }

        return authFactor
    }

    override fun checkApplicationAccess(user: UserEntity, application: ApplicationEntity): UserApplicationEntity {
        val userApplication = userApplicationService.findByUserAndApplicationOrNull(user, application)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_ACCESS_DENIED
                )
            )
        return userApplication
    }
}
