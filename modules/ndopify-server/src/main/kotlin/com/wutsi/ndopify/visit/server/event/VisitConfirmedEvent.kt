package com.wutsi.ndopify.visit.server.event

/**
 * Published by `VisitService.schedule`, inside its transaction, when the agent confirms a visit: the customer gets its
 * code and the address, both get the date and time.
 */
data class VisitConfirmedEvent(
    val visitId: Long,
    val tenantId: Long,
)
