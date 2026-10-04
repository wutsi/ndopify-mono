package com.wutsi.ndopify.party.dao

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.domain.PartyEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PartyRepository : CrudRepository<PartyEntity, Long>, JpaSpecificationExecutor<AgentEntity> {
    fun findByEmail(email: String): PartyEntity?
}
