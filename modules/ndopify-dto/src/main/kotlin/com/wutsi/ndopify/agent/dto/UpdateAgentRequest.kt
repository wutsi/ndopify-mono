package com.wutsi.ndopify.agent.dto

/**
 * First and last name aren't updatable: they're the party's identity, verified by KYC against its identity
 * document and mobile-money holder name.
 */
data class UpdateAgentRequest(
    val whatsappNumber: String? = null,

    val agentType: AgentType? = null,
    val experienceLevel: ExperienceLevel? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long>? = null,
    val biography: String? = null,
)
