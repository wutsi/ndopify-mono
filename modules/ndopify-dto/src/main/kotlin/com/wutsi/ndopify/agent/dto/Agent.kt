package com.wutsi.ndopify.agent.dto

import com.wutsi.ndopify.party.dto.Party
import java.util.Date

data class Agent(
    val id: Long = -1,
    val party: Party = Party(),
    val agentType: AgentType = AgentType.UNKNOWN,
    val biography: String? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val experienceLevel: ExperienceLevel = ExperienceLevel.UNKNOWN,
    val whatsappNumber: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
