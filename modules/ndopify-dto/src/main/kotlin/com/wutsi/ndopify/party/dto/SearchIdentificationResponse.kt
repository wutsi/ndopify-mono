package com.wutsi.ndopify.party.dto

data class SearchIdentificationResponse(
    val identifications: List<IdentificationSummary> = emptyList(),
)
