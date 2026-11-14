package com.wutsi.ndopify.visit.server.event

import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.visit.server.service.VisitService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Cancels the open visits once the mandate's closing (or cancelling) transaction has committed. Asynchronous, so a
 * visit failure can neither roll the closing back nor slow it; `VisitJobs.cancelEndedMandates` sweeps the visits this
 * listener misses (a failed task, a crash, a closing before the listener existed). `CHECKED_IN` visits are left alone.
 */
@Component
class VisitMandateEventListener(
    private val service: VisitService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMandateClosed(event: MandateClosedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", MandateClosedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("mandate_id", event.mandateId)
            logger.add("count", service.cancelForEndedMandate(event.mandateId))
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
