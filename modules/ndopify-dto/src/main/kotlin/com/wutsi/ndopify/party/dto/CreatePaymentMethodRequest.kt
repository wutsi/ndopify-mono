package com.wutsi.ndopify.party.dto

import java.util.Date

data class CreatePaymentMethodRequest(
    val number: String = "",
    val providerName: String? = null,
    val holderName: String? = null,
    val expiresAt: Date? = null,
    val type: PaymentMethodType = PaymentMethodType.UNKNOWN,
)
