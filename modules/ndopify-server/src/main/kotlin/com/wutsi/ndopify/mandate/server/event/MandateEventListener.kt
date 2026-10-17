package com.wutsi.ndopify.mandate.server.event

import com.wutsi.ndopify.contract.server.event.ContractSignedEvent
import com.wutsi.ndopify.mandate.server.service.MandateDispatcher
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Offers a new mandate to an agent (a declined or expired one is re-offered by `AssignmentEventListener`), and approves
 * a mandate once its contract is signed.
 *
 * The offer runs on an executor thread once the mandate is committed, so a dispatch failure never fails, nor rolls back, the
 * customer's mandate: it's only logged, and `MandateJobs.dispatch` retries the mandate later.
 * That thread has no request: the tenant and a logger are set on it for the duration of the call, and removed
 * afterwards since the executor threads are reused.
 */
@Component
class MandateEventListener(
    private val dispatcher: MandateDispatcher,
    private val service: MandateService,
) {
    /**
     * Synchronous, inside the signature's transaction: the mandate is APPROVED if, and only if, its contract is SIGNED
     * (a failure here rolls back the last signature too). Lets the contract domain stay unaware of mandates.
     */
    @EventListener
    fun onContractSigned(event: ContractSignedEvent) {
        service.onContractSigned(event.contractId)
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMandateCreated(event: MandateCreatedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", MandateCreatedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("mandate_id", event.mandateId)

            val assignment = dispatcher.dispatch(event.mandateId)
            logger.add("assignment_id", assignment?.id)
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
