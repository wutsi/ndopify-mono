package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.platform.image.ImagePreviewService
import com.wutsi.ndopify.platform.image.UnprocessableImageException
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.local.StorageServiceLocal
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The authenticated preview of a mandate page: the generated, watermarked JPEG behind the same checks as the document
 * reads, never the original bytes nor a storage URL.
 */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateDocumentPreviewEndpoint.sql"])
class MandateDocumentPreviewEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @TempDir
    lateinit var dir: Path

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    @MockitoBean
    private lateinit var imagePreview: ImagePreviewService

    private lateinit var storage: StorageServiceLocal
    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    private val page0Path = "mandate/100/documents/page-10-0.png"
    private val legacyPagePath = "mandate/100/documents/page-10-2.png"
    private val brokenPagePath = "mandate/100/documents/broken.png"
    private val closedPagePath = "mandate/300/documents/page-13-0.png"
    private val otherAgentPagePath = "mandate/200/documents/page-14-0.png"

    private val jpeg = jpeg(10, 10, Color.RED)
    private val previewJpeg = jpeg(600, 400, Color.BLUE)

    @BeforeEach
    fun setUpStorage() {
        admin = false
        storage = StorageServiceLocal(dir.toString(), "storage", 8080)
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.S3)
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        listOf(page0Path, legacyPagePath, closedPagePath, otherAgentPagePath).forEach { path ->
            storage.store(path, jpeg.inputStream(), "image/png")
        }
        storage.store(brokenPagePath, "not an image".toByteArray().inputStream(), "image/png")

        doAnswer { invocation ->
            val original = invocation.getArgument<ByteArray>(0)
            if (String(original) == "not an image") {
                throw UnprocessableImageException("boom")
            }
            previewJpeg
        }.whenever(imagePreview).preview(any(), anyOrNull(), any())
    }

    private fun jpeg(width: Int, height: Int, color: Color): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        image.createGraphics().apply {
            this.color = color
            fillRect(0, 0, width, height)
            dispose()
        }
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "jpg", out)
        return out.toByteArray()
    }

    private fun preview(mandateId: Long, documentId: Long, pageId: Long) =
        rest.exchange(
            "/v1/mandates/$mandateId/documents/$documentId/pages/$pageId/preview",
            HttpMethod.GET,
            null,
            ByteArray::class.java,
        )

    private fun previewError(mandateId: Long, documentId: Long, pageId: Long) =
        rest.getForEntity(
            "/v1/mandates/$mandateId/documents/$documentId/pages/$pageId/preview",
            ErrorResponse::class.java,
        )

    @Test
    fun `preview - generated on the first read, stored next to the original, the original untouched`() {
        val original = Files.readAllBytes(dir.resolve(page0Path))

        val response = preview(100, 10, 100)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.IMAGE_JPEG, response.headers.contentType)
        assertContentEquals(previewJpeg, response.body)
        verify(imagePreview, times(1)).preview(any(), any(), any())
        assertTrue(Files.exists(dir.resolve("$page0Path.preview-v1.jpg")))
        assertContentEquals(original, Files.readAllBytes(dir.resolve(page0Path)))
    }

    @Test
    fun `preview - the second read serves the stored preview without regenerating`() {
        val first = preview(100, 10, 100)

        val second = preview(100, 10, 100)

        assertContentEquals(first.body, second.body)
        verify(imagePreview, times(1)).preview(any(), any(), any())
    }

    @Test
    fun `preview - a legacy page with no storage type uses the default storage`() {
        val response = preview(100, 10, 102)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(imagePreview, times(1)).preview(any(), any(), any())
        verify(storageProvider, times(1)).get()
        assertTrue(Files.exists(dir.resolve("$legacyPagePath.preview-v1.jpg")))
    }

    @Test
    fun `preview - a page that cannot be previewed fails closed`() {
        val original = Files.readAllBytes(dir.resolve(brokenPagePath))

        val response = previewError(100, 10, 103)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(ErrorCode.HTTP_INTERNAL, response.body?.error?.code)
        assertFalse(Files.exists(dir.resolve("$brokenPagePath.preview-v1.jpg")))
        assertContentEquals(original, Files.readAllBytes(dir.resolve(brokenPagePath)))
    }

    @Test
    fun `preview - a closed mandate still serves its pages`() {
        val response = preview(300, 13, 106)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.IMAGE_JPEG, response.headers.contentType)
    }

    @Test
    fun `preview - an admin reads another agent's mandate`() {
        admin = true

        val response = preview(200, 14, 107)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `preview - anonymous`() {
        anonymousUser = true

        val response = previewError(100, 10, 100)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    @Test
    fun `preview - a mandate of another agent`() {
        val response = previewError(200, 14, 107)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `preview - unknown mandate`() {
        val response = previewError(999, 10, 100)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `preview - unknown document`() {
        val response = previewError(100, 999, 100)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `preview - a page of another document`() {
        val response = previewError(100, 10, 106)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `preview - unknown page`() {
        val response = previewError(100, 10, 999)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_PAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `preview - the document reads carry no url and no path`() {
        val body = rest.getForEntity("/v1/mandates/100/documents/10", String::class.java).body!!

        assertFalse(body.contains("\"url\""), body)
        assertFalse(body.contains("\"path\""), body)
        assertFalse(body.contains("mandate/100/documents"), body)
    }
}
