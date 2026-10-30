package com.wutsi.ndopify.npc.ui.location

import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.web.core.service.TenantContext

/**
 * An agent's area: a city of the tenant's country and some of its neighbourhoods. Checked by the join wizard's area
 * step and the profile page, since ndopify-server doesn't validate them.
 */
class SectorValidator(
    private val locationClient: NdLocationClient,
    private val tenantContext: TenantContext,
) {
    /** The city must be one of the tenant's cities, and every neighbourhood one of its neighbourhoods. */
    fun errors(cityId: Long, neighborhoodIds: List<Long>): Map<String, String> {
        val locations = locations(listOf(cityId) + neighborhoodIds)
        val city = locations[cityId]
        if (city == null || city.type != LocationType.CITY || city.country != tenantContext.getTenant().countryCode) {
            return mapOf("cityId" to "Cette ville n'est pas proposée. Choisissez-en une dans la liste.")
        }

        val valid = neighborhoodIds.all { id ->
            val neighborhood = locations[id]
            neighborhood != null && neighborhood.type == LocationType.NEIGHBORHOOD && neighborhood.parentId == cityId
        }
        return if (valid) {
            emptyMap()
        } else {
            mapOf("neighborhoodIds" to "Choisissez des quartiers de la ville sélectionnée.")
        }
    }

    fun locations(ids: List<Long>): Map<Long, Location> {
        if (ids.isEmpty()) return emptyMap()
        return locationClient.search(SearchLocationRequest(ids = ids.distinct(), limit = ids.size))
            .locations
            .associateBy { location -> location.id }
    }
}
