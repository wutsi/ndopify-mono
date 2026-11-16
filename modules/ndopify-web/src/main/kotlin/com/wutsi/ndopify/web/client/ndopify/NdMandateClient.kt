package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.mandate.dto.CreateMandateRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.Mandate
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server mandate endpoint (`/v1/mandates`). The customer's `public-portal` token reads its own
 * mandate only: any other is a 403.
 */
@Service
class NdMandateClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): Mandate =
        rest.get()
            .uri("/v1/mandates/{id}", id)
            .retrieve()
            .body<GetMandateResponse>()
            ?.mandate
            ?: Mandate()

    /**
     * Creates a mandate (the SEARCH one of a visitor of a listing's page): its id. Without a token: the endpoint is
     * public, and the server takes the search's criteria from the origin listing, never from the request. A 400 / 404 /
     * 409 is the caller's to handle.
     */
    fun create(request: CreateMandateRequest): Long =
        rest.post()
            .uri("/v1/mandates")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<CreateMandateResponse>()
            ?.mandateId
            ?: -1
}
