package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.agent.server.service.AgentTelegramService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.security.server.service.CallerService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/agents/me/telegram")
class AgentTelegramEndpoints(
    private val callerService: CallerService,
    private val agentService: AgentService,
    private val telegramService: AgentTelegramService,
) {
    /**
     * E-mails the caller's agent a connection code. 404 AGENT_NOT_FOUND when the caller has no agent, and 409
     * AGENT_TELEGRAM_ALREADY_CONNECTED when its chat is already linked (no e-mail then).
     */
    @PostMapping("/code")
    fun requestCode() {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
            ?: throw NotFoundException(Error(ErrorCode.AGENT_NOT_FOUND))
        val agent = agentService.findByPartyIdOrNull(partyId)
            ?: throw NotFoundException(Error(ErrorCode.AGENT_NOT_FOUND))
        telegramService.requestCode(agent)
    }
}
