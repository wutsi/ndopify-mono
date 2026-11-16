package com.wutsi.ndopify.match.server.event

import com.wutsi.ndopify.match.dto.MatchStatus

/**
 * Published by `MatchProposalService.accept` / `reject`, inside their transaction, when the customer answers a proposed
 * match: [status] is ACCEPTED or REJECTED, final. Carries ids rather than the entities, so the listener reloads what it
 * needs. [tenantId] is the match's tenant, since the listener runs on a thread that has none.
 */
data class MatchAnsweredEvent(
    val matchId: Long,
    val mandateId: Long,
    val tenantId: Long,
    val status: MatchStatus,
)
