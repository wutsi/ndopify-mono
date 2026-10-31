package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.GetMatchResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/match/GetMatchEndpoint.sql"])
class GetMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/matches/1", GetMatchResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = response.body!!.match
        assertEquals(1L, match.id)
        assertEquals(1L, match.mandateId)
        assertEquals(MatchStatus.ACTIVE, match.status)
        assertEquals(0.9, match.score)
        assertEquals(1.0, match.neighborhoodScore)
        assertEquals(1.0, match.priceScore)
        assertEquals(0.7, match.roomsScore)
        assertEquals(0.5, match.amenitiesScore)
        assertEquals(1.0, match.availabilityScore)
        assertEquals(1.0, match.furnitureScore)
        assertEquals(101L, match.listingId)
        assertEquals(160000L, match.listingPrice)
    }

    @Test
    fun `match not found`() {
        val response = rest.getForEntity("/v1/matches/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `match of another tenant is not found`() {
        val response = rest.getForEntity("/v1/matches/9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `match of another agent`() {
        val response = rest.getForEntity("/v1/matches/5", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }
}
