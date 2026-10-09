package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.common.dto.HttpHeader
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.platform.mail.Email
import com.wutsi.ndopify.platform.mail.MailBodyResolver
import com.wutsi.ndopify.platform.mail.MailService
import com.wutsi.ndopify.platform.mail.Receipient
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.util.UUID

@Service
open class OtpService(
    private val otpGenerator: OtpGenerator,
    private val userService: UserService,
    private val authFactorService: AuthFactorService,
    private val passwordEncryptor: PasswordEncryptor,
    private val tenantService: TenantService,
    private val mailService: MailService,
    private val mailBodyResolver: MailBodyResolver,
) {
    companion object {
        const val SUBJECT = "Votre code de vérification {{tenant_name}}"
    }

    /**
     * Stores a new OTP for the user and emails it. Only the hash of the code is persisted, so this is the one
     * moment the clear code can be sent. If the email cannot be delivered the exception propagates and the
     * transaction rolls back: the caller is told the request failed instead of waiting for a code that will
     * never arrive. `rollbackOn` is needed because delivery errors are checked (MessagingException), which
     * jakarta's @Transactional does not roll back on by default.
     */
    @Transactional(rollbackOn = [Exception::class])
    fun create(request: CreateOtpRequest): AuthFactorEntity {
        val user = userService.findByEmailOrCreate(request.email)
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

        return sendCode(code, authFactor)
    }

    private fun sendCode(code: String, authFactor: AuthFactorEntity): AuthFactorEntity {
        val user = authFactor.user

        // The tenant of the request wins; fall back to the user's home tenant.
        val tenantId = TenantContext.get() ?: user.tenantId
            ?: throw BadRequestException(
                Error(
                    code = ErrorCode.HTTP_MISSING_PARAMETER,
                    parameter = Parameter(name = HttpHeader.TENANT_ID, type = ParameterType.PARAMETER_TYPE_HEADER),
                )
            )
        val tenant = tenantService.findById(tenantId)

        val expiresAt = requireNotNull(authFactor.expiresAt) { "An OTP must have an expiry" }
        val ttlMinutes = (expiresAt.time - authFactor.modifiedAt.time) / 60_000 // rounded down: never overstates

        val fullName = user.party?.fullName()?.takeIf { it.isNotBlank() }
        mailService.send(
            Email(
                recipient = Receipient(email = user.email, displayName = fullName),
                subject = SUBJECT.replace("{{tenant_name}}", tenant.name),
                body = mailBodyResolver.resolve(
                    path = "/mail/auth/otp.mjml",
                    context = mapOf(
                        "tenant_logo_url" to tenant.logoUrl,
                        "tenant_name" to tenant.name,
                        "recipient_name" to (fullName ?: user.email),
                        "otp_code" to code,
                        "ttl" to ttlMinutes,
                    )
                )
            )
        )
        return authFactor
    }
}
