package com.wutsi.ndopify.listing.server.event

import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Tells the agent of a listing that it's published (not the customer, for now), once the publication is committed, so
 * no notification goes out for a rolled-back one, and that it was sent back to DRAFT for correction once the send-back
 * is committed. A failure is only logged: the listing is already published, or sent back, and isn't notified again.
 * The executor thread has no request: the tenant and a logger are set on it for the duration of the call, and removed
 * afterwards since the executor threads are reused.
 */
@Component
class ListingEventListener(
    private val service: ListingService,
) {
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

            val sent = service.sendPublishedNotification(event.listingId)
            logger.add("notification_sent", sent)
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

    /**
     * Tells the agent of a listing sent back to DRAFT that it must be corrected, once the send-back is committed. The
     * same tenant / logger handling as the publication, and a failure is only logged: the listing is already sent
     * back, and the service's guard means a resubmitted one isn't notified.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onListingSentBack(event: ListingSentBackEvent) {
        val logger = DefaultKVLogger()
        KVLoggerThreadLocal.set(logger)
        TenantContext.set(event.tenantId)
        try {
            logger.add("event", ListingSentBackEvent::class.java.simpleName)
            logger.add("tenant_id", event.tenantId)
            logger.add("listing_id", event.listingId)

            val sent = service.sendSentBackNotification(event.listingId)
            logger.add("notification_sent", sent)
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
