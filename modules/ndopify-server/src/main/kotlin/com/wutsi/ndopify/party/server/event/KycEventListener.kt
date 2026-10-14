package com.wutsi.ndopify.party.server.event

import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class KycEventListener(
    private val service: KycService,
) {
    /**
     * Runs on an executor thread once the case is committed, so the verification (endpoint or `KycJobs`) doesn't
     * wait for SMTP. That thread has no request: the tenant and a logger are set on it for the duration of the
     * call, and removed afterwards since the executor threads are reused. A failure is only logged — the case is
     * already resolved.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onKycCaseCompleted(event: KycCaseCompletedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", KycCaseCompletedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("kyc_case_id", event.caseId)

            service.sendStatusEmail(event.caseId)
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
