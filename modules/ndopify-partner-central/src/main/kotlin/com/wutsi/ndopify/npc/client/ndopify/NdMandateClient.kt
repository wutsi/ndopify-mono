package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.mandate.dto.SearchMandateResponse
import com.wutsi.ndopify.mandate.dto.UpdateMandateRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server mandate endpoints (`/v1/mandates`).
 * Mandates are tenant-scoped: the `X-Tenant-ID` header (see `RestConfiguration`) decides which tenant receives it.
 */
@Service
class NdMandateClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** Records a mandate: the server finds or creates the customer from its contact details. */
    fun create(request: CreateMandateRequest): CreateMandateResponse =
        rest.post()
            .uri("/v1/mandates")
            .body(request)
            .retrieve()
            .body<CreateMandateResponse>()
            ?: CreateMandateResponse()

    fun get(id: Long): Mandate =
        rest.get()
            .uri("/v1/mandates/{id}", id)
            .retrieve()
            .body<GetMandateResponse>()
            ?.mandate
            ?: Mandate()

    /** The agent's mandates in progress: assigned, waiting for the customer's signature, or signed. */
    fun active(agentId: Long): List<MandateSummary> =
        rest.get()
            .uri(
                "/v1/mandates?agent-id={agentId}&status={assigned}&status={pending}&status={approved}",
                agentId,
                MandateStatus.ASSIGNED,
                MandateStatus.PENDING_APPROVAL,
                MandateStatus.APPROVED,
            )
            .retrieve()
            .body<SearchMandateResponse>()
            ?.mandates
            ?: emptyList()

    fun update(id: Long, request: UpdateMandateRequest) {
        rest.post().uri("/v1/mandates/{id}", id).body(request).retrieve().toBodilessEntity()
    }

    /** Sends the mandate to the customer for signature (again, for a mandate already waiting for it). */
    fun requestApproval(id: Long) {
        rest.post().uri("/v1/mandates/{id}/request-approval", id).retrieve().toBodilessEntity()
    }

    fun close(id: Long, request: CloseMandateRequest) {
        rest.post().uri("/v1/mandates/{id}/close", id).body(request).retrieve().toBodilessEntity()
    }

    fun cancel(id: Long, request: CancelMandateRequest) {
        rest.post().uri("/v1/mandates/{id}/cancel", id).body(request).retrieve().toBodilessEntity()
    }
}
