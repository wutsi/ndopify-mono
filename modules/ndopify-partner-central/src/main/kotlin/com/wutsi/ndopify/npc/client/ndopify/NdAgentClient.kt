package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server agent endpoints (`/v1/agents`).
 * Agents are tenant-scoped: the `X-Tenant-ID` header (see `RestConfiguration`) decides which tenant a new agent
 * joins.
 */
@Service
class NdAgentClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /**
     * Registers an agent: the server creates its party, user, payment method and KYC case, then e-mails a welcome.
     * Fails with a 409 `AGENT_ALREADY_EXISTS` when [CreateAgentRequest.email] already belongs to an agent.
     */
    fun create(request: CreateAgentRequest): CreateAgentResponse =
        rest.post()
            .uri("/v1/agents")
            .body(request)
            .retrieve()
            .body<CreateAgentResponse>()
            ?: CreateAgentResponse()
}
