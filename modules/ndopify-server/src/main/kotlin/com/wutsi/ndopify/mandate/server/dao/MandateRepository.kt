package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Date

/** Number of mandates of an agent. */
interface AgentMandateCount {
    val agentId: Long
    val total: Long
}

@Repository
interface MandateRepository : CrudRepository<MandateEntity, Long>, JpaSpecificationExecutor<MandateEntity> {
    fun findByContractId(contractId: String): MandateEntity?

    // Ids only, so that no entity is loaded before findByIdForUpdate: Hibernate wouldn't refresh it when locking
    @Query("SELECT m.id FROM MandateEntity m WHERE m.contractId = :contractId")
    fun findIdByContractId(contractId: String): Long?

    @Query("SELECT m.contractId FROM MandateEntity m WHERE m.id = :id")
    fun findContractIdById(id: Long): String?

    // The customer's search still open in the same city and for the same listing type: a listing asked for from its
    // public page attaches to it instead of creating a duplicate search. Newest first, should a customer have two.
    fun findFirstByCustomerIdAndMandateTypeAndCityIdAndListingTypeAndStatusNotInOrderByIdDesc(
        customerId: Long,
        mandateType: MandateType,
        cityId: Long,
        listingType: ListingType,
        statuses: Collection<MandateStatus>,
    ): MandateEntity?

    // The searches the customer created since [after] (the rate cap of the public listing page)
    @Query(
        "SELECT COUNT(m) FROM MandateEntity m WHERE m.customerId = :customerId " +
            "AND m.mandateType = :mandateType AND m.createdAt > :after"
    )
    fun countByCustomerIdAndMandateTypeAndCreatedAtAfter(
        customerId: Long,
        mandateType: MandateType,
        after: Date,
    ): Long

    // With its neighbourhoods, for a caller outside a transaction (the lazy collection couldn't be read there)
    @EntityGraph(attributePaths = ["neighborhoodIds"])
    @Query("SELECT m FROM MandateEntity m WHERE m.id = :id")
    fun findWithNeighborhoodsById(id: Long): MandateEntity?

    @EntityGraph(attributePaths = ["neighborhoodIds"])
    fun findByAgentIdAndMandateTypeAndStatus(
        agentId: Long,
        mandateType: MandateType,
        status: MandateStatus,
    ): List<MandateEntity>

    // Ids only: each mandate is then loaded with its neighbourhoods. With an agent, oldest first
    @Query(
        "SELECT m.id FROM MandateEntity m WHERE m.mandateType = :mandateType AND m.status = :status " +
            "AND m.agentId IS NOT NULL ORDER BY m.id"
    )
    fun findIdsWithAgent(mandateType: MandateType, status: MandateStatus): List<Long>

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
