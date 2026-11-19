package com.wutsi.ndopify.listing.server.endpoint

import com.nhaarman.mockitokotlin2.any
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
import com.wutsi.ndopify.listing.dto.AttachListingImageResponse
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/listing/UploadListingImageEndpoint.sql"])
class AttachListingImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN)

    @Autowired
    private lateinit var dao: ListingImageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    private val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

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

    @Test
    fun attach() {
        doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
        doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())

        val response = attach(path)

        assertEquals(HttpStatus.OK, response.statusCode)
        val image = dao.findByListingIdOrderById(101L).single()
        assertEquals(response.body?.imageId, image.id)
        assertEquals(TENANT_ID, image.tenantId)
        assertEquals(101L, image.listingId)
        assertEquals(path, image.path)
        assertEquals("https://cdn.example.com/$path", image.url)
        assertEquals("image/png", image.mimeType)
        assertEquals(ImageStatus.PENDING_VERIFICATION, image.status)
        assertNull(image.errorMessage)
    }

    @Test
    fun `attach - idempotent`() {
        doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
        doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())

        val first = attach(path)
        val second = attach(path)

        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.body?.imageId, second.body?.imageId)
        assertEquals(1, dao.findByListingIdOrderById(101L).size)
    }

    @Test
    fun `attach - another listing's path`() {
        val other = "listings/102/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        val response = rest.postForEntity(
            "/v1/listings/101/images/attach",
            AttachUploadRequest(other),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(0, dao.findByListingIdOrderById(101L).size)
    }

    @Test
    fun `attach - missing object`() {
        doReturn(null).whenever(storage).metadata(path)

        val response = rest.postForEntity(
            "/v1/listings/101/images/attach",
            AttachUploadRequest(path),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(0, dao.findByListingIdOrderById(101L).size)
    }

    @Test
    fun `attach - not an image`() {
        doReturn(StoredObject("text/plain", 4)).whenever(storage).metadata(path)
        doReturn("nope".toByteArray()).whenever(storage).readPrefix(eq(path), any())

        val response = rest.postForEntity(
            "/v1/listings/101/images/attach",
            AttachUploadRequest(path),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage).delete(path)
        assertEquals(0, dao.findByListingIdOrderById(101L).size)
    }

    @Test
    fun `attach - anonymous`() {
        anonymousUser = true

        val response = rest.postForEntity(
            "/v1/listings/101/images/attach",
            AttachUploadRequest(path),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals(0, dao.findByListingIdOrderById(101L).size)
    }

    private fun attach(path: String) =
        rest.postForEntity(
            "/v1/listings/101/images/attach",
            AttachUploadRequest(path),
            AttachListingImageResponse::class.java,
        )
}
