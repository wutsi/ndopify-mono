package com.wutsi.ndopify.party.dto

data class SearchPaymentMethodResponse(
    val paymentMethods: List<PaymentMethodSummary> = emptyList(),
)
