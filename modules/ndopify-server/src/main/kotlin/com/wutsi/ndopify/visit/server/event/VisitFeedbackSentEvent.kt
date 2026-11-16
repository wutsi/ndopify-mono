package com.wutsi.ndopify.visit.server.event

/**
 * Published by `VisitService.feedback`, inside its transaction, when a customer answers the visit feedback form. Carries
 * ids rather than the entity, so the listener reloads what it needs ([tenantId] is set because it runs on a thread that
 * has none).
 */
data class VisitFeedbackSentEvent(
    val visitId: Long,
    val tenantId: Long,
)
