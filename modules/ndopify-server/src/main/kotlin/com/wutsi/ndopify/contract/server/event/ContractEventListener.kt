package com.wutsi.ndopify.contract.server.event

import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class ContractEventListener(
    private val service: ContractService,
) {
    /**
     * E-mails the parties the contract to sign, once the request is committed, so no e-mail goes out for a rolled-back
     * request, and the caller doesn't wait for SMTP. That thread has no request: the tenant and a logger are set on it
     * for the duration of the call, and removed afterwards since the executor threads are reused. A failure is only
     * logged — the contract is already PENDING_SIGNATURE.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onSignatureRequested(event: ContractSignatureRequestedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", ContractSignatureRequestedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("contract_id", event.contractId)

            val sent = service.sendSignatureRequestEmails(event.contractId)
            logger.add("email_count", sent)
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
