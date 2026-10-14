package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
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
import java.time.Clock
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KycCaseVerifierSelfieTest {
    private val clock = mock<Clock>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val infoExtractorProvider = mock<KycInfoExtractorProvider>()
    private val infoExtractor = mock<KycInfoExtractor>()
    private val logger = DefaultKVLogger()
    private val verifier = KycCaseVerifierSelfie(clock, storageProvider, infoExtractorProvider, logger)

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
        path = "kyc/image-back.jpg",
        mimeType = "image/jpeg",
    )

    private val selfie = KycImageEntity(
        id = "image-selfie",
        imageType = KycImageType.SELFIE,
        path = "kyc/image-selfie.png",
        mimeType = "image/png",
    )

    private fun case(images: List<KycImageEntity> = listOf(front, selfie)) = KycCaseEntity(
        id = "case-1",
        party = party,
        images = images,
        identificationType = IdentificationType.NATIONAL_ID,
    )

    @BeforeEach
    fun setUp() {
        doReturn(now).whenever(clock).millis()
        doReturn(storage).whenever(storageProvider).get(any<StorageType>())
        doReturn(infoExtractor).whenever(infoExtractorProvider).get(KycVerificationType.SELFIE)
    }

    @AfterEach
    fun tearDown() {
        logger.log()
    }

    private fun setUpExtraction(info: KycInfo) {
        doReturn(info).whenever(infoExtractor).extract(any())
    }

    private fun info(
        valid: Boolean = true,
        invalidityReason: String? = null,
        confidence: Double = 1.0,
    ) = KycInfo(
        type = IdentificationType.NATIONAL_ID,
        valid = valid,
        invalidityReason = invalidityReason,
        confidence = confidence,
    )

    @Test
    fun `selfie matches with full confidence - verified`() {
        setUpExtraction(info())

        val case = case()
        val results = verifier.verify(case)
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(KycVerificationType.SELFIE, result.type)
        assertEquals(100, result.score)
        assertNull(result.errorCode)
        assertNull(result.errorMessage)
        assertEquals(case, result.case)
        assertNotNull(result.id)
        assertEquals(Date(now), result.createdAt)
        assertEquals(Date(now), result.modifiedAt)
    }

    @Test
    fun `front and selfie images are downloaded and sent to the extractor`() {
        setUpExtraction(info())

        verifier.verify(case(listOf(front, back, selfie)))

        verify(storage).get(eq(front.path!!), any())
        verify(storage).get(eq(selfie.path!!), any())
        verify(storage, never()).get(eq(back.path!!), any())

        val captor = argumentCaptor<List<File>>()
        verify(infoExtractor).extract(captor.capture())
        val files = captor.firstValue
        assertEquals(2, files.size)
        assertTrue(files[0].name.endsWith(".jpg"))
        assertTrue(files[1].name.endsWith(".png"))
    }

    @Test
    fun `high confidence - verified with score from confidence`() {
        setUpExtraction(info(confidence = 0.95))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(95, result.score)
        assertNull(result.errorCode)
    }

    @Test
    fun `low confidence - rejected with low score`() {
        setUpExtraction(info(confidence = 0.5))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.LOW_SCORE, result.errorCode)
        assertEquals(50, result.score)
        assertNull(result.errorMessage)
    }

    @Test
    fun `selfie does not match - rejected`() {
        setUpExtraction(info(valid = false, invalidityReason = "Selfie does not match", confidence = 0.99))

        val results = verifier.verify(case())
        assertEquals(1, results.size)
        val result = results[0]

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.INVALID, result.errorCode)
        assertEquals("Selfie does not match", result.errorMessage)
    }

    @Test
    fun `no front or selfie image - no verification`() {
        val results = verifier.verify(case(listOf(back)))

        assertTrue(results.isEmpty())
        verify(infoExtractor, never()).extract(any())
    }

    @Test
    fun `no image - no verification`() {
        val results = verifier.verify(case(emptyList()))

        assertTrue(results.isEmpty())
    }

    @Test
    fun `missing image path - no verification`() {
        val results = verifier.verify(case(listOf(front.copy(path = null), selfie.copy(path = null))))

        assertTrue(results.isEmpty())
    }
}
