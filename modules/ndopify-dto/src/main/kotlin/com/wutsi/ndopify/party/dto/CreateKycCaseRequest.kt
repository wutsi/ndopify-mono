package com.wutsi.ndopify.party.dto

import jakarta.validation.constraints.NotEmpty

data class CreateKycCaseRequest(
    @get:NotEmpty val identificationId: String = "",
    val paymentMethodId: String? = null,
)
