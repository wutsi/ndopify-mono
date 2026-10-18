package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.AssignmentSummary
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
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
import tools.jackson.databind.json.JsonMapper
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NdAssignmentClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdAssignmentClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdAssignmentClient("http://localhost:8080", builder)
    }

    @Test
    fun pending() {
        server.expect(requestTo("http://localhost:8080/v1/assignments?agent-id=20&status=PENDING"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"assignments":[{"id":7,"mandateId":101,"agentId":20,"status":"PENDING","score":0.8,""" +
                        """"createdAt":0,"modifiedAt":0,"expiresAt":4102444800000}]}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val assignments = client.pending(20)

        server.verify()
        assertEquals(listOf(7L), assignments.map { it.id })
        assertEquals(101L, assignments[0].mandateId)
        assertEquals(AssignmentStatus.PENDING, assignments[0].status)
    }

    @Test
    fun accept() {
        server.expect(requestTo("http://localhost:8080/v1/assignments/7/accept"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.accept(7)

        server.verify()
    }

    @Test
    fun decline() {
        server.expect(requestTo("http://localhost:8080/v1/assignments/7/decline"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.decline(7)

        server.verify()
    }

    @Test
    fun `accept - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/assignments/7/accept"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.accept(7) }
    }

    @Test
    fun accepted() {
        server.expect(requestTo("http://localhost:8080/v1/assignments?mandate-id=101&status=ACCEPTED"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(
                        SearchAssignmentResponse(
                            listOf(AssignmentSummary(id = 7, mandateId = 101, status = AssignmentStatus.ACCEPTED, modifiedAt = Date(1000))),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val assignment = client.accepted(101)

        server.verify()
        assertEquals(7L, assignment?.id)
        assertEquals(1000L, assignment?.modifiedAt?.time)
    }

    @Test
    fun `accepted - none`() {
        server.expect(requestTo("http://localhost:8080/v1/assignments?mandate-id=101&status=ACCEPTED"))
            .andRespond(withSuccess("""{"assignments":[]}""", MediaType.APPLICATION_JSON))

        assertNull(client.accepted(101))
    }
}
