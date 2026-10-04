package com.wutsi.ndopify.party.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import java.util.Date

data class IdentificationSummary(
    val id: String = "",
    val partyId: Long = -1,
    val type: IdentificationType = IdentificationType.UNKNOWN,
    val issuingCountryCode: String? = "",
    val numberSuffix: String? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
