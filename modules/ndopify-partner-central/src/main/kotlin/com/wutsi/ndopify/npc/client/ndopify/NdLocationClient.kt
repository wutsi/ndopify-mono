package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.common.dto.ImportResponse
import com.wutsi.ndopify.refdata.dto.GetLocationResponse
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server location endpoints (`/v1/locations`).
 * Locations are global reference data: the `X-Tenant-ID` header added by `RestConfiguration` is ignored by the server.
 */
@Service
class NdLocationClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    fun get(id: Long): GetLocationResponse =
        rest.get()
            .uri("/v1/locations/{id}", id)
            .retrieve()
            .body<GetLocationResponse>()
            ?: GetLocationResponse()

    /** Query parameter names match the `@BindParam` aliases on [SearchLocationRequest]. */
    fun search(request: SearchLocationRequest): SearchLocationResponse =
        rest.get()
            .uri { uri ->
                request.keyword?.takeIf { it.isNotEmpty() }?.let { uri.queryParam("q", it) }
                request.ids.forEach { uri.queryParam("id", it) }
                request.parentId?.let { uri.queryParam("parent-id", it) }
                request.types.forEach { uri.queryParam("type", it) }
                request.country?.takeIf { it.isNotEmpty() }?.let { uri.queryParam("country", it) }
                uri.path("/v1/locations")
                    .queryParam("limit", request.limit)
                    .queryParam("offset", request.offset)
                    .build()
            }
            .retrieve()
            .body<SearchLocationResponse>()
            ?: SearchLocationResponse()

    /** Imports the GeoNames + neighbourhood reference data of [country] (ISO code). */
    fun import(country: String): ImportResponse =
        rest.get()
            .uri { uri -> uri.path("/v1/locations/import").queryParam("country", country).build() }
            .retrieve()
            .body<ImportResponse>()
            ?: ImportResponse()
}
