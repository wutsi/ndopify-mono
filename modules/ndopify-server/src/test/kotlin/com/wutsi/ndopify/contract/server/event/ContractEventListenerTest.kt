package com.wutsi.ndopify.contract.server.event

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractEventListenerTest {
    private val service = mock<ContractService>()
    private val listener = ContractEventListener(service)

    private var tenantId: Long? = null

    private fun assertThreadCleaned() {
        assertNull(TenantContext.get()) // Removed: the executor threads are reused
        assertNull(KVLoggerThreadLocal.get())
    }

    @Test
    fun `signature requested - the parties are e-mailed, in its tenant`() {
        doAnswer {
            tenantId = TenantContext.get()
            2
        }.whenever(service).sendSignatureRequestEmails("c-1")

        listener.onSignatureRequested(ContractSignatureRequestedEvent(contractId = "c-1", tenantId = 7L))

        verify(service).sendSignatureRequestEmails("c-1")
        assertEquals(7L, tenantId)
        assertThreadCleaned()
    }

    @Test
    fun `signature requested - a failure is only logged`() {
        doThrow(IllegalStateException("boom")).whenever(service).sendSignatureRequestEmails("c-1")

        listener.onSignatureRequested(ContractSignatureRequestedEvent(contractId = "c-1", tenantId = 7L))

        assertThreadCleaned()
    }
}
