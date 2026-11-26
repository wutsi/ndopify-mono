package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.server.domain.MandateDocumentPageEntity
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MandateDocumentPageRepository : CrudRepository<MandateDocumentPageEntity, Long> {
    fun findByDocumentIdOrderByPageIndex(documentId: Long): List<MandateDocumentPageEntity>

    fun findByDocumentIdInOrderByPageIndex(documentIds: Collection<Long>): List<MandateDocumentPageEntity>

    fun findByPath(path: String): MandateDocumentPageEntity?

    @Query("SELECT MAX(p.pageIndex) FROM MandateDocumentPageEntity p WHERE p.documentId = :documentId")
    fun findMaxPageIndex(documentId: Long): Int?
}
