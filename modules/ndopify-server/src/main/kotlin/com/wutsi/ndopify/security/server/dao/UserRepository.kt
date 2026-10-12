package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface UserRepository : CrudRepository<UserEntity, Long> {
    fun findByEmailIgnoreCase(email: String): Optional<UserEntity>
    fun findByPartyId(partyId: Long): UserEntity?

    // Doesn't load the entity: its eager party is @TenantId-filtered, so loading a user of another tenant fails.
    @Query(
        "SELECT COUNT(u) > 0 FROM UserEntity u " +
            "WHERE u.id = :id AND u.deleted = false AND (u.tenantId IS NULL OR u.tenantId = :tenantId)"
    )
    fun isVisible(@Param("id") id: Long, @Param("tenantId") tenantId: Long?): Boolean
}
