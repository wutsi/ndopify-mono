package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.UpdateMandateDocumentRequest
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Renaming a document of a mandate: only the title changes, and the version must be current. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentMutationEndpoint.sql"])
class UpdateMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    @Autowired
    private lateinit var pageDao: MandateDocumentPageRepository

    @Test
    fun `rename - title saved, status, pages and data kept, version bumped`() {
        val response = rename(100, 1000, UpdateMandateDocumentRequest(title = "Titre foncier lot 12", version = 0))

        assertEquals(HttpStatus.OK, response.statusCode)
        val document = documentDao.findById(1000).orElseThrow()
        assertEquals("Titre foncier lot 12", document.title)
        assertEquals(DocumentStatus.FAILED, document.status)
        assertEquals(listOf("BLURRY"), document.problemCodes)
        assertEquals("""{"a": 1}""", document.data)
        assertEquals(1, document.dataVersion)
        assertEquals(listOf(2000L, 2001L), pageDao.findByDocumentIdOrderByPageIndex(1000).map { it.id })
        assertNotEquals(0L, document.version)
    }

    @Test
    fun `rename - a blank title`() {
        val response = renameError(100, 1000, UpdateMandateDocumentRequest(title = "   "))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("title", response.body?.error?.parameter?.name)
    }

    @Test
    fun `rename - a stale version`() {
        val response = renameError(100, 1000, UpdateMandateDocumentRequest(title = "Titre", version = 5))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_VERSION_CONFLICT, response.body?.error?.code)
    }

    @Test
    fun `rename - a document of another mandate`() {
        val response = renameError(100, 1100, UpdateMandateDocumentRequest(title = "Titre"))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `rename - a closed mandate`() {
        val response = renameError(300, 3000, UpdateMandateDocumentRequest(title = "Titre"))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
    }

    @Test
    fun `rename - another agent's mandate`() {
        val response = renameError(200, 1100, UpdateMandateDocumentRequest(title = "Titre"))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    private fun rename(id: Long, documentId: Long, request: UpdateMandateDocumentRequest) =
        rest.postForEntity("/v1/mandates/$id/documents/$documentId", request, Any::class.java)

    private fun renameError(id: Long, documentId: Long, request: UpdateMandateDocumentRequest) =
        rest.postForEntity("/v1/mandates/$id/documents/$documentId", request, ErrorResponse::class.java)
}
