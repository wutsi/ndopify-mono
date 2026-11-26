package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.stereotype.Service

/**
 * Who may change an agent's profile: the agent itself (JWT user → membership → party is the agent's party) or an
 * admin ([CallerService.isAdmin]). 401 AUTH_UNAUTHORIZED without a token, 403 HTTP_ACCESS_DENIED otherwise.
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

    private fun accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
}
