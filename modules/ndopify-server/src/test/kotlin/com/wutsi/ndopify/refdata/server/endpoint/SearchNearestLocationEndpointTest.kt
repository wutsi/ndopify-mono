package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/SearchNearestLocationEndpoint.sql"])
class SearchNearestLocationEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun nearest() {
        val response = rest.getForEntity("/v1/locations/nearest?id=237002&radius=5", SearchNearestLocationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val locations = response.body!!.locations
        assertEquals(listOf(237034L, 237001L, 237072L, 237091L, 237093L), locations.map { it.location.id })
        assertTrue(locations.all { it.location.type == LocationType.NEIGHBORHOOD })
        assertEquals("Ekoudou", locations[0].location.name)
        assertEquals(1.51, locations[0].distance, 0.01)
        assertEquals(4.34, locations[4].distance, 0.01)
    }

    @Test
    fun `default radius`() {
        val response = rest.getForEntity("/v1/locations/nearest?id=237034", SearchNearestLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(listOf(237072L, 237001L), locations.map { it.location.id })
        assertEquals(0.85, locations[0].distance, 0.01)
        assertEquals(0.91, locations[1].distance, 0.01)
    }

    @Test
    fun `multiple origins`() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&id=237500&radius=5",
            SearchNearestLocationResponse::class.java
        )

        val locations = response.body!!.locations
        assertEquals(listOf(237501L, 237034L, 237001L, 237072L, 237091L, 237093L), locations.map { it.location.id })
        assertEquals(1.11, locations[0].distance, 0.01)
    }

    @Test
    fun radius() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&radius=2",
            SearchNearestLocationResponse::class.java
        )

        assertEquals(listOf(237034L, 237001L), response.body!!.locations.map { it.location.id })
    }

    @Test
    fun `large radius`() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&radius=50",
            SearchNearestLocationResponse::class.java
        )

        assertEquals(listOf(237500L, 237501L), response.body!!.locations.takeLast(2).map { it.location.id })
    }

    @Test
    fun `negative radius`() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&radius=-1",
            SearchNearestLocationResponse::class.java
        )

        assertTrue(response.body!!.locations.isEmpty())
    }

    @Test
    fun type() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&type=CITY&radius=5",
            SearchNearestLocationResponse::class.java
        )

        assertEquals(listOf(2370101L), response.body!!.locations.map { it.location.id })
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/refdata/SearchNearestLocationEndpoint.sql"],
        statements = [
            "INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES " +
                "(2370102, 237, 'CITY', 'CM', 'Soa', 'Soa', 3.97, 11.59), " +
                "(237800, 2370102, 'NEIGHBORHOOD', 'CM', 'Soa Bastos', 'Soa-Bastos', 3.8900, 11.5110)", // 0.3 km, another city
        ],
    )
    fun `parent - only the children of the location`() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&radius=5&parent-id=2370101",
            SearchNearestLocationResponse::class.java
        )

        assertEquals(
            listOf(237034L, 237001L, 237072L, 237091L, 237093L),
            response.body!!.locations.map { it.location.id }
        )
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/refdata/SearchNearestLocationEndpoint.sql"],
        statements = [
            "INSERT INTO T_LOCATION (id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES " +
                "(2370102, 237, 'CITY', 'CM', 'Soa', 'Soa', 3.97, 11.59), " +
                "(237800, 2370102, 'NEIGHBORHOOD', 'CM', 'Soa Bastos', 'Soa-Bastos', 3.8900, 11.5110)",
        ],
    )
    fun `no parent - children of every location`() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&radius=5",
            SearchNearestLocationResponse::class.java
        )

        assertEquals(237800L, response.body!!.locations.first().location.id)
    }

    @Test
    fun `limit and offset`() {
        val response = rest.getForEntity(
            "/v1/locations/nearest?id=237002&radius=5&limit=2&offset=1",
            SearchNearestLocationResponse::class.java
        )

        assertEquals(listOf(237001L, 237072L), response.body!!.locations.map { it.location.id })
    }

    @Test
    fun `origin without coordinates`() {
        val response = rest.getForEntity("/v1/locations/nearest?id=237999", SearchNearestLocationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.locations.isEmpty())
    }

    @Test
    fun `unknown origin`() {
        val response = rest.getForEntity("/v1/locations/nearest?id=999", SearchNearestLocationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.locations.isEmpty())
    }

    @Test
    fun `no origin`() {
        val response = rest.getForEntity("/v1/locations/nearest", SearchNearestLocationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.locations.isEmpty())
    }
}
