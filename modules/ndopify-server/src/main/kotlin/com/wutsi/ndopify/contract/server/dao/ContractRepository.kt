package com.wutsi.ndopify.contract.server.dao

import com.wutsi.ndopify.contract.dto.ContractStatus
import com.wutsi.ndopify.contract.server.domain.ContractEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
interface ContractRepository : CrudRepository<ContractEntity, String>, JpaSpecificationExecutor<ContractEntity> {
    // Serializes the signatures of a contract, so that the last one always sees the others and marks it SIGNED
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContractEntity c WHERE c.id = :id")
    fun findByIdForUpdate(id: String): ContractEntity?

    // The contracts to expire: not signed by all their parties in time, or signed and past their end date
    @Query(
        """
        SELECT c.id FROM ContractEntity c
        WHERE (c.status = :pending AND c.signatureDeadline < :now) OR (c.status = :signed AND c.validUntil < :now)
        ORDER BY c.createdAt
        """
    )
    fun findIdsToExpire(pending: ContractStatus, signed: ContractStatus, now: Date, page: Pageable): List<String>
}
