package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/** Client for the ndopify-server agent endpoint (`/v1/agents/{id}`), which needs a token but accepts any caller's. */
@Service
class NdAgentClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): Agent =
        rest.get()
            .uri("/v1/agents/{id}", id)
            .retrieve()
            .body<GetAgentResponse>()
            ?.agent
            ?: Agent()
}
