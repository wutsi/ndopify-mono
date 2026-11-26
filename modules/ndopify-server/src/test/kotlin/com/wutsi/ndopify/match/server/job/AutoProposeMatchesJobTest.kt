package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.MatchStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/AutoProposeMatchesJob.sql"])
class AutoProposeMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun autoPropose() {
        jobs.autoPropose()
        awaitAsyncTasks()

        assertEquals(
            mapOf(
                1L to MatchStatus.PROPOSED,
                2L to MatchStatus.PROPOSED,
                3L to MatchStatus.ACTIVE, // 23h old
                4L to MatchStatus.SHORTLISTED,
                5L to MatchStatus.DISMISSED,
                6L to MatchStatus.PROPOSED, // already proposed
                7L to MatchStatus.EXPIRED,
                8L to MatchStatus.ACTIVE, // listing rented
                9L to MatchStatus.ACTIVE, // search closed
                10L to MatchStatus.PROPOSED,
                20L to MatchStatus.PROPOSED, // co-brokerage
                31L to MatchStatus.ACTIVE, // inactive tenant
            ),
            statuses(),
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "customer e-mails not received")
        assertEquals(2, smtp.receivedMessages.size)
        val subjects = smtp.receivedMessages.map { it.subject }
        assertTrue(subjects.any { it == "Votre recherche : appartement à Yaounde · mandat 1" })
        assertTrue(subjects.any { it == "Votre recherche : appartement à Yaounde · mandat 2" })

        val body = smtp.receivedMessages
            .first { it.subject == "Votre recherche : appartement à Yaounde · mandat 1" }
            .content.toString()
        assertTrue(body.contains("https://www.ndopify.com/match/1"))
        assertTrue(body.contains("https://www.ndopify.com/match/2"))
        assertTrue(body.contains("https://www.ndopify.com/match/20"))
        assertTrue(body.contains("Kim Possible"))
        assertFalse(body.contains("John Doe")) // anti-sautage: never the counterpart agent
    }

    @Test
    fun `nothing left on the next run`() {
        jobs.autoPropose()
        awaitAsyncTasks()
        val proposedAt = proposedAt()

        jobs.autoPropose()

        assertEquals(proposedAt, proposedAt())
        assertEquals(2, smtp.receivedMessages.size)
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun statuses(): Map<Long, MatchStatus> =
        jdbc.queryForList("SELECT id, status FROM T_MATCH").associate { row ->
            (row["id"] as Number).toLong() to MatchStatus.valueOf(row["status"] as String)
        }

    private fun proposedAt(): Map<Long, Any?> =
        jdbc.queryForList("SELECT id, proposed_at FROM T_MATCH WHERE status = 'PROPOSED'").associate { row ->
            (row["id"] as Number).toLong() to row["proposed_at"]
        }
}
