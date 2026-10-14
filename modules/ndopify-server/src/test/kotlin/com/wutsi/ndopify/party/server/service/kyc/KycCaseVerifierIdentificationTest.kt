package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.service.KycInfoExtractor
import com.wutsi.ndopify.party.server.service.KycInfoExtractorProvider
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.dto.StorageType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.io.File
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KycCaseVerifierIdentificationTest {
    private val clock = mock<Clock>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val infoExtractorProvider = mock<KycInfoExtractorProvider>()
    private val infoExtractor = mock<KycInfoExtractor>()
    private val logger = DefaultKVLogger()
    private val verifier = KycCaseVerifierIdentification(clock, storageProvider, infoExtractorProvider, logger)
    private val fmt = SimpleDateFormat("yyyy-MM-dd")

    private val now = 1_700_000_000_000L

    private val party = PartyEntity(
        id = 200L,
        firstName = "John",
        lastName = "Doe",
    )

    private val front = KycImageEntity(
        id = "image-front",
        imageType = KycImageType.FRONT,
        path = "kyc/image-front.jpg",
        mimeType = "image/jpeg",
    )

    private val back = KycImageEntity(
        id = "image-back",
        imageType = KycImageType.BACK,
        path = "kyc/image-back.png",
        mimeType = "image/png",
    )

    private val unknown = KycImageEntity(
        id = "image-unknown",
        imageType = KycImageType.UNKNOWN,
        path = "kyc/image-unknown.jpg",
        mimeType = "image/jpeg",
    )

    private fun case(images: List<KycImageEntity> = listOf(front, back)) = KycCaseEntity(
        id = "case-1",
        party = party,
        images = images,
        identificationType = IdentificationType.NATIONAL_ID,
    )

    @BeforeEach
    fun setUp() {
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        doReturn(now).whenever(clock).millis()
        doReturn(storage).whenever(storageProvider).get(any<StorageType>())
        doReturn(infoExtractor).whenever(infoExtractorProvider).get(KycVerificationType.IDENTIFICATION)
    }

    @AfterEach
    fun tearDown() {
        logger.log()
    }

    private fun setUpExtraction(info: KycInfo) {
        doReturn(info).whenever(infoExtractor).extract(any())
    }

    private fun info(
        type: IdentificationType = IdentificationType.NATIONAL_ID,
        firstName: String = "John",
        lastName: String = "Doe",
        valid: Boolean = true,
        invalidityReason: String? = null,
        expiryDate: Date? = null,
        confidence: Double = 1.0,
        number: String = "454A-409-AA",
    ) = KycInfo(
        type = type,
        firstName = firstName,
        lastName = lastName,
        valid = valid,
        invalidityReason = invalidityReason,
        expiryDate = expiryDate,
        confidence = confidence,
        number = number,
    )

    @Test
    fun `matching name - verified with full score`() {
        setUpExtraction(info())

        val case = case()
        val results = verifier.verify(case)
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(KycVerificationType.IDENTIFICATION, result.type)
        assertEquals(100, result.score)
        assertNull(result.errorCode)
        assertNull(result.errorMessage)
        assertEquals(case, result.case)
        assertNotNull(result.id)
        assertEquals(Date(now), result.createdAt)
        assertEquals(Date(now), result.modifiedAt)
    }

    @Test
    fun `front and back images are downloaded and sent to the extractor`() {
        setUpExtraction(info())

        verifier.verify(case(listOf(front, back, unknown)))

        verify(storage).get(eq(front.path!!), any())
        verify(storage).get(eq(back.path!!), any())
        verify(storage, never()).get(eq(unknown.path!!), any())

        val captor = argumentCaptor<List<File>>()
        verify(infoExtractor).extract(captor.capture())
        val files = captor.firstValue
        assertEquals(2, files.size)
        assertTrue(files[0].name.endsWith(".jpg"))
        assertTrue(files[1].name.endsWith(".png"))
    }

    @Test
    fun `invalid identification - rejected`() {
        setUpExtraction(info(valid = false, invalidityReason = "Blurred image"))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.INVALID, result.errorCode)
        assertEquals("Blurred image", result.errorMessage)
    }

    @Test
    fun `type mismatch - rejected`() {
        setUpExtraction(info(type = IdentificationType.PASSPORT))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.TYPE_MISMATCH, result.errorCode)
        assertNull(result.errorMessage)
    }

    @Test
    fun `expired identification - rejected`() {
        setUpExtraction(info(expiryDate = fmt.parse("2000-01-01")))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.EXPIRED, result.errorCode)
    }

    @Test
    fun `not expired identification - verified`() {
        setUpExtraction(info(expiryDate = fmt.parse("2099-01-01")))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.VERIFIED, result.status)
        assertNull(result.errorCode)
    }

    @Test
    fun `mismatching name - rejected with low score`() {
        setUpExtraction(info(firstName = "Alice", lastName = "Wong"))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.LOW_SCORE, result.errorCode)
        assertTrue(result.score!! < 90)
    }

    @Test
    fun `no front or back image - conflict`() {
        val results = verifier.verify(case(listOf(unknown)))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `no image - conflict`() {
        val results = verifier.verify(case(emptyList()))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `missing image path - conflict`() {
        val results = verifier.verify(case(listOf(front.copy(path = null))))
        assertTrue(results.isEmpty())
    }

    @Test
    fun `images are read from the storage they were stored in`() {
        setUpExtraction(info())

        verifier.verify(case(listOf(front.copy(storageType = StorageType.S3), back.copy(storageType = StorageType.LOCAL))))

        verify(storageProvider).get(StorageType.S3)
        verify(storageProvider).get(StorageType.LOCAL)
    }

    @Test
    fun `downloaded images are deleted`() {
        val files = mutableListOf<File>()
        doAnswer { inv ->
            files.addAll(inv.getArgument<List<File>>(0))
            info()
        }.whenever(infoExtractor).extract(any())

        verifier.verify(case())

        assertEquals(2, files.size)
        assertTrue(files.none { file -> file.exists() })
    }

    @Test
    fun `downloaded images are deleted when the extraction fails`() {
        val files = mutableListOf<File>()
        doAnswer { inv ->
            files.addAll(inv.getArgument<List<File>>(0))
            throw RuntimeException("LLM unavailable")
        }.whenever(infoExtractor).extract(any())

        assertFailsWith<RuntimeException> { verifier.verify(case()) }

        assertEquals(2, files.size)
        assertTrue(files.none { file -> file.exists() })
    }
}
