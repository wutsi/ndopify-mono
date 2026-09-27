package com.wutsi.ndopify.refdata.server.mapper

import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import org.springframework.stereotype.Service

@Service
class LocationMapper {
    fun toLocation(entity: LocationEntity): Location {
        return Location(
            id = entity.id,
            name = entity.name,
            country = entity.country,
            type = entity.type,
            parentId = entity.parentId,
            population = entity.population ?: 0L,
            longitude = entity.longitude,
            latitude = entity.latitude,
        )
    }
}
