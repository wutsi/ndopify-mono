package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchLocationRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdLocationClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdLocationClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdLocationClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/locations/2370201"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"location":{"id":2370201,"parentId":23702,"name":"Bafoussam","type":"CITY","country":"CM","population":347517}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val location = client.get(2370201).location

        server.verify()
        assertEquals(2370201L, location.id)
        assertEquals(23702L, location.parentId)
        assertEquals("Bafoussam", location.name)
        assertEquals(LocationType.CITY, location.type)
        assertEquals("CM", location.country)
    }

    @Test
    fun notFound() {
        server.expect(requestTo("http://localhost:8080/v1/locations/999"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException.NotFound> { client.get(999) }
    }

    @Test
    fun search() {
        server.expect(
            requestTo(
                "http://localhost:8080/v1/locations" +
                    "?q=baf&id=1&id=2&parent-id=23702&type=CITY&type=NEIGHBORHOOD&country=CM&limit=10&offset=5",
            ),
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"locations":[""" +
                        """{"id":1,"name":"Bafoussam","type":"CITY","country":"CM","population":0},""" +
                        """{"id":2,"name":"Bafang","type":"CITY","country":"CM","population":0}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val response = client.search(
            SearchLocationRequest(
                keyword = "baf",
                ids = listOf(1, 2),
                parentId = 23702,
                types = listOf(LocationType.CITY, LocationType.NEIGHBORHOOD),
                country = "CM",
                limit = 10,
                offset = 5,
            ),
        )

        server.verify()
        assertEquals(listOf(1L, 2L), response.locations.map { it.id })
    }

    @Test
    fun searchWithDefaults() {
        server.expect(requestTo("http://localhost:8080/v1/locations?limit=20&offset=0"))
            .andRespond(withSuccess("""{"locations":[]}""", MediaType.APPLICATION_JSON))

        val response = client.search(SearchLocationRequest())

        server.verify()
        assertEquals(0, response.locations.size)
    }

    @Test
    fun import() {
        server.expect(requestTo("http://localhost:8080/v1/locations/import?country=CM"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"added":10,"updated":2,"errors":1,"errorMessages":[]}""", MediaType.APPLICATION_JSON))

        val response = client.import("CM")

        server.verify()
        assertEquals(10, response.added)
        assertEquals(2, response.updated)
        assertEquals(1, response.errors)
    }
}
