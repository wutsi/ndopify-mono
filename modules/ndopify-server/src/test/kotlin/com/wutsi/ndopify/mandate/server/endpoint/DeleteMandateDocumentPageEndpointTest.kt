package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Deleting a page of a document of a mandate: never the last one, the other indexes stay put. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentMutationEndpoint.sql"])
class DeleteMandateDocumentPageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    @Autowired
    private lateinit var pageDao: MandateDocumentPageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val s3 = mock<StorageService>()
    private val local = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(s3).whenever(storageProvider).get(StorageType.S3)
        doReturn(local).whenever(storageProvider).get(StorageType.LOCAL)
    }

    @Test
    fun `delete - the first page is removed, the other keeps its index, the object deleted`() {
        val response = deletePage(100, 1000, 2000)

        assertEquals(HttpStatus.OK, response.statusCode)
        val pages = pageDao.findByDocumentIdOrderByPageIndex(1000)
        assertEquals(listOf(2001L), pages.map { it.id })
        assertEquals(listOf(1), pages.map { it.pageIndex })
        assertEquals(DocumentStatus.UPLOADED, documentDao.findById(1000).orElseThrow().status)
        verify(s3).delete("mandate/100/documents/page-1000-0.png")
    }

    @Test
    fun `delete - the only page of a document`() {
        val response = deletePageError(100, 1001, 2002)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_LAST_PAGE, response.body?.error?.code)
        assertTrue(pageDao.existsById(2002))
        verify(s3, never()).delete(any())
    }

    @Test
    fun `delete - a page of an 11-page grandfathered document`() {
        val response = deletePage(100, 1008, 2060)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(10, pageDao.findByDocumentIdOrderByPageIndex(1008).size)
        verify(s3).delete("mandate/100/documents/page-1008-0.png")
    }

    private fun deletePage(id: Long, documentId: Long, pageId: Long) =
        rest.exchange("/v1/mandates/$id/documents/$documentId/pages/$pageId", HttpMethod.DELETE, null, Void::class.java)

    private fun deletePageError(id: Long, documentId: Long, pageId: Long) =
        rest.exchange("/v1/mandates/$id/documents/$documentId/pages/$pageId", HttpMethod.DELETE, null, ErrorResponse::class.java)
}
