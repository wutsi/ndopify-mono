package com.wutsi.ndopify.contract.dto

import java.util.Date

data class ContractParty(
    val id: String = "",
    val role: ContractPartyRole = ContractPartyRole.UNKNOWN,
    val partyId: Long = -1,
    val legalName: String = "",
    val legalEmail: String = "",
    val signatureRequired: Boolean = true,
    val signed: Boolean = false,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val signedAt: Date? = null,
)
