package com.wutsi.ndopify.visit.server.event

/** Published by `VisitService.noShow` / `markAgentNoShows`, inside the visit's transaction: the customer caused it, or the agent. */
data class VisitNoShowEvent(
    val visitId: Long,
    val tenantId: Long,
)
