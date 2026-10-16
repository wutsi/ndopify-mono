package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.Mandate
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
}
