package com.wutsi.ndopify.mandate.dto

data class SearchMandateResponse(
    val mandates: List<MandateSummary> = emptyList(),
)
