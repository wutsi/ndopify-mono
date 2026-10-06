package com.wutsi.ndopify.party.dto

data class SearchKycCaseRequest(
    val partyId: Long? = null,
    val statuses: List<KycStatus> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0,
)
