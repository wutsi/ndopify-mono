package com.wutsi.ndopify.agent.dto

data class SearchMobileChangeResponse(
    val changes: List<MobileChangeSummary> = emptyList(),
)
