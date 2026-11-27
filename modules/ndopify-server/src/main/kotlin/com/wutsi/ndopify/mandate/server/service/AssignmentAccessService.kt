package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.dto.SearchAssignmentRequest
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may read an assignment (a mandate's offer to an agent), which is the offer's agent or an admin (see
 * [CallerService.isAdmin]). 401 AUTH_UNAUTHORIZED without a token, 403 ASSIGNMENT_ACCESS_DENIED otherwise. Only for
 * the API's callers: the server's own reads (Telegram callbacks, jobs) aren't checked.
 */
@Service
class AssignmentAccessService(
    private val callerService: CallerService,
    private val agentService: AgentService,
) {
    /**
     * Its agent or an admin. 401 AUTH_UNAUTHORIZED without a token, then 403 ASSIGNMENT_ACCESS_DENIED unless the
     * caller's agent (JWT user → membership → party → agent) is the offer's, with a null party id or no agent a 403
     * before the entity comparison.
     */
    fun ensureCanRead(assignment: AssignmentEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }

        val agentId = callerService.findPartyIdOrNull(principal)
            ?.let { partyId -> agentService.findByPartyIdOrNull(partyId) }
            ?.id
        if (agentId == null || assignment.agentId != agentId) {
            throw accessDenied()
        }
    }

    /**
     * The caller's agent: its user (JWT), through its membership of the current tenant, must have an agent account.
     * 401 AUTH_UNAUTHORIZED without a token, 403 ASSIGNMENT_ACCESS_DENIED without an agent.
     */
    fun callerAgentId(): Long {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
        val agent = partyId?.let { agentService.findByPartyIdOrNull(it) }
        if (agent == null) {
            throw accessDenied()
        }
        return agent.id!!
    }

    /**
     * The search the caller may run: an admin's as asked; an agent's restricted to its own offers (`agent-id` forced).
     * 401 AUTH_UNAUTHORIZED without a token, 403 ASSIGNMENT_ACCESS_DENIED without an agent, or when the request names
     * another agent.
     */
    fun restrict(request: SearchAssignmentRequest): SearchAssignmentRequest {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return request
        }

        val agentId = callerAgentId()
        if (request.agentIds.any { id -> id != agentId }) {
            throw accessDenied()
        }
        return request.copy(agentIds = listOf(agentId))
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.ASSIGNMENT_ACCESS_DENIED))
}
