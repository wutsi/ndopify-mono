package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.refdata.dto.Location
import com.wutsi.ndopify.refdata.dto.SearchLocationResponse
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun search() {
        server.expect(requestTo("http://localhost:8080/v1/locations?id=2370201&id=5&limit=2"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(SearchLocationResponse(locations = listOf(Location(id = 2370201, name = "Douala"), Location(id = 5, name = "Bonapriso")))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(listOf("Douala", "Bonapriso"), client.search(listOf(2370201, 5)).map { it.name })
        server.verify()
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
