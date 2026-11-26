package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.AttachMandateDocumentPageResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Replacing a page's file of a document of a mandate, keeping its index. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentMutationEndpoint.sql"])
class ReplaceMandateDocumentPageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    @Autowired
    private lateinit var pageDao: MandateDocumentPageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val s3 = mock<StorageService>()
    private val local = mock<StorageService>()

    private val oldPath = "mandate/100/documents/page-1000-1.png"
    private val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"

    @BeforeEach
    fun setUpStorage() {
        doReturn(s3).whenever(storageProvider).get()
        doReturn(StorageType.S3).whenever(s3).type()
        doReturn(local).whenever(storageProvider).get(StorageType.LOCAL)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun uploaded(path: String) {
        doReturn(StoredObject("image/png", 3)).whenever(s3).metadata(path)
        doReturn(png(1, 1)).whenever(s3).readPrefix(eq(path), any())
    }

    @Test
    fun `replace - the page keeps its id and index, gets the new path, and the document reset`() {
        uploaded(newPath)

        val response = replacePage(100, 1000, 2001, AttachUploadRequest(path = newPath))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2001L, response.body!!.pageId)
        val page = pageDao.findById(2001).orElseThrow()
        assertEquals(1, page.pageIndex)
        assertEquals(newPath, page.path)
        assertEquals(StorageType.S3, page.storageType)
        val document = documentDao.findById(1000).orElseThrow()
        assertEquals(DocumentStatus.UPLOADED, document.status)
        assertEquals(emptyList(), document.problemCodes)
        assertNull(document.data)
        assertNull(document.dataVersion)
        assertNull(document.issuedAt)
        assertNull(document.expiresAt)
        verify(local).delete(oldPath)
    }

    @Test
    fun `replace - the same path twice deletes the old object once`() {
        uploaded(newPath)

        replacePage(100, 1000, 2001, AttachUploadRequest(path = newPath))
        replacePage(100, 1000, 2001, AttachUploadRequest(path = newPath))

        verify(local, times(1)).delete(oldPath)
    }

    @Test
    fun `replace - a page of another document`() {
        val response = replacePageError(100, 1000, 2002, AttachUploadRequest(path = newPath))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND, response.body?.error?.code)
    }

    private fun replacePage(id: Long, documentId: Long, pageId: Long, request: AttachUploadRequest) =
        rest.postForEntity(
            "/v1/mandates/$id/documents/$documentId/pages/$pageId",
            request,
            AttachMandateDocumentPageResponse::class.java,
        )

    private fun replacePageError(id: Long, documentId: Long, pageId: Long, request: AttachUploadRequest) =
        rest.postForEntity(
            "/v1/mandates/$id/documents/$documentId/pages/$pageId",
            request,
            ErrorResponse::class.java,
        )
}
