package com.wutsi.ndopify.party.dto

import java.util.Date

data class KycCaseSummary(
    val id: String = "",
    val partyId: Long = -1,

    val score: Int? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val errorCode: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
