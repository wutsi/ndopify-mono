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

/** The visit reminder, end to end: one per idle accepted match, and never twice. */
@Sql(value = ["/db/test/clean.sql", "/db/test/match/RemindAcceptedMatchesJob.sql"])
class RemindAcceptedMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun remind() {
        val alreadyReminded = visitRemindedAt(4L)

        jobs.remindAcceptedWithoutVisit()

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "one e-mail per idle accepted match")
        assertEquals(
            "Votre recherche à Yaounde · mandat 1",
            smtp.receivedMessages.first { it.allRecipients.single().toString().contains("ray.sponsible") }.subject,
        )
        val body = smtp.receivedMessages.first { it.allRecipients.single().toString().contains("ray.sponsible") }
            .content.toString()
        assertTrue(body.contains("Demander une visite"))
        assertTrue(body.contains("/match/1?token="))

        assertNotNull(visitRemindedAt(1L)) // Reminded
        assertNotNull(visitRemindedAt(21L)) // Tenant 2
        assertNotNull(visitRemindedAt(7L)) // No e-mail: marked as handled
        assertNull(visitRemindedAt(2L)) // Accepted 2 days ago
        assertNull(visitRemindedAt(3L)) // Open visit
        assertEquals(alreadyReminded, visitRemindedAt(4L)) // Already reminded, left as is
        assertNull(visitRemindedAt(5L)) // Listing rented
        assertNull(visitRemindedAt(6L)) // Mandate closed
        assertNull(visitRemindedAt(31L)) // Inactive tenant
    }

    @Test
    fun `reminded once`() {
        jobs.remindAcceptedWithoutVisit()
        assertTrue(smtp.waitForIncomingEmail(5000, 2))

        jobs.remindAcceptedWithoutVisit()

        assertFalse(smtp.waitForIncomingEmail(1000, 3), "no second reminder")
        assertEquals(2, smtp.receivedMessages.size)
    }

    private fun visitRemindedAt(id: Long): Any? =
        jdbc.queryForList("SELECT visit_reminded_at FROM T_MATCH WHERE id = ?", id).single()["visit_reminded_at"]
}
