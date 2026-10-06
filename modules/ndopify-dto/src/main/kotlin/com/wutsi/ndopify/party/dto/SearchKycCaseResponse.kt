package com.wutsi.ndopify.party.dto

data class SearchKycCaseResponse(
    val cases: List<KycCaseSummary> = emptyList(),
)
