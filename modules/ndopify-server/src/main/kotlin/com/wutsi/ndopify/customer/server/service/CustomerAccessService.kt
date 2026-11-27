package com.wutsi.ndopify.customer.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.dto.SearchCustomerRequest
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may read a customer, which holds its contact and KYC-linked data: the customer itself, the agent of one of its
 * assigned mandates, or an admin (see [CallerService.isAdmin]). A non-admin's search is narrowed to those customers.
 * 401 AUTH_UNAUTHORIZED without a token, 403 HTTP_ACCESS_DENIED otherwise. Only for the API's callers: the server's own
 * reads don't call this.
 */
@Service
class CustomerAccessService(
    private val callerService: CallerService,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val mandateLookup: CustomerMandateLookup,
) {
    fun ensureCanRead(customer: CustomerEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }

        val partyId = callerService.findPartyIdOrNull(principal)
        if (customer.party.id != null && customer.party.id == partyId) {
            return
        }

        // A caller without a party isn't an agent: 403 before the agent lookup, never a repository call with a null id
        if (partyId == null) {
            throw accessDenied()
        }

        val agent = agentService.findByPartyIdOrNull(partyId)
        val customerId = customer.id
        if (agent?.id != null && customerId != null && mandateLookup.isCustomerOfAgent(customerId, agent.id)) {
            return
        }
        throw accessDenied()
    }

    /**
     * The search a caller may run: an admin's as is; a customer's narrowed to itself, an agent's to the customers of
     * its mandates (any status). Null when the caller may read no customer, so the endpoint answers an empty list rather
     * than a 403 for a query. 403 for a caller without a party. `party-id` is left to the query and only narrows.
     */
    fun restrict(request: SearchCustomerRequest): SearchCustomerRequest? {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return request
        }

        val partyId = callerService.findPartyIdOrNull(principal)
            ?: throw accessDenied()

        val allowed = mutableListOf<Long>()
        customerService.findByPartyIdOrNull(partyId)?.id?.let { allowed.add(it) }
        agentService.findByPartyIdOrNull(partyId)?.id?.let { agentId ->
            allowed.addAll(mandateLookup.findCustomerIds(agentId))
        }

        if (allowed.isEmpty()) {
            return null
        }
        return if (request.ids.isEmpty()) {
            request.copy(ids = allowed.distinct())
        } else {
            val ids = request.ids.filter { it in allowed }.distinct()
            if (ids.isEmpty()) null else request.copy(ids = ids)
        }
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
}
