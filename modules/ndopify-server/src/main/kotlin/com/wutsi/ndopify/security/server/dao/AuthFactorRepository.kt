package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AuthFactorRepository : CrudRepository<AuthFactorEntity, Long> {
    fun findByUserAndAuthType(user: UserEntity, authType: AuthType): Optional<AuthFactorEntity>

    // Locks the row as it is loaded: a wrong code counts on it, and Hibernate wouldn't refresh an instance it already
    // has (two codes sent in parallel must not both read the same count).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM AuthFactorEntity f WHERE f.user = :user AND f.authType = :authType")
    fun findByUserAndAuthTypeForUpdate(
        @Param("user") user: UserEntity,
        @Param("authType") authType: AuthType,
    ): Optional<AuthFactorEntity>
}
