package com.wutsi.ndopify.visit.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.visit.dto.SearchCoBrokerageVisitResponse
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/CoBrokerageVisitEndpoint.sql"])
class CoBrokerageVisitEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `the listing agent sees the other agent's confirmed and checked-in visits`() {
        val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", SearchCoBrokerageVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(2L, 1L), response.body!!.visits.map { it.id }) // scheduledAt ascending
        assertEquals(listOf(30L, 30L), response.body!!.visits.map { it.agentId })
        assertEquals(VisitStatus.CHECKED_IN, response.body!!.visits.first().status)
        assertEquals(VisitType.VIDEO, response.body!!.visits.first().type)
        assertEquals(VisitStatus.CONFIRMED, response.body!!.visits.last().status)
    }

    @Test
    fun `the answer carries no customer, note, code, address, report or feedback`() {
        val body = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", String::class.java).body!!

        assertFalse(body.contains("Ray Sponsible"))
        assertFalse(body.contains("ray.sponsible"))
        assertFalse(body.contains("1234"))
        assertFalse(body.contains("5678"))
        assertFalse(body.contains("note"))
        assertFalse(body.contains("address"))
        assertFalse(body.contains("feedback"))
        assertFalse(body.contains("agentComment"))
    }

    @Test
    fun `an empty listing answers an empty list`() {
        val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=104", SearchCoBrokerageVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.visits.isEmpty())
    }

    @Test
    fun `another agent is forbidden`() {
        userId = 12L // agent 30, not listing 101's agent

        val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `the customer is forbidden`() {
        userId = 13L

        val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `a missing listing is 404`() {
        val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `a non-positive listing id is 400`() {
        val response = rest.getForEntity("/v1/visits/co-brokerage?listing-id=0", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun `no listing id answers the caller's co-brokerage visits across its listings`() {
        val response = rest.getForEntity("/v1/visits/co-brokerage", SearchCoBrokerageVisitResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf(2L, 1L, 6L), response.body!!.visits.map { it.id }) // scheduledAt ascending
        assertEquals(listOf(101L, 101L, 102L), response.body!!.visits.map { it.listingId })
        assertEquals(listOf(30L, 30L, 30L), response.body!!.visits.map { it.agentId })
        assertEquals(VisitStatus.CHECKED_IN, response.body!!.visits.first().status)
    }

    @Test
    fun `no listing id - the answer carries no customer field`() {
        val response = rest.getForEntity("/v1/visits/co-brokerage", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertTrue(body.contains("listingId"))
        assertFalse(body.contains("Ray Sponsible"))
        assertFalse(body.contains("ray.sponsible"))
        assertFalse(body.contains("1234"))
        assertFalse(body.contains("5678"))
        assertFalse(body.contains("note"))
        assertFalse(body.contains("address"))
        assertFalse(body.contains("feedback"))
        assertFalse(body.contains("agentComment"))
    }

    @Test
    fun `no listing id - the customer is forbidden`() {
        userId = 13L

        val response = rest.getForEntity("/v1/visits/co-brokerage", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `no listing id - a caller without an agent is forbidden`() {
        userId = 17L

        val response = rest.getForEntity("/v1/visits/co-brokerage", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.VISIT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        assertEquals(
            HttpStatus.UNAUTHORIZED,
            rest.getForEntity("/v1/visits/co-brokerage?listing-id=101", Any::class.java).statusCode,
        )
    }
}
