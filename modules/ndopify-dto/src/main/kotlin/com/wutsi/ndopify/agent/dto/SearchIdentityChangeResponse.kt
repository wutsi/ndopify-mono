package com.wutsi.ndopify.agent.dto

data class SearchIdentityChangeResponse(
    val changes: List<IdentityChangeSummary> = emptyList(),
)
