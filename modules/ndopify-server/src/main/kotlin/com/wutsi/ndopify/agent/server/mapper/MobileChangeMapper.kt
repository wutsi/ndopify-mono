package com.wutsi.ndopify.agent.server.mapper

import com.wutsi.ndopify.agent.dto.MobileChange
import com.wutsi.ndopify.agent.dto.MobileChangeSummary
import com.wutsi.ndopify.agent.server.domain.MobileChangeEntity
import org.springframework.stereotype.Service

@Service
class MobileChangeMapper {
    fun toMobileChange(entity: MobileChangeEntity): MobileChange {
        return MobileChange(
            id = entity.id ?: -1,
            agentId = entity.agent.id ?: -1,
            oldMobileNumber = entity.oldMobileNumber,
            newMobileNumber = entity.newMobileNumber,
            newGateway = entity.newGateway,
            holderName = entity.holderName,
            status = entity.status,
            errorCode = entity.errorCode,
            failureReason = entity.failureReason,
            retries = entity.retries,
            createdAt = entity.createdAt,
            verifiedAt = entity.verifiedAt,
            verifyByUserId = entity.verifyByUserId,
        )
    }

    fun toMobileChangeSummary(entity: MobileChangeEntity): MobileChangeSummary {
        return MobileChangeSummary(
            id = entity.id ?: -1,
            agentId = entity.agent.id ?: -1,
            oldMobileNumber = entity.oldMobileNumber,
            newMobileNumber = entity.newMobileNumber,
            newGateway = entity.newGateway,
            status = entity.status,
            createdAt = entity.createdAt,
            verifiedAt = entity.verifiedAt ?: entity.createdAt,
        )
    }
}
