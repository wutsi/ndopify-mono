package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentRequest
import com.wutsi.ndopify.mandate.dto.CreateMandateDocumentResponse
import com.wutsi.ndopify.mandate.dto.DocumentStatus
import com.wutsi.ndopify.mandate.dto.DocumentType
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentPageRepository
import com.wutsi.ndopify.mandate.server.dao.MandateDocumentRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.RoleCode
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Who may add a document to a mandate, and the rules of the create itself. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateDocumentEndpoint.sql"])
class CreateMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var documentDao: MandateDocumentRepository

    @Autowired
    private lateinit var pageDao: MandateDocumentPageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    // The default storage (S3), where the new pages are uploaded
    private val storage = mock<StorageService>()

    private var admin = false

    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    private val path1 = "mandate/100/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    private val path2 = "mandate/100/documents/7c9e6679-7425-40de-944b-e07fc1f90ae7.png"

    @BeforeEach
    fun setUpStorage() {
        admin = false
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

    @Test
    fun `create - a listing mandate's document from two uploaded paths`() {
        uploaded(path1)
        uploaded(path2)

        val response = create(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1, path2)))

        assertEquals(HttpStatus.OK, response.statusCode)
        val document = documentDao.findById(response.body!!.documentId).orElseThrow()
        assertEquals(DocumentStatus.UPLOADED, document.status)
        assertEquals("Titre foncier", document.title)
        val pages = pageDao.findByDocumentIdOrderByPageIndex(document.id!!)
        assertEquals(listOf(0, 1), pages.map { it.pageIndex })
        assertEquals(listOf(path1, path2), pages.map { it.path })
        assertEquals(StorageType.S3, pages[0].storageType)
        assertEquals(StorageType.S3, pages[1].storageType)
    }

    @Test
    fun `create - a search mandate's document trims the title`() {
        val searchPath = "mandate/101/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        uploaded(searchPath)

        val response = create(
            101,
            CreateMandateDocumentRequest(type = DocumentType.PAY_SLIP, title = "  Bulletin mars  ", paths = listOf(searchPath)),
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("Bulletin mars", documentDao.findById(response.body!!.documentId).orElseThrow().title)
    }

    @Test
    fun `create - unknown mandate`() {
        val response = createError(999, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - a mandate of another tenant`() {
        val response = createError(500, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - a mandate of another agent`() {
        val response = createError(200, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - a mandate only offered to the caller`() {
        val response = createError(400, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - an admin adds a document to another agent's mandate`() {
        admin = true
        val otherPath = "mandate/200/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        uploaded(otherPath)

        val response = create(200, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(otherPath)))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(DocumentStatus.UPLOADED, documentDao.findById(response.body!!.documentId).orElseThrow().status)
    }

    @Test
    fun `create - a closed mandate`() {
        val response = createError(300, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - a cancelled mandate`() {
        val response = createError(301, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - an unknown type`() {
        val response = createError(100, CreateMandateDocumentRequest(type = DocumentType.UNKNOWN, paths = listOf(path1)))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - empty paths`() {
        val response = createError(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `create - a path of another mandate is refused and nothing is stored`() {
        val other = "mandate/101/documents/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        val response = createError(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(other)))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `create - a path a page already has`() {
        uploaded(path1)
        create(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        val response = createError(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(1, documentDao.findByMandateIdOrderByIdDesc(100L).size)
    }

    @Test
    fun `create - an image that cannot be previewed is refused and its object deleted`() {
        pixelBomb(path1)

        val response = createError(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals("path", response.body?.error?.parameter?.name)
        assertNull(response.body?.error?.parameter?.value)
        verify(storage).delete(path1)
        assertTrue(documentDao.findByMandateIdOrderByIdDesc(100L).isEmpty())
    }

    @Test
    fun `create - anonymous`() {
        anonymousUser = true

        val response = createError(100, CreateMandateDocumentRequest(type = DocumentType.LAND_TITLE, paths = listOf(path1)))

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }

    private fun create(id: Long, request: CreateMandateDocumentRequest) =
        rest.postForEntity("/v1/mandates/$id/documents", request, CreateMandateDocumentResponse::class.java)

    private fun createError(id: Long, request: CreateMandateDocumentRequest) =
        rest.postForEntity("/v1/mandates/$id/documents", request, ErrorResponse::class.java)
}
