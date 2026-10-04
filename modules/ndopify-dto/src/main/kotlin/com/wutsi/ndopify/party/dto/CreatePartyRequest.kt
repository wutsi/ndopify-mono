package com.wutsi.ndopify.party.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import java.util.Date

data class Party(
    val id: Long? = null,

    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val kycStatus: KycStatus = KycStatus.UNKNOWN,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
