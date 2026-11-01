package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.SearchMandateRequest
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may see or change a mandate, which holds its customer's data: an admin (see [CallerService.isAdmin]), or the
 * agent it's assigned to. The agent it's offered to (PENDING assignment) may also see it, to decide. 401 without a
 * token, 403 MANDATE_ACCESS_DENIED otherwise. Only for the API's callers: the server's own reads (dispatch, listeners,
 * jobs) aren't checked.
 */
@Service
class MandateAccessService(
    private val callerService: CallerService,
    private val agentService: AgentService,
    private val assignmentDao: AssignmentRepository,
    private val customerService: CustomerService,
) {
    fun ensureCanRead(mandate: MandateEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }

        val agentId = findAgentId(principal)
        val offered = agentId != null &&
            assignmentDao.existsByMandateAndAgentIdAndStatus(mandate, agentId, AssignmentStatus.PENDING)
        if (agentId == null || (mandate.agentId != agentId && !offered)) {
            throw accessDenied()
        }
    }

    /**
     * As [ensureCanRead], plus the mandate's customer itself (see [ensureIsCustomer]), for what the customer reads
     * from its match proposal page: the mandate and its proposed matches. Returns true when the caller is allowed only
     * as the customer (not an admin nor an agent who could read it anyway), so the caller can limit what it shows.
     */
    fun ensureCanReadAsCustomerOrAgent(mandate: MandateEntity): Boolean {
        try {
            ensureCanRead(mandate)
            return false
        } catch (ex: ForbiddenException) {
            if (isCustomer(mandate)) {
                return true
            }
            throw ex
        }
    }

    fun ensureCanUpdate(mandate: MandateEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }

        val agentId = findAgentId(principal)
        if (agentId == null || mandate.agentId != agentId) {
            throw accessDenied()
        }
    }

    /**
     * For what only the mandate's customer itself may do (ex: answer a match its agent proposed): the caller's party
     * must be the customer's, whatever its application. No admin bypass, and the agent can't act for its customer.
     */
    fun ensureIsCustomer(mandate: MandateEntity) {
        if (!isCustomer(mandate)) {
            throw accessDenied()
        }
    }

    /**
     * The search the caller may run: an admin's as is, an agent's restricted to its own mandates, or to those offered
     * to it (`offered-to`, PENDING offers: the ones [ensureCanRead] lets it see). 403 if it asks for another agent's.
     */
    fun restrict(request: SearchMandateRequest): SearchMandateRequest {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return request
        }

        val agentId = findAgentId(principal) ?: throw accessDenied()
        if (request.agentIds.any { id -> id != agentId } || (request.offeredTo != null && request.offeredTo != agentId)) {
            throw accessDenied()
        }
        return if (request.offeredTo != null) request else request.copy(agentIds = listOf(agentId))
    }

    private fun isCustomer(mandate: MandateEntity): Boolean {
        val principal = callerService.getPrincipal()
        val partyId = callerService.findPartyIdOrNull(principal)
        val customer = customerService.findByIdOrNull(mandate.customerId)
        return partyId != null && customer?.party?.id == partyId
    }

    private fun findAgentId(principal: JwtPrincipal): Long? {
        return callerService.findPartyIdOrNull(principal)
            ?.let { partyId -> agentService.findByPartyIdOrNull(partyId) }
            ?.id
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.MANDATE_ACCESS_DENIED))
}
