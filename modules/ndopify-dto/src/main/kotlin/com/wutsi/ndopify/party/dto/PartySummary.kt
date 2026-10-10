package com.wutsi.ndopify.party.dto

import java.util.Date

data class PartySummary(
    val id: Long = -1,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val kycStatus: KycStatus = KycStatus.UNKNOWN,
    val photoUrl: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
