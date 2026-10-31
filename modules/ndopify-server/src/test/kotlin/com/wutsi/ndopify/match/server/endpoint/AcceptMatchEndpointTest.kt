package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The customer answers signed in to the e-signature portal: the only sign-in it has today. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class AcceptMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application = ApplicationCode.E_SIGNATURE

    @Autowired
    private lateinit var dao: MatchRepository

    @BeforeTest
    fun asCustomer() {
        userId = 13L // Ray Sponsible, the customer of search 1
    }

    @Test
    fun accept() {
        val response = rest.postForEntity("/v1/matches/2/accept", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(2L).get()
        assertEquals(MatchStatus.ACCEPTED, match.status)
        assertNotNull(match.answeredAt)
    }

    @Test
    fun twice() {
        rest.postForEntity("/v1/matches/2/accept", null, Any::class.java)

        assertConflict(2L)
    }

    @Test
    fun `not proposed`() {
        assertConflict(1L) // Active
        assertConflict(7L) // Already accepted
    }

    @Test
    fun `the agent can't answer for the customer`() {
        userId = 11L

        assertForbidden(2L)
    }

    @Test
    fun `another customer`() {
        userId = 14L // Sam Sung, the customer of search 4

        assertForbidden(2L)
    }

    @Test
    fun `match not found`() {
        val response = rest.postForEntity("/v1/matches/999/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/matches/2/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MatchStatus.PROPOSED, dao.findById(2L).get().status)
    }

    private fun assertForbidden(id: Long) {
        val response = rest.postForEntity("/v1/matches/$id/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.PROPOSED, dao.findById(id).get().status)
    }

    private fun assertConflict(id: Long) {
        val response = rest.postForEntity("/v1/matches/$id/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_PROPOSED, response.body?.error?.code)
    }
}
