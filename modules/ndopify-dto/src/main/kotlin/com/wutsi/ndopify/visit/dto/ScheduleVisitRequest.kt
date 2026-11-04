package com.wutsi.ndopify.visit.dto

import java.util.Date

data class ScheduleVisitRequest(
    val scheduledAt: Date = Date(),

    // null: keep the type the customer requested
    val type: VisitType? = null,
)
