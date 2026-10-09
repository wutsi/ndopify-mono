package com.wutsi.ndopify.security.server.service

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.dao.AuthFactorRepository
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
open class AuthFactorService(
    private val dao: AuthFactorRepository,
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
        ttl: Int? = null,
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
                    expiresAt = ttl?.let { Date(now.time + it * 1000L) }
                )
            )
        } else {
            dao.save(
                authFactor.copy(
                    data = data,
                    salt = salt,
                    modifiedAt = now,
                    expiresAt = ttl?.let { Date(now.time + it * 1000L) }
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
}
