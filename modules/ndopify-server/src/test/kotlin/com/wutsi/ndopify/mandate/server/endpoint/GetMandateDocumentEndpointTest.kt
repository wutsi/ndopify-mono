package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.GetMandateDocumentResponse
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reading one document of a mandate: private, a page carries neither its path nor a URL. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateDocumentEndpoint.sql"])
class GetMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val s3 = mock<StorageService>()
    private val local = mock<StorageService>()
    private val defaultStorage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(s3).whenever(storageProvider).get(StorageType.S3)
        doReturn(local).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(defaultStorage).whenever(storageProvider).get()
    }

    @Test
    fun `get - pages ordered by index, without any url`() {
        val document = rest.getForEntity("/v1/mandates/100/documents/10", GetMandateDocumentResponse::class.java).body!!.document

        assertEquals(2, document.pages.size)
        assertEquals(listOf(0, 1), document.pages.map { it.index })
        assertEquals(listOf("image/png", "image/png"), document.pages.map { it.contentType })
        assertTrue(document.fields.isEmpty())
        verify(s3, never()).generatePresignedUrl(any(), any())
        verify(local, never()).generatePresignedUrl(any(), any())
        verify(defaultStorage, never()).generatePresignedUrl(any(), any())
    }

    @Test
    fun `get - a local page and a legacy page`() {
        val document = rest.getForEntity("/v1/mandates/100/documents/11", GetMandateDocumentResponse::class.java).body!!.document

        assertEquals(listOf(0, 1), document.pages.map { it.index })
        verify(s3, never()).generatePresignedUrl(any(), any())
        verify(local, never()).generatePresignedUrl(any(), any())
        verify(defaultStorage, never()).generatePresignedUrl(any(), any())
    }

    @Test
    fun `get - the json body carries no path and no public url`() {
        val body = rest.getForEntity("/v1/mandates/100/documents/10", String::class.java).body!!

        assertFalse(body.contains("\"path\""), body)
        assertFalse(body.contains("\"url\""), body)
        assertFalse(body.contains("publicUrl"), body)
    }

    @Test
    fun `get - a document of another mandate`() {
        val response = rest.getForEntity("/v1/mandates/100/documents/12", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `get - an unknown document`() {
        val response = rest.getForEntity("/v1/mandates/100/documents/999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_DOCUMENT_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `get - unknown mandate`() {
        val response = rest.getForEntity("/v1/mandates/999/documents/10", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `get - a mandate of another agent`() {
        val response = rest.getForEntity("/v1/mandates/200/documents/10", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `get - anonymous`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/mandates/100/documents/10", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
