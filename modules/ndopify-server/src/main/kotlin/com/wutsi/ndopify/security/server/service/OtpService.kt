package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.event.OtpCreatedEvent
import com.wutsi.ndopify.security.server.mail.OtpMailet
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.util.UUID

@Service
open class OtpService(
    private val otpGenerator: OtpGenerator,
    private val userService: UserService,
    private val authFactorService: AuthFactorService,
    private val membershipService: MembershipService,
    private val passwordEncryptor: PasswordEncryptor,
    private val mailet: OtpMailet,
    private val eventPublisher: ApplicationEventPublisher,
) {
    companion object {
        const val SUBJECT = "Votre code de vérification {{tenant_name}}"
    }

    /**
     * Stores a new OTP for the user and publishes an [OtpCreatedEvent] carrying the clear code (only its hash is
     * persisted). `OtpEventListener` emails it asynchronously once committed, so an email that cannot be delivered
     * no longer fails the request: the user asks for a new code. The tenant is resolved here, before the hand-off,
     * so a missing or unknown tenant still fails the request (and rolls back) instead of failing silently later.
     * A user who isn't a member of the tenant is not found: it can't get a code branded by another tenant.
     * `rollbackOn` is needed because jakarta's @Transactional does not roll back on checked exceptions by default.
     */
    @Transactional(rollbackOn = [Exception::class])
    fun create(request: CreateOtpRequest): AuthFactorEntity {
        val tenant = mailet.resolveTenant()
        val user = userService.findByEmail(request.email)
        if (membershipService.findByUserOrNull(user) == null) {
            throw NotFoundException(
                error = Error(
                    code = ErrorCode.USER_NOT_FOUND,
                    message = "User not found: ${request.email}",
                )
            )
        }
        val salt = UUID.randomUUID().toString()
        val code = otpGenerator.generate()
        val data = passwordEncryptor.encrypt(code, salt)
        val authFactor = authFactorService.findByUserAndAuthTypeOrNullOrCreate(
            user,
            AuthType.OTP,
            data,
            salt,
            request.ttl
        )

        eventPublisher.publishEvent(
            OtpCreatedEvent(authFactorId = authFactor.id!!, code = code, tenantId = tenant.id!!)
        )
        return authFactor
    }

    /**
     * Emails the code of an OTP. Called by `OtpEventListener` on its own thread, with `TenantContext` set: the
     * transaction gives the mailet a session to load the user and its membership's party.
     */
    @Transactional
    fun sendEmail(authFactorId: Long, code: String) {
        val authFactor = authFactorService.findById(authFactorId)
        mailet.send(code, authFactor)
    }
}
