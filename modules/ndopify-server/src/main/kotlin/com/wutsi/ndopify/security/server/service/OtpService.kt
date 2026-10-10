package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
open class OtpService(
    private val otpGenerator: OtpGenerator,
    private val userService: UserService,
    private val authFactorService: AuthFactorService,
    private val passwordEncryptor: PasswordEncryptor,
    private val mailet: OtpMailet,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(OtpService::class.java)

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
        val user = userService.findByEmail(request.email)
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

        mailet.send(code, authFactor)
        return authFactor
    }
}
