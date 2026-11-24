package com.wutsi.ndopify.web.core.client.ndopify

import com.wutsi.ndopify.security.dto.AuthenticateResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

/**
 * Client for ndopify-server's `POST /v1/auth/refresh`: the endpoint is authenticated by the token it renews and takes
 * no body, so the token is passed explicitly on the app's plain `RestTemplate` (no interceptor, no `X-Tenant-ID`).
 * Renewing a token that the server refuses (past its session's cap, or a removed membership) throws, like any call.
 */
@Service
class NdTokenClient internal constructor(
    @param:Value("\${ndopify.server.api.base-url}") private val baseUrl: String,
    private val rest: RestTemplate,
) {
    /** The renewed token, `""` when the answer has none. */
    fun refresh(accessToken: String): String {
        val uri = UriComponentsBuilder.fromUriString(baseUrl)
            .path("/v1/auth/refresh")
            .build()
            .toUri()
        val headers = HttpHeaders().apply { setBearerAuth(accessToken) }
        return rest.postForEntity(uri, HttpEntity<Void>(headers), AuthenticateResponse::class.java)
            .body?.accessToken ?: ""
    }
}
