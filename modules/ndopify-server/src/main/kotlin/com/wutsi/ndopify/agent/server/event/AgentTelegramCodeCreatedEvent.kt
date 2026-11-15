package com.wutsi.ndopify.agent.server.event

/**
 * Published by [com.wutsi.ndopify.agent.server.service.AgentTelegramService.requestCode], inside its transaction.
 * [code] is the clear 6-digit code, so [toString] masks it: it must never end up in a log. [tenantId] is the
 * request's tenant, since the listener runs on a thread that has none.
 */
data class AgentTelegramCodeCreatedEvent(
    val tenantId: Long,
    val agentId: Long,
    val code: String,
    val ttlMinutes: Long,
) {
    override fun toString() =
        "AgentTelegramCodeCreatedEvent(tenantId=$tenantId, agentId=$agentId, code=******, ttlMinutes=$ttlMinutes)"
}
