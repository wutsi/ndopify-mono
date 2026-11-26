package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.GetMandateDocumentResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** With `ndopify.endpoints.ops=false` (as in prod), the two extraction endpoints don't exist. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/ExtractMandateDocumentEndpoint.sql"])
@TestPropertySource(properties = ["ndopify.endpoints.ops=false"])
class ExtractMandateDocumentOpsEndpointDisabledTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Test
    fun `the per-document endpoint is not served`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/1000/extract",
            null,
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `the per-type endpoint is not served`() {
        val response = rest.postForEntity(
            "/v1/mandate-documents/extract?type=LAND_TITLE",
            null,
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.HTTP_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `the other document endpoints are served`() {
        val response = rest.getForEntity("/v1/mandates/100/documents/1000", GetMandateDocumentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
    }
}
