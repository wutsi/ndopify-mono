package com.wutsi.ndopify.agent.dto

data class UpdateAgentRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,

    val agentType: AgentType? = null,
    val experienceLevel: ExperienceLevel? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long>? = null,
    val biography: String? = null,
)
