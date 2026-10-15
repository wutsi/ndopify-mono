package com.wutsi.ndopify.mandate.server.event

import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class AssignmentEventListener(
    private val service: AssignmentService,
) {
    /**
     * Runs on an executor thread once the acceptance is committed, so the customer isn't told about an agent whose
     * acceptance rolled back, and the agent doesn't wait for SMTP. That thread has no request: the tenant and a logger
     * are set on it for the duration of the call, and removed afterwards since the executor threads are reused. A
     * failure is only logged — the mandate is already assigned.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAssignmentAccepted(event: AssignmentAcceptedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", AssignmentAcceptedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("assignment_id", event.assignmentId)

            service.sendAssignedEmail(event.assignmentId)
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
