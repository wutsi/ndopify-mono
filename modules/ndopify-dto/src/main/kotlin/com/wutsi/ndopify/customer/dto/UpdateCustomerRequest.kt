package com.wutsi.ndopify.customer.dto

import jakarta.validation.constraints.NotEmpty

/** The corrected details of a mandate's customer: names and e-mail of its party, plus its WhatsApp number. */
data class UpdateCustomerRequest(
    @get:NotEmpty val firstName: String = "",
    @get:NotEmpty val lastName: String = "",
    @get:NotEmpty val email: String = "",
    @get:NotEmpty val whatsappNumber: String = "",
)
