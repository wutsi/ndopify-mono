package com.wutsi.ndopify.agent.server.event

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.platform.tenant.TenantContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentTelegramCodeEventListenerTest {
    private val service = mock<AgentService>()
    private val listener = AgentTelegramCodeEventListener(service)

    @Test
    fun `the email is sent with the event's tenant and the context is cleared`() {
        var tenantDuringCall: Long? = null
        doAnswer {
            tenantDuringCall = TenantContext.get()
            null
        }.whenever(service).sendTelegramCode(any(), any(), any())

        listener.onAgentTelegramCodeCreated(AgentTelegramCodeCreatedEvent(2L, 10L, "123456", 15))

        verify(service).sendTelegramCode(10L, "123456", 15)
        assertEquals(2L, tenantDuringCall)
        assertNull(TenantContext.get())
    }

    @Test
    fun `a failure is logged, not thrown`() {
        doThrow(IllegalStateException("down")).whenever(service).sendTelegramCode(any(), any(), any())

        listener.onAgentTelegramCodeCreated(AgentTelegramCodeCreatedEvent(2L, 10L, "123456", 15))

        assertNull(TenantContext.get())
    }
}
