package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/ExpireAssignmentEndpoint.sql"])
class ExpireAssignmentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private fun assertStatus(id: Long, status: AssignmentStatus) {
        val assignment = rest.getForEntity("/v1/assignments/$id", GetAssignmentResponse::class.java).body!!.assignment
        assertEquals(status, assignment.status)
    }

    private fun pending(mandateId: Long) =
        rest.getForEntity("/v1/assignments?mandate-id=$mandateId&status=PENDING", SearchAssignmentResponse::class.java)
            .body!!.assignments

    @Test
    fun expire() {
        val response = rest.postForEntity("/v1/assignments/1/expire", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertStatus(1, AssignmentStatus.EXPIRED)
    }

    @Test
    fun `the mandate goes to the next agent`() {
        rest.postForEntity("/v1/assignments/1/expire", null, Any::class.java)
        awaitAsyncTasks() // Re-dispatched after commit

        // Agent 20 let it expire: never offered it again
        assertEquals(30L, pending(1).single().agentId)
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
        assertEquals("John Doe <john.doe@gmail.com>", smtp.receivedMessages.single().allRecipients.single().toString())
    }

    @Test
    fun `past its TTL, not expired by the job yet - expired`() {
        val response = rest.postForEntity("/v1/assignments/2/expire", null, Any::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertStatus(2, AssignmentStatus.EXPIRED)
    }

    @Test
    fun `assignment not pending`() {
        val response = rest.postForEntity("/v1/assignments/3/expire", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, response.body?.error?.code)
        assertStatus(3, AssignmentStatus.ACCEPTED)
    }

    @Test
    fun `assignment not found`() {
        val response = rest.postForEntity("/v1/assignments/999/expire", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `assignment from another tenant is not found`() {
        val response = rest.postForEntity("/v1/assignments/9/expire", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }
}
