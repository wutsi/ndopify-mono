package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.dto.SearchAgentResponse
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.mapper.AgentMapper
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.platform.logger.KVLogger
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/agents")
class AgentEndpoints(
    private val service: AgentService,
    private val mapper: AgentMapper,
    private val logger: KVLogger
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): GetAgentResponse {
        val agent = service.findById(id)
        return GetAgentResponse(agent = mapper.toAgent(agent))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchAgentRequest,
    ): SearchAgentResponse {
        val agents = service.search(request)
        return SearchAgentResponse(agents = agents.map { agent -> mapper.toAgentSummary(agent) })
    }

    @PostMapping
    fun create(
        @RequestBody @Valid request: CreateAgentRequest,
    ): CreateAgentResponse {
        val agent = service.create(request)
        if (agent.party.kycStatus == KycStatus.PENDING) {
            try {
                service.sendWelcomeEmail(agent)
            } catch (ex: Exception) {
                logger.add("kyc_email_error", ex.message)
            }
        }
        return CreateAgentResponse(agentId = agent.id ?: -1)
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateAgentRequest,
    ) {
        service.update(id, request)
    }
}
