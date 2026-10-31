package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/match/DismissMatchEndpoint.sql"])
class DismissMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MatchRepository

    @Test
    fun dismiss() {
        val response = rest.postForEntity("/v1/matches/1/dismiss", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(1L).get()
        assertEquals(MatchStatus.DISMISSED, match.status)
        assertEquals(150000L, match.listingPrice) // The price the agent said no to, scored at 160 000
    }

    @Test
    fun `a run leaves it dismissed`() {
        rest.postForEntity("/v1/matches/1/dismiss", null, Any::class.java)

        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MatchStatus.DISMISSED, dao.findById(1L).get().status)
        assertEquals(MatchStatus.DISMISSED, dao.findById(4L).get().status)
    }

    @Test
    fun `already dismissed`() {
        val response = rest.postForEntity("/v1/matches/4/dismiss", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_ACTIVE, response.body?.error?.code)
    }

    @Test
    fun `match of another agent`() {
        val response = rest.postForEntity("/v1/matches/5/dismiss", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.ACTIVE, dao.findById(5L).get().status)
    }

    @Test
    fun `match not found`() {
        val response = rest.postForEntity("/v1/matches/999/dismiss", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/matches/1/dismiss", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MatchStatus.ACTIVE, dao.findById(1L).get().status)
    }
}
