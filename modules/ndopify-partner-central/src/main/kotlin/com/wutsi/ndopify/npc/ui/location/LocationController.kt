package com.wutsi.ndopify.npc.ui.location

import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.service.TenantContext
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/**
 * JSON proxy to `GET /v1/locations` for the city/neighbourhood dropdowns (slim-select).
 * Takes the same query parameters as the server (`q`, `id`, `parent-id`, `type`, `limit`, `offset`), but it's public
 * (see `SecurityConfiguration`), so the request is narrowed before it's forwarded:
 * - only cities and neighbourhoods can be searched, defaulting to cities;
 * - the country is the current tenant's, whatever the browser sends;
 * - `limit` is capped at [MAX_LIMIT].
 */
@RestController
@RequestMapping("/locations")
class LocationController(
    private val client: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    companion object {
        private val ALLOWED_TYPES = listOf(LocationType.CITY, LocationType.NEIGHBORHOOD)
        const val MAX_LIMIT = 50
        private val MAX_AGE: Duration = Duration.ofHours(1)
    }

    @GetMapping
    fun search(@ModelAttribute request: SearchLocationRequest): ResponseEntity<SearchLocationResponse> {
        val response = client.search(
            request.copy(
                types = request.types.filter { type -> ALLOWED_TYPES.contains(type) }
                    .ifEmpty { listOf(LocationType.CITY) },
                country = tenantContext.getTenant().countryCode,
                limit = request.limit.coerceIn(1, MAX_LIMIT),
                offset = request.offset.coerceAtLeast(0),
            )
        )
        // Reference data rarely changes: let the browser reuse it while the agent goes back and forth in the wizard.
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(MAX_AGE).cachePublic())
            .body(response)
    }
}
