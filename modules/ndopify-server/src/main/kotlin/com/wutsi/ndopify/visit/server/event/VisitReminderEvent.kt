package com.wutsi.ndopify.visit.server.event

/** Published by `VisitService.remindDue`, in each visit's transaction: the visit starts within 24 h. */
data class VisitReminderEvent(
    val visitId: Long,
    val tenantId: Long,
)
