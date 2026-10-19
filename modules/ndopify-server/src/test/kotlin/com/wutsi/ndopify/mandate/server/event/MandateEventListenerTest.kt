package com.wutsi.ndopify.mandate.server.event

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.server.event.ContractSignedEvent
import com.wutsi.ndopify.mandate.server.service.MandateDispatcher
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MandateEventListenerTest {
    private val dispatcher = mock<MandateDispatcher>()
    private val service = mock<MandateService>()
    private val listener = MandateEventListener(dispatcher, service)

    @Test
    fun `contract signed - the mandate is approved`() {
        listener.onContractSigned(ContractSignedEvent(contractId = "c-1", tenantId = 7L))

        verify(service).onContractSigned("c-1")
    }

    @Test
    fun `contract signed - a failure is rethrown, so the signature rolls back`() {
        doThrow(IllegalStateException("boom")).whenever(service).onContractSigned("c-1")

        assertFailsWith<IllegalStateException> {
            listener.onContractSigned(ContractSignedEvent(contractId = "c-1", tenantId = 7L))
        }
    }

    @Test
    fun `created - the mandate is dispatched, in its tenant`() {
        var tenantId: Long? = null
        doAnswer {
            tenantId = TenantContext.get()
            null
        }.whenever(dispatcher).dispatch(10L)

        listener.onMandateCreated(MandateCreatedEvent(mandateId = 10L, tenantId = 7L))

        verify(dispatcher).dispatch(10L)
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get()) // Removed: the executor threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `created - a failing dispatch is only logged, never fails the mandate`() {
        doThrow(IllegalStateException("boom")).whenever(dispatcher).dispatch(10L)

        listener.onMandateCreated(MandateCreatedEvent(mandateId = 10L, tenantId = 7L))

        assertNull(TenantContext.get())
    }

    @Test
    fun `approved - the agent is e-mailed, in its tenant`() {
        var tenantId: Long? = null
        doAnswer {
            tenantId = TenantContext.get()
            true
        }.whenever(service).sendApprovedEmail(10L)

        listener.onMandateApproved(MandateApprovedEvent(mandateId = 10L, tenantId = 7L))

        verify(service).sendApprovedEmail(10L)
        assertEquals(7L, tenantId)
        assertNull(TenantContext.get()) // Removed: the executor threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `approved - a failing e-mail is only logged, never fails the approval`() {
        doThrow(IllegalStateException("boom")).whenever(service).sendApprovedEmail(10L)

        listener.onMandateApproved(MandateApprovedEvent(mandateId = 10L, tenantId = 7L))

        assertNull(TenantContext.get())
    }
}
