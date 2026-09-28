package com.wutsi.ndopify.agent.server.mapper

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.AgentSummary
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import org.springframework.stereotype.Service

@Service
class AgentMapper {
    fun toAgent(entity: AgentEntity): Agent {
        return Agent(
            id = entity.id ?: -1,
            userId = entity.userId,
            mobileChangeId = entity.mobileChange?.id,
            identityChangeId = entity.identityChange?.id,
            firstName = entity.firstName,
            lastName = entity.lastName,
            agentType = entity.agentType,
            biography = entity.biography,
            agencyName = entity.agencyName,
            cityId = entity.cityId,
            neighborhoodIds = entity.neighborhoodIds,
            photoUrl = entity.photoUrl,
            agencyLogoUrl = entity.agencyLogoUrl,
            mobileMoneyNumber = entity.mobileMoneyNumber,
            mobileMoneyGateway = entity.mobileMoneyGateway,
            mobileMoneyKycStatus = entity.mobileMoneyKycStatus,
            identityKycStatus = entity.identityKycStatus,
            status = entity.status,
        )
    }

    fun toAgentSummary(entity: AgentEntity): AgentSummary {
        return AgentSummary(
            id = entity.id ?: -1,
            firstName = entity.firstName,
            lastName = entity.lastName,
            agentType = entity.agentType,
            agencyName = entity.agencyName,
            photoUrl = entity.photoUrl,
            agencyLogoUrl = entity.agencyLogoUrl,
        )
    }
}
