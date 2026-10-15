package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.mandate.dto.DispatchMandateRequest
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** With `ndopify.endpoints.ops=false` (as in prod), `POST /v1/assignments/dispatch` doesn't exist. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DispatchAssignmentEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class DispatchAssignmentOpsEndpointDisabledTest : TenantAwareEndpointIntegrationTest() {
    @Test
    fun `not served`() {
        val response = rest.postForEntity("/v1/assignments/dispatch", DispatchMandateRequest(3), String::class.java)

        // The path matches GET /{id}: method not supported, mapped to 404 too
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        val assignments = rest.getForEntity("/v1/assignments?mandate-id=3", SearchAssignmentResponse::class.java)
            .body!!.assignments
        assertTrue(assignments.isEmpty())
    }
}
