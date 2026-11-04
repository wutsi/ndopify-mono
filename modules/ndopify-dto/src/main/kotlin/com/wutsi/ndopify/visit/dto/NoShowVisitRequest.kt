package com.wutsi.ndopify.visit.dto

data class NoShowVisitRequest(
    val reason: VisitNoShowReason = VisitNoShowReason.CUSTOMER_ABSENT,

    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Double? = null,
)
