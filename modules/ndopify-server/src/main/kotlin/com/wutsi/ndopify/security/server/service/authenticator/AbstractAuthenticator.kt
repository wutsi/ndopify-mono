package com.wutsi.ndopify.security.server.service.authenticator

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
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
import com.wutsi.ndopify.security.server.service.Authenticator
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserApplicationService
import java.time.Clock

abstract class AbstractAuthenticator(
    protected val userApplicationService: UserApplicationService,
    protected val applicationService: ApplicationService,
    protected val tokenService: AccessTokenService,
    protected val authFactorService: AuthFactorService,
    protected val membershipService: MembershipService,
    protected val clock: Clock,
) : Authenticator {
    override fun authenticate(request: AuthenticateRequest): String {
        validateRequest(request)
        membershipService.currentTenantId()

        val user = findUser(request.email)
        val membership = findMembership(user)

        val application = findApplication(request.applicationCode)
        val userApplication = checkApplicationAccess(membership, application)

        // Check credentials
        val authFactor = checkCredentials(request, user)
        if (authFactor.hasExpired(clock)) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_CREDENTIALS_EXPIRED
                )
            )
        }

        onAuthenticated(authFactor)

        // Token
        return tokenService.create(
            application = request.applicationCode,
            userId = user.id ?: -1,
            roles = userApplication.roles.map { role -> role.code }.toTypedArray(),
            tenantId = membership.tenantId,
            ttlSeconds = 60 * 60, // 1 hour
            partyId = membership.party?.id,
        )
    }

    /** Called once the credentials are checked, before the token is issued. */
    protected open fun onAuthenticated(authFactor: AuthFactorEntity) {
        authFactorService.saveLastLoggedIn(authFactor)
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
        membership: MembershipEntity,
        application: ApplicationEntity
    ): UserApplicationEntity {
        val userApplication = userApplicationService.findByMembershipAndApplicationOrNull(membership, application)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_ACCESS_DENIED
                )
            )
        return userApplication
    }

    /** The user must be a member of the current tenant: memberships are @TenantId-filtered. */
    private fun findMembership(user: UserEntity): MembershipEntity {
        return membershipService.findByUserOrNull(user)
            ?: throw ConflictException(
                error = Error(
                    code = ErrorCode.AUTH_ACCESS_DENIED,
                    message = "User ${user.email} is not a member of this tenant"
                )
            )
    }
}
