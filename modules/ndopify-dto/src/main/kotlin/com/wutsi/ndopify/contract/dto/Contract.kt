package com.wutsi.ndopify.contract.dto

import java.util.Date

data class Contract(
    val id: String = "",
    val number: String = "",
    val type: ContractType = ContractType.UNKNOWN,
    val status: ContractStatus = ContractStatus.UNKNOWN,
    val contentUrl: String? = null,
    val parties: List<ContractParty> = emptyList(),
    val createdAt: Date = Date(),
    val durationMonths: Int = 0,
    val signatureDeadline: Date? = null,
    val validFrom: Date? = null,
    val validUntil: Date? = null,
    val signedAt: Date? = null,
)
