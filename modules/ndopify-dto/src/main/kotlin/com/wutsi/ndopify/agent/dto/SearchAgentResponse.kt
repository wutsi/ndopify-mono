package com.wutsi.ndopify.agent.dto

data class SearchAgentResponse(
    val agents: List<AgentSummary> = emptyList(),
)
