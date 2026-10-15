package com.wutsi.ndopify.party.server.event

import com.wutsi.ndopify.party.dto.KycStatus

/**
 * Published by `KycService.verify` when a case is resolved (VERIFIED or REJECTED), inside the transaction that
 * completes it. Carries ids rather than the entity; [tenantId] is the case's tenant, since the asynchronous listeners
 * run on a thread that has none. [partyId] and [status] let other domains (ex: agent) react without reloading the case.
 */
data class KycCaseCompletedEvent(
    val caseId: String,
    val tenantId: Long,
    val partyId: Long,
    val status: KycStatus,
)
