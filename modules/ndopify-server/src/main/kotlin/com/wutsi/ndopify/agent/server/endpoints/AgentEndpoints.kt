package com.wutsi.ndopify.agent.server.endpoints

import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.dto.SearchAgentResponse
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.dto.UpdateIdentyRequest
import com.wutsi.ndopify.agent.dto.UpdateImageRequest
import com.wutsi.ndopify.agent.dto.UpdateMobileMoneyRequest
import com.wutsi.ndopify.agent.server.mapper.AgentMapper
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.common.dto.HttpHeader
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/agents")
class AgentEndpoints(
    private val service: AgentService,
    private val mapper: AgentMapper,
) {
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): GetAgentResponse {
        val agent = service.findById(id, tenantId)
        return GetAgentResponse(agent = mapper.toAgent(agent))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchAgentRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): SearchAgentResponse {
        val agents = service.search(request, tenantId)
        return SearchAgentResponse(agents = agents.map { agent -> mapper.toAgentSummary(agent) })
    }

    @PostMapping
    fun create(
        @RequestBody @Valid request: CreateAgentRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ): CreateAgentResponse {
        val agent = service.create(request, tenantId)
        return CreateAgentResponse(agentId = agent.id ?: -1)
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateAgentRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.update(id, request, tenantId)
    }

    @PostMapping("/{id}/photo")
    fun photo(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateImageRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.updatePhoto(id, request, tenantId)
    }

    @PostMapping("/{id}/agency-logo")
    fun agencyLogo(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateImageRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.updateAgencyLogo(id, request, tenantId)
    }

    @PostMapping("/{id}/mobile-money")
    fun updateMobileMoney(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateMobileMoneyRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.updateMobileMoney(id, request, tenantId)
    }

    @PostMapping("/{id}/identity")
    fun updateIdentity(
        @PathVariable id: Long,
        @RequestBody @Valid request: UpdateIdentyRequest,
        @RequestHeader(HttpHeader.TENANT_ID) tenantId: Long,
    ) {
        service.updateIdentity(id, request, tenantId)
    }
}
