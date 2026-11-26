package com.wutsi.ndopify.agent.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AgentAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val principal = mock<JwtPrincipal>()
    private val service = AgentAccessService(callerService)
    private val agent = AgentEntity(id = 10, party = PartyEntity(id = 100))

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin may update any agent`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanUpdate(agent)
    }

    @Test
    fun `agent may update itself`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        service.ensureCanUpdate(agent)
    }

    @Test
    fun `another agent may not update it`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        assertDenied { service.ensureCanUpdate(agent) }
    }

    @Test
    fun `caller without a party is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureCanUpdate(agent) }
    }

    @Test
    fun `agent without a party id is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureCanUpdate(AgentEntity(id = 11, party = PartyEntity())) }
    }
}
