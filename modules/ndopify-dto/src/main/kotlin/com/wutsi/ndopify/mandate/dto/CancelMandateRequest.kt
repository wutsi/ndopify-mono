package com.wutsi.ndopify.mandate.dto

data class CancelMandateRequest(
    val cancellationCode: MandateCancellationCode? = null,
    val cancellationReason: String? = null,
)
