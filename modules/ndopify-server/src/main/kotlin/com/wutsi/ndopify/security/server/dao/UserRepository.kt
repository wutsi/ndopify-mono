package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : CrudRepository<UserEntity, Long> {
    fun findByEmailIgnoreCase(email: String): Optional<UserEntity>
}
