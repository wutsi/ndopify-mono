package com.wutsi.ndopify.security.server.service.authenticator

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.BadRequestException
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
import com.wutsi.ndopify.security.server.service.UserApplicationService
import com.wutsi.ndopify.security.server.service.UserService
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class GoogleOneTapAuthenticator(
    userApplicationService: UserApplicationService,
    applicationService: ApplicationService,
    tokenService: AccessTokenService,
    authFactorService: AuthFactorService,
    membershipService: MembershipService,
    clock: Clock,

    private val userService: UserService,
) : AbstractAuthenticator(
    userApplicationService,
    applicationService,
    tokenService,
    authFactorService,
    membershipService,
    clock
) {
    override fun getAuthType(): AuthType {
        return AuthType.GOOGLE_ONE_TAP
    }

    override fun validateRequest(request: AuthenticateRequest) {
        if (request.googleOneTapPayload == null) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.AUTH_MISSING_PAYLOAD
                )
            )
        }
    }

    /**
     * Creates the global user on first sign-in, but never a membership: signing in with Google doesn't join a
     * tenant, so a new user is denied (and the transaction rolled back) until it signs up.
     */
    override fun findUser(email: String): UserEntity {
        return userService.findByEmailOrCreate(email)
    }

    override fun checkCredentials(request: AuthenticateRequest, user: UserEntity): AuthFactorEntity {
        return authFactorService.findByUserAndAuthTypeOrNullOrCreate(
            user,
            AuthType.GOOGLE_ONE_TAP,
            request.googleOneTapPayload!!.sub
        )
    }

    override fun checkApplicationAccess(
        membership: MembershipEntity,
        application: ApplicationEntity
    ): UserApplicationEntity {
        return userApplicationService.findByMembershipAndApplicationOrCreate(membership, application)
    }
}
