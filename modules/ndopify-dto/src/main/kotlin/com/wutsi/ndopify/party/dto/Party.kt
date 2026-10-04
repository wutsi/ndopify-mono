package com.wutsi.ndopify.party.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import java.util.Date

data class Party(
    val id: Long = -1,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val kycStatus: KycStatus = KycStatus.UNKNOWN,
    val photoUrl: String? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
