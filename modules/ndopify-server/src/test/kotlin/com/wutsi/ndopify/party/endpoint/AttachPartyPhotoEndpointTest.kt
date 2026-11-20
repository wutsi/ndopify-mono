package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
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

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UpdatePartyPhotoEndpoint.sql"])
class AttachPartyPhotoEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: PartyRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    private val path = "party/100/photo/0f8fad5b-d9cb-469f-a165-70867728950e.png"

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

        val response = rest.postForEntity("/v1/parties/100/photo/attach", AttachUploadRequest(path), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val party = dao.findById(100L).get()
        assertEquals("https://cdn.example.com/$path", party.photoUrl)
        assertTrue(party.modifiedAt.after(party.createdAt))
    }

    @Test
    fun `attach - another party's path`() {
        val response = attachError(100L, "party/101/photo/0f8fad5b-d9cb-469f-a165-70867728950e.png")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertNull(dao.findById(100L).get().photoUrl)
    }

    @Test
    fun `attach - missing object`() {
        doReturn(null).whenever(storage).metadata(path)

        val response = attachError(100L, path)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertNull(dao.findById(100L).get().photoUrl)
    }

    @Test
    fun `attach - too large`() {
        doReturn(StoredObject("image/png", 6L * 1024 * 1024)).whenever(storage).metadata(path)

        val response = attachError(100L, path)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage).delete(path)
        assertNull(dao.findById(100L).get().photoUrl)
    }

    @Test
    fun `attach - not an image`() {
        doReturn(StoredObject("text/plain", 4)).whenever(storage).metadata(path)
        doReturn("nope".toByteArray()).whenever(storage).readPrefix(eq(path), any())

        val response = attachError(100L, path)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage).delete(path)
        assertNull(dao.findById(100L).get().photoUrl)
    }

    @Test
    fun `attach - another party`() {
        val response = attachError(101L, "party/101/photo/0f8fad5b-d9cb-469f-a165-70867728950e.png")

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        assertNull(dao.findById(101L).get().photoUrl)
    }

    @Test
    fun `attach - anonymous`() {
        anonymousUser = true

        val response = attachError(100L, path)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertNull(dao.findById(100L).get().photoUrl)
    }

    @Test
    fun `attach - unknown party`() {
        val response = attachError(999L, path)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }

    private fun attachError(id: Long, path: String) =
        rest.postForEntity("/v1/parties/$id/photo/attach", AttachUploadRequest(path), ErrorResponse::class.java)
}
