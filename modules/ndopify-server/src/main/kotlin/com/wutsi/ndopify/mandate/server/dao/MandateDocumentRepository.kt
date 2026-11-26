package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.server.domain.MandateDocumentEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MandateDocumentRepository : CrudRepository<MandateDocumentEntity, Long> {
    // A mandate's documents, newest first
    fun findByMandateIdOrderByIdDesc(mandateId: Long): List<MandateDocumentEntity>
}
