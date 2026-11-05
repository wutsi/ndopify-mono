package com.wutsi.ndopify.mandate.dto

data class MandateStats(
    val mandateId: Long = -1,
    val properties: Int = 0,
    val interested: Int = 0,
    val upcomingVisits: Int = 0,
    val completedVisits: Int = 0,
)
