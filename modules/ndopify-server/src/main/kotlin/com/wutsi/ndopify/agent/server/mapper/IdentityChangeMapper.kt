package com.wutsi.ndopify.agent.server.mapper

import com.wutsi.ndopify.agent.dto.IdentityChange
import com.wutsi.ndopify.agent.dto.IdentityChangeSummary
import com.wutsi.ndopify.agent.server.domain.IdentityChangeEntity
import org.springframework.stereotype.Service

@Service
class IdentityChangeMapper {
    fun toIdentityChange(entity: IdentityChangeEntity): IdentityChange {
        return IdentityChange(
            id = entity.id ?: -1,
            verifyByUserId = entity.verifyByUserId,
            agentId = entity.agent.id ?: -1,
            oldFirstName = entity.oldFirstName,
            oldLastName = entity.oldLastName,
            newFirstName = entity.newFirstName,
            newLastName = entity.newLastName,
            identityType = entity.identityType,
            imageUrls = entity.imageUrls,
            holderName = entity.holderName,
            retries = entity.retries,
            status = entity.status,
            errorCode = entity.errorCode,
            failureReason = entity.failureReason,
            createdAt = entity.createdAt,
            verifiedAt = entity.verifiedAt,
        )
    }

    fun toIdentityChangeSummary(entity: IdentityChangeEntity): IdentityChangeSummary {
        return IdentityChangeSummary(
            id = entity.id ?: -1,
            agentId = entity.agent.id ?: -1,
            oldFirstName = entity.oldFirstName,
            oldLastName = entity.oldLastName,
            newFirstName = entity.newFirstName,
            newLastName = entity.newLastName,
            identityType = entity.identityType,
            status = entity.status,
            createdAt = entity.createdAt,
            verifiedAt = entity.verifiedAt ?: entity.createdAt,
        )
    }
}
