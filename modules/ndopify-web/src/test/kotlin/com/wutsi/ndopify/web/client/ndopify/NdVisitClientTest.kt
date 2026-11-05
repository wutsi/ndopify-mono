package com.wutsi.ndopify.web.client.ndopify

import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitResponse
import com.wutsi.ndopify.visit.dto.GetVisitResponse
import com.wutsi.ndopify.visit.dto.SearchVisitResponse
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
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
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NdVisitClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdVisitClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdVisitClient("http://localhost:8080", builder)
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/visits"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.matchId").value(12))
            .andExpect(jsonPath("$.type").value("ON_SITE"))
            .andExpect(jsonPath("$.note").value("Fin de journée"))
            .andExpect(jsonPath("$.requestedAt").exists())
            .andRespond(withSuccess(json(CreateVisitResponse(visitId = 9)), MediaType.APPLICATION_JSON))

        assertEquals(9L, client.create(CreateVisitRequest(12, VisitType.ON_SITE, Date(), "Fin de journée")))
        server.verify()
    }

    @Test
    fun `open for match`() {
        server.expect(requestTo("http://localhost:8080/v1/visits?match-id=12&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(SearchVisitResponse(visits = listOf(VisitSummary(id = 9, status = VisitStatus.REQUESTED)))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(9L, client.openForMatch(12)?.id)
        server.verify()
    }

    @Test
    fun `no open visit`() {
        server.expect(requestTo("http://localhost:8080/v1/visits?match-id=12&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=1"))
            .andRespond(withSuccess(json(SearchVisitResponse()), MediaType.APPLICATION_JSON))

        assertNull(client.openForMatch(12))
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/visits/9"))
            .andRespond(
                withSuccess(
                    json(GetVisitResponse(Visit(id = 9, status = VisitStatus.REQUESTED))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(VisitStatus.REQUESTED, client.get(9).status)
    }

    @Test
    fun cancel() {
        server.expect(requestTo("http://localhost:8080/v1/visits/9/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.reason").value("Empêchement"))
            .andRespond(withStatus(HttpStatus.OK))

        client.cancel(9, CancelVisitRequest("Empêchement"))
        server.verify()
    }

    @Test
    fun `get - forbidden`() {
        server.expect(requestTo("http://localhost:8080/v1/visits/9")).andRespond(withStatus(HttpStatus.FORBIDDEN))
        assertFailsWith<HttpClientErrorException.Forbidden> { client.get(9) }
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
