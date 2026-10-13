package com.wutsi.ndopify.customer.dto

import jakarta.validation.constraints.NotEmpty

data class CreateCustomerRequest(
    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val whatsappNumber: String = "",
)
