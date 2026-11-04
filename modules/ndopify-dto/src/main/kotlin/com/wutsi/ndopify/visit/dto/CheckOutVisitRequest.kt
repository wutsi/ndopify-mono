package com.wutsi.ndopify.visit.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CheckOutVisitRequest(
    // Private to the agent and the admins
    @get:NotEmpty @get:Size(max = 2000) val comment: String = "",

    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
)
