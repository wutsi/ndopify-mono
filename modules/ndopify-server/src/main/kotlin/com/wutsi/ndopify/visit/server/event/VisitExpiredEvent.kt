package com.wutsi.ndopify.visit.server.event

/** Published by `VisitService.expireDue`, in each visit's transaction: a request not scheduled in time expired. */
data class VisitExpiredEvent(
    val visitId: Long,
    val tenantId: Long,
)
