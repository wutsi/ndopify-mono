package com.wutsi.ndopify.agent.server.endpoint

import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.dto.SearchAgentResponse
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.mapper.AgentMapper
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.security.server.service.CallerService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
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
    private val callerService: CallerService,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): GetAgentResponse {
        val agent = service.findById(id)
        return GetAgentResponse(agent = mapper.toAgent(agent))
    }

    /**
     * The caller's own agent in the current tenant, resolved from the access token: 404 AGENT_NOT_FOUND when the
     * caller has no party, or its party isn't an agent.
     */
    @GetMapping("/me")
    fun me(): GetAgentResponse {
        val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
            ?: throw NotFoundException(error = Error(code = ErrorCode.AGENT_NOT_FOUND))
        val agent = service.findByPartyIdOrNull(partyId)
            ?: throw NotFoundException(error = Error(code = ErrorCode.AGENT_NOT_FOUND))
        return GetAgentResponse(agent = mapper.toAgent(agent))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchAgentRequest,
    ): SearchAgentResponse {
        val agents = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchAgentResponse(agents = agents.map { agent -> mapper.toAgentSummary(agent) })
    }

    @PostMapping
    fun create(
        @RequestBody @Valid request: CreateAgentRequest,
    ): CreateAgentResponse {
        val agent = service.create(request)
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
