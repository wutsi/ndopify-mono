package com.wutsi.ndopify.contract.dto

data class SignContractRequest(
    val partyId: String = "",
    val ipAddress: String? = null,
    val userAgent: String? = null,
)
