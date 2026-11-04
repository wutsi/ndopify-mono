package com.wutsi.ndopify.visit.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.visit.dto.VisitStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/VisitJobs.sql"])
class VisitJobsTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: VisitJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun expire() {
        jobs.expire()

        assertEquals(
            mapOf(
                1L to VisitStatus.EXPIRED,
                2L to VisitStatus.REQUESTED,
                3L to VisitStatus.CONFIRMED,
                4L to VisitStatus.CONFIRMED,
                5L to VisitStatus.CONFIRMED,
                6L to VisitStatus.CHECKED_IN,
                7L to VisitStatus.CANCELLED,
                21L to VisitStatus.EXPIRED,
                22L to VisitStatus.CONFIRMED,
            ),
            statuses(),
        )
        assertTrue(smtp.waitForIncomingEmail(5000, 4), "two e-mails per expired request")
        assertTrue(smtp.receivedMessages.any { it.subject == "Ndopify: votre demande de visite a expiré" })
    }

    @Test
    fun `nothing left to expire on the next run`() {
        jobs.expire()

        jobs.expire()

        assertTrue(smtp.waitForIncomingEmail(1000, 4))
        assertEquals(4, smtp.receivedMessages.size)
    }

    @Test
    fun noShow() {
        jobs.noShow()

        assertEquals(
            mapOf(
                1L to VisitStatus.REQUESTED,
                2L to VisitStatus.REQUESTED,
                3L to VisitStatus.CONFIRMED,
                4L to VisitStatus.NO_SHOW,
                5L to VisitStatus.CONFIRMED,
                6L to VisitStatus.CHECKED_IN,
                7L to VisitStatus.CANCELLED,
                21L to VisitStatus.REQUESTED,
                22L to VisitStatus.NO_SHOW,
            ),
            statuses(),
        )
        assertTrue(smtp.waitForIncomingEmail(5000, 4), "both sides are told for each agent no-show")
        assertTrue(smtp.receivedMessages.any { it.subject == "Ndopify: la visite n'a pas eu lieu" })
    }

    private fun statuses(): Map<Long, VisitStatus> =
        jdbc.queryForList("SELECT id, status FROM T_VISIT").associate { row ->
            (row["id"] as Number).toLong() to VisitStatus.valueOf(row["status"] as String)
        }
}
