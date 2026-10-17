package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `MandateService.close` (CLOSED) and `MandateService.cancel` (CANCELLED), inside their transaction, when
 * a mandate ends. Carries ids rather than the entity, so listeners reload what they need (status, age, final price,
 * cancellation code). [tenantId] is the mandate's tenant, since listeners run on a thread that has none. No listener
 * yet: notifications plug into this event, as an `@Async @TransactionalEventListener(AFTER_COMMIT)`, so nothing fires
 * for a rolled-back closing.
 */
data class MandateClosedEvent(
    val mandateId: Long,
    val tenantId: Long,
)
