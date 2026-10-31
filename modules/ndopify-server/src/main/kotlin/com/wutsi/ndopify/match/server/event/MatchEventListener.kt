package com.wutsi.ndopify.match.server.event

import com.wutsi.ndopify.listing.server.event.ListingPublishedEvent
import com.wutsi.ndopify.mandate.server.event.MandateApprovedEvent
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Matches a search mandate once it's APPROVED, and a listing once it's PUBLISHED, against the agent's inventory / open
 * searches. Lets the mandate and listing domains stay unaware of matches.
 *
 * Runs on an executor thread once the approval / publication is committed, so a matching failure never fails, nor
 * rolls back, it: it's only logged (`POST /v1/matches/run` re-runs a mandate). That thread has no request: the tenant
 * and a logger are set on it for the duration of the call, and removed afterwards since the executor threads are
 * reused.
 */
@Component
class MatchEventListener(
    private val service: MatchService,
) {
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onMandateApproved(event: MandateApprovedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", MandateApprovedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("mandate_id", event.mandateId)

            val count = service.matchMandate(event.mandateId)
            logger.add("match_count", count)
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

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onListingPublished(event: ListingPublishedEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", ListingPublishedEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("listing_id", event.listingId)

            val count = service.matchListing(event.listingId)
            logger.add("match_count", count)
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
