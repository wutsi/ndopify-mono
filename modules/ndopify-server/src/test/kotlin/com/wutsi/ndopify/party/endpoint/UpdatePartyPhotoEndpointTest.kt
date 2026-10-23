package com.wutsi.ndopify.party.endpoint

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
import com.wutsi.ndopify.party.server.dao.PartyRepository
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
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UpdatePartyPhotoEndpoint.sql"])
class UpdatePartyPhotoEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var dao: PartyRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
        whenever(storage.publicUrl(any())).thenAnswer { URL("https://cdn.example.com/${it.arguments[0]}") }
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
    fun photo() {
        val response = rest.postForEntity(
            "/v1/parties/100/photo",
            request("me.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val party = dao.findById(100L).get()
        val path = party.photoUrl!!.removePrefix("https://cdn.example.com/")
        assertTrue(path.matches(Regex("party/100/photo/[0-9a-f-]{36}\\.jpg")), path)
        assertEquals(TENANT_ID, party.tenantId)
        assertTrue(party.modifiedAt.after(party.createdAt))

        verify(storage).store(eq(path), any(), eq("image/jpeg"))
    }

    @Test
    fun `not an image`() {
        val response = rest.postForEntity(
            "/v1/parties/100/photo",
            request("me.pdf", MediaType.APPLICATION_PDF, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertNull(dao.findById(100L).get().photoUrl)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `no file`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        val empty = HttpEntity(LinkedMultiValueMap<String, Any>().apply { add("other", "x") }, headers)

        val response = rest.postForEntity("/v1/parties/100/photo", empty, ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
        assertEquals("multipartFile", response.body?.error?.parameter?.name)
    }

    @Test
    fun `party not found`() {
        val response = rest.postForEntity(
            "/v1/parties/999/photo",
            request("me.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `party belongs to a different tenant`() {
        overrideTenantId = 2L

        val response = rest.postForEntity(
            "/v1/parties/100/photo",
            request("me.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.PARTY_NOT_FOUND, response.body?.error?.code)
    }
}
