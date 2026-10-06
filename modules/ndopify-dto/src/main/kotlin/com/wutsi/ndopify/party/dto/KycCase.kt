package com.wutsi.ndopify.party.dto

import java.util.Date

data class KycCase(
    val id: String = "",
    val partyId: String = "",
    val identificationId: String = "",
    val paymentMethodId: String? = null,
    val score: Int? = null,
    val status: KycStatus = KycStatus.UNKNOWN,
    val errorCode: String? = null,
    val verifications: List<KycVerification> = emptyList(),
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
