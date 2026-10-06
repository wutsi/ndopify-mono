package com.wutsi.ndopify.party.server.service.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.dto.IdentificationImageType
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.service.IdentificationInfoExtractor
import com.wutsi.ndopify.party.server.service.IdentificationInfoExtractorProvider
import com.wutsi.ndopify.party.server.service.identification.IdentificationInfo
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.Date
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KycVerifierIdentificationTest {
    private val dao = mock<KycVerificationRepository>()
    private val clock = mock<Clock>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val infoExtractorProvider = mock<IdentificationInfoExtractorProvider>()
    private val infoExtractor = mock<IdentificationInfoExtractor>()
    private val verifier = KycVerifierIdentification(dao, clock, storageProvider, infoExtractorProvider)
    private val fmt = SimpleDateFormat("yyyy-MM-dd")

    private val now = 1_700_000_000_000L

    private val party = PartyEntity(
        id = 200L,
        firstName = "John",
        lastName = "Doe",
    )

    private val image = IdentificationImageEntity(
        id = "image-1",
        imageType = IdentificationImageType.FRONT,
        path = "identifications/image-1.jpg",
        mimeType = "image/jpeg",
    )

    private val identification = IdentificationEntity(
        id = "identification-1",
        party = party,
        images = listOf(image),
        type = IdentificationType.NATIONAL_ID,
    )

    private fun verification(identification: IdentificationEntity = this.identification): KycVerificationEntity {
        return KycVerificationEntity(
            id = "verification-100",
            case = KycCaseEntity(
                party = party,
                identification = identification,
            ),
            status = KycStatus.PENDING,
        )
    }

    private fun setUpExtraction(info: IdentificationInfo) {
        doReturn(now).whenever(clock).millis()
        doReturn(storage).whenever(storageProvider).get()
        doReturn(infoExtractor).whenever(infoExtractorProvider).get()
        doReturn(info).whenever(infoExtractor).extract(any())
    }

    @BeforeEach
    fun setUp() {
        fmt.timeZone = TimeZone.getTimeZone("UTC")
    }

    @Test
    fun `invalid identification - rejected`() {
        setUpExtraction(
            IdentificationInfo(
                type = IdentificationType.NATIONAL_ID,
                firstName = "John",
                lastName = "Doe",
                valid = false,
                invalidityReason = "Blurred image",
            )
        )

        val verif = verification()
        val result = verifier.verify(verif)

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.INVALID, result.errorCode)
        assertEquals("Blurred image", result.errorMessage)

        verify(dao).save(
            verif.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
        verify(dao).save(result)
    }

    @Test
    fun `type mismatch - rejected`() {
        setUpExtraction(
            IdentificationInfo(
                type = IdentificationType.PASSPORT,
                firstName = "John",
                lastName = "Doe",
                valid = true,
            )
        )

        val verif = verification()
        val result = verifier.verify(verif)

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.TYPE_MISMATCH, result.errorCode)
    }

    @Test
    fun `expired identification - rejected`() {
        setUpExtraction(
            IdentificationInfo(
                type = IdentificationType.NATIONAL_ID,
                firstName = "John",
                lastName = "Doe",
                valid = true,
                expiryDate = fmt.parse("2000-01-01"),
            )
        )

        val verif = verification()
        val result = verifier.verify(verif)

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.EXPIRED, result.errorCode)
    }

    @Test
    fun `not expired identification - verified without error code`() {
        setUpExtraction(
            IdentificationInfo(
                type = IdentificationType.NATIONAL_ID,
                firstName = "John",
                lastName = "Doe",
                valid = true,
                expiryDate = fmt.parse("2099-01-01"),
            )
        )

        val verif = verification()
        val result = verifier.verify(verif)

        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(null, result.errorCode)
    }

    @Test
    fun `matching name - verified with full score`() {
        setUpExtraction(
            IdentificationInfo(
                type = IdentificationType.NATIONAL_ID,
                firstName = "John",
                lastName = "Doe",
                valid = true,
            )
        )

        val verif = verification()
        val result = verifier.verify(verif)

        assertEquals(KycStatus.VERIFIED, result.status)
        assertEquals(null, result.errorCode)
        assertEquals(100, result.score)

        verify(dao).save(
            verif.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
        verify(dao).save(
            verif.copy(
                status = KycStatus.VERIFIED,
                score = 100,
                errorCode = null,
                errorMessage = null,
                modifiedAt = Date(now),
            )
        )
    }

    @Test
    fun `mismatching name - rejected with low score`() {
        setUpExtraction(
            IdentificationInfo(
                type = IdentificationType.NATIONAL_ID,
                firstName = "Alice",
                lastName = "Wong",
                valid = true,
            )
        )

        val verif = verification()
        val result = verifier.verify(verif)

        assertEquals(KycStatus.REJECTED, result.status)
        assertEquals(KycErrorCode.LOW_SCORE, result.errorCode)
        assertEquals(true, result.score!! < 90)
    }

    @Test
    fun `missing image path - conflict exception`() {
        doReturn(now).whenever(clock).millis()

        val imageWithoutPath = image.copy(path = null)
        val identificationWithoutPath = identification.copy(images = listOf(imageWithoutPath))
        val verif = verification(identificationWithoutPath)

        assertFailsWith<ConflictException> {
            verifier.verify(verif)
        }

        verify(dao).save(
            verif.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(now),
            )
        )
    }
}
