package com.wutsi.ndopify.party.dto

data class SearchIdentificationRequest(
    val partyId: Long? = null,
    val types: List<IdentificationType> = emptyList(),
    val statuses: List<IdentificationStatus> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0
)
