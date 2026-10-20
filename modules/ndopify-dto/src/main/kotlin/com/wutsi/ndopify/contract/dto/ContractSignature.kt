package com.wutsi.ndopify.contract.dto

import java.util.Date

// What the public verification page shows of one signature: nothing else (no e-mail, IP, user agent, ids, hash)
data class ContractSignature(
    val contractId: String = "",
    val contractNumber: String = "",
    val legalName: String = "",
    val signedAt: Date = Date(),
    val signedContentUrl: String? = null,
)
