package com.wutsi.ndopify.visit.server.event

/** Published by `VisitService.cancel`, inside its transaction: the other side of the visit is e-mailed after commit. */
data class VisitCancelledEvent(
    val visitId: Long,
    val tenantId: Long,
)
