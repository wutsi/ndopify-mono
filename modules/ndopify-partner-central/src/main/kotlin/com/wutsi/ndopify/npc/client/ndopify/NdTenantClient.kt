package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.refdata.dto.SearchTenantResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

/**
 * Client for the ndopify-server tenant endpoints (`/v1/tenants`).
 * Tenants are global reference data, so no `X-Tenant-ID` header is sent.
 */
@Service
class NdTenantClient internal constructor(
    @param:Value("\${ndopify.server.api.base-url}") private val baseUrl: String,
    private val rest: RestTemplate,

    ) {
    fun search(): SearchTenantResponse {
        val uri = UriComponentsBuilder.fromUriString(baseUrl)
            .path("/v1/tenants")
            .queryParam("active", true)
            .build()
            .toUri()
        return rest.getForEntity(uri, SearchTenantResponse::class.java).body ?: SearchTenantResponse()
    }
}
