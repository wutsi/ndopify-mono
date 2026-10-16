package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DeclineAssignmentEndpoint.sql"])
class DeclineAssignmentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun decline() {
        val response = rest.postForEntity("/v1/assignments/1/decline", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val assignment = rest.getForEntity("/v1/assignments/1", GetAssignmentResponse::class.java).body!!.assignment
        assertEquals(AssignmentStatus.DECLINED, assignment.status)

        // The mandate is still waiting for an agent, once re-dispatched
        awaitAsyncTasks()
        val mandate = rest.getForEntity("/v1/mandates/1", GetMandateResponse::class.java).body!!.mandate
        assertNull(mandate.agentId)
        assertEquals(MandateStatus.PENDING_ASSIGNMENT, mandate.status)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/mandate/DeclineAssignmentEndpoint.sql"],
        statements = ["UPDATE T_PARTY SET kyc_status = 'VERIFIED' WHERE id IN (200, 300)"],
    )
    fun `the mandate goes to the next agent`() {
        rest.postForEntity("/v1/assignments/1/decline", null, Any::class.java)
        awaitAsyncTasks() // Re-dispatched after commit

        // Agent 20 declined it: never offered it again
        val pending = rest.getForEntity("/v1/assignments?mandate-id=1&status=PENDING", SearchAssignmentResponse::class.java)
            .body!!.assignments
        assertEquals(30L, pending.single().agentId)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        assertEquals("John Doe <john.doe@gmail.com>", smtp.receivedMessages.single().allRecipients.single().toString())
    }

    @Test
    fun `assignment not found`() {
        val response = rest.postForEntity("/v1/assignments/999/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `assignment from another tenant is not found`() {
        val response = rest.postForEntity("/v1/assignments/9/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.postForEntity("/v1/assignments/1/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertStatus(1, AssignmentStatus.PENDING)
    }

    @Test
    fun `offer of another agent`() {
        val response = rest.postForEntity("/v1/assignments/2/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
        assertStatus(2, AssignmentStatus.PENDING)
    }

    @Test
    fun `offer of another agent no longer pending is forbidden, not conflict`() {
        val response = rest.postForEntity("/v1/assignments/5/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    @Sql(
        scripts = ["/db/test/clean.sql", "/db/test/mandate/DeclineAssignmentEndpoint.sql"],
        statements = ["UPDATE T_MEMBERSHIP SET party_id = NULL WHERE user_id = 11"],
    )
    fun `user without party`() {
        val response = rest.postForEntity("/v1/assignments/1/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, response.body?.error?.code)
        assertStatus(1, AssignmentStatus.PENDING)
    }

    @Test
    fun `assignment not pending`() {
        val response = rest.postForEntity("/v1/assignments/3/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, response.body?.error?.code)
        assertStatus(3, AssignmentStatus.ACCEPTED)
    }

    @Test
    fun `assignment past its TTL`() {
        val response = rest.postForEntity("/v1/assignments/4/decline", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, response.body?.error?.code)
        assertStatus(4, AssignmentStatus.PENDING)
    }

    private fun assertStatus(id: Long, status: AssignmentStatus) {
        val assignment = rest.getForEntity("/v1/assignments/$id", GetAssignmentResponse::class.java).body!!.assignment
        assertEquals(status, assignment.status)
    }
}
