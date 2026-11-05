package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.dto.GetVisitResponse
import com.wutsi.ndopify.visit.dto.SearchVisitResponse
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitSummary
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server visit endpoints (`/v1/visits`), called with the customer's `public-portal` token (sent
 * by `RestConfiguration`'s `Authorization` interceptor). ndopify-server answers 401 for a token it rejects, 403 for
 * another customer's visit, and 404 when the visit is gone. The client lets those exceptions propagate.
 */
@Service
class NdVisitClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun create(request: CreateVisitRequest): Long =
        rest.post()
            .uri("/v1/visits")
            .body(request)
            .retrieve()
            .body<CreateVisitResponse>()
            ?.visitId
            ?: -1

    /** The match's open visit, if any: REQUESTED, CONFIRMED or CHECKED_IN. */
    fun openForMatch(matchId: Long): VisitSummary? =
        rest.get()
            .uri {
                it.path("/v1/visits")
                    .queryParam("match-id", matchId)
                    .queryParam("status", "REQUESTED")
                    .queryParam("status", "CONFIRMED")
                    .queryParam("status", "CHECKED_IN")
                    .queryParam("limit", 1)
                    .build()
            }
            .retrieve()
            .body<SearchVisitResponse>()
            ?.visits
            ?.firstOrNull()

    fun get(id: Long): Visit =
        rest.get()
            .uri("/v1/visits/{id}", id)
            .retrieve()
            .body<GetVisitResponse>()
            ?.visit
            ?: Visit()

    fun cancel(id: Long, request: CancelVisitRequest) {
        rest.post()
            .uri("/v1/visits/{id}/cancel", id)
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
