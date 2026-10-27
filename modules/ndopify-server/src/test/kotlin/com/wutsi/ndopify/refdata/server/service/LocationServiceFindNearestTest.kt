package com.wutsi.ndopify.refdata.server.service

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.LocationType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/LocationServiceFindNearest.sql"])
class LocationServiceFindNearestTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var service: LocationService

    @Test
    fun `at the center of a neighborhood`() {
        val nearest = service.findNearest(3.8925339696406587, 11.511400038328873, LocationType.NEIGHBORHOOD, 2370101)

        assertEquals(237002L, nearest?.first?.id)
        assertEquals(0.0, nearest!!.second, 0.01)
    }

    @Test
    fun `between neighborhoods`() {
        // 0.5 km south of Bastos, 1.0 km north of Ekoudou
        val nearest = service.findNearest(3.888, 11.5116, LocationType.NEIGHBORHOOD, 2370101)

        assertEquals(237002L, nearest?.first?.id)
        assertEquals(0.5, nearest!!.second, 0.05)
    }

    @Test
    fun `far from every neighborhood`() {
        val nearest = service.findNearest(4.30, 11.53, LocationType.NEIGHBORHOOD, 2370101)

        assertEquals(237501L, nearest?.first?.id)
    }

    @Test
    fun `other type`() {
        val nearest = service.findNearest(3.8925339696406587, 11.511400038328873, LocationType.CITY, 237)

        assertEquals(2370101L, nearest?.first?.id)
    }

    @Test
    fun `no location in the parent`() {
        assertNull(service.findNearest(3.8925339696406587, 11.511400038328873, LocationType.NEIGHBORHOOD, 9999))
    }
}
