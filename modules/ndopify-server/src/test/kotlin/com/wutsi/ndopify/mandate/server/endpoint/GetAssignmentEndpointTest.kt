package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetAssignmentEndpoint.sql"])
class GetAssignmentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun get() {
        val response = rest.getForEntity("/v1/assignments/1", GetAssignmentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)

        val assignment = response.body!!.assignment
        assertEquals(1L, assignment.id)
        assertEquals(1L, assignment.mandateId)
        assertEquals(20L, assignment.agentId)
        assertEquals(AssignmentStatus.PENDING, assignment.status)
        assertEquals(0.75, assignment.score)
        assertEquals("2024-06-10 10:00", format(assignment.createdAt))
        assertEquals("2024-06-10 10:30", format(assignment.modifiedAt))
        assertEquals("2024-06-10 11:00", format(assignment.expiresAt!!))
    }

    @Test
    fun `assignment not found`() {
        val response = rest.getForEntity("/v1/assignments/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `assignment from another tenant is not found`() {
        val response = rest.getForEntity("/v1/assignments/9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.ASSIGNMENT_NOT_FOUND, response.body?.error?.code)
    }

    private fun format(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm")
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }
}
