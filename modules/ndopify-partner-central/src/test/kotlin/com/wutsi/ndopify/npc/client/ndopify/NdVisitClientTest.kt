package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.GetVisitResponse
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.SearchVisitResponse
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
import java.time.Instant
import java.util.Date
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
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(GetVisitResponse(Visit(id = 7001, status = VisitStatus.CONFIRMED, type = VisitType.ON_SITE))),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val visit = client.get(7001)
        assertEquals(VisitStatus.CONFIRMED, visit.status)
        assertNull(visit.code)
        server.verify()
    }

    @Test
    fun search() {
        server.expect(
            requestTo("http://localhost:8080/v1/visits?agent-id=20&status=REQUESTED&status=CONFIRMED&status=CHECKED_IN&limit=20"),
        )
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    json(
                        SearchVisitResponse(
                            listOf(
                                VisitSummary(id = 7001, status = VisitStatus.REQUESTED),
                                VisitSummary(id = 7002, status = VisitStatus.CONFIRMED),
                            ),
                        ),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertEquals(
            listOf(7001L, 7002L),
            client.search(20, listOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)).map { it.id },
        )
        server.verify()
    }

    @Test
    fun schedule() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001/schedule"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.scheduledAt").exists())
            .andExpect(jsonPath("$.type").value("ON_SITE"))
            .andRespond(withSuccess())

        client.schedule(
            7001,
            ScheduleVisitRequest(Date.from(Instant.parse("2026-11-07T09:30:00Z")), VisitType.ON_SITE),
        )
        server.verify()
    }

    @Test
    fun cancel() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001/cancel"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json(json(CancelVisitRequest("Empêchement"))))
            .andExpect(jsonPath("$.reason").value("Empêchement"))
            .andRespond(withSuccess())

        client.cancel(7001, CancelVisitRequest("Empêchement"))
        server.verify()
    }

    @Test
    fun checkIn() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001/check-in"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.code").value("4827"))
            .andExpect(jsonPath("$.latitude").value(4.0247))
            .andExpect(jsonPath("$.longitude").value(9.6943))
            .andRespond(withSuccess())

        client.checkIn(7001, CheckInVisitRequest("4827", 4.0247, 9.6943, 12.0))
        server.verify()
    }

    @Test
    fun noShow() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001/no-show"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.reason").value("CUSTOMER_ABSENT"))
            .andRespond(withSuccess())

        client.noShow(7001, NoShowVisitRequest(VisitNoShowReason.CUSTOMER_ABSENT))
        server.verify()
    }

    @Test
    fun checkOut() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001/check-out"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.comment").value("Client intéressé, reviendra avec son épouse."))
            .andRespond(withSuccess())

        client.checkOut(7001, CheckOutVisitRequest("Client intéressé, reviendra avec son épouse."))
        server.verify()
    }

    @Test
    fun `get - another agent`() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001"))
            .andRespond(withStatus(HttpStatus.FORBIDDEN))

        assertFailsWith<HttpClientErrorException.Forbidden> { client.get(7001) }
    }

    @Test
    fun `check-in - conflict`() {
        server.expect(requestTo("http://localhost:8080/v1/visits/7001/check-in"))
            .andRespond(withStatus(HttpStatus.CONFLICT))

        assertFailsWith<HttpClientErrorException.Conflict> {
            client.checkIn(7001, CheckInVisitRequest("4827", 4.0247, 9.6943, 12.0))
        }
    }

    private fun json(value: Any): String = JsonMapper.builder().build().writeValueAsString(value)
}
