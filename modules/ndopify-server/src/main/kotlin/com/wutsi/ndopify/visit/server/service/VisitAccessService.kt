package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import com.wutsi.ndopify.visit.dto.SearchVisitRequest
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Who may see or act on a visit: its customer, its agent, or an admin (read and schedule only). The customer alone
 * requests, gives its feedback and cancels as itself; the agent alone checks in, marks a no-show and checks out (no
 * admin bypass). 401 without a token, 403 VISIT_ACCESS_DENIED otherwise. Only for the API's callers: the server's own
 * reads (jobs, listeners) aren't checked.
 */
@Service
class VisitAccessService(
    private val callerService: CallerService,
    private val agentService: AgentService,
    private val customerService: CustomerService,
    private val partyService: PartyService,
) {
    fun ensureCanRead(visit: VisitEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }
        val partyId = callerService.findPartyIdOrNull(principal)
        if (partyId == null || (!isAgent(partyId, visit) && !isCustomer(partyId, visit))) {
            throw accessDenied()
        }
    }

    /** The visit's customer only: its party, whatever the application. No admin bypass, the agent can't act for it. */
    fun ensureIsCustomer(visit: VisitEntity) {
        if (!isCustomer(visit)) {
            throw accessDenied()
        }
    }

    /** The mandate's customer only (a visit isn't created yet): no admin bypass either. */
    fun ensureCanRequest(mandate: MandateEntity) {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
        if (partyId == null || customerService.findById(mandate.customerId).party.id != partyId) {
            throw accessDenied()
        }
    }

    /** The visit's agent only: check-in, no-show and check-out. No admin bypass. */
    fun ensureIsAgent(visit: VisitEntity) {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
        if (partyId == null || !isAgent(partyId, visit)) {
            throw accessDenied()
        }
    }

    /** True when the caller is the visit's customer. */
    fun isCustomer(visit: VisitEntity): Boolean {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal()) ?: return false
        return isCustomer(partyId, visit)
    }

    /**
     * The search the caller may run: an admin's as is, an agent's restricted to its own visits, a customer's to its own
     * mandates. 403 if it asks for another agent's or another customer's.
     */
    fun restrict(request: SearchVisitRequest): SearchVisitRequest {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return request
        }

        val agentId = findAgentId(principal)
        if (agentId != null) {
            if (request.agentIds.any { id -> id != agentId }) {
                throw accessDenied()
            }
            return request.copy(agentIds = listOf(agentId))
        }

        val customerId = findCustomerId(principal) ?: throw accessDenied()
        if (request.customerIds.any { id -> id != customerId }) {
            throw accessDenied()
        }
        return request.copy(customerIds = listOf(customerId))
    }

    private fun isAgent(partyId: Long, visit: VisitEntity): Boolean =
        agentService.findByPartyIdOrNull(partyId)?.id == visit.agentId

    private fun isCustomer(partyId: Long, visit: VisitEntity): Boolean =
        customerService.findByIdOrNull(visit.customerId)?.party?.id == partyId

    private fun findAgentId(principal: JwtPrincipal): Long? =
        callerService.findPartyIdOrNull(principal)
            ?.let { partyId -> agentService.findByPartyIdOrNull(partyId) }
            ?.id

    private fun findCustomerId(principal: JwtPrincipal): Long? =
        callerService.findPartyIdOrNull(principal)
            ?.let { partyId -> partyService.findByIdOrNull(partyId) }
            ?.let { party -> customerService.findByPartyOrNull(party) }
            ?.id

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.VISIT_ACCESS_DENIED))
}
