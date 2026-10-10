package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.security.dto.AuthenticateRequest
import com.wutsi.ndopify.security.dto.AuthenticateResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server authentication endpoints (`/v1/auth`).
 * The issued access token carries the tenant, so the `X-Tenant-ID` header is sent (see `RestConfiguration`).
 */
@Service
class NdAuthClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun authenticate(request: AuthenticateRequest): AuthenticateResponse =
        rest.post()
            .uri("/v1/auth")
            .body(request)
            .retrieve()
            .body<AuthenticateResponse>()
            ?: AuthenticateResponse()
}
