package com.wutsi.ndopify.agent.server.dao

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AgentRepository : CrudRepository<AgentEntity, Long>, JpaSpecificationExecutor<AgentEntity> {
    fun findByParty(party: PartyEntity): AgentEntity?

    fun findByPartyId(partyId: Long): AgentEntity?

    /**
     * The agents' parties are fetched in the search's own query: `party` is an eager to-one, otherwise loaded
     * afterwards by batches of `default_batch_fetch_size`, one more query per 50 agents (ex: the dispatch's
     * candidates).
     */
    @EntityGraph(attributePaths = ["party"])
    override fun findAll(spec: Specification<AgentEntity>, pageable: Pageable): Page<AgentEntity>
}
