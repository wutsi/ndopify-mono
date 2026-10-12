package com.wutsi.ndopify.party.dto

import java.util.Date

data class KycCase(
    val id: String = "",
    val partyId: Long = -1L,
    val identificationType: IdentificationType = IdentificationType.UNKNOWN,
    val score: Int? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val retries: Int? = null,
    val verifications: List<KycVerification> = emptyList(),
    val images: List<KycImage> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
