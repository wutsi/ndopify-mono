package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `AssignmentService`, inside its transaction, when a PENDING offer is DECLINED (or EXPIRED): its
 * mandate is then free to be offered to the next agent. Carries ids rather than the entity, so listeners reload what
 * they need. [tenantId] is the assignment's tenant, since listeners run on a thread that has none.
 */
data class AssignmentClosedEvent(
    val assignmentId: Long,
    val mandateId: Long,
    val tenantId: Long,
)
