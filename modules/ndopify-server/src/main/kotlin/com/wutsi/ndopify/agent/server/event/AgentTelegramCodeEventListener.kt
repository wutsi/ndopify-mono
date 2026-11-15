package com.wutsi.ndopify.agent.server.event

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AgentTelegramCodeEventListener(
    private val agentService: AgentService,
) {
    /**
     * Runs on an executor thread once the code is committed, so the request doesn't wait for SMTP. That thread has
     * no request: the tenant and a logger are set on it for the duration of the call, and removed afterwards since
     * the executor threads are reused. A failure is logged — the caller already got its response.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAgentTelegramCodeCreated(event: AgentTelegramCodeCreatedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", AgentTelegramCodeCreatedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("agent_id", event.agentId)

            agentService.sendTelegramCode(event.agentId, event.code, event.ttlMinutes)
            logger.add("success", true)
        } catch (ex: Exception) {
            logger.add("success", false)
            logger.setException(ex)
        } finally {
            logger.log()
            TenantContext.remove()
            KVLoggerThreadLocal.remove()
        }
    }
}
