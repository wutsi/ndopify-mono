package com.wutsi.ndopify.listing.server.event

import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class ListingMandateEventListener(
    private val service: ListingService,
) {
    /**
     * Synchronous, inside the mandate's closing (or cancelling) transaction: the listing's status commits (or rolls
     * back) with the mandate's, so a closed mandate never leaves its listing on the market. Lets the mandate domain
     * stay unaware of listings. A failure here (ex: a concurrent change of the listing, `@Version`) fails the closing.
     */
    @EventListener
    fun onMandateClosed(event: MandateClosedEvent) {
        service.onMandateClosed(event.mandateId)
    }
}
