package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server assignment endpoints (`/v1/assignments`): the offers of a mandate to an agent.
 * Accept and decline are only for the offer's agent: ndopify-server checks it from the `Authorization` token.
 */
@Service
class NdAssignmentClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The agent's open offers, newest first. */
    fun pending(agentId: Long): List<AssignmentSummary> =
        rest.get()
            .uri("/v1/assignments?agent-id={agentId}&status={status}", agentId, AssignmentStatus.PENDING)
            .retrieve()
            .body<SearchAssignmentResponse>()
            ?.assignments
            ?: emptyList()

    fun accept(id: Long) {
        rest.post().uri("/v1/assignments/{id}/accept", id).retrieve().toBodilessEntity()
    }

    fun decline(id: Long) {
        rest.post().uri("/v1/assignments/{id}/decline", id).retrieve().toBodilessEntity()
    }
}
