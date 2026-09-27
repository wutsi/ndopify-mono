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

    fun findByUserAndAuthTypeOrNullOrCreate(user: UserEntity, authType: AuthType, data: String): AuthFactorEntity {
        val authFactor = findByUserAndAuthTypeOrNull(user, authType)
        return if (authFactor == null) {
            dao.save(
                AuthFactorEntity(
                    user = user,
                    authType = authType,
                    data = data,
                )
            )
        } else {
            authFactor.data = data
            dao.save(authFactor)
        }
    }

    fun saveLastLoggedIn(authFactor: AuthFactorEntity): AuthFactorEntity {
        val millis = clock.millis()
        authFactor.lastLoggedInAt = Date(millis)
        return dao.save(authFactor)
    }
}
