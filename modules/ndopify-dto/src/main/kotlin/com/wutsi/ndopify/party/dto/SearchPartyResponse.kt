package com.wutsi.ndopify.party.dto

data class SearchPartyResponse(
    val parties: List<PartySummary> = emptyList(),
)
