package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NdAgentClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdAgentClient

    private val request = CreateAgentRequest(
        firstName = "Ray",
        lastName = "Sponsible",
        email = "ray@gmail.com",
        whatsappNumber = "+237670000001",
        mobileMoneyNumber = "+237670000002",
        agentType = AgentType.INDEPENDANT,
        experienceLevel = ExperienceLevel.JUNIOR,
        cityId = 2220957,
        neighborhoodIds = listOf(1001, 1002),
    )

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdAgentClient("http://localhost:8080", builder)
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(
                content().json(
                    """
                    {
                        "firstName":"Ray",
                        "lastName":"Sponsible",
                        "email":"ray@gmail.com",
                        "whatsappNumber":"+237670000001",
                        "mobileMoneyNumber":"+237670000002",
                        "agentType":"INDEPENDANT",
                        "experienceLevel":"JUNIOR",
                        "cityId":2220957,
                        "neighborhoodIds":[1001,1002]
                    }
                    """.trimIndent(),
                ),
            )
            .andRespond(withSuccess("""{"agentId":42}""", MediaType.APPLICATION_JSON))

        val response = client.create(request)

        server.verify()
        assertEquals(42L, response.agentId)
    }

    @Test
    fun agentAlreadyExists() {
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andRespond(
                withStatus(HttpStatus.CONFLICT)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"${ErrorCode.AGENT_ALREADY_EXISTS}"}}"""),
            )

        val ex = assertFailsWith<HttpClientErrorException.Conflict> { client.create(request) }
        assertEquals(ErrorCode.AGENT_ALREADY_EXISTS, ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code)
    }

    @Test
    fun badRequest() {
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException.BadRequest> { client.create(CreateAgentRequest()) }
    }

    @Test
    fun serverError() {
        server.expect(requestTo("http://localhost:8080/v1/agents"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertFailsWith<HttpServerErrorException> { client.create(request) }
    }
}
