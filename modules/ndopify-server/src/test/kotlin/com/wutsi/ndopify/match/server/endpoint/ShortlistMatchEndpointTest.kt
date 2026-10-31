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

// Same fixture as dismiss: 1 is ACTIVE, 4 DISMISSED, 5 agent 30's
@Sql(value = ["/db/test/clean.sql", "/db/test/match/DismissMatchEndpoint.sql"])
class ShortlistMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MatchRepository

    @Test
    fun shortlist() {
        val response = rest.postForEntity("/v1/matches/1/shortlist", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(1L).get()
        assertEquals(MatchStatus.SHORTLISTED, match.status)
        assertEquals(160000L, match.listingPrice) // Not a price said no to: unchanged
    }

    @Test
    fun `a run leaves it shortlisted`() {
        rest.postForEntity("/v1/matches/1/shortlist", null, Any::class.java)

        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(1L).get()
        assertEquals(MatchStatus.SHORTLISTED, match.status)
        assertEquals(0.9, match.score) // Not re-scored
    }

    @Test
    fun `listed on demand`() {
        rest.postForEntity("/v1/matches/1/shortlist", null, Any::class.java)

        val active = rest.getForEntity("/v1/matches?mandate-id=1", Map::class.java)
        val shortlisted = rest.getForEntity("/v1/matches?mandate-id=1&status=SHORTLISTED", Map::class.java)

        // 3 is ACTIVE, but its listing is rented
        assertEquals(listOf(2), (active.body!!["matches"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(listOf(1), (shortlisted.body!!["matches"] as List<*>).map { (it as Map<*, *>)["id"] })
    }

    @Test
    fun twice() {
        rest.postForEntity("/v1/matches/1/shortlist", null, Any::class.java)

        val response = rest.postForEntity("/v1/matches/1/shortlist", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_ACTIVE, response.body?.error?.code)
    }

    @Test
    fun dismissed() {
        val response = rest.postForEntity("/v1/matches/4/shortlist", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_ACTIVE, response.body?.error?.code)
    }

    @Test
    fun `match of another agent`() {
        val response = rest.postForEntity("/v1/matches/5/shortlist", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.ACTIVE, dao.findById(5L).get().status)
    }

    @Test
    fun `match not found`() {
        val response = rest.postForEntity("/v1/matches/999/shortlist", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/matches/1/shortlist", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
