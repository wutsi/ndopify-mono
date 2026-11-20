package com.wutsi.ndopify.party.endpoint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.common.dto.AttachUploadRequest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.KycImageRepository
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.storage.StoredObject
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/party/UploadKycImageEndpoint.sql"])
class AttachKycImageEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Autowired
    private lateinit var caseDao: KycCaseRepository

    @Autowired
    private lateinit var imageDao: KycImageRepository

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    // The default storage (S3), where the new photos are uploaded
    private val storage = mock<StorageService>()

    // The fixture's image is LOCAL: its object is deleted through its own storage
    private val local = mock<StorageService>()

    private val path = "kyc/kyc-case-100/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    private val old = "kyc/kyc-case-100/images/kyc-image-100-back.png"

    @BeforeEach
    fun setUpStorage() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(StorageType.S3).whenever(storage).type()
        doReturn(local).whenever(storageProvider).get(StorageType.LOCAL)
    }

    private fun png(width: Int, height: Int): ByteArray {
        val out = ByteArrayOutputStream()
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out)
        return out.toByteArray()
    }

    private fun uploaded(path: String) {
        doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
        doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())
    }

    @Test
    fun `attach - new image`() {
        uploaded(path)

        val response = attach("kyc-case-100", KycImageType.FRONT, path)

        assertEquals(HttpStatus.OK, response.statusCode)
        val case = caseDao.findById("kyc-case-100").orElseThrow()
        val image = imageDao.findByKycCaseAndIdentificationTypeAndImageType(
            case,
            IdentificationType.NATIONAL_ID,
            KycImageType.FRONT,
        )
        assertNotNull(image)
        assertEquals(path, image.path)
        assertEquals("image/png", image.mimeType)
        assertEquals(StorageType.S3, image.storageType)
        assertEquals(1L, image.tenantId)
        assertEquals(2, imageDao.findAll().count())
        verify(storage, never()).delete(any())
        verify(local, never()).delete(any())
        verify(storage, never()).publicUrl(any())
    }

    @Test
    fun `attach - replaces the old image and deletes its object`() {
        uploaded(path)

        val response = attach("kyc-case-100", KycImageType.BACK, path)

        assertEquals(HttpStatus.OK, response.statusCode)
        val image = imageDao.findById("kyc-image-100-back").orElseThrow()
        assertEquals(path, image.path)
        assertEquals("image/png", image.mimeType)
        assertEquals(StorageType.S3, image.storageType)
        assertTrue(image.modifiedAt.after(image.createdAt))
        assertEquals(1, imageDao.findAll().count { it.imageType == KycImageType.BACK })
        verify(local).delete(old)
        verify(storage, never()).delete(any())
    }

    @Test
    fun `attach - the old object is deleted after the new one is verified`() {
        uploaded(path)

        attach("kyc-case-100", KycImageType.BACK, path)

        val order = inOrder(storage, local)
        order.verify(storage).readPrefix(eq(path), any())
        order.verify(local).delete(old)
    }

    @Test
    fun `attach - idempotent`() {
        uploaded(path)

        val first = attach("kyc-case-100", KycImageType.FRONT, path)
        val second = attach("kyc-case-100", KycImageType.FRONT, path)

        assertEquals(HttpStatus.OK, first.statusCode)
        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(1, imageDao.findAll().count { it.imageType == KycImageType.FRONT })
        verify(storage, times(1)).metadata(path)
        verify(storage, never()).delete(any())
        verify(local, never()).delete(any())
    }

    @Test
    fun `attach - failed delete still attaches`() {
        uploaded(path)
        doThrow(IllegalStateException("storage down")).whenever(local).delete(old)

        val response = attach("kyc-case-100", KycImageType.BACK, path)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(path, imageDao.findById("kyc-image-100-back").orElseThrow().path)
    }

    @Test
    fun `attach - another case's path`() {
        val other = "kyc/kyc-case-200/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        val response = attachError("kyc-case-100", KycImageType.FRONT, other)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).metadata(any())
        assertEquals(0, imageDao.findAll().count { it.imageType == KycImageType.FRONT })
    }

    @Test
    fun `attach - path traversal`() {
        val traversal = "kyc/kyc-case-100/images/../../kyc-case-200/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

        val response = attachError("kyc-case-100", KycImageType.FRONT, traversal)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `attach - missing object`() {
        doReturn(null).whenever(storage).metadata(path)

        val response = attachError("kyc-case-100", KycImageType.FRONT, path)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        assertEquals(0, imageDao.findAll().count { it.imageType == KycImageType.FRONT })
    }

    @Test
    fun `attach - not an image`() {
        doReturn(StoredObject("text/plain", 4)).whenever(storage).metadata(path)
        doReturn("nope".toByteArray()).whenever(storage).readPrefix(eq(path), any())

        val response = attachError("kyc-case-100", KycImageType.BACK, path)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage).delete(path)
        verify(local, never()).delete(any())
        assertEquals(old, imageDao.findById("kyc-image-100-back").orElseThrow().path)
    }

    @Test
    fun `attach - too large`() {
        doReturn(StoredObject("image/png", 5L * 1024 * 1024 + 1)).whenever(storage).metadata(path)

        val response = attachError("kyc-case-100", KycImageType.FRONT, path)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
        verify(storage).delete(path)
        assertEquals(0, imageDao.findAll().count { it.imageType == KycImageType.FRONT })
    }

    @Test
    fun `attach - another party`() {
        val other = "kyc/kyc-case-200/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        uploaded(other)

        val response = attachError("kyc-case-200", KycImageType.FRONT, other)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
        verify(storage, never()).metadata(any())
        assertEquals(1, imageDao.findAll().count())
    }

    @Test
    fun `attach - case not pending`() {
        val submitted = "kyc/case-submitted/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
        uploaded(submitted)

        val response = attachError("case-submitted", KycImageType.FRONT, submitted)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
        verify(storage, never()).metadata(any())
        assertEquals(1, imageDao.findAll().count())
    }

    @Test
    fun `attach - unknown case`() {
        val response = attachError("unknown-case", KycImageType.FRONT, path)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `attach - case of another tenant`() {
        val response = attachError("kyc-case-900", KycImageType.FRONT, path)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_NOT_FOUND, response.body?.error?.code)
        verify(storage, never()).metadata(any())
    }

    @Test
    fun `attach - anonymous`() {
        anonymousUser = true

        val response = attachError("kyc-case-100", KycImageType.FRONT, path)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
        assertEquals(1, imageDao.findAll().count())
    }

    private fun url(caseId: String, imageType: KycImageType) =
        "/v1/kyc/cases/$caseId/images/attach?identificationType=NATIONAL_ID&imageType=$imageType"

    private fun attach(caseId: String, imageType: KycImageType, path: String) =
        rest.postForEntity(url(caseId, imageType), AttachUploadRequest(path), Void::class.java)

    private fun attachError(caseId: String, imageType: KycImageType, path: String) =
        rest.postForEntity(url(caseId, imageType), AttachUploadRequest(path), ErrorResponse::class.java)
}
