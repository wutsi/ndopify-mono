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
class AgentEventListener(
    private val service: AgentService,
) {
    /**
     * Runs on an executor thread once the agent is committed, so no email goes out for a rolled-back registration
     * and the request doesn't wait for SMTP. That thread has no request: the tenant and a logger are set on it for
     * the duration of the call, and removed afterwards since the executor threads are reused. A failure is only
     * logged — the agent is already saved.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAgentCreated(event: AgentCreatedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", AgentCreatedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("agent_id", event.agentId)

            service.sendWelcomeEmail(event.agentId)
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
