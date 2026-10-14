package com.wutsi.ndopify.agent.server.event

/**
 * Published by `AgentService.create`, inside its transaction. Carries ids rather than the entity, so listeners
 * reload what they need instead of relying on a detached entity. [tenantId] is the agent's tenant, since the
 * listener runs on a thread that has none.
 */
data class AgentCreatedEvent(
    val agentId: Long,
    val tenantId: Long,
)
