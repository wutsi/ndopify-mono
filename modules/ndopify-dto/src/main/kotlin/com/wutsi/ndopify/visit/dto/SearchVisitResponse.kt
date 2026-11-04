package com.wutsi.ndopify.visit.dto

data class SearchVisitResponse(
    val visits: List<VisitSummary> = emptyList(),
)
