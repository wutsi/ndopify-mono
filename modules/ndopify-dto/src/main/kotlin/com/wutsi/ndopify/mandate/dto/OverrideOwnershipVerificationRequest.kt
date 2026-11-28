package com.wutsi.ndopify.mandate.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** The admin's mandatory justification for overriding a LISTING mandate's owner verdict (#377). */
data class OverrideOwnershipVerificationRequest(
    @get:NotBlank @get:Size(max = 500) val reason: String = "",
)
