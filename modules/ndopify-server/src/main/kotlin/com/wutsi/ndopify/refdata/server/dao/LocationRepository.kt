package com.wutsi.ndopify.refdata.server.dao

import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface LocationRepository : CrudRepository<LocationEntity, Long> {
    fun findByType(type: LocationType): List<LocationEntity>
}
