package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The mandate's customer reads its mandate but never its documents: the mandate read leaks none of them, and every
 * document endpoint is the agent's or an admin's, so the customer gets 403 [ErrorCode.MANDATE_ACCESS_DENIED]. User 11
 * (the caller) is the mandate's customer's party, user 12 its agent's.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentCustomerAccessEndpoint.sql"])
class MandateDocumentCustomerAccessEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private val pagePath = "mandate/100/documents/page-10-0.png"

    @Test
    fun `the customer reads its mandate without any document`() {
        val response = rest.getForEntity("/v1/mandates/100", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertFalse(body.contains("\"documents\""), body)
        assertFalse(body.contains(pagePath), body)
        assertFalse(body.contains("mandate/100/documents"), body)
    }

    @Test
    fun `the customer can't search the documents`() {
        assertDenied(rest.getForEntity("/v1/mandates/100/documents", ErrorResponse::class.java))
    }

    @Test
    fun `the customer can't get a document`() {
        assertDenied(rest.getForEntity("/v1/mandates/100/documents/10", ErrorResponse::class.java))
    }

    @Test
    fun `the customer can't create a document`() {
        val request = CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(pagePath))

        assertDenied(rest.postForEntity("/v1/mandates/100/documents", request, ErrorResponse::class.java))
    }

    @Test
    fun `the customer can't presign an upload`() {
        val request = PresignUploadRequest(contentType = "image/png")

        assertDenied(rest.postForEntity("/v1/mandates/100/documents/presign", request, ErrorResponse::class.java))
    }

    @Test
    fun `the customer can't rename a document`() {
        val request = UpdateMandateDocumentRequest(title = "Autre")

        assertDenied(rest.postForEntity("/v1/mandates/100/documents/10", request, ErrorResponse::class.java))
    }

    @Test
    fun `the customer can't add a page`() {
        val request = AttachUploadRequest(path = pagePath)

        assertDenied(rest.postForEntity("/v1/mandates/100/documents/10/pages", request, ErrorResponse::class.java))
    }

    @Test
    fun `the customer can't delete a page`() {
        assertDenied(
            rest.exchange(
                "/v1/mandates/100/documents/10/pages/100",
                HttpMethod.DELETE,
                null,
                ErrorResponse::class.java,
            ),
        )
    }

    @Test
    fun `the customer can't delete a document`() {
        assertDenied(
            rest.exchange(
                "/v1/mandates/100/documents/10",
                HttpMethod.DELETE,
                null,
                ErrorResponse::class.java,
            ),
        )
    }

    @Test
    fun `the agent's search returns no document`() {
        userId = 12L

        val body = rest.getForEntity("/v1/mandates", String::class.java).body!!

        assertFalse(body.contains("\"documents\""), body)
    }

    private fun assertDenied(response: ResponseEntity<ErrorResponse>) {
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }
}
