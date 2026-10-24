package com.wutsi.ndopify.listing.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
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
import java.net.URL
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/UploadListingImageEndpoint.sql"])
class UploadListingImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: ListingImageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
        whenever(storage.publicUrl(any())).thenAnswer { URL("https://cdn.example.com/${it.arguments[0]}") }
    }

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun request(
        fileName: String,
        contentType: MediaType,
        content: ByteArray,
    ): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val resource = object : ByteArrayResource(content) {
            override fun getFilename(): String = fileName
        }

        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = contentType

        val body = LinkedMultiValueMap<String, Any>()
        body.add("multipartFile", HttpEntity(resource, fileHeaders))

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return HttpEntity(body, headers)
    }

    @Test
    fun upload() {
        val response = rest.postForEntity(
            "/v1/listings/101/images/upload",
            request("living-room.png", MediaType.IMAGE_PNG, png(320, 200)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val image = dao.findByListingIdOrderById(101L).single()
        assertEquals(TENANT_ID, image.tenantId)
        assertEquals(101L, image.listingId)
        assertTrue(image.path.matches(Regex("listings/101/images/[0-9a-f-]{36}\\.png")), image.path)
        assertEquals("https://cdn.example.com/${image.path}", image.url)
        assertEquals("image/png", image.mimeType)
        assertEquals(ImageStatus.PENDING_VERIFICATION, image.status)
        assertNull(image.errorMessage)

        verify(storage).store(eq(image.path), any(), eq("image/png"))
    }

    @Test
    fun webp() {
        val response = rest.postForEntity(
            "/v1/listings/101/images/upload",
            request("garden.webp", MediaType.parseMediaType("image/webp"), byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val image = dao.findByListingIdOrderById(101L).single()
        assertTrue(image.path.endsWith(".webp"), image.path)
        assertEquals("image/webp", image.mimeType)
    }

    @Test
    fun `anonymous - 401`() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/listings/101/images/upload",
            request("living-room.png", MediaType.IMAGE_PNG, png(320, 200)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `not an image`() {
        val response = rest.postForEntity(
            "/v1/listings/101/images/upload",
            request("lease.pdf", MediaType.APPLICATION_PDF, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `listing not found`() {
        val response = rest.postForEntity(
            "/v1/listings/999/images/upload",
            request("living-room.png", MediaType.IMAGE_PNG, png(320, 200)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `listing of another tenant`() {
        val response = rest.postForEntity(
            "/v1/listings/900/images/upload",
            request("living-room.png", MediaType.IMAGE_PNG, png(320, 200)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }
}
