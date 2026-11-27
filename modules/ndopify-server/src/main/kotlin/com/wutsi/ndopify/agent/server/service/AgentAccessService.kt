package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may change an agent's profile, or search by mobile-money number: the agent itself (JWT user → membership →
 * party is the agent's party) or an admin ([CallerService.isAdmin]) for a change; an admin only for the mobile-money
 * search. 401 AUTH_UNAUTHORIZED without a token, 403 HTTP_ACCESS_DENIED otherwise.
 */
@Service
class AgentAccessService(private val callerService: CallerService) {
    fun ensureCanUpdate(agent: AgentEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.isAdmin(principal)) {
            return
        }

        val partyId = callerService.findPartyIdOrNull(principal)
        if (agent.party.id == null || partyId != agent.party.id) {
            throw accessDenied()
        }
    }

    /**
     * The mobile-money search is admin-only: it reveals which party holds the number. The other filters stay open to
     * any signed-in caller. 401 AUTH_UNAUTHORIZED without a token, 403 HTTP_ACCESS_DENIED otherwise.
     */
    fun ensureCanSearchByMobileMoneyNumber(request: SearchAgentRequest) {
        if (request.mobileMoneyNumber == null) {
            return
        }

        val principal = callerService.getPrincipal()
        if (!callerService.isAdmin(principal)) {
            throw accessDenied()
        }
    }

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
}
