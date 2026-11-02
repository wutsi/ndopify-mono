package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.MatchStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/ExpireMatchesJob.sql"])
class ExpireMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun expire() {
        jobs.expire()

        assertEquals(
            mapOf(
                1L to MatchStatus.EXPIRED,
                2L to MatchStatus.PROPOSED, // Not 7 days yet
                3L to MatchStatus.ACCEPTED,
                4L to MatchStatus.REJECTED,
                5L to MatchStatus.ACTIVE,
                6L to MatchStatus.EXPIRED,
                21L to MatchStatus.EXPIRED,
                31L to MatchStatus.PROPOSED, // Inactive tenant
            ),
            statuses(),
        )
        assertEquals(0, smtp.receivedMessages.size)
    }

    @Test
    fun `nothing left to expire on the next run`() {
        jobs.expire()
        val modifiedAt = modifiedAt()

        jobs.expire()

        assertEquals(modifiedAt, modifiedAt())
        assertTrue(modifiedAt.isNotEmpty())
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun statuses(): Map<Long, MatchStatus> =
        jdbc.queryForList("SELECT id, status FROM T_MATCH").associate { row ->
            (row["id"] as Number).toLong() to MatchStatus.valueOf(row["status"] as String)
        }

    private fun modifiedAt(): Map<Long, Any?> =
        jdbc.queryForList("SELECT id, modified_at FROM T_MATCH WHERE status = 'EXPIRED'").associate { row ->
            (row["id"] as Number).toLong() to row["modified_at"]
        }
}
