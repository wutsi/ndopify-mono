package com.wutsi.ndopify.mandate.server.event

/**
 * Published by `MandateService.onContractSigned`, inside the signature's transaction, when a mandate is APPROVED (its
 * contract is signed). Carries ids rather than the entity, so listeners reload what they need. [tenantId] is the
 * mandate's tenant, since listeners run on a thread that has none.
 */
data class MandateApprovedEvent(
    val mandateId: Long,
    val tenantId: Long,
)
