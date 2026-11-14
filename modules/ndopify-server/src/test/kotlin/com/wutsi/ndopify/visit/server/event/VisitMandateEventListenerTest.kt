package com.wutsi.ndopify.visit.server.event

import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.visit.server.service.VisitService
import kotlin.test.Test
import kotlin.test.assertNull

class VisitMandateEventListenerTest {
    private val service = mock<VisitService>()
    private val listener = VisitMandateEventListener(service)

    @Test
    fun `the ended mandate is cancelled and the tenant is cleared`() {
        listener.onMandateClosed(MandateClosedEvent(mandateId = 1L, tenantId = 7L))

        verify(service).cancelForEndedMandate(1L)
        assertNull(TenantContext.get())
    }

    @Test
    fun `a failure is logged, never rethrown, and the tenant is cleared`() {
        doThrow(RuntimeException("boom")).whenever(service).cancelForEndedMandate(1L)

        listener.onMandateClosed(MandateClosedEvent(mandateId = 1L, tenantId = 7L))

        assertNull(TenantContext.get())
    }
}
