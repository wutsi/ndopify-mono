package com.wutsi.ndopify.visit.server.event

/** Published by `VisitService.checkOut`, inside its transaction, when the agent closes the visit. */
data class VisitCompletedEvent(
    val visitId: Long,
    val tenantId: Long,
)
