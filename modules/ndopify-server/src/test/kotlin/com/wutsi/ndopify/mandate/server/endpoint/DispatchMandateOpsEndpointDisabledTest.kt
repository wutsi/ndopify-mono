package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.GetMandateResponse
import com.wutsi.ndopify.mandate.dto.SearchAssignmentResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** With `ndopify.endpoints.ops=false` (as in prod), `POST /v1/mandates/{id}/dispatch` doesn't exist. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/DispatchMandateEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class DispatchMandateOpsEndpointDisabledTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Test
    fun `not served`() {
        val response = rest.postForEntity("/v1/mandates/3/dispatch", null, ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
        val assignments = rest.getForEntity("/v1/assignments?mandate-id=3", SearchAssignmentResponse::class.java)
            .body!!.assignments
        assertTrue(assignments.isEmpty())
    }

    @Test
    fun `the other endpoints are served`() {
        val response = rest.getForEntity("/v1/mandates/3", GetMandateResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
