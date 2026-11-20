package com.wutsi.ndopify.party.server.dao

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
interface KycCaseRepository : CrudRepository<KycCaseEntity, String>, JpaSpecificationExecutor<KycCaseEntity> {
    /** Locks the case's row until the end of the transaction: see `KycService.verify`. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateById(id: String): KycCaseEntity?

    /**
     * The case's status, read from the database (not from the persistence context, which may hold an older one) with
     * its row locked until the end of the transaction: see `KycService.attach`. Native, so not tenant-scoped: only
     * for a case already loaded through the tenant-scoped repository.
     */
    @Query(value = "SELECT status FROM T_KYC_CASE WHERE id = :id FOR UPDATE", nativeQuery = true)
    fun findStatusForUpdateById(id: String): String?

    fun findByStatusAndModifiedAtBefore(status: KycStatus, modifiedAt: Date): List<KycCaseEntity>
}
