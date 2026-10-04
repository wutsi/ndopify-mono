package com.wutsi.ndopify.party.dto

data class CreatePaymentMethod(
    val number: String = "",
    val providerName: String? = null,
    val holderName: String? = null,
    val methodType: PaymentMethodType = PaymentMethodType.UNKNOWN,
)
