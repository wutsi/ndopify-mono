package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.KycImageRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import org.springframework.util.LinkedMultiValueMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UploadKycImageEndpoint.sql"])
class UploadKycImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var caseDao: KycCaseRepository

    @Autowired
    private lateinit var imageDao: KycImageRepository

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    private val storage = mock<StorageService>()

    // Where a directly uploaded photo is
    private val s3 = mock<StorageService>()

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)
        doReturn(s3).whenever(storageProvider).get(StorageType.S3)
        doReturn(StorageType.LOCAL).whenever(storage).type()
    }

    private fun request(
        fileName: String,
        contentType: MediaType,
        content: ByteArray
    ): HttpEntity<LinkedMultiValueMap<String, Any>> {
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

    private fun url(caseId: String, imageType: KycImageType) =
        "/v1/kyc/cases/$caseId/images/upload?identificationType=NATIONAL_ID&imageType=$imageType"

    @Test
    fun `upload new image`() {
        val response = rest.postForEntity(
            url("kyc-case-100", KycImageType.FRONT),
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val case = caseDao.findById("kyc-case-100").get()
        val image = imageDao.findByKycCaseAndIdentificationTypeAndImageType(
            case,
            IdentificationType.NATIONAL_ID,
            KycImageType.FRONT,
        )
        assertNotNull(image)
        assertEquals("kyc/kyc-case-100/images/${image.id}.jpg", image.path)
        assertEquals("image/jpeg", image.mimeType)
        assertEquals(StorageType.LOCAL, image.storageType)
        assertEquals(1L, image.tenantId)

        verify(storage).store(eq("kyc/kyc-case-100/images/${image.id}.jpg"), any(), eq("image/jpeg"))
    }

    @Test
    fun `re-upload replaces existing image`() {
        val response = rest.postForEntity(
            url("kyc-case-100", KycImageType.BACK),
            request("back.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        val image = imageDao.findById("kyc-image-100-back").get()
        assertEquals("kyc/kyc-case-100/images/kyc-image-100-back.jpg", image.path)
        assertEquals("image/jpeg", image.mimeType)
        assertEquals(StorageType.LOCAL, image.storageType)
        assertTrue(image.modifiedAt.after(image.createdAt))

        assertEquals(1, imageDao.findAll().count())
        verify(storage).store(eq("kyc/kyc-case-100/images/kyc-image-100-back.jpg"), any(), eq("image/jpeg"))
        // The .png is at another path: deleted
        verify(storage).delete("kyc/kyc-case-100/images/kyc-image-100-back.png")
    }

    @Test
    fun `re-upload of the same path deletes nothing`() {
        val response = rest.postForEntity(
            url("kyc-case-100", KycImageType.BACK),
            request("back.png", MediaType.IMAGE_PNG, byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(storage).store(eq("kyc/kyc-case-100/images/kyc-image-100-back.png"), any(), eq("image/png"))
        verify(storage, never()).delete(any())
    }

    @Test
    fun `re-upload replaces a direct upload and deletes its object`() {
        val direct = "kyc/kyc-case-100/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        jdbc.update("UPDATE T_KYC_IMAGE SET path='$direct', storage_type='S3' WHERE id='kyc-image-100-back'")

        val response = rest.postForEntity(
            url("kyc-case-100", KycImageType.BACK),
            request("back.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            Void::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        val image = imageDao.findById("kyc-image-100-back").get()
        assertEquals("kyc/kyc-case-100/images/kyc-image-100-back.jpg", image.path)
        assertEquals(StorageType.LOCAL, image.storageType)
        verify(s3).delete(direct)
        verify(storage, never()).delete(any())
    }

    @Test
    fun `case submitted during the store`() {
        doAnswer {
            // Another tab submits the case while the photo is stored: a plain SQL update, outside the request's session
            jdbc.update("UPDATE T_KYC_CASE SET status='SUBMITTED' WHERE id='kyc-case-100'")
            null
        }.whenever(storage).store(any(), any(), any())

        val response = rest.postForEntity(
            url("kyc-case-100", KycImageType.FRONT),
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
        assertEquals(1, imageDao.findAll().count()) // only the fixture's BACK image
        verify(storage).delete(any())
    }

    @Test
    fun `case not found`() {
        val response = rest.postForEntity(
            url("unknown-case", KycImageType.FRONT),
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    @Test
    fun `case of another party`() {
        val response = rest.postForEntity(
            url("kyc-case-200", KycImageType.FRONT),
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
        assertEquals(1, imageDao.findAll().count()) // only the fixture's BACK image
    }

    @Test
    fun `case of another tenant`() {
        val response = rest.postForEntity(
            url("kyc-case-900", KycImageType.FRONT),
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
    }

    // Every status but PENDING_VERIFICATION, so a status added to KycStatus later is covered too (with a fixture row).
    @ParameterizedTest
    @EnumSource(value = KycStatus::class, names = ["PENDING_VERIFICATION"], mode = EnumSource.Mode.EXCLUDE)
    fun `case not pending - bad request, documents frozen`(status: KycStatus) {
        val caseId = "case-" + status.name.lowercase().replace('_', '-')
        assertEquals(status, caseDao.findById(caseId).orElseThrow().status)

        val response = rest.postForEntity(
            url(caseId, KycImageType.FRONT),
            request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
        assertEquals(1, imageDao.findAll().count())
    }

    @Test
    fun `invalid mime type`() {
        val response = rest.postForEntity(
            url("kyc-case-100", KycImageType.FRONT),
            request("front.txt", MediaType.TEXT_PLAIN, byteArrayOf(1, 2, 3)),
            ErrorResponse::class.java,
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_IMAGE_INVALID_MIME_TYPE, response.body?.error?.code)
        verify(storage, never()).store(any(), any(), any())
        assertEquals(1, imageDao.findAll().count())
    }
}
