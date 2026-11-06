package com.wutsi.ndopify.mandate.dto

data class SearchMandateStatsResponse(
    val stats: List<MandateStats> = emptyList(),
)
