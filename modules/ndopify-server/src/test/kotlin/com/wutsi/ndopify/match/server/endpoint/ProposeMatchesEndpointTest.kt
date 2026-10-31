package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.ProposeMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** `POST /v1/matches/propose`: several matches of one mandate, in one e-mail. Fixture: see `ProposeMatchEndpoint.sql`. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/ProposeMatchEndpoint.sql"])
class ProposeMatchesEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MatchRepository

    @Test
    fun propose() {
        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(listOf(1L, 10L)), Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        listOf(1L, 10L).forEach { id ->
            val match = dao.findById(id).get()
            assertEquals(MatchStatus.PROPOSED, match.status)
            assertNotNull(match.proposedAt)
        }

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val message = smtp.receivedMessages.single()
        assertEquals("Ndopify: Kim Possible vous propose 2 biens à Bastos (Yaounde)", message.subject)
        val body = message.content.toString()
        assertTrue(body.contains("https://www.ndopify.com/match/1"))
        assertTrue(body.contains("https://www.ndopify.com/match/10"))
        assertTrue(body.indexOf("Nice apartment") < body.indexOf("Kept apartment")) // Best score first
    }

    @Test
    fun `all or none`() {
        // 2 is already proposed: 1 stays ACTIVE
        assertConflict(listOf(1L, 2L), ErrorCode.MATCH_NOT_ACTIVE)
    }

    @Test
    fun `listing rented`() {
        assertConflict(listOf(1L, 3L), ErrorCode.LISTING_NOT_PUBLISHED)
    }

    @Test
    fun `matches of several mandates`() {
        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(listOf(1L, 8L)), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(MatchStatus.ACTIVE, dao.findById(1L).get().status)
    }

    @Test
    fun `no match`() {
        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(), ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `match of another agent`() {
        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(listOf(5L)), ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertEquals(MatchStatus.ACTIVE, dao.findById(5L).get().status)
    }

    @Test
    fun `match not found`() {
        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(listOf(1L, 999L)), ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(listOf(1L)), ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun assertConflict(ids: List<Long>, code: String) {
        val response = rest.postForEntity("/v1/matches/propose", ProposeMatchRequest(ids), ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertEquals(MatchStatus.ACTIVE, dao.findById(1L).get().status)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }
}
