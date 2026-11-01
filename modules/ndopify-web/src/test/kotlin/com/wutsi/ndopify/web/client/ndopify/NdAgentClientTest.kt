package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.GetAgentResponse
import org.junit.jupiter.api.BeforeEach
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
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdAgentClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdAgentClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdAgentClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/agents/5"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(GetAgentResponse(Agent(id = 5))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(5L, client.get(5).id)
        server.verify()
    }

    @Test
    fun `get - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/agents/5")).andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException.NotFound> { client.get(5) }
    }
}
