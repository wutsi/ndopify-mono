package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.common.dto.ImportResponse
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.refdata.dto.GetLocationResponse
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import com.wutsi.ndopify.refdata.server.io.GeonamesImporter
import com.wutsi.ndopify.refdata.server.io.NeighbourhoodImporter
import com.wutsi.ndopify.refdata.server.mapper.LocationMapper
import com.wutsi.ndopify.refdata.server.service.LocationService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/locations")
class LocationEndpoints(
    private val geonamesImporter: GeonamesImporter,
    private val neighbourhoodImporter: NeighbourhoodImporter,
    private val service: LocationService,
    private val mapper: LocationMapper,
) {
    @GetMapping("/import")
    fun import(@RequestParam country: String): ImportResponse {
        val result1 = geonamesImporter.import(country)
        val result2 = neighbourhoodImporter.import(country)
        val errorMessages = result1.errorMessages.toMutableList()
        errorMessages.addAll(result2.errorMessages)
        return ImportResponse(
            added = result1.added + result2.added,
            updated = result1.updated + result2.updated,
            errors = result1.errors + result2.errors,
            errorMessages = errorMessages
        )
    }

    @GetMapping("/{id}")
    fun get(
        @PathVariable id: Long,
    ): GetLocationResponse {
        val location = service.findById(id)
        return GetLocationResponse(location = mapper.toLocation(location))
    }

    @GetMapping
    fun search(
        @ModelAttribute request: SearchLocationRequest,
    ): SearchLocationResponse {
        val locations = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchLocationResponse(
            locations = locations.map { location -> mapper.toLocation(location) }
        )
    }
}
