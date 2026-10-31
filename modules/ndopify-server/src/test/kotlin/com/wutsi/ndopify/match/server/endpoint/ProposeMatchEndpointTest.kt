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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class ProposeMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MatchRepository

    @Test
    fun propose() {
        val response = rest.postForEntity("/v1/matches/1/propose", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(1L).get()
        assertEquals(MatchStatus.PROPOSED, match.status)
        assertNotNull(match.proposedAt)
        assertEquals(150000L, match.listingPrice) // The price the customer is shown, scored at 160 000

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        assertEquals("Ndopify: Kim Possible vous propose un bien à Bastos (Yaounde)", message.subject)
        val body = message.content.toString()
        assertTrue(body.contains("https://www.ndopify.com/match/1"))
        assertTrue(body.contains("Nice apartment"))
    }

    @Test
    fun `a run leaves it proposed`() {
        rest.postForEntity("/v1/matches/1/propose", null, Any::class.java)

        val response = rest.postForEntity("/v1/matches/run?mandate-id=1", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MatchStatus.PROPOSED, dao.findById(1L).get().status)
    }

    @Test
    fun `not active`() {
        assertConflict(4L, ErrorCode.MATCH_NOT_ACTIVE)
        assertConflict(2L, ErrorCode.MATCH_NOT_ACTIVE) // Already proposed
    }

    @Test
    fun `search closed`() {
        assertConflict(6L, ErrorCode.MANDATE_NOT_APPROVED)
    }

    @Test
    fun `listing rented`() {
        assertConflict(3L, ErrorCode.LISTING_NOT_PUBLISHED)
    }

    @Test
    fun `match of another agent`() {
        val response = rest.postForEntity("/v1/matches/5/propose", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.ACTIVE, dao.findById(5L).get().status)
    }

    @Test
    fun `match not found`() {
        val response = rest.postForEntity("/v1/matches/999/propose", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/matches/1/propose", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(MatchStatus.ACTIVE, dao.findById(1L).get().status)
    }

    private fun assertConflict(id: Long, code: String) {
        val status = dao.findById(id).get().status

        val response = rest.postForEntity("/v1/matches/$id/propose", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertEquals(status, dao.findById(id).get().status)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }
}
