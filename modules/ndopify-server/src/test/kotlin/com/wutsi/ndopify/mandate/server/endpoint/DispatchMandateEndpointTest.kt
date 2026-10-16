package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.DispatchMandateResponse
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DispatchMandateEndpoint.sql"])
class DispatchMandateEndpointTest : TenantAwareEndpointIntegrationTest() {
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
        assertTrue(body.contains("150 000 XAF / mois"), body)
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
}
