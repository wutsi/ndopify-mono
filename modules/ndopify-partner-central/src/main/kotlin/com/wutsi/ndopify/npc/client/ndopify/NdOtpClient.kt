package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.security.dto.CreateOtpRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Client for the ndopify-server OTP endpoints (`/v1/otp`).
 * The OTP email is branded with the tenant, so the `X-Tenant-ID` header is sent (see `RestConfiguration`).
 */
@Service
class NdOtpClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** Generates a one-time code for [CreateOtpRequest.email] and emails it; the code itself is never returned. */
    fun create(request: CreateOtpRequest) {
        rest.post()
            .uri("/v1/otp")
            .body(request)
            .retrieve()
            .toBodilessEntity()
    }
}
