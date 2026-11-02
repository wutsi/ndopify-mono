package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.MatchStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/match/RematchJob.sql"])
class RematchJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun rematch() {
        jobs.rematch()

        assertEquals(
            mapOf(
                (1L to 101L) to MatchStatus.ACTIVE, // Created: its run was lost
                (1L to 103L) to MatchStatus.DISMISSED, // Never touched
                (4L to 201L) to MatchStatus.ACTIVE, // Of tenant 2
            ),
            statuses(),
        ) // 1 x 102 deleted: no longer matching. Nothing for 2 (not approved), 3 (no agent), 5 (inactive tenant)
        assertEquals(0, smtp.receivedMessages.size) // The agents are e-mailed by notifyAgents
    }

    @Test
    fun `rematch twice`() {
        jobs.rematch()
        val first = statuses()

        jobs.rematch()

        assertEquals(first, statuses())
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun statuses(): Map<Pair<Long, Long>, MatchStatus> =
        jdbc.queryForList("SELECT mandate_id, listing_id, status FROM T_MATCH").associate { row ->
            ((row["mandate_id"] as Number).toLong() to (row["listing_id"] as Number).toLong()) to
                MatchStatus.valueOf(row["status"] as String)
        }
}
