package com.wutsi.ndopify.visit.dto

import jakarta.validation.constraints.NotEmpty

data class CheckInVisitRequest(
    @get:NotEmpty val code: String = "",

    // Optional: the agent has no GPS, so the position is typed in or left out
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
)
