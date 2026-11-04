package com.wutsi.ndopify.visit.server.job

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

/** The 24 h reminder, end to end: one per due visit, to both sides, and never twice. */
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/RemindVisitsJob.sql"])
class RemindVisitsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: VisitJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun remind() {
        jobs.remind()

        assertTrue(smtp.waitForIncomingEmail(5000, 4), "two e-mails per reminded visit")
        assertEquals(
            "Ndopify: rappel de votre visite",
            smtp.receivedMessages.first { it.allRecipients.single().toString().contains("ray.sponsible") }.subject,
        )
        assertNotNull(reminderSentAt(1L))
        assertNotNull(reminderSentAt(5L)) // Tenant 2
        assertNull(reminderSentAt(2L)) // In more than 24 h
        assertNotNull(reminderSentAt(3L)) // Already reminded (confirmed less than 24 h before)
        assertNull(reminderSentAt(4L)) // Cancelled
    }

    @Test
    fun `reminded once`() {
        jobs.remind()
        assertTrue(smtp.waitForIncomingEmail(5000, 4))

        jobs.remind()

        assertFalse(smtp.waitForIncomingEmail(1000, 5), "no second reminder")
        assertEquals(4, smtp.receivedMessages.size)
    }

    private fun reminderSentAt(id: Long): Any? =
        jdbc.queryForList("SELECT reminder_sent_at FROM T_VISIT WHERE id = ?", id).single()["reminder_sent_at"]
}
