package com.wutsi.ndopify.visit.server.event

/**
 * Published by `VisitService.create`, inside its transaction, when a customer requests a visit. Carries ids rather than
 * the entity, so the listener reloads what it needs ([tenantId] is set because it runs on a thread that has none).
 */
data class VisitRequestedEvent(
    val visitId: Long,
    val tenantId: Long,
)
