package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.DispatchMandateResponse
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DispatchMandateEndpoint.sql"])
class DispatchMandateEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    private fun dispatch(mandateId: Long) =
        rest.postForEntity("/v1/mandates/$mandateId/dispatch", null, DispatchMandateResponse::class.java)

    private fun assignments(mandateId: Long) =
        rest.getForEntity("/v1/assignments?mandate-id=$mandateId", SearchAssignmentResponse::class.java)
            .body!!.assignments

    @Test
    fun dispatch() {
        val now = System.currentTimeMillis()
        val response = dispatch(3)

        assertEquals(HttpStatus.OK, response.statusCode)
        val assignmentId = response.body!!.assignmentId
        assertNotNull(assignmentId)

        // Agent 20 already works on mandate 4: the idle agent 30 wins
        val assignment = rest.getForEntity("/v1/assignments/$assignmentId", GetAssignmentResponse::class.java)
            .body!!.assignment
        assertEquals(3L, assignment.mandateId)
        assertEquals(30L, assignment.agentId)
        assertEquals(AssignmentStatus.PENDING, assignment.status)
        assertTrue(assignment.expiresAt.time >= now + 3_600_000 - 1000) // 1h TTL, no night in the tests
    }

    @Test
    fun `the agent is e-mailed the offer`() {
        dispatch(3)

        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received") // sent asynchronously
        val messages = smtp.receivedMessages
        assertEquals(1, messages.size)
        assertEquals("John Doe <john.doe@gmail.com>", messages[0].allRecipients.single().toString())
        assertEquals("Ndopify: Un nouveau mandat vous est proposé à Yaounde", messages[0].subject)

        val body = messages[0].content.toString()
        assertTrue(body.contains("John Doe"), body)
        assertTrue(body.contains("Appartement"), body)
        assertTrue(body.contains("150 000 FCFA / mois"), body) // T_TENANT's default monetary_format
        assertTrue(body.contains("http://localhost:8081/partner-central"), body)
    }

    @Test
    fun `already offered - nothing offered`() {
        val response = dispatch(1)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body!!.assignmentId)
        assertEquals(listOf(1L), assignments(1).map { it.id })
    }

    @Test
    fun `waiting for too long - moved to manual assignment`() {
        val response = dispatch(5)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body!!.assignmentId)
        assertTrue(assignments(5).isEmpty())

        val mandate = rest.getForEntity("/v1/mandates/5", GetMandateResponse::class.java).body!!.mandate
        assertEquals(MandateStatus.MANUAL_ASSIGNMENT, mandate.status)
    }

    @Test
    fun `already assigned - nothing offered`() {
        val response = dispatch(4)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body!!.assignmentId)
        assertTrue(assignments(4).isEmpty())
    }

    @Test
    fun `query count does not grow with the number of candidates`() {
        // The mandate has a neighbourhood, so the dispatcher reads every candidate's neighbourhoods to score it.
        // More candidates than default_batch_fetch_size (50): a per-batch load would add a query, a SUBSELECT not.
        jdbc.update(
            "INSERT IGNORE INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name) " +
                "VALUES (111, 2370201, 'NEIGHBORHOOD', 'CM', 'Bastos', 'Bastos')",
        )
        awaitAsyncTasks()
        insertMandate(6)
        val few = countQueries {
            dispatch(6)
            awaitAsyncTasks() // the offer e-mail runs on another thread: count its queries too, or the comparison races
        }

        (1..60).forEach { i ->
            val partyId = 1000L + i
            jdbc.update(
                "INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, kyc_status) " +
                    "VALUES (?, 1, ?, 'Agent', ?, 'VERIFIED')",
                partyId,
                "candidate$i@example.com",
                "Number$i",
            )
            jdbc.update(
                "INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES (?, 1, ?, 2370201)",
                2000L + i,
                partyId,
            )
        }
        insertMandate(7)
        val many = countQueries {
            dispatch(7)
            awaitAsyncTasks()
        }

        assertEquals(few, many)
    }

    @Test
    fun `mandate not found`() {
        val response = rest.postForEntity("/v1/mandates/999/dispatch", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `mandate from another tenant is not found`() {
        val response = rest.postForEntity("/v1/mandates/9/dispatch", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    private fun insertMandate(id: Long) {
        jdbc.update(
            "INSERT INTO T_MANDATE(id, tenant_id, customer_id, city_id, status, mandate_type, listing_type, " +
                "created_at, modified_at) " +
                "VALUES (?, 1, 10, 2370201, 'PENDING_ASSIGNMENT', 'SEARCH', 'RENTAL', NOW(), NOW())",
            id,
        )
        jdbc.update("INSERT INTO T_MANDATE_NEIGHBORHOOD(mandate_id, neighborhood_id) VALUES (?, 111)", id)
    }
}
