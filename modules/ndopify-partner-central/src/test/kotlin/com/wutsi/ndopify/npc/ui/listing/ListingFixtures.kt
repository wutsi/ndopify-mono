package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdLocationClient
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse

/** The listing pages' location lookups: by ids (names), or a city's neighbourhoods (`parent-id`). */
object ListingFixtures {
    fun mockNeighborhoods(locationClient: NdLocationClient) {
        whenever(locationClient.search(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<SearchLocationRequest>(0)
            val locations = if (request.parentId != null) {
                ProfileFixtures.LOCATIONS.filter { it.parentId == request.parentId && it.type in request.types }
            } else {
                ProfileFixtures.LOCATIONS.filter { request.ids.contains(it.id) }
            }
            SearchLocationResponse(locations = locations)
        }
    }
}
