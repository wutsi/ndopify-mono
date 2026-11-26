package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
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
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.util.LinkedMultiValueMap
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Adding a page to a document of a mandate: attach an uploaded path, or post the file as multipart. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentMutationEndpoint.sql"])
class AddMandateDocumentPageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    @Autowired
    private lateinit var pageDao: MandateDocumentPageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(StorageType.S3).whenever(storage).type()
    }

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun uploaded(path: String) {
        doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
        doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())
    }

    private fun uploadRequest(content: ByteArray): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val body = LinkedMultiValueMap<String, Any>()
        val resource = object : ByteArrayResource(content) {
            override fun getFilename(): String = "page.png"
        }
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.IMAGE_PNG
        body.add("file", HttpEntity(resource, fileHeaders))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return HttpEntity(body, headers)
    }

    @Test
    fun `add - a new page is appended and the document reset`() {
        val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        uploaded(newPath)

        val response = addPage(100, 1000, AttachUploadRequest(path = newPath))

        assertEquals(HttpStatus.OK, response.statusCode)
        val page = pageDao.findById(response.body!!.pageId).orElseThrow()
        assertEquals(2, page.pageIndex)
        assertEquals(newPath, page.path)
        assertEquals(StorageType.S3, page.storageType)
        val document = documentDao.findById(1000).orElseThrow()
        assertEquals(DocumentStatus.UPLOADED, document.status)
        assertEquals(emptyList(), document.problemCodes)
        assertNull(document.data)
        assertNull(document.dataVersion)
        assertNull(document.issuedAt)
        assertNull(document.expiresAt)
    }

    @Test
    fun `add - the same path again returns the same page and deletes nothing`() {
        val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        uploaded(newPath)

        val first = addPage(100, 1000, AttachUploadRequest(path = newPath)).body!!.pageId
        val second = addPage(100, 1000, AttachUploadRequest(path = newPath)).body!!.pageId

        assertEquals(first, second)
        assertEquals(3, pageDao.findByDocumentIdOrderByPageIndex(1000).size)
        verify(storage, never()).delete(any())
    }

    @Test
    fun `add - a path another page has`() {
        val response = addPageError(100, 1000, AttachUploadRequest(path = "mandate/100/documents/page-1001-0.png"))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `add - a posted file is appended at the next index`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/1000/pages/upload",
            uploadRequest(png(1, 1)),
            AttachMandateDocumentPageResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val page = pageDao.findById(response.body!!.pageId).orElseThrow()
        assertEquals(2, page.pageIndex)
        assertTrue(page.path.startsWith("mandate/100/documents/"), page.path)
        verify(storage).store(argThat { startsWith("mandate/100/documents/") }, any(), eq("image/png"))
    }

    private fun addPage(id: Long, documentId: Long, request: AttachUploadRequest) =
        rest.postForEntity("/v1/mandates/$id/documents/$documentId/pages", request, AttachMandateDocumentPageResponse::class.java)

    private fun addPageError(id: Long, documentId: Long, request: AttachUploadRequest) =
        rest.postForEntity("/v1/mandates/$id/documents/$documentId/pages", request, ErrorResponse::class.java)
}
