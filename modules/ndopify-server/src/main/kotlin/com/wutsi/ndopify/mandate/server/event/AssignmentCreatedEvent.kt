package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `MandateDispatcher.dispatch`, inside its transaction, when a mandate is offered to an agent. Carries ids
 * rather than the entity, so listeners reload what they need. [tenantId] is the assignment's tenant, since listeners
 * run on a thread that has none. Its listener (notifying the agent) must be `@TransactionalEventListener(AFTER_COMMIT)`.
 */
data class AssignmentCreatedEvent(
    val assignmentId: Long,
    val tenantId: Long,
)
