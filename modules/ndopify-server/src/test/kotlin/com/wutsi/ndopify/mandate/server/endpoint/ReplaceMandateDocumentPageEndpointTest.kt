package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
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
import java.io.OutputStream
import java.util.zip.CRC32
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
        doReturn(s3).whenever(storageProvider).get(StorageType.S3)
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
        doAnswer { invocation ->
            invocation.getArgument<OutputStream>(1).write(png(1, 1))
            null
        }.whenever(s3).get(eq(path), any())
    }

    /** A PNG whose header claims 10000x10000 pixels: Tika reads an image, the preview pipeline refuses it. */
    private fun pixelBomb(path: String) {
        doReturn(StoredObject("image/png", 33)).whenever(s3).metadata(path)
        doReturn(pngHeader(10_000, 10_000)).whenever(s3).readPrefix(eq(path), any())
        doAnswer { invocation ->
            invocation.getArgument<OutputStream>(1).write(pngHeader(10_000, 10_000))
            null
        }.whenever(s3).get(eq(path), any())
    }

    private fun pngHeader(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val data = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 13))
            write("IHDR".toByteArray())
            write(intBytes(width))
            write(intBytes(height))
            write(byteArrayOf(8, 2, 0, 0, 0))
        }.toByteArray()
        out.write(data)
        val crc = CRC32().apply { update(data, 4, data.size - 4) }
        out.write(intBytes(crc.value.toInt()))
        return out.toByteArray()
    }

    private fun intBytes(value: Int) = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )

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

    @Test
    fun `replace - an image that cannot be previewed is refused, the new object deleted, the old one kept`() {
        pixelBomb(newPath)

        val response = replacePageError(100, 1000, 2001, AttachUploadRequest(path = newPath))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("path", response.body?.error?.parameter?.name)
        verify(s3).delete(newPath)
        verify(local, never()).delete(any())
        assertEquals(oldPath, pageDao.findById(2001).orElseThrow().path)
    }

    @Test
    fun `replace - a posted file that cannot be previewed is refused and deleted`() {
        val body = LinkedMultiValueMap<String, Any>()
        val resource = object : ByteArrayResource(pngHeader(10_000, 10_000)) {
            override fun getFilename(): String = "page.png"
        }
        body.add("file", HttpEntity(resource, HttpHeaders().apply { contentType = MediaType.IMAGE_PNG }))
        val headers = HttpHeaders().apply { contentType = MediaType.MULTIPART_FORM_DATA }

        val response = rest.postForEntity(
            "/v1/mandates/100/documents/1000/pages/2001/upload",
            HttpEntity(body, headers),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("file", response.body?.error?.parameter?.name)
        verify(s3).delete(argThat { startsWith("mandate/100/documents/") && endsWith(".png") })
        verify(local, never()).delete(any())
        assertEquals(oldPath, pageDao.findById(2001).orElseThrow().path)
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
