package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `MandateService.create`, inside its transaction. Carries ids rather than the entity, so listeners
 * reload what they need. [tenantId] is the mandate's tenant, since the listener runs on a thread that has none.
 */
data class MandateCreatedEvent(
    val mandateId: Long,
    val tenantId: Long,
)
