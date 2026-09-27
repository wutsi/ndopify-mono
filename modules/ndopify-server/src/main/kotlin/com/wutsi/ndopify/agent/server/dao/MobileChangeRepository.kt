package com.wutsi.ndopify.agent.server.dao

import com.wutsi.ndopify.agent.server.domain.MobileChangeEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MobileChangeRepository :
    CrudRepository<MobileChangeEntity, Long>,
    JpaSpecificationExecutor<MobileChangeEntity>
