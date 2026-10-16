package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.CreateAgentResponse
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import com.wutsi.ndopify.agent.dto.SearchAgentResponse
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
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

    /** The agent of [partyId], with its whole profile; null when the party isn't an agent. */
    fun findByPartyId(partyId: Long): Agent? {
        val summary = rest.get()
            .uri { uri -> uri.path("/v1/agents").queryParam("party-id", partyId).queryParam("limit", 1).build() }
            .retrieve()
            .body<SearchAgentResponse>()
            ?.agents
            ?.firstOrNull()
            ?: return null
        return get(summary.id)
    }

    fun get(id: Long): Agent =
        (rest.get().uri("/v1/agents/{id}", id).retrieve().body<GetAgentResponse>() ?: GetAgentResponse()).agent

    /** Updates the agent's profile: a null field is left unchanged, so an empty biography is sent as `""`. */
    fun update(id: Long, request: UpdateAgentRequest) {
        rest.post()
            .uri("/v1/agents/{id}", id)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
