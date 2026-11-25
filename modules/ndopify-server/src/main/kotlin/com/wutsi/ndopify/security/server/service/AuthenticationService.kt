package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.refdata.server.service.ApplicationService
import com.wutsi.ndopify.security.dto.AuthenticateRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration

@Service
class AuthenticationService(
    private val provider: AuthenticatorProvider,
    private val tokenService: AccessTokenService,
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val applicationService: ApplicationService,
    private val userApplicationService: UserApplicationService,
    private val properties: AccessTokenProperties,
    private val clock: Clock,
) {
    /**
     * `noRollbackFor` keeps the wrong-code count: an authenticator writes it before answering 409, and a rollback
     * with that 409 would make every attempt the first one (no account ever locks).
     */
    @Transactional(noRollbackFor = [ConflictException::class])
    fun authenticate(request: AuthenticateRequest): String {
        val authenticator = provider.get(request.authType)
            ?: throw BadRequestException(
                Error(code = ErrorCode.AUTH_TYPE_NOT_SUPPORTED, parameter = Parameter(value = request.authType)),
            )
        return authenticator.authenticate(request)
    }

    /**
     * Re-issues the caller's current token for the same application, user, tenant and party, with a fresh life bounded
     * by `max-lifetime`: 409 `AUTH_ACCESS_DENIED` when the user, its membership or the application access is gone,
     * 409 `AUTH_CREDENTIALS_EXPIRED` past the cap. Nothing is written.
     */
    fun refresh(): String {
        val token = tokenService.getAccessToken()
        val principal = token.principal
        val user = userService.findByIdOrNull(principal.getUserId())
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
        val membership = membershipService.findByUserOrNull(user)
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
        val application = applicationService.findByCodeOrNull(principal.getApplication())
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))
        val userApplication = userApplicationService.findByMembershipAndApplicationOrNull(membership, application)
            ?: throw ConflictException(Error(code = ErrorCode.AUTH_ACCESS_DENIED))

        val now = clock.instant()
        val ttl = properties.renewal(
            lifetime = Duration.between(token.issuedAt, token.expiresAt),
            now = now,
            expiresAt = token.expiresAt,
            sessionStartedAt = token.sessionStartedAt,
        ) ?: throw ConflictException(Error(code = ErrorCode.AUTH_CREDENTIALS_EXPIRED))
        if (ttl <= Duration.ZERO) {
            throw ConflictException(Error(code = ErrorCode.AUTH_CREDENTIALS_EXPIRED))
        }

        return tokenService.create(
            application = principal.getApplication(),
            userId = user.id ?: -1,
            roles = userApplication.roles.map { role -> role.code }.toTypedArray(),
            tenantId = membership.tenantId,
            ttlSeconds = ttl.seconds.toInt(),
            partyId = membership.party?.id,
            issuedAt = now,
            sessionStartedAt = token.sessionStartedAt,
        )
    }
}
