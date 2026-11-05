package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.GetVisitResponse
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.SearchVisitResponse
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for ndopify-server's agent-side visit endpoints (`/v1/visits`). ndopify-server answers 403 for another
 * agent's visit, 404 when it is gone, and 409 when its status no longer allows the action. The customer's code is
 * never returned to the agent. Visits are tenant-scoped by `X-Tenant-ID` (the shared interceptor, see
 * `RestConfiguration`).
 */
@Service
class NdVisitClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): Visit =
        rest.get()
            .uri("/v1/visits/{id}", id)
            .retrieve()
            .body<GetVisitResponse>()
            ?.visit
            ?: Visit()

    /** The visits of [agentId] in [statuses], one `status` parameter each. */
    fun search(agentId: Long, statuses: List<VisitStatus>, limit: Int = 20): List<VisitSummary> =
        rest.get()
            .uri { uri ->
                uri.path("/v1/visits")
                    .queryParam("agent-id", agentId)
                statuses.forEach { uri.queryParam("status", it) }
                uri.queryParam("limit", limit).build()
            }
            .retrieve()
            .body<SearchVisitResponse>()
            ?.visits
            ?: emptyList()

    /** Confirms a REQUESTED visit at [request]'s slot and type. */
    fun schedule(id: Long, request: ScheduleVisitRequest) {
        rest.post().uri("/v1/visits/{id}/schedule", id).body(request).retrieve().toBodilessEntity()
    }

    /** Cancels a REQUESTED or CONFIRMED visit. */
    fun cancel(id: Long, request: CancelVisitRequest) {
        rest.post().uri("/v1/visits/{id}/cancel", id).body(request).retrieve().toBodilessEntity()
    }

    /** Starts a CONFIRMED visit with the customer's code, in its check-in window. */
    fun checkIn(id: Long, request: CheckInVisitRequest) {
        rest.post().uri("/v1/visits/{id}/check-in", id).body(request).retrieve().toBodilessEntity()
    }

    /** Records the customer's absence on a CONFIRMED visit, from 15 min after its slot. */
    fun noShow(id: Long, request: NoShowVisitRequest) {
        rest.post().uri("/v1/visits/{id}/no-show", id).body(request).retrieve().toBodilessEntity()
    }

    /** Ends a CHECKED_IN visit with the agent's private report, at least 10 min after its check-in. */
    fun checkOut(id: Long, request: CheckOutVisitRequest) {
        rest.post().uri("/v1/visits/{id}/check-out", id).body(request).retrieve().toBodilessEntity()
    }
}
