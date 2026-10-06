package com.wutsi.ndopify.party.dto

data class SearchPaymentMethodRequest(
    val partyId: Long? = null,
    val types: List<PaymentMethodType> = emptyList(),
    val statuses: List<PaymentMethodStatus> = emptyList(),

    val limit: Int = 20,
    val offset: Int = 0
)
