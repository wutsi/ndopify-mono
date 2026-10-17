package com.wutsi.ndopify.contract.server.dao

import com.wutsi.ndopify.contract.server.domain.ContractEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ContractRepository : CrudRepository<ContractEntity, String> {
    // Serializes the signatures of a contract, so that the last one always sees the others and marks it SIGNED
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContractEntity c WHERE c.id = :id")
    fun findByIdForUpdate(id: String): ContractEntity?
}
