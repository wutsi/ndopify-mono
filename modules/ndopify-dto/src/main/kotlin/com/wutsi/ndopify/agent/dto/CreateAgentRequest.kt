package com.wutsi.ndopify.agent.dto

import jakarta.validation.constraints.NotEmpty

data class CreateAgentRequest(
    val userId: Long? = null,

    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",

    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),

    val agentType: AgentType = AgentType.UNKNOWN,
    val biography: String? = null,
    val agencyName: String? = null,
)
