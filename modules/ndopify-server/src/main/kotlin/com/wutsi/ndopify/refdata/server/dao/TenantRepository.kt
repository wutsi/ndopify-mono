package com.wutsi.ndopify.refdata.server.dao

import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TenantRepository : CrudRepository<TenantEntity, Long> {
    fun findByActive(active: Boolean): List<TenantEntity>
}
