package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.dto.CreateOtpRequest
import com.wutsi.ndopify.security.server.dao.AuthFactorRepository
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date
import java.util.UUID

@Service
open class AuthFactorService(
    private val dao: AuthFactorRepository,
    private val otpGenerator: OtpGenerator,
    private val userService: UserService,
    private val passwordEncryptor: PasswordEncryptor,
    private val clock: Clock,
) {
    fun findByIdOrNull(id: Long): AuthFactorEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findByUserAndAuthTypeOrNull(user: UserEntity, authType: AuthType): AuthFactorEntity? {
        return dao.findByUserAndAuthType(user, authType).orElse(null)
    }

    fun findByUserAndAuthTypeOrNullOrCreate(
        user: UserEntity,
        authType: AuthType,
        data: String,
        salt: String? = null,
    ): AuthFactorEntity {
        val authFactor = findByUserAndAuthTypeOrNull(user, authType)
        val now = Date(clock.millis())
        return if (authFactor == null) {
            dao.save(
                AuthFactorEntity(
                    user = user,
                    authType = authType,
                    data = data,
                    salt = salt,
                    createdAt = now,
                    modifiedAt = now,
                )
            )
        } else {
            dao.save(
                authFactor.copy(
                    data = data,
                    salt = salt,
                    modifiedAt = now,
                )
            )
        }
    }

    fun saveLastLoggedIn(authFactor: AuthFactorEntity): AuthFactorEntity {
        val millis = clock.millis()
        return dao.save(
            authFactor.copy(
                lastLoggedInAt = Date(millis)
            )
        )
    }

    @Transactional
    fun generateOtp(request: CreateOtpRequest): AuthFactorEntity {
        val user = userService.findByEmailOrCreate(request.email)
        val salt = UUID.randomUUID().toString()
        val data = passwordEncryptor.encrypt(otpGenerator.generate(), salt)
        return findByUserAndAuthTypeOrNullOrCreate(user, AuthType.OTP, data, salt)
    }
}
