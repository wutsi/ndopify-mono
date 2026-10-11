package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.GetLocationResponse
import com.wutsi.ndopify.refdata.dto.LocationType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/GetLocationEndpoint.sql"])
class GetLocationEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/locations/2370201", GetLocationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val location = response.body!!.location
        assertEquals(2370201L, location.id)
        assertEquals(23702L, location.parentId)
        assertEquals("Bafoussam", location.name)
        assertEquals(LocationType.CITY, location.type)
        assertEquals("CM", location.country)
        assertEquals(347517L, location.population)
        assertEquals(5.47, location.latitude)
        assertEquals(10.42, location.longitude)
    }

    @Test
    fun `location not found`() {
        val response = rest.getForEntity("/v1/locations/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LOCATION_NOT_FOUND, response.body?.error?.code)
    }
}
