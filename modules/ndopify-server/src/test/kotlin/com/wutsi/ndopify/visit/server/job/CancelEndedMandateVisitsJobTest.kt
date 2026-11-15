package com.wutsi.ndopify.visit.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.visit.dto.VisitStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/CancelMandateVisitsEndpoint.sql",
        "/db/test/visit/EndedMandates.sql",
    ],
)
class CancelEndedMandateVisitsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: VisitJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `the sweep cancels the open visits of ended mandates and keeps the checked-in one`() {
        jobs.cancelEndedMandates()

        assertEquals(
            mapOf(
                1L to VisitStatus.CANCELLED,
                2L to VisitStatus.CHECKED_IN,
                3L to VisitStatus.CANCELLED,
                4L to VisitStatus.CANCELLED,
            ),
            statuses(),
        )
        awaitAsyncTasks()
        val bodies = smtp.receivedMessages.toList().joinToString("\n") { it.content.toString() }
        assertFalse(bodies.contains("La visite est annul"), "a SYSTEM cancellation notifies neither side")
        assertFalse(
            smtp.receivedMessages.any { message ->
                message.allRecipients.any { it.toString().contains("john.doe@gmail.com") }
            },
            "B ended the listing mandate itself: no co-brokerage cancellation is mailed to B",
        )
    }

    private fun statuses(): Map<Long, VisitStatus> =
        jdbc.queryForList("SELECT id, status FROM T_VISIT").associate { row ->
            (row["id"] as Number).toLong() to VisitStatus.valueOf(row["status"] as String)
        }
}
