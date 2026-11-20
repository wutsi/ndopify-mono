package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
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

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UploadKycImageEndpoint.sql"])
class PresignKycImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
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

        val response = presign("kyc-case-100", "image/jpeg")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("https://s3.example.com/put", response.body?.url)
        assertTrue(
            response.body!!.path!!.matches(Regex("kyc/kyc-case-100/images/[0-9a-f-]{36}\\.jpg")),
            response.body?.path,
        )
        verify(storage).presignedUploadUrl(eq(response.body!!.path!!), eq("image/jpeg"), any())
        verify(storage, never()).publicUrl(any())
    }

    @Test
    fun `presign - local`() {
        doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())

        val response = presign("kyc-case-100", "image/png")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.body?.url)
        assertNull(response.body?.path)
    }

    @Test
    fun `presign - not an image`() {
        val response = presignError("kyc-case-100", "application/pdf")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - another party`() {
        val response = presignError("kyc-case-200", "image/jpeg")

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - case not pending`() {
        val response = presignError("case-submitted", "image/jpeg")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - unknown case`() {
        val response = presignError("unknown-case", "image/jpeg")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - case of another tenant`() {
        val response = presignError("kyc-case-900", "image/jpeg")

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    @Test
    fun `presign - anonymous`() {
        anonymousUser = true

        val response = presignError("kyc-case-100", "image/jpeg")

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        verify(storage, never()).presignedUploadUrl(any(), any(), any())
    }

    private fun url(caseId: String) =
        "/v1/kyc/cases/$caseId/images/presign?identificationType=NATIONAL_ID&imageType=FRONT"

    private fun presign(caseId: String, contentType: String) =
        rest.postForEntity(url(caseId), PresignUploadRequest(contentType), PresignUploadResponse::class.java)

    private fun presignError(caseId: String, contentType: String) =
        rest.postForEntity(url(caseId), PresignUploadRequest(contentType), ErrorResponse::class.java)
}
