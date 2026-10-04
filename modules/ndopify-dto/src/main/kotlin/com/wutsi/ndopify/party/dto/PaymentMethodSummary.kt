package com.wutsi.ndopify.party.dto

import java.util.Date

data class PaymentMethodSummary(
    val id: String = "",
    val partyId: Long = -1,
    val number: String = "",
    val methodType: PaymentMethodType = PaymentMethodType.UNKNOWN,
    val verificationStatus: PaymentMethodStatus = PaymentMethodStatus.UNKNOWN,
    val expiresAt: Date? = null,
    val createdAt: Date = Date(),
    val modifiedAt: Date = Date(),
)
