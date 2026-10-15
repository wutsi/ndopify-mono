package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"])
class AcceptAssignmentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun accept() {
        val response = rest.postForEntity("/v1/assignments/1/accept", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val assignment = rest.getForEntity("/v1/assignments/1", GetAssignmentResponse::class.java).body!!.assignment
        assertEquals(AssignmentStatus.ACCEPTED, assignment.status)

        val mandate = rest.getForEntity("/v1/mandates/1", GetMandateResponse::class.java).body!!.mandate
        assertEquals(20L, mandate.agentId)
        assertEquals(MandateStatus.ASSIGNED, mandate.status)

        // The customer is told who its agent is, asynchronously
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        val messages = smtp.receivedMessages
        assertEquals(1, messages.size)
        assertEquals("Ndopify: Un agent a été assigné à votre mandat", messages[0].subject)
        assertEquals("Ray Sponsible <ray.sponsible@gmail.com>", messages[0].allRecipients.single().toString())

        val body = messages[0].content.toString()
        assertTrue(body.contains("Ray Sponsible"))
        assertTrue(body.contains("Kim Possible"))
        assertTrue(body.contains("+237670000020"))
        assertTrue(body.contains("10 ans"))
    }

    @Test
    fun `assignment not found`() {
        val response = rest.postForEntity("/v1/assignments/999/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `assignment from another tenant is not found`() {
        val response = rest.postForEntity("/v1/assignments/9/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/assignments/1/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertStatus(1, AssignmentStatus.PENDING)
    }

    @Test
    fun `offer of another agent`() {
        val response = rest.postForEntity("/v1/assignments/2/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
        assertStatus(2, AssignmentStatus.PENDING)
        assertNoEmail()
    }

    @Test
    fun `offer of another agent no longer pending is forbidden, not conflict`() {
        val response = rest.postForEntity("/v1/assignments/5/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/mandate/AcceptAssignmentEndpoint.sql"],
        statements = ["UPDATE T_MEMBERSHIP SET party_id = NULL WHERE user_id = 11"],
    )
    fun `user without party`() {
        val response = rest.postForEntity("/v1/assignments/1/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
        assertStatus(1, AssignmentStatus.PENDING)
    }

    @Test
    fun `assignment not pending`() {
        val response = rest.postForEntity("/v1/assignments/3/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, response.body?.error?.code)
        assertStatus(3, AssignmentStatus.ACCEPTED)
        assertNoEmail()
    }

    @Test
    fun `assignment past its TTL`() {
        val response = rest.postForEntity("/v1/assignments/4/accept", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, response.body?.error?.code)
        assertStatus(4, AssignmentStatus.PENDING)
    }

    private fun assertNoEmail() {
        // The email is sent asynchronously: give a wrongly sent one the time to arrive
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no email expected")
    }

    private fun assertStatus(id: Long, status: AssignmentStatus) {
        val assignment = rest.getForEntity("/v1/assignments/$id", GetAssignmentResponse::class.java).body!!.assignment
        assertEquals(status, assignment.status)
    }
}
