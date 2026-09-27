package com.wutsi.ndopify.refdata.server.endpoint

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/refdata/SearchLocationEndpoint.sql"])
class SearchLocationEndpointTest : BaseEndpointIntegrationTest() {
    @Test
    fun all() {
        val response = rest.getForEntity("/v1/locations", SearchLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(12, locations.size)
    }

    @Test
    fun `by country`() {
        val response = rest.getForEntity("/v1/locations?country=CA", SearchLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(3, locations.size)
    }

    @Test
    fun `by parent-id`() {
        val response = rest.getForEntity("/v1/locations?parent-id=23702", SearchLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(4, locations.size)
    }

    @Test
    fun `by parent-id and type`() {
        val response = rest.getForEntity("/v1/locations?parent-id=23701&type=CITY", SearchLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(2, locations.size)
    }

    @Test
    fun `by keyword`() {
        val response = rest.getForEntity("/v1/locations?q=baf", SearchLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(3, locations.size)
    }

    @Test
    fun `by id`() {
        val response = rest.getForEntity("/v1/locations?id=23702&id=237", SearchLocationResponse::class.java)

        val locations = response.body!!.locations
        assertEquals(2, locations.size)
    }
}
