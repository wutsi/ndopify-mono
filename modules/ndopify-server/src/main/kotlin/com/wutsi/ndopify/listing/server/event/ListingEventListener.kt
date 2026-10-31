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
 * Tells the agent of a listing that it's published (not the customer, for now), once the publication is committed, so no e-mail
 * goes out for a rolled-back one. A failure is only logged: the listing is already published, and isn't sent again.
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

            val sent = service.sendPublishedEmails(event.listingId)
            logger.add("email_sent", sent)
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
