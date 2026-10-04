package com.wutsi.ndopify.agent.server.mapper

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.AgentSummary
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.mapper.PartyMapper
import org.springframework.stereotype.Service

@Service
class AgentMapper(private val partyMapper: PartyMapper) {
    fun toAgent(entity: AgentEntity): Agent {
        return Agent(
            id = entity.id ?: -1,
            party = partyMapper.toParty(entity.party),
            agentType = entity.agentType,
            experienceLevel = entity.experienceLevel,
            biography = entity.biography,
            cityId = entity.cityId,
            neighborhoodIds = entity.neighborhoodIds,
            whatsappNumber = entity.whatsappNumber,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    fun toAgentSummary(entity: AgentEntity): AgentSummary {
        return AgentSummary(
            id = entity.id ?: -1,
            party = partyMapper.toParty(entity.party),
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }
}
