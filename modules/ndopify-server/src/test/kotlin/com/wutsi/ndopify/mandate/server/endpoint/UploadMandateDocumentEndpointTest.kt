package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.encryption.AccessPermission
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy
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
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The multipart upload of a mandate's document pages. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateDocumentEndpoint.sql"])
class UploadMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
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

    private fun png(): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
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

    /** A one-page PDF protected by a user password: PDFBox refuses to load it without it. */
    private fun encryptedPdf(): ByteArray = PDDocument().use { document ->
        document.addPage(PDPage(PDRectangle.LETTER))
        document.protect(StandardProtectionPolicy("owner", "user", AccessPermission()))
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }

    /** One posted file: its name, bytes and declared content type. */
    private class FilePart(
        val name: String,
        val bytes: ByteArray,
        val contentType: MediaType = MediaType.IMAGE_PNG,
    )

    private fun image(name: String = "page.png") = FilePart(name, png())

    private fun document(name: String, bytes: ByteArray) = FilePart(name, bytes, MediaType.APPLICATION_PDF)

    private fun request(
        type: DocumentType,
        vararg files: FilePart,
    ): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("type", type.name)
        files.forEach { file ->
            val resource = object : ByteArrayResource(file.bytes) {
                override fun getFilename(): String = file.name
            }
            val fileHeaders = HttpHeaders()
            fileHeaders.contentType = file.contentType
            body.add("files", HttpEntity(resource, fileHeaders))
        }

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return HttpEntity(body, headers)
    }

    @Test
    fun `upload - two pages`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, image("page1.png"), image("page2.png")),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val document = documentDao.findByMandateIdOrderByIdDesc(100L).first()
        assertEquals(DocumentStatus.UPLOADED, document.status)
        assertEquals("Titre foncier", document.title)
        val pages = pageDao.findByDocumentIdOrderByPageIndex(document.id!!)
        assertEquals(listOf(0, 1), pages.map { it.pageIndex })
        assertTrue(pages.all { it.storageType == StorageType.S3 })
        assertTrue(pages.all { it.path.startsWith("mandate/100/documents/") })
        verify(storage, times(2)).store(argThat { startsWith("mandate/100/documents/") }, any(), eq("image/png"))
        // Two originals, and the preview generated next to each one at write
        verify(storage, times(2)).store(argThat { endsWith(".preview-v1.jpg") }, any(), eq("image/jpeg"))
    }

    @Test
    fun `upload - a text file is refused and nothing is stored`() {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("type", DocumentType.LAND_TITLE.name)
        val resource = object : ByteArrayResource("not an image".toByteArray()) {
            override fun getFilename(): String = "note.txt"
        }
        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = MediaType.TEXT_PLAIN
        body.add("files", HttpEntity(resource, fileHeaders))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            HttpEntity(body, headers),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `upload - a later file refused deletes the files already stored`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(
                DocumentType.LAND_TITLE,
                image("page1.png"),
                image("page2.png"),
                FilePart("note.txt", "not an image".toByteArray()),
            ),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, times(2)).store(argThat { startsWith("mandate/100/documents/") }, any(), eq("image/png"))
        // Each stored page's original and its preview
        verify(storage, times(4)).delete(argThat { startsWith("mandate/100/documents/") })
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `upload - no files`() {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("type", DocumentType.LAND_TITLE.name)
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA

        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            HttpEntity(body, headers),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `upload - another agent's mandate`() {
        val response = rest.postForEntity(
            "/v1/mandates/200/documents/upload",
            request(DocumentType.LAND_TITLE, image("page1.png")),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `upload - unknown mandate`() {
        val response = rest.postForEntity(
            "/v1/mandates/999/documents/upload",
            request(DocumentType.LAND_TITLE, image("page1.png")),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `upload - anonymous`() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, image("page1.png")),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `upload - a one-page pdf becomes one jpeg page`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, document("scan.pdf", pdf(1))),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val document = documentDao.findByMandateIdOrderByIdDesc(100L).first()
        val pages = pageDao.findByDocumentIdOrderByPageIndex(document.id!!)
        assertEquals(1, pages.size)
        assertEquals(0, pages[0].pageIndex)
        assertTrue(pages[0].path.endsWith(".jpg"), pages[0].path)
        assertEquals("image/jpeg", pages[0].mimeType)
        verify(storage).store(argThat { endsWith(".jpg") && !endsWith(".preview-v1.jpg") }, any(), eq("image/jpeg"))
        // The display copy of the rendered page, like a photo's
        verify(storage).store(eq("${pages[0].path}.preview-v1.jpg"), any(), eq("image/jpeg"))
        // The PDF itself is never stored
        verify(storage, never()).store(argThat { endsWith(".pdf") }, any(), any())
    }

    @Test
    fun `upload - an image and a two-page pdf keep their order`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(
                DocumentType.LAND_TITLE,
                image("page.png"),
                document("scan.pdf", pdf(2)),
                image("page.png"),
            ),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val document = documentDao.findByMandateIdOrderByIdDesc(100L).first()
        val pages = pageDao.findByDocumentIdOrderByPageIndex(document.id!!)
        assertEquals(listOf(0, 1, 2, 3), pages.map { it.pageIndex })
        assertEquals(listOf("image/png", "image/jpeg", "image/jpeg", "image/png"), pages.map { it.mimeType })

        val storedPaths = argumentCaptor<String>()
        val storedTypes = argumentCaptor<String>()
        verify(storage, times(8)).store(storedPaths.capture(), any(), storedTypes.capture())
        val originals = storedPaths.allValues.indices.filter { !storedPaths.allValues[it].endsWith(".preview-v1.jpg") }
        assertEquals(
            listOf("image/png", "image/jpeg", "image/jpeg", "image/png"),
            originals.map { storedTypes.allValues[it] },
        )
    }

    @Test
    fun `upload - an encrypted pdf is refused and nothing is stored`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, document("scan.pdf", encryptedPdf())),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED, response.body?.error?.code)
        assertEquals("file", response.body?.error?.parameter?.name)
        verify(storage, never()).store(any(), any(), any())
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `upload - a corrupt pdf is refused and nothing is stored`() {
        val corrupt = "%PDF-1.7\n".toByteArray() + ByteArray(64) { 0x42 }

        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, document("scan.pdf", corrupt)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE, response.body?.error?.code)
        assertEquals("file", response.body?.error?.parameter?.name)
        verify(storage, never()).store(any(), any(), any())
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `upload - a pdf over 10 pages is refused and nothing is stored`() {
        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, document("scan.pdf", pdf(11))),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES, response.body?.error?.code)
        assertEquals("file", response.body?.error?.parameter?.name)
        verify(storage, never()).store(any(), any(), any())
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `upload - a pdf over 5 MB is refused on file`() {
        val oversized = pdf(1) + ByteArray(5 * 1024 * 1024)

        val response = rest.postForEntity(
            "/v1/mandates/100/documents/upload",
            request(DocumentType.LAND_TITLE, document("scan.pdf", oversized)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("file", response.body?.error?.parameter?.name)
        verify(storage, never()).store(any(), any(), any())
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }
}
