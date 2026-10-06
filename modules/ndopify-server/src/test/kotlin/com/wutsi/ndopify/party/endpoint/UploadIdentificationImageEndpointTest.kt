package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.TenantAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.party.server.dao.IdentificationImageRepository
import com.wutsi.ndopify.party.server.dao.IdentificationRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UploadIdentificationImageEndpoint.sql"])
class UploadIdentificationImageEndpointTest : TenantAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var identificationDao: IdentificationRepository

    @Autowired
    private lateinit var imageDao: IdentificationImageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(StorageType.LOCAL).whenever(storage).type()
    }

    private fun request(fileName: String, contentType: MediaType, content: ByteArray): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val resource = object : ByteArrayResource(content) {
            override fun getFilename(): String = fileName
        }

        val fileHeaders = HttpHeaders()
        fileHeaders.contentType = contentType
        val filePart = HttpEntity(resource, fileHeaders)

        val body = LinkedMultiValueMap<String, Any>()
        body.add("multipartFile", filePart)

        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        return HttpEntity(body, headers)
    }

    @Test
    fun upload() {
        val response = rest.postForEntity(
            "/v1/identifications/id-100/images/upload?imageType=FRONT",
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        verify(storage).store(
            eq("identifications/id-100/images/img-100-front.jpg"),
            any(),
            eq("image/jpeg"),
        )

        val identification = identificationDao.findById("id-100").get()
        val image = imageDao.findByIdentificationAndImageType(identification, IdentificationImageType.FRONT)!!
        assertEquals("identifications/id-100/images/img-100-front.jpg", image.path)
        assertEquals("image/jpeg", image.mimeType)
        assertEquals(StorageType.LOCAL, image.storageType)
        assertNotNull(image.uploadedAt)
    }

    @Test
    fun `identification not found`() {
        val response = rest.postForEntity(
            "/v1/identifications/unknown-id/images/upload?imageType=FRONT",
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `image not found`() {
        val response = rest.postForEntity(
            "/v1/identifications/id-300/images/upload?imageType=FRONT",
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `already uploaded`() {
        val response = rest.postForEntity(
            "/v1/identifications/id-100/images/upload?imageType=BACK",
            request("back.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_ALREADY_UPLOADED, response.body?.error?.code)
    }

    @Test
    fun `invalid mime type`() {
        val response = rest.postForEntity(
            "/v1/identifications/id-100/images/upload?imageType=FRONT",
            request("front.txt", MediaType.TEXT_PLAIN, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.IDENTIFICATION_IMAGE_INVALID_MIME_TYPE, response.body?.error?.code)

        val identification = identificationDao.findById("id-100").get()
        val image = imageDao.findByIdentificationAndImageType(identification, IdentificationImageType.FRONT)!!
        assertNull(image.path)
    }
}
