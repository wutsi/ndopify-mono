package com.wutsi.ndopify.listing.server.event

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import kotlin.test.Test

class ListingMandateEventListenerTest {
    private val service = mock<ListingService>()
    private val listener = ListingMandateEventListener(service)

    @Test
    fun `closes the listing of the mandate`() {
        listener.onMandateClosed(MandateClosedEvent(mandateId = 5L, tenantId = 1L))

        verify(service).onMandateClosed(5L)
    }
}
