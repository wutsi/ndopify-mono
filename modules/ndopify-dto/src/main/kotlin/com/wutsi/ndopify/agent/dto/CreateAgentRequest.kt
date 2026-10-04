package com.wutsi.ndopify.agent.dto

import jakarta.validation.constraints.NotEmpty

data class CreateAgentRequest(
    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val whatsappNumber: String = "",
    @get:NotEmpty val mobileMoneyNumber: String = "",

    val agentType: AgentType = AgentType.UNKNOWN,
    val experienceLevel: ExperienceLevel = ExperienceLevel.UNKNOWN,
    val cityId: Long = -1,
    val neighborhoodIds: List<Long> = emptyList(),
    val biography: String? = null,
)
