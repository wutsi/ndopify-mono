package com.wutsi.ndopify.match.server.event

import com.wutsi.ndopify.mandate.server.event.ExistingSearchInterestEvent
import com.wutsi.ndopify.match.server.service.MatchInterestService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Registers a customer's interest in a listing on the search it already has ([ExistingSearchInterestEvent]), once the
 * creation is committed: the match of the pair, the agent's message and, for an approved search, its proposal.
 *
 * Runs on an executor thread once the creation is committed, so a failure never fails, nor rolls back, the customer's
 * request: it's only logged. That thread has no request: the tenant and a logger are set on it for the duration of the
 * call, and removed afterwards since the executor threads are reused.
 */
@Component
class MatchInterestEventListener(
    private val service: MatchInterestService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onExistingSearchInterest(event: ExistingSearchInterestEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", ExistingSearchInterestEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("mandate_id", event.mandateId)
            logger.add("listing_id", event.listingId)

            val matchId = service.registerInterest(event.mandateId, event.listingId)
            logger.add("match_id", matchId)
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
