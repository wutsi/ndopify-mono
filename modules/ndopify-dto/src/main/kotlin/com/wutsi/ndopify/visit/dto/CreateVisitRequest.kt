package com.wutsi.ndopify.visit.dto

import jakarta.validation.constraints.Size
import java.util.Date

data class CreateVisitRequest(
    val matchId: Long = -1,
    val type: VisitType = VisitType.UNKNOWN,

    // A date and time on one of the 7 days starting tomorrow, 08:00-20:00 in the tenant's time zone, on a 30-minute
    // boundary
    val requestedAt: Date = Date(),

    @get:Size(max = 500) val note: String? = null,
)
