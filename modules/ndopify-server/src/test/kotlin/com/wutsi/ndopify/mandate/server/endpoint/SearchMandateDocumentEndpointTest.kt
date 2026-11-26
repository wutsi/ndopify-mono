package com.wutsi.ndopify.mandate.server.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateDocumentResponse
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

/** The documents of a mandate, newest first. */
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateDocumentEndpoint.sql"])
class SearchMandateDocumentEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val s3 = mock<StorageService>()
    private val local = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(s3).whenever(storageProvider).get()
        doReturn(s3).whenever(storageProvider).get(StorageType.S3)
        doReturn(local).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(URL("https://s3.example.com/page")).whenever(s3).generatePresignedUrl(any(), any())
        doReturn(URL("https://local.example.com/page")).whenever(local).generatePresignedUrl(any(), any())
    }

    @Test
    fun `search - a mandate's documents newest first`() {
        val documents = rest.getForEntity("/v1/mandates/100/documents", SearchMandateDocumentResponse::class.java).body!!.documents

        assertEquals(listOf(11L, 10L), documents.map { it.id })
        assertEquals(listOf("Bulletin mars", "Titre foncier"), documents.map { it.title })
    }

    @Test
    fun `search - a closed mandate's documents`() {
        val response = rest.getForEntity("/v1/mandates/300/documents", SearchMandateDocumentResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(emptyList(), response.body?.documents)
    }

    @Test
    fun `search - unknown mandate`() {
        val response = rest.getForEntity("/v1/mandates/999/documents", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `search - a mandate of another agent`() {
        val response = rest.getForEntity("/v1/mandates/200/documents", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `search - anonymous`() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/mandates/100/documents", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
    }
}
