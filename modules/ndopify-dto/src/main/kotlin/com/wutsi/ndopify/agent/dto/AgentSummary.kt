package com.wutsi.ndopify.agent.dto

data class AgentSummary(
    val id: Long = -1,

    val firstName: String = "",
    val lastName: String = "",
    val agentType: AgentType = AgentType.UNKNOWN,
    val agencyName: String? = null,
    val photoUrl: String? = null,
    val agencyLogoUrl: String? = null,
)
