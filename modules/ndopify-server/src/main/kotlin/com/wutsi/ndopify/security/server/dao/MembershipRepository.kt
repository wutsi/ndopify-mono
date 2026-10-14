package com.wutsi.ndopify.security.server.dao

import com.wutsi.ndopify.security.server.domain.MembershipEntity
import com.wutsi.ndopify.security.server.domain.UserEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

// MembershipEntity is @TenantId-filtered: every lookup only sees the current tenant's memberships.
@Repository
interface MembershipRepository : CrudRepository<MembershipEntity, Long> {
    fun findByUser(user: UserEntity): MembershipEntity?
    fun findByPartyId(partyId: Long): MembershipEntity?
}
