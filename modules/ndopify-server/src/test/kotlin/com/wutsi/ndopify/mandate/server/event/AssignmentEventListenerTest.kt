package com.wutsi.ndopify.mandate.server.event

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.mandate.server.service.MandateDispatcher
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AssignmentEventListenerTest {
    private val service = mock<AssignmentService>()
    private val dispatcher = mock<MandateDispatcher>()
    private val listener = AssignmentEventListener(service, dispatcher)

    private var tenantId: Long? = null

    private fun assertThreadCleaned() {
        assertNull(TenantContext.get()) // Removed: the executor threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    // --- Closed (decline, expiry)

    @Test
    fun `closed - the mandate is dispatched, in its tenant`() {
        doAnswer {
            tenantId = TenantContext.get()
            AssignmentEntity(id = 2L)
        }.whenever(dispatcher).dispatch(10L)

        listener.onAssignmentClosed(AssignmentClosedEvent(assignmentId = 1L, mandateId = 10L, tenantId = 7L))

        verify(dispatcher).dispatch(10L)
        assertEquals(7L, tenantId)
        assertThreadCleaned()
    }

    @Test
    fun `closed - a failing dispatch is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(dispatcher).dispatch(10L)

        listener.onAssignmentClosed(AssignmentClosedEvent(assignmentId = 1L, mandateId = 10L, tenantId = 7L))

        assertThreadCleaned()
    }

    // --- Created: offer e-mail

    @Test
    fun `created - the agent is e-mailed, in its tenant`() {
        doAnswer {
            tenantId = TenantContext.get()
            Unit
        }.whenever(service).sendOfferEmail(1L)

        listener.onAssignmentCreated(AssignmentCreatedEvent(assignmentId = 1L, tenantId = 7L))

        verify(service).sendOfferEmail(1L)
        assertEquals(7L, tenantId)
        assertThreadCleaned()
    }

    @Test
    fun `created - a failing e-mail is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(service).sendOfferEmail(1L)

        listener.onAssignmentCreated(AssignmentCreatedEvent(assignmentId = 1L, tenantId = 7L))

        assertThreadCleaned()
    }

    // --- Accepted: e-mail to the customer

    @Test
    fun `accepted - the customer is e-mailed, in its tenant`() {
        doAnswer {
            tenantId = TenantContext.get()
            Unit
        }.whenever(service).sendAssignedEmail(1L)

        listener.onAssignmentAccepted(AssignmentAcceptedEvent(assignmentId = 1L, tenantId = 7L))

        verify(service).sendAssignedEmail(1L)
        assertEquals(7L, tenantId)
        assertThreadCleaned()
    }

    @Test
    fun `accepted - a failing e-mail is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(service).sendAssignedEmail(1L)

        listener.onAssignmentAccepted(AssignmentAcceptedEvent(assignmentId = 1L, tenantId = 7L))

        assertThreadCleaned()
    }
}
