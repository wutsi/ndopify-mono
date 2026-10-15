package com.wutsi.ndopify.mandate.server.dao

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface AssignmentRepository : CrudRepository<AssignmentEntity, Long>, JpaSpecificationExecutor<AssignmentEntity> {
    fun findByMandateAndStatusIn(mandate: MandateEntity, status: List<AssignmentStatus>): List<AssignmentEntity>
}
