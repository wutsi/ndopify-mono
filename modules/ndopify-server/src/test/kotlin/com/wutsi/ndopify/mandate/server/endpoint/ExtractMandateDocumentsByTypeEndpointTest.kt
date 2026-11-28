package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.SearchMandateDocumentResponse
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Requeues a whole type: its documents but the extracting ones go back to UPLOADED, cleared. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/ExtractMandateDocumentsByTypeEndpoint.sql"])
class ExtractMandateDocumentsByTypeEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    private fun documents() = rest
        .getForEntity("/v1/mandates/100/documents", SearchMandateDocumentResponse::class.java)
        .body!!.documents
        .associateBy { document -> document.id }

    @Test
    fun `extract - resets the type's documents but the extracting one`() {
        val response = rest.postForEntity("/v1/mandate-documents/extract?type=LAND_TITLE", null, Void::class.java)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        val documents = documents()
        val requeued = assertNotNull(documents[1001])
        assertEquals(DocumentStatus.UPLOADED, requeued.status)
        assertEquals(emptyList(), requeued.problemCodes)
        assertNull(requeued.issuedAt)
        assertNull(requeued.expiresAt)
        assertEquals(emptyList(), requeued.fields)
        assertEquals(DocumentStatus.EXTRACTING, documents[1002]!!.status)
        assertEquals(DocumentStatus.EXTRACTED, documents[1003]!!.status)
    }

    @Test
    fun `extract - a financial type is requeued, another type is left untouched`() {
        val response = rest.postForEntity("/v1/mandate-documents/extract?type=PAY_SLIP", null, Void::class.java)

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        val documents = documents()
        val requeued = assertNotNull(documents[1003])
        assertEquals(DocumentStatus.UPLOADED, requeued.status)
        assertEquals(emptyList(), requeued.fields)
        assertNull(requeued.issuedAt)
        val untouched = assertNotNull(documents[1001])
        assertEquals(DocumentStatus.EXTRACTED, untouched.status)
        assertEquals(listOf("DOCUMENT_EXPIRED"), untouched.problemCodes)
    }

    @Test
    fun `extract - a type without an extractor is a bad request`() {
        val response = rest.postForEntity(
            "/v1/mandate-documents/extract?type=UNKNOWN",
            null,
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("type", response.body?.error?.parameter?.name)
        assertEquals(DocumentStatus.EXTRACTED, documents()[1003]!!.status)
    }

    @Test
    fun `extract - anonymous`() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/mandate-documents/extract?type=LAND_TITLE",
            null,
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
