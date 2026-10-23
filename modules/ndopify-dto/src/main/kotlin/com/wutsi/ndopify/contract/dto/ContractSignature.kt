package com.wutsi.ndopify.contract.dto

import java.util.Date

data class ContractSignature(
    val contractId: String = "",
    val contractNumber: String = "",
    val legalName: String = "",
    val signedAt: Date = Date(),
)
