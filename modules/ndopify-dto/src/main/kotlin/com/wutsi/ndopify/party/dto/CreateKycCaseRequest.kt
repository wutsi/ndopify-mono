package com.wutsi.ndopify.party.dto

data class CreateKycCaseRequest(
    val partyId: Long = -1,
    val identificationType: IdentificationType = IdentificationType.UNKNOWN,
)
