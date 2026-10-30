package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.refdata.dto.Location
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

    /** The locations of [ids] (ex: a listing's city and neighborhood). */
    fun search(ids: List<Long>): List<Location> =
        rest.get()
            .uri { uri ->
                ids.forEach { uri.queryParam("id", it) }
                uri.path("/v1/locations")
                    .queryParam("limit", ids.size)
                    .build()
            }
            .retrieve()
            .body<SearchLocationResponse>()
            ?.locations
            ?: emptyList()
}
