package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.SearchMatchResponse
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
    fun search() {
        server.expect(
            requestTo("http://localhost:8080/v1/matches?mandate-id=204&mandate-id=205&status=ACTIVE&status=PROPOSED&limit=100"),
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(
                        SearchMatchResponse(
                            listOf(
                                Match(id = 9001, mandateId = 204, listingId = 3012, status = MatchStatus.ACTIVE, score = 0.95),
                                Match(id = 9002, mandateId = 205, listingId = 3044, status = MatchStatus.PROPOSED),
                            ),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val matches = client.search(listOf(204, 205), listOf(MatchStatus.ACTIVE, MatchStatus.PROPOSED))

        server.verify()
        assertEquals(listOf(9001L, 9002L), matches.map { it.id })
        assertEquals(0.95, matches[0].score)
        assertEquals(MatchStatus.PROPOSED, matches[1].status)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/matches/9001"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(GetMatchResponse(Match(id = 9001, mandateId = 204, listingId = 3012, status = MatchStatus.ACTIVE))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val match = client.get(9001)

        server.verify()
        assertEquals(204L, match.mandateId)
        assertEquals(3012L, match.listingId)
    }

    @Test
    fun `get - not found`() {
        server.expect(requestTo("http://localhost:8080/v1/matches/9001"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND))

        assertFailsWith<HttpClientErrorException> { client.get(9001) }
    }

    @Test
    fun propose() {
        server.expect(requestTo("http://localhost:8080/v1/matches/9001/propose"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.propose(9001)
        server.verify()
    }

    @Test
    fun `propose - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/matches/9001/propose"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException> { client.propose(9001) }
    }

    @Test
    fun dismiss() {
        server.expect(requestTo("http://localhost:8080/v1/matches/9001/dismiss"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.dismiss(9001)
        server.verify()
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
