package com.wutsi.ndopify.contract.dto

import java.util.Date

data class Contract(
    val id: String = "",
    val number: String = "",
    val type: ContractType = ContractType.UNKNOWN,
    val status: ContractStatus = ContractStatus.UNKNOWN,
    val contentUrl: String? = null,
    val signedContentUrl: String? = null, // The signed PDF, once all parties signed
    val parties: List<ContractParty> = emptyList(),
    val createdAt: Date = Date(),
    val durationMonths: Int = 0,
    val signatureRequestedAt: Date? = null, // When the signature was last requested
    val signatureDeadline: Date? = null,
    val validFrom: Date? = null,
    val validUntil: Date? = null,
    val signedAt: Date? = null,
)
