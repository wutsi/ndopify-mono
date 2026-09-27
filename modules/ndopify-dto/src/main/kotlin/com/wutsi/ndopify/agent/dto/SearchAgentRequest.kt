package com.wutsi.ndopify.agent.dto

data class SearchAgentRequest(
    val ids: List<Long> = emptyList(),
    val userId: Long? = null,
    val cityId: Long? = null,
    val status: AgentStatus? = null,
    val mobileMoneyNumber: String? = null,

    val limit: Int = 20,
    val offset: Int = 0,
)
