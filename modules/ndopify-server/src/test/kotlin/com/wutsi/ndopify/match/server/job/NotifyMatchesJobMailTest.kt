package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The job with the real mailets, out of any transaction like the scheduler: the mailets read the mandates' lazy
 * neighbourhoods, which a session closed after the selection made fail (LazyInitializationException).
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/NotifyCoBrokerageJob.sql"])
class NotifyMatchesJobMailTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `e-mails the own and the co-brokerage matches`() {
        jobs.notifyAgents()

        assertTrue(smtp.waitForIncomingEmail(5000, 2))
        val subjects = smtp.receivedMessages.map { it.subject }
        assertEquals(2, subjects.size)
        assertTrue(subjects.any { it.contains("d'un autre agent") })

        assertTrue(notifiedAt(1L))
        assertTrue(notifiedAt(2L))
    }

    private fun notifiedAt(id: Long): Boolean =
        jdbc.queryForList("SELECT notified_at FROM T_MATCH WHERE id=?", id).single()["notified_at"] != null
}
