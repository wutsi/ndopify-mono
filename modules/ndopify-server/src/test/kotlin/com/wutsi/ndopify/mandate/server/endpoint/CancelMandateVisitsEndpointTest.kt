package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateCancellationCode
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/visit/CancelMandateVisitsEndpoint.sql"])
class CancelMandateVisitsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `cancelling a search mandate cancels its open visits, keeps the checked-in one, and never copies its reason`() {
        rest.postForEntity(
            "/v1/mandates/1/cancel",
            CancelMandateRequest(
                cancellationCode = MandateCancellationCode.FOUND_INDEPENDENTLY,
                cancellationReason = "J'ai trouvé un appartement par un ami",
            ),
            Any::class.java,
        )

        assertEquals("CANCELLED", visit(1)["status"])
        assertEquals("SYSTEM", visit(1)["cancelled_by"])
        assertNotNull(visit(1)["cancelled_at"])
        assertNull(visit(1)["cancellation_reason"])
        assertEquals("CANCELLED", visit(3)["status"])
        assertEquals("CHECKED_IN", visit(2)["status"])

        assertTrue(smtp.waitForIncomingEmail(5000, 5), "customer + A for both cancelled visits, plus B for visit 1")
        val bodies = smtp.receivedMessages.toList().joinToString("\n") { it.content.toString() }
        assertTrue(bodies.contains("La visite est annul"), "the neutral wording, to both sides")
        assertFalse(bodies.contains("J'ai trouvé un appartement par un ami"))
        assertTrue(
            smtp.receivedMessages.any { it.subject == "Ndopify: la visite de votre bien est annulée" },
        )
    }

    @Test
    fun `cancelling a listing mandate cancels the visits on its listing`() {
        rest.postForEntity("/v1/mandates/11/cancel", CancelMandateRequest(cancellationCode = MandateCancellationCode.OTHER), Any::class.java)

        assertEquals("CANCELLED", visit(1)["status"])
        assertEquals("CANCELLED", visit(3)["status"])
        assertEquals("CANCELLED", visit(4)["status"])
        assertEquals("CHECKED_IN", visit(2)["status"])
    }

    private fun visit(id: Long): Map<String, Any?> =
        jdbc.queryForMap("SELECT status, cancelled_by, cancelled_at, cancellation_reason FROM T_VISIT WHERE id=?", id)
}
