package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Date

@Repository
interface MandateDocumentRepository : CrudRepository<MandateDocumentEntity, Long> {
    // A mandate's documents, newest first
    fun findByMandateIdOrderByIdDesc(mandateId: Long): List<MandateDocumentEntity>

    /**
     * The document under a write lock, for the extraction's claim, save and requeue: a page change and an extraction
     * can't overwrite each other. The tenant filter still applies (JPQL, not native).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM MandateDocumentEntity d WHERE d.id = :id")
    fun findByIdForUpdate(id: Long): MandateDocumentEntity?

    /** The documents of [status], oldest first, at most [pageable]'s size (the job's batch). */
    fun findByStatusOrderByIdAsc(status: DocumentStatus, pageable: Pageable): List<MandateDocumentEntity>

    /** The [status] documents not written since [cutoff]: the extraction of them was interrupted. */
    fun findByStatusAndModifiedAtBefore(status: DocumentStatus, cutoff: Date): List<MandateDocumentEntity>

    /** The [type]'s documents not in [status] (the ops requeue leaves the ones being extracted alone). */
    fun findByTypeAndStatusNot(type: DocumentType, status: DocumentStatus): List<MandateDocumentEntity>
}
