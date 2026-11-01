package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server match endpoints (`/v1/matches`), called with the customer's `public-portal` token (sent
 * by `RestConfiguration`'s `Authorization` interceptor). ndopify-server answers 401 for a token it rejects, 403
 * (`MANDATE_ACCESS_DENIED`) for another customer's match or one not yet proposed, and 409 when the match is no
 * longer PROPOSED.
 */
@Service
class NdMatchClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): Match =
        rest.get()
            .uri("/v1/matches/{id}", id)
            .retrieve()
            .body<GetMatchResponse>()
            ?.match
            ?: Match()

    fun accept(id: Long) {
        rest.post()
            .uri("/v1/matches/{id}/accept", id)
            .retrieve()
            .toBodilessEntity()
    }

    fun reject(id: Long, request: RejectMatchRequest) {
        rest.post()
            .uri("/v1/matches/{id}/reject", id)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
