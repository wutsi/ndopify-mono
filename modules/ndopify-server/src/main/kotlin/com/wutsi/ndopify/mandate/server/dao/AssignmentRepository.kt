package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Date

/** Number of offers of an agent in a given status. */
interface AgentAssignmentCount {
    val agentId: Long
    val status: AssignmentStatus
    val total: Long
}

@Repository
interface AssignmentRepository : CrudRepository<AssignmentEntity, Long>, JpaSpecificationExecutor<AssignmentEntity> {
    fun findByMandateAndStatusIn(mandate: MandateEntity, status: List<AssignmentStatus>): List<AssignmentEntity>

    fun existsByMandateAndStatus(mandate: MandateEntity, status: AssignmentStatus): Boolean

    fun findByStatusAndExpiresAtBefore(status: AssignmentStatus, date: Date, page: Pageable): List<AssignmentEntity>

    // One query for all the candidates, instead of one per agent
    @Query(
        "SELECT a.agentId AS agentId, a.status AS status, COUNT(a) AS total FROM AssignmentEntity a " +
            "WHERE a.agentId IN :agentIds GROUP BY a.agentId, a.status"
    )
    fun countByAgentIds(agentIds: Collection<Long>): List<AgentAssignmentCount>
}
