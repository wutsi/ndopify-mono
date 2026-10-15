package com.wutsi.ndopify.mandate.server.mapper

import com.wutsi.ndopify.mandate.dto.Assignment
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import org.springframework.stereotype.Service

@Service
class AssignmentMapper {
    fun toAssignment(entity: AssignmentEntity): Assignment {
        return Assignment(
            id = entity.id ?: -1,
            mandateId = entity.mandate.id ?: -1,
            agentId = entity.agentId,
            status = entity.status,
            score = entity.score,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            expiresAt = entity.expiresAt,
        )
    }

    fun toAssignmentSummary(entity: AssignmentEntity): AssignmentSummary {
        return AssignmentSummary(
            id = entity.id ?: -1,
            mandateId = entity.mandate.id ?: -1,
            agentId = entity.agentId,
            status = entity.status,
            score = entity.score,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
            expiresAt = entity.expiresAt,
        )
    }
}
