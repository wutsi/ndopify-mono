package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.refdata.dto.AuthType
import com.wutsi.ndopify.security.server.domain.AuthFactorEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface AuthFactorRepository : CrudRepository<AuthFactorEntity, Long> {
    fun findByUserAndAuthType(user: UserEntity, authType: AuthType): Optional<AuthFactorEntity>
}
