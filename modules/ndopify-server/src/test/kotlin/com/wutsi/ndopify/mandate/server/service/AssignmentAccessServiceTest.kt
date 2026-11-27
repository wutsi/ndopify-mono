package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.dto.SearchAssignmentRequest
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AssignmentAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val agentService = mock<AgentService>()
    private val principal = mock<JwtPrincipal>()
    private val service = AssignmentAccessService(callerService, agentService)

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin may read any assignment`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanRead(AssignmentEntity(id = 1, agentId = 20L))
    }

    @Test
    fun `the offer's agent may read it`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20L))
        service.ensureCanRead(AssignmentEntity(id = 1, agentId = 20L))
    }

    @Test
    fun `another agent may not read it`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(300L)
        whenever(agentService.findByPartyIdOrNull(300L)).thenReturn(AgentEntity(id = 30L))
        assertDenied { service.ensureCanRead(AssignmentEntity(id = 1, agentId = 20L)) }
    }

    @Test
    fun `caller without an agent may not read it`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(null)
        assertDenied { service.ensureCanRead(AssignmentEntity(id = 1, agentId = 20L)) }
    }

    @Test
    fun `caller without a party may not read it`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureCanRead(AssignmentEntity(id = 1, agentId = 20L)) }
    }

    @Test
    fun `callerAgentId - the caller's agent id`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20L))
        assertEquals(20L, service.callerAgentId())
    }

    @Test
    fun `callerAgentId - denied without an agent`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(null)
        assertDenied { service.callerAgentId() }
    }

    @Test
    fun `restrict - an admin's request is as asked`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        val request = SearchAssignmentRequest(agentIds = listOf(30L))
        assertEquals(request, service.restrict(request))
    }

    @Test
    fun `restrict - an own agent-id is kept`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20L))
        assertEquals(listOf(20L), service.restrict(SearchAssignmentRequest(agentIds = listOf(20L))).agentIds)
    }

    @Test
    fun `restrict - no agent-id is forced to the caller's agent`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20L))
        assertEquals(listOf(20L), service.restrict(SearchAssignmentRequest()).agentIds)
    }

    @Test
    fun `restrict - a foreign agent-id is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20L))
        assertDenied { service.restrict(SearchAssignmentRequest(agentIds = listOf(30L))) }
    }

    @Test
    fun `restrict - no agent is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(null)
        assertDenied { service.restrict(SearchAssignmentRequest()) }
    }
}
