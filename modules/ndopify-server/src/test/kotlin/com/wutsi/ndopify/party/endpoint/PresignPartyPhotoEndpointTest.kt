package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.common.dto.PresignUploadRequest
import com.wutsi.ndopify.common.dto.PresignUploadResponse
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UpdatePartyPhotoEndpoint.sql"])
class PresignPartyPhotoEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
    }

    @Test
    fun presign() {
        doReturn(URL("https://s3.example.com/put")).whenever(storage).presignedUploadUrl(any(), any(), any())

        val response = rest.postForEntity(
            "/v1/parties/100/photo/presign",
            PresignUploadRequest("image/jpeg"),
            PresignUploadResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("https://s3.example.com/put", response.body?.url)
        assertTrue(response.body!!.path!!.matches(Regex("party/100/photo/[0-9a-f-]{36}\\.jpg")), response.body?.path)
    }

    @Test
    fun `presign - local`() {
        doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())

        val response = rest.postForEntity(
            "/v1/parties/100/photo/presign",
            PresignUploadRequest("image/png"),
            PresignUploadResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body?.url)
        assertNull(response.body?.path)
    }

    @Test
    fun `presign - not an image`() {
        val response = presignError(100L, "application/pdf")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - another party`() {
        val response = presignError(101L, "image/jpeg")

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - anonymous`() {
        anonymousUser = true

        val response = presignError(100L, "image/jpeg")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - unknown party`() {
        val response = presignError(999L, "image/jpeg")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }

    private fun presignError(id: Long, contentType: String) =
        rest.postForEntity(
            "/v1/parties/$id/photo/presign",
            PresignUploadRequest(contentType),
            ErrorResponse::class.java,
        )
}
