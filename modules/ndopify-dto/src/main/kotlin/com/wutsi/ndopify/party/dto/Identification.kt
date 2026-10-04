package com.wutsi.ndopify.party.dto

import com.wutsi.ndopify.refdata.dto.KycStatus
import java.util.Date

data class Identification(
    val id: String = "",
    val partyId: Long = -1,
    val type: IdentificationType = IdentificationType.UNKNOWN,
    val issuingCountryCode: String? = "",
    val numberSuffix: String? = null,
    val issuedAt: Date? = null,
    val expiresAt: Date? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val images: List<IdentificationImage> = emptyList(),
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
