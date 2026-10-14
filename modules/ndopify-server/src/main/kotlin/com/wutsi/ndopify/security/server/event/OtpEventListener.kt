package com.wutsi.ndopify.security.server.event

import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.security.server.service.OtpService
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class OtpEventListener(
    private val service: OtpService,
) {
    /**
     * Runs on an executor thread once the OTP is committed, so the request doesn't wait for SMTP. That thread has
     * no request: the tenant and a logger are set on it for the duration of the call, and removed afterwards since
     * the executor threads are reused. A failure is logged — the caller already got its response.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOtpCreated(event: OtpCreatedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", OtpCreatedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("auth_factor_id", event.authFactorId)

            service.sendEmail(event.authFactorId, event.code)
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
