package com.wutsi.ndopify.match.server.event

/**
 * Published by `MatchProposalService.propose`, inside its transaction, when the agent proposes matches to its customer
 * ([matchIds], of one mandate, sent in one e-mail). Carries ids rather than the entities, so the listener reloads what
 * it needs. [tenantId] is the matches' tenant, since the listener runs on a thread that has none.
 */
data class MatchProposedEvent(
    val matchIds: List<Long>,
    val tenantId: Long,
)
