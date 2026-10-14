package com.wutsi.ndopify.party.server.event

/**
 * Published by `KycService.verify` when a case is resolved (VERIFIED or REJECTED), inside the transaction that
 * completes it. Carries ids rather than the entity; [tenantId] is the case's tenant, since the listener runs on a
 * thread that has none.
 */
data class KycCaseCompletedEvent(
    val caseId: String,
    val tenantId: Long,
)
