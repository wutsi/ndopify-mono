package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The customer answers signed in to the e-signature portal: the only sign-in it has today. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class RejectMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val application = ApplicationCode.E_SIGNATURE

    @Autowired
    private lateinit var dao: MatchRepository

    @BeforeTest
    fun asCustomer() {
        userId = 13L // Ray Sponsible, the customer of search 1
    }

    @Test
    fun reject() {
        val response = rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(reason = " Trop loin "), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(2L).get()
        assertEquals(MatchStatus.REJECTED, match.status)
        assertNotNull(match.answeredAt)
        assertEquals("Trop loin", match.rejectionReason)
        assertEquals(140000L, match.listingPrice) // The price the customer said no to
    }

    @Test
    fun `without reason`() {
        val response = rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(dao.findById(2L).get().rejectionReason)
    }

    @Test
    fun `blank reason`() {
        rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(reason = "  "), Any::class.java)

        assertNull(dao.findById(2L).get().rejectionReason)
    }

    @Test
    fun `reason too long`() {
        val request = RejectMatchRequest(reason = "x".repeat(1001))

        val response = rest.postForEntity("/v1/matches/2/reject", request, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(MatchStatus.PROPOSED, dao.findById(2L).get().status)
    }

    @Test
    fun `final - no accept after`() {
        rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(), Any::class.java)

        val response = rest.postForEntity("/v1/matches/2/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_PROPOSED, response.body?.error?.code)
        assertEquals(MatchStatus.REJECTED, dao.findById(2L).get().status)
    }

    @Test
    fun `not proposed`() {
        val response = rest.postForEntity("/v1/matches/7/reject", RejectMatchRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_PROPOSED, response.body?.error?.code)
        assertEquals(MatchStatus.ACCEPTED, dao.findById(7L).get().status)
    }

    @Test
    fun `the agent can't answer for the customer`() {
        userId = 11L

        val response = rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.PROPOSED, dao.findById(2L).get().status)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/matches/2/reject", RejectMatchRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MatchStatus.PROPOSED, dao.findById(2L).get().status)
    }
}
