package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `AssignmentService.accept`, inside its transaction, once the agent accepted the offer and the mandate is
 * assigned to it. Carries ids rather than the entity, so listeners reload what they need. [tenantId] is the
 * assignment's tenant, since the listener runs on a thread that has none.
 */
data class AssignmentAcceptedEvent(
    val assignmentId: Long,
    val tenantId: Long,
)
