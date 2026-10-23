package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.GetAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** With `ndopify.endpoints.ops=false` (as in prod), `POST /v1/assignments/{id}/expire` doesn't exist. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/ExpireAssignmentEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class ExpireAssignmentOpsEndpointDisabledTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `not served`() {
        val response = rest.postForEntity("/v1/assignments/1/expire", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
        val assignment = rest.getForEntity("/v1/assignments/1", GetAssignmentResponse::class.java).body!!.assignment
        assertEquals(AssignmentStatus.PENDING, assignment.status)
    }

    @Test
    fun `the other endpoints are served`() {
        val response = rest.getForEntity("/v1/assignments/1", GetAssignmentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
