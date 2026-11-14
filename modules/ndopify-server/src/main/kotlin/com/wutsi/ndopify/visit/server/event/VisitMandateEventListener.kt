package com.wutsi.ndopify.visit.server.event

import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.visit.server.service.VisitService
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class VisitMandateEventListener(
    private val service: VisitService,
) {
    /**
     * Synchronous, inside the mandate's closing (or cancelling) transaction, like `ListingMandateEventListener`: a
     * mandate never commits with an open visit it just ended. `CHECKED_IN` visits are left alone.
     */
    @EventListener
    fun onMandateClosed(event: MandateClosedEvent) {
        service.cancelForEndedMandate(event.mandateId)
    }
}
