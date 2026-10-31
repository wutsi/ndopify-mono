package com.wutsi.ndopify.match.server.event

/**
 * Published by `MatchProposalService.propose`, inside its transaction, when the agent proposes a match to its customer.
 * Carries ids rather than the entity, so the listener reloads what it needs. [tenantId] is the match's tenant, since
 * the listener runs on a thread that has none.
 */
data class MatchProposedEvent(
    val matchId: Long,
    val tenantId: Long,
)
