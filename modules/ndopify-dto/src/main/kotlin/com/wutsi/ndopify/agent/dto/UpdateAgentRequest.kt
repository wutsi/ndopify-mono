package com.wutsi.ndopify.agent.dto

data class UpdateAgentRequest(
    val agentType: AgentType = AgentType.UNKNOWN,
    val biography: String? = null,
    val agencyName: String? = null,

    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
)
