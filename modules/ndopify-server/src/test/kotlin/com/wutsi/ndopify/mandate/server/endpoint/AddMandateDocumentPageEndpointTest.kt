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
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
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
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
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
        doAnswer { invocation ->
            invocation.getArgument<OutputStream>(1).write(png(1, 1))
            null
        }.whenever(storage).get(eq(path), any())
    }

    /** A PNG whose header claims 10000x10000 pixels: Tika reads an image, the preview pipeline refuses it. */
    private fun pixelBomb(path: String) {
        doReturn(StoredObject("image/png", 33)).whenever(storage).metadata(path)
        doReturn(pngHeader(10_000, 10_000)).whenever(storage).readPrefix(eq(path), any())
        doAnswer { invocation ->
            invocation.getArgument<OutputStream>(1).write(pngHeader(10_000, 10_000))
            null
        }.whenever(storage).get(eq(path), any())
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

    private fun uploadRequest(
        content: ByteArray,
        name: String = "page.png",
        contentType: MediaType = MediaType.IMAGE_PNG,
    ): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val body = LinkedMultiValueMap<String, Any>()
        val resource = object : ByteArrayResource(content) {
            override fun getFilename(): String = name
        }
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = contentType
        body.add("file", HttpEntity(resource, fileHeaders))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return HttpEntity(body, headers)
    }

    /** A PDF of [pages] pages with a text line on each, generated in memory: no binary fixture. */
    private fun pdf(pages: Int): ByteArray = PDDocument().use { document ->
        repeat(pages) { index ->
            val page = PDPage(PDRectangle.LETTER)
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 12f)
                content.newLineAtOffset(72f, 720f)
                content.showText("Page $index")
                content.endText()
            }
        }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
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
        // Generated at write, next to the attached original
        verify(storage).store(eq("$newPath.preview-v1.jpg"), any(), eq("image/jpeg"))
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
        // Generated at write, next to the posted file
        verify(storage).store(eq("${page.path}.preview-v1.jpg"), any(), eq("image/jpeg"))
    }

    @Test
    fun `add - an image that cannot be previewed is refused and its object deleted`() {
        val newPath = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        pixelBomb(newPath)

        val response = addPageError(100, 1000, AttachUploadRequest(path = newPath))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("path", response.body?.error?.parameter?.name)
        verify(storage).delete(newPath)
        assertEquals(2, pageDao.findByDocumentIdOrderByPageIndex(1000).size)
    }

    @Test
    fun `add - a posted pdf appends its rendered pages`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/1000/pages/upload",
            uploadRequest(pdf(2), "scan.pdf", MediaType.APPLICATION_PDF),
            AttachMandateDocumentPageResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val pages = pageDao.findByDocumentIdOrderByPageIndex(1000)
        // The document already had 2 pages: the PDF's pages follow, in its order
        assertEquals(listOf(0, 1, 2, 3), pages.map { it.pageIndex })
        assertTrue(pages.drop(2).all { it.path.endsWith(".jpg") }, "${pages.map { it.path }}")
        assertTrue(pages.drop(2).all { it.mimeType == "image/jpeg" }, "${pages.map { it.mimeType }}")
        verify(storage, times(2)).store(argThat { endsWith(".jpg") && !endsWith(".preview-v1.jpg") }, any(), eq("image/jpeg"))
        verify(storage, never()).store(argThat { endsWith(".pdf") }, any(), any())
    }

    @Test
    fun `add - a posted pdf over 10 pages is refused`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/1000/pages/upload",
            uploadRequest(pdf(11), "scan.pdf", MediaType.APPLICATION_PDF),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES, response.body?.error?.code)
        assertEquals("file", response.body?.error?.parameter?.name)
        verify(storage, never()).store(any(), any(), any())
        assertEquals(2, pageDao.findByDocumentIdOrderByPageIndex(1000).size)
    }

    private fun addPage(id: Long, documentId: Long, request: AttachUploadRequest) =
        rest.postForEntity("/v1/mandates/$id/documents/$documentId/pages", request, AttachMandateDocumentPageResponse::class.java)

    private fun addPageError(id: Long, documentId: Long, request: AttachUploadRequest) =
        rest.postForEntity("/v1/mandates/$id/documents/$documentId/pages", request, ErrorResponse::class.java)
}
