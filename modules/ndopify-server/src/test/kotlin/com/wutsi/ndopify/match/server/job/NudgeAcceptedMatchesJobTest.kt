package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The agent nudge, end to end: one per idle accepted match, and never twice. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/NudgeAcceptedMatchesJob.sql"])
class NudgeAcceptedMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun nudge() {
        val alreadyNudged = agentVisitNudgedAt(4L)

        jobs.nudgeAcceptedWithoutVisitAgent()

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "one e-mail per idle accepted match")
        assertEquals(
            "Ndopify: Ray Sponsible attend une visite",
            smtp.receivedMessages.first { it.allRecipients.single().toString().contains("kim.possible") }.subject,
        )
        val body = smtp.receivedMessages.first { it.allRecipients.single().toString().contains("kim.possible") }
            .content.toString()
        assertTrue(body.contains("Planifier la visite"))
        assertTrue(body.contains("https://partner.ndopify.com/mandates/1#match-1"))

        assertNotNull(agentVisitNudgedAt(1L)) // Nudged
        assertNotNull(agentVisitNudgedAt(21L)) // Tenant 2
        assertNotNull(agentVisitNudgedAt(7L)) // No agent e-mail nor chat: marked as handled
        assertNull(agentVisitNudgedAt(2L)) // Accepted 1 day ago
        assertNull(agentVisitNudgedAt(3L)) // Open visit
        assertEquals(alreadyNudged, agentVisitNudgedAt(4L)) // Already nudged, left as is
        assertNull(agentVisitNudgedAt(5L)) // Listing rented
        assertNull(agentVisitNudgedAt(6L)) // Mandate closed
        assertNull(agentVisitNudgedAt(31L)) // Inactive tenant
    }

    @Test
    fun `nudged once`() {
        jobs.nudgeAcceptedWithoutVisitAgent()
        assertTrue(smtp.waitForIncomingEmail(5000, 2))

        jobs.nudgeAcceptedWithoutVisitAgent()

        assertFalse(smtp.waitForIncomingEmail(1000, 3), "no second nudge")
        assertEquals(2, smtp.receivedMessages.size)
    }

    private fun agentVisitNudgedAt(id: Long): Any? =
        jdbc.queryForList("SELECT agent_visit_nudged_at FROM T_MATCH WHERE id = ?", id).single()["agent_visit_nudged_at"]
}
