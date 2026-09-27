package com.wutsi.ndopify.agent.server.dao

import com.wutsi.ndopify.agent.server.domain.IdentityChangeEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface IdentityChangeRepository : CrudRepository<IdentityChangeEntity, Long>
