package com.wutsi.ndopify.match.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/match/ProposeMatchEndpoint.sql",
        "/db/test/match/ResendMatchEndpoint.sql",
    ],
)
class ResendMatchEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: MatchRepository

    @Test
    fun `resend a proposed match`() {
        val response = rest.postForEntity("/v1/matches/2/resend", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(2L).get()
        assertEquals(MatchStatus.PROPOSED, match.status)
        assertNotNull(match.resentAt)
        // The 7-day answer window restarts: the expiry job won't take it tomorrow
        assertTrue(
            match.proposedAt!!.after(Date(System.currentTimeMillis() - 60_000)),
            "proposedAt not restarted: ${match.proposedAt}",
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val message = smtp.receivedMessages.single()
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", message.allRecipients.single().toString())
        val body = message.content.toString()
        assertTrue(body.contains("https://www.ndopify.com/match/2?token="))
        assertFalse(body.contains("cr&eacute;neau de visite"))
    }

    @Test
    fun `resend an accepted match`() {
        val before = dao.findById(12L).get()

        val response = rest.postForEntity("/v1/matches/12/resend", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val match = dao.findById(12L).get()
        assertEquals(MatchStatus.ACCEPTED, match.status)
        assertEquals(before.answeredAt, match.answeredAt)
        assertEquals(before.proposedAt, match.proposedAt)
        assertNotNull(match.resentAt)

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val body = smtp.receivedMessages.single().content.toString()
        assertTrue(body.contains("https://www.ndopify.com/match/12?token="))
        // The customer already said it was interested: the e-mail invites it to book a visit (accented as sent)
        assertTrue(body.contains("choisissez un cr&eacute;neau de visite"))
    }

    @Test
    fun `resend again too soon`() {
        rest.postForEntity("/v1/matches/2/resend", null, Any::class.java)

        val response = rest.postForEntity("/v1/matches/2/resend", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MATCH_RATE_LIMITED, response.body?.error?.code)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        assertFalse(smtp.waitForIncomingEmail(1000, 2), "only one email expected")
        assertEquals(1, smtp.receivedMessages.size)
    }

    @Test
    fun `resend an active match`() {
        assertConflict(1L, ErrorCode.MATCH_NOT_PROPOSED)
    }

    @Test
    fun `resend an expired match`() {
        // EXPIRED keeps POST /propose: a resend never changes the status
        assertConflict(11L, ErrorCode.MATCH_NOT_PROPOSED)
    }

    @Test
    fun `resend a dismissed match`() {
        assertConflict(4L, ErrorCode.MATCH_NOT_PROPOSED)
    }

    @Test
    fun `resend a match of a closed search`() {
        assertConflict(7L, ErrorCode.MANDATE_NOT_APPROVED)
    }

    @Test
    fun `resend a match whose listing was rented since`() {
        assertConflict(13L, ErrorCode.LISTING_NOT_PUBLISHED)
    }

    @Test
    fun `resend a match of another agent`() {
        // Match 5 is on search 2, agent 30's; the default user is Kim Possible, agent 20
        val response = rest.postForEntity("/v1/matches/5/resend", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    @Test
    fun `resend an unknown match`() {
        val response = rest.postForEntity("/v1/matches/999/resend", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MATCH_NOT_FOUND, response.body?.error?.code)
    }

    private fun assertConflict(id: Long, code: String) {
        val response = rest.postForEntity("/v1/matches/$id/resend", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(code, response.body?.error?.code)
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }
}
