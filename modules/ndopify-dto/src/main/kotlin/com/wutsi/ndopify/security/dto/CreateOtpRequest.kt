package com.wutsi.ndopify.security.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class CreateOtpRequest(
    @get:NotBlank
    val email: String = "",

    @get:Min(value = 60) @get:Max(value = 600)
    val ttl: Int = 5 * 60, // 5 minutes in seconds
)
