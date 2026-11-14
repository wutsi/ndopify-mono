package com.wutsi.ndopify.visit.dto

data class SearchCoBrokerageVisitResponse(
    val visits: List<CoBrokerageVisitSummary> = emptyList(),
)
