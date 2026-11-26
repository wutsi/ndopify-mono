package com.wutsi.ndopify.mandate.server.endpoint

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

/** The presign of a mandate document's upload. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/CreateMandateDocumentEndpoint.sql"])
class PresignMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
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

        val response = presign(100, "image/png")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("https://s3.example.com/put", response.body?.url)
        assertTrue(response.body!!.path!!.startsWith("mandate/100/documents/"), response.body?.path)
    }

    @Test
    fun `presign - local storage returns no url`() {
        doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())

        val response = presign(100, "image/png")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body?.url)
        assertNull(response.body?.path)
    }

    @Test
    fun `presign - not an image`() {
        val response = presignError(100, "application/pdf")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - a mandate of another agent`() {
        val response = presignError(200, "image/png")

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - a closed mandate`() {
        val response = presignError(300, "image/png")

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ALREADY_CLOSED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - unknown mandate`() {
        val response = presignError(999, "image/png")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - anonymous`() {
        anonymousUser = true

        val response = presignError(100, "image/png")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    private fun presign(id: Long, contentType: String) =
        rest.postForEntity("/v1/mandates/$id/documents/presign", PresignUploadRequest(contentType), PresignUploadResponse::class.java)

    private fun presignError(id: Long, contentType: String) =
        rest.postForEntity("/v1/mandates/$id/documents/presign", PresignUploadRequest(contentType), ErrorResponse::class.java)
}
