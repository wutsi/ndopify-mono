package com.wutsi.ndopify.party.dto

import java.util.Date

data class KycVerification(
    val id: String = "",
    val kycCaseId: String = "",
    val type: KycVerificationType = KycVerificationType.UNKNOWN,
    val status: KycStatus = KycStatus.UNKNOWN,
    val score: Int? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
)
