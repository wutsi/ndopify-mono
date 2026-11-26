package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
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

    private fun request(
        type: DocumentType,
        vararg files: Pair<String, ByteArray>,
    ): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val body = LinkedMultiValueMap<String, Any>()
        body.add("type", type.name)
        files.forEach { (name, content) ->
            val resource = object : ByteArrayResource(content) {
                override fun getFilename(): String = name
            }
            val fileHeaders = HttpHeaders()
            fileHeaders.contentType = MediaType.IMAGE_PNG
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
            request(DocumentType.LAND_TITLE, "page1.png" to png(), "page2.png" to png()),
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
                "page1.png" to png(),
                "page2.png" to png(),
                "note.txt" to "not an image".toByteArray(),
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
            request(DocumentType.LAND_TITLE, "page1.png" to png()),
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
            request(DocumentType.LAND_TITLE, "page1.png" to png()),
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
            request(DocumentType.LAND_TITLE, "page1.png" to png()),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }
}
