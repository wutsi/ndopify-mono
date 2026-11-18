package com.wutsi.ndopify.visit.dto

import java.util.Date

/**
 * The agent books a visit for an accepted match: the type and the slot, on the 7 days starting tomorrow,
 * 08:00-20:00 in the tenant's time zone, on a 30-minute boundary (ndopify-server's `VisitSlots`).
 */
data class CreateMatchVisitRequest(
    val type: VisitType = VisitType.UNKNOWN,
    val scheduledAt: Date = Date(),
)
