package com.wutsi.ndopify.contract.dto

import java.util.Date

data class Contract(
    val id: String = "",
    val number: String = "",
    val type: ContractType = ContractType.UNKNOWN,
    val status: ContractStatus = ContractStatus.UNKNOWN,
    val contentUrl: String = "",
    val parties: List<ContractParty> = emptyList(),
    val createdAt: Date = Date(),
    val validFrom: Date = Date(),
    val validUntil: Date = Date(),
    val signedAt: Date? = null,
)
