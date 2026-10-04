package com.wutsi.ndopify.agent.dto

data class SearchAgentRequest(
    val ids: List<Long> = emptyList(),
    val partyIds: List<Long> = emptyList(),
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val mobileMoneyNumber: String? = null,

    val limit: Int = 20,
    val offset: Int = 0,
)
