package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

/** Number of mandates of an agent. */
interface AgentMandateCount {
    val agentId: Long
    val total: Long
}

@Repository
interface MandateRepository : CrudRepository<MandateEntity, Long>, JpaSpecificationExecutor<MandateEntity> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MandateEntity m WHERE m.id = :id")
    fun findByIdForUpdate(id: Long): MandateEntity?

    @Query(
        "SELECT m.agentId AS agentId, COUNT(m) AS total FROM MandateEntity m " +
            "WHERE m.agentId IN :agentIds AND m.status IN :statuses GROUP BY m.agentId"
    )
    fun countByAgentIdsAndStatusIn(
        agentIds: Collection<Long>,
        statuses: Collection<MandateStatus>,
    ): List<AgentMandateCount>

    // Mandates in [status] without an offer in [assignmentStatus] (ex: waiting for an agent, without a PENDING offer:
    // came at night, or had no candidate). Oldest first
    @Query(
        "SELECT m.id FROM MandateEntity m WHERE m.status = :status AND NOT EXISTS (" +
            "SELECT a FROM AssignmentEntity a WHERE a.mandate = m AND a.status = :assignmentStatus) " +
            "ORDER BY m.createdAt, m.id"
    )
    fun findIdsWithoutAssignment(
        status: MandateStatus,
        assignmentStatus: AssignmentStatus,
        page: Pageable,
    ): List<Long>
}
