package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.common.dto.ImportResponse
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.server.dao.LocationRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ImportLocationEndpointTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: LocationRepository

    @Sql(value = ["/db/test/clean.sql", "/db/test/refdata/ImportLocationEndpoint.sql"])
    @Test
    fun cm() {
        val response = rest.getForEntity("/v1/locations/import?country=CM", ImportResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val countries = dao.findByType(LocationType.COUNTRY)
        assertEquals(1, countries.size)
        assertEquals("Cameroon", countries[0].name)
        assertEquals("CM", countries[0].country)

        val states = dao.findByType(LocationType.STATE)
        assertEquals(10, states.size)
        states.forEach { state -> assertEquals(countries[0].id, state.parentId) }

        val stateIds = states.map { it.id }
        val cities = dao.findByType(LocationType.CITY)
        cities.forEach { city -> assertTrue(stateIds.contains(city.parentId)) }

        val neighbourhoods = dao.findByType(LocationType.NEIGHBORHOOD)
        assertEquals(true, neighbourhoods.size > 0)
        neighbourhoods.forEach { neighbourhood ->
            assertNotNull(cities.find { city -> city.id == neighbourhood.parentId })
        }
    }

    @Sql(value = ["/db/test/clean.sql"])
    @Test
    fun ca() {
        val response = rest.getForEntity("/v1/locations/import?country=CA", ImportResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val countries = dao.findByType(LocationType.COUNTRY)
        assertEquals(1, countries.size)
        assertEquals("Canada", countries[0].name)
        assertEquals("CA", countries[0].country)

        val states = dao.findByType(LocationType.STATE)
        assertEquals(13, states.size)
        states.forEach { state -> assertEquals(countries[0].id, state.parentId) }

        val stateIds = states.map { it.id }
        val cities = dao.findByType(LocationType.CITY)
        cities.forEach { city -> assertTrue(stateIds.contains(city.parentId)) }

        val neighbourhoods = dao.findByType(LocationType.NEIGHBORHOOD)
        assertEquals(83, neighbourhoods.size)
        neighbourhoods.forEach { neighbourhood ->
            assertNotNull(cities.find { city -> city.id == neighbourhood.parentId })
        }
    }

    @Test
    fun invalid() {
        val response = rest.getForEntity("/v1/locations/import?country=xx", ErrorResponse::class.java)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }
}
