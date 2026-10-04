package com.wutsi.ndopify.party.dto

import jakarta.validation.constraints.NotEmpty

data class CreateIdentificationRequest(
    val type: IdentificationType = IdentificationType.UNKNOWN,
    @get:NotEmpty val issuingCountryCode: String = "",
    @get:NotEmpty val imageTypes: List<IdentificationImageType> = emptyList(),
)
