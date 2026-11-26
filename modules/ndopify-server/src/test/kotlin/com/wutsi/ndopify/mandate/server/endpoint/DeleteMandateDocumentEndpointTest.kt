package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Deleting a document of a mandate: its pages and every page's object go with it. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentMutationEndpoint.sql"])
class DeleteMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
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
    fun `delete - the document, its pages and each page's object are removed`() {
        val response = delete(100, 1000)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(documentDao.existsById(1000))
        assertEquals(emptyList(), pageDao.findByDocumentIdOrderByPageIndex(1000))
        verify(s3).delete("mandate/100/documents/page-1000-0.png")
        verify(local).delete("mandate/100/documents/page-1000-1.png")
    }

    @Test
    fun `delete - a failed object deletion still removes the rows`() {
        doThrow(RuntimeException("boom")).whenever(s3).delete(any())

        val response = delete(100, 1000)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(documentDao.existsById(1000))
        assertEquals(emptyList(), pageDao.findByDocumentIdOrderByPageIndex(1000))
    }

    @Test
    fun `delete - a closed mandate`() {
        val response = deleteError(300, 3000)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
        assertTrue(documentDao.existsById(3000))
    }

    private fun delete(id: Long, documentId: Long) =
        rest.exchange("/v1/mandates/$id/documents/$documentId", HttpMethod.DELETE, null, Void::class.java)

    private fun deleteError(id: Long, documentId: Long) =
        rest.exchange("/v1/mandates/$id/documents/$documentId", HttpMethod.DELETE, null, ErrorResponse::class.java)
}
