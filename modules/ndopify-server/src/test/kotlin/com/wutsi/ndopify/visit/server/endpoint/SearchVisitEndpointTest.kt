package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.SearchVisitResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/SearchVisitEndpoint.sql"])
class SearchVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `an agent sees its own visits only`() {
        val visits = search("/v1/visits")

        assertEquals(listOf(1L, 2L, 3L, 5L), visits.map { it.id }) // Soonest first
    }

    @Test
    fun `an agent asking for another agent's visits`() {
        val response = rest.getForEntity("/v1/visits?agent-id=30", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `a customer sees the visits of its own mandates only`() {
        userId = 13L

        val visits = search("/v1/visits")

        assertEquals(listOf(1L, 2L, 4L, 5L), visits.map { it.id })
    }

    @Test
    fun `a customer asking for another customer's visits`() {
        userId = 13L

        val response = rest.getForEntity("/v1/visits?customer-id=11", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `a party that is neither an agent nor a customer`() {
        userId = 17L

        val response = rest.getForEntity("/v1/visits", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `status filter`() {
        val visits = search("/v1/visits?status=CONFIRMED")

        assertEquals(listOf(1L, 2L, 3L), visits.map { it.id })
    }

    @Test
    fun `scheduled range`() {
        val from = Instant.ofEpochMilli(at("2026-12-01", 10, 30).toEpochMilli())
        val to = Instant.ofEpochMilli(at("2026-12-02", 23, 59).toEpochMilli())

        val visits = search("/v1/visits?scheduled-from=$from&scheduled-to=$to")

        assertEquals(listOf(2L, 3L), visits.map { it.id })
    }

    @Test
    fun `by listing`() {
        assertEquals(listOf(1L, 3L), search("/v1/visits?listing-id=101").map { it.id })
        assertEquals(listOf(2L), search("/v1/visits?listing-id=102").map { it.id })
        assertEquals(emptyList(), search("/v1/visits?listing-id=109").map { it.id }) // Another agent's visit
    }

    @Test
    fun `limit and offset`() {
        val visits = search("/v1/visits?limit=1&offset=1")

        assertEquals(listOf(2L), visits.map { it.id })
    }

    @Test
    fun `no visit`() {
        val visits = search("/v1/visits?status=COMPLETED")

        assertTrue(visits.isEmpty())
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/visits", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun search(url: String) =
        rest.getForEntity(url, SearchVisitResponse::class.java).body!!.visits

    private fun at(date: String, hour: Int, minute: Int): Instant =
        LocalDate.parse(date).atTime(LocalTime.of(hour, minute)).atZone(ZoneId.of("Africa/Douala")).toInstant()
}
