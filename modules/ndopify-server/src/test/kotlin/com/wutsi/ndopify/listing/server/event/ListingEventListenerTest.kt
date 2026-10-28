package com.wutsi.ndopify.listing.server.event

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingEventListenerTest {
    private val service = mock<ListingService>()
    private val listener = ListingEventListener(service)

    @Test
    fun `published - the e-mails are sent, in its tenant`() {
        var tenantId: Long? = null
        doAnswer {
            tenantId = TenantContext.get()
            true
        }.whenever(service).sendPublishedEmails(10L)

        listener.onListingPublished(ListingPublishedEvent(listingId = 10L, tenantId = 7L))

        verify(service).sendPublishedEmails(10L)
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get()) // Removed: the executor threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `published - a failure is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(service).sendPublishedEmails(10L)

        listener.onListingPublished(ListingPublishedEvent(listingId = 10L, tenantId = 7L))

        assertNull(TenantContext.get())
        assertNull(KVLoggerThreadLocal.get())
    }
}
