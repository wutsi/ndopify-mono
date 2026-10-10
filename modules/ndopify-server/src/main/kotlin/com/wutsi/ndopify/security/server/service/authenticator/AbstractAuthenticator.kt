package com.wutsi.ndopify.security.server.service.authenticator

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
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
import com.wutsi.ndopify.security.server.service.Authenticator
import com.wutsi.ndopify.security.server.service.UserApplicationService
import java.time.Clock

abstract class AbstractAuthenticator(
    protected val userApplicationService: UserApplicationService,
    protected val applicationService: ApplicationService,
    protected val tokenService: AccessTokenService,
    protected val authFactorService: AuthFactorService,
    protected val clock: Clock,
) : Authenticator {
    override fun authenticate(request: AuthenticateRequest, tenantId: Long?): String {
        validateRequest(request)

        val user = findUser(request.email)
        val application = findApplication(request.applicationCode)
        val userApplication = checkApplicationAccess(user, application)

        // Check credentials
        val authFactor = checkCredentials(request, user)
        if (authFactor.hasExpired(clock)) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_CREDENTIALS_EXPIRED
                )
            )
        }

        // Timestamps
        authFactorService.saveLastLoggedIn(authFactor)

        // Token
        return tokenService.create(
            application = request.applicationCode,
            userId = user.id ?: -1,
            roles = userApplication.roles.map { role -> role.code }.toTypedArray(),
            tenantId = tenantId,
            ttlSeconds = null
        )
    }

    protected abstract fun getAuthType(): AuthType

    protected abstract fun validateRequest(request: AuthenticateRequest)

    protected abstract fun findUser(email: String): UserEntity

    private fun findApplication(applicationCode: String): ApplicationEntity {
        val application = applicationService.findByCodeOrNull(applicationCode)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_ACCESS_DENIED
                )
            )

        if (!application.supportedAuthTypes.contains(getAuthType())) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_TYPE_NOT_SUPPORTED,
                    message = "Application ${application.code} does not support ${getAuthType()}"
                )
            )
        }
        return application
    }

    protected abstract fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity

    protected open fun checkApplicationAccess(
        user: UserEntity,
        application: ApplicationEntity
    ): UserApplicationEntity {
        val userApplication = userApplicationService.findByUserAndApplicationOrNull(user, application)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_ACCESS_DENIED
                )
            )
        return userApplication
    }
}
