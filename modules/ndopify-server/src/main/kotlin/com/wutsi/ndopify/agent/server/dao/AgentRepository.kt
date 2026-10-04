package com.wutsi.ndopify.agent.server.dao

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AgentRepository : CrudRepository<AgentEntity, Long>, JpaSpecificationExecutor<AgentEntity> {
    fun findByParty(party: PartyEntity): AgentEntity?
}
