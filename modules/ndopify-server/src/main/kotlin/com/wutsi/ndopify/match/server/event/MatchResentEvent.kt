package com.wutsi.ndopify.match.server.event

/**
 * Published by `MatchProposalService.resend`, inside its transaction, when the agent asks for the match's link to be
 * e-mailed again. Carries ids rather than the entity, so the listener reloads what it needs. [tenantId] is the match's
 * tenant, since the listener runs on a thread that has none.
 */
data class MatchResentEvent(
    val matchId: Long,
    val tenantId: Long,
)
