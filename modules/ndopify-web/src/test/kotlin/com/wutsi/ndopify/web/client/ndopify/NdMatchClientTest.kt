package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
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

class NdMatchClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdMatchClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdMatchClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/matches/12"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    JsonMapper.builder().build().writeValueAsString(GetMatchResponse(Match(id = 12, status = MatchStatus.PROPOSED))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val match = client.get(12)

        assertEquals(12L, match.id)
        assertEquals(MatchStatus.PROPOSED, match.status)
        server.verify()
    }

    @Test
    fun `get - forbidden`() {
        server.expect(requestTo("http://localhost:8080/v1/matches/12")).andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertFailsWith<HttpClientErrorException.Forbidden> { client.get(12) }
    }

    @Test
    fun `get - unauthorized`() {
        server.expect(requestTo("http://localhost:8080/v1/matches/12")).andRespond(withStatus(HttpStatus.UNAUTHORIZED))

        assertFailsWith<HttpClientErrorException.Unauthorized> { client.get(12) }
    }

    @Test
    fun accept() {
        server.expect(requestTo("http://localhost:8080/v1/matches/12/accept"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.OK))

        client.accept(12)

        server.verify()
    }

    @Test
    fun `accept - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/matches/12/accept")).andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException.Conflict> { client.accept(12) }
    }

    @Test
    fun reject() {
        server.expect(requestTo("http://localhost:8080/v1/matches/12/reject"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.reasonCode").value(MatchRejectionReason.OTHER.name))
            .andExpect(jsonPath("$.reason").value("Trop cher"))
            .andRespond(withStatus(HttpStatus.OK))

        client.reject(12, RejectMatchRequest(reasonCode = MatchRejectionReason.OTHER, reason = "Trop cher"))

        server.verify()
    }
}
