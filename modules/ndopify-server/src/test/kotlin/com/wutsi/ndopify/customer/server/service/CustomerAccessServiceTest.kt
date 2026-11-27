package com.wutsi.ndopify.customer.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.dto.SearchCustomerRequest
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CustomerAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val mandateLookup = mock<CustomerMandateLookup>()
    private val principal = mock<JwtPrincipal>()
    private val service = CustomerAccessService(callerService, customerService, agentService, mandateLookup)
    private val customer = CustomerEntity(id = 1, party = PartyEntity(id = 100))

    @BeforeEach
    fun setUp() {
        doReturn(principal).whenever(callerService).getPrincipal()
    }

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test
    fun `admin reads any customer`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureCanRead(customer)
    }

    @Test
    fun `customer reads itself`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        service.ensureCanRead(customer)
    }

    @Test
    fun `agent reads the customer of its mandate`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20))
        whenever(mandateLookup.isCustomerOfAgent(1L, 20L)).thenReturn(true)
        service.ensureCanRead(customer)
    }

    @Test
    fun `another agent is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20))
        whenever(mandateLookup.isCustomerOfAgent(1L, 20L)).thenReturn(false)
        assertDenied { service.ensureCanRead(customer) }
    }

    @Test
    fun `offered-only agent is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20))
        whenever(mandateLookup.isCustomerOfAgent(1L, 20L)).thenReturn(false)
        assertDenied { service.ensureCanRead(customer) }
    }

    @Test
    fun `caller without a party is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureCanRead(customer) }
    }

    @Test
    fun `admin search is not restricted`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        val request = SearchCustomerRequest(ids = listOf(1L, 2L))
        assertSame(request, service.restrict(request))
    }

    @Test
    fun `customer search is narrowed to itself`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        whenever(customerService.findByPartyIdOrNull(100L)).thenReturn(customer)
        assertEquals(listOf(1L), service.restrict(SearchCustomerRequest())!!.ids)
    }

    @Test
    fun `agent search is narrowed to its customers`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(customerService.findByPartyIdOrNull(200L)).thenReturn(null)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20))
        whenever(mandateLookup.findCustomerIds(20L)).thenReturn(listOf(1L, 2L))
        assertEquals(listOf(1L, 2L), service.restrict(SearchCustomerRequest())!!.ids)
    }

    @Test
    fun `foreign id is excluded`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        whenever(customerService.findByPartyIdOrNull(100L)).thenReturn(customer)
        assertNull(service.restrict(SearchCustomerRequest(ids = listOf(999L))))
    }

    @Test
    fun `caller without a party search is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.restrict(SearchCustomerRequest()) }
    }

    @Test
    fun `a party that is neither customer nor agent gets no customer`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(300L)
        whenever(customerService.findByPartyIdOrNull(300L)).thenReturn(null)
        whenever(agentService.findByPartyIdOrNull(300L)).thenReturn(null)
        assertNull(service.restrict(SearchCustomerRequest()))
    }

    @Test
    fun `request ids are intersected`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(200L)
        whenever(customerService.findByPartyIdOrNull(200L)).thenReturn(null)
        whenever(agentService.findByPartyIdOrNull(200L)).thenReturn(AgentEntity(id = 20))
        whenever(mandateLookup.findCustomerIds(20L)).thenReturn(listOf(1L, 2L))
        val request = SearchCustomerRequest(ids = listOf(1L, 2L, 3L, 2L))
        assertEquals(listOf(1L, 2L), service.restrict(request)!!.ids)
    }
}
