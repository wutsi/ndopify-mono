package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `MandateDispatcher.dispatch`, inside its transaction, when a mandate is moved to MANUAL_ASSIGNMENT.
 * Carries ids rather than the entity, so listeners reload what they need. [tenantId] is the mandate's tenant, since
 * listeners run on a thread that has none. No listener yet: alerting (ops, customer) plugs into this event, as an
 * `@Async @TransactionalEventListener(AFTER_COMMIT)`, so nothing fires for a rolled-back dispatch.
 */
data class MandateManualAssignmentEvent(
    val mandateId: Long,
    val tenantId: Long,
)
