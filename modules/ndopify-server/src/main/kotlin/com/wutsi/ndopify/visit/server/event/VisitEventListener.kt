package com.wutsi.ndopify.visit.server.event

import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.visit.server.service.VisitService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * The visit domain's asynchronous work: the e-mails, once the change is committed. The executor thread has no request,
 * so the tenant and a logger are set on it for the duration of the call, and removed afterwards since the threads are
 * reused. A failure is logged, never rethrown.
 */
@Component
class VisitEventListener(
    private val service: VisitService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onVisitRequested(event: VisitRequestedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", VisitRequestedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("visit_id", event.visitId)

            service.sendRequestedEmail(event.visitId)
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
