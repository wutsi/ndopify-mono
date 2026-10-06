package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.IdentificationEntity
import com.wutsi.ndopify.party.server.domain.IdentificationImageEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.IdentificationInfoExtractorProvider
import com.wutsi.ndopify.party.server.service.KycVerifier.Companion.LOW_SCORE_THRESHOLD
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.util.KycUtils
import com.wutsi.ndopify.util.MimeUtils
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.time.Clock
import java.util.Date

@Service
class KycVerifierIdentification(
    dao: KycVerificationRepository,
    clock: Clock,
    private val storageProvider: StorageServiceProvider,
    private val infoExtractorProvider: IdentificationInfoExtractorProvider,
) : AbstractKycVerifier(dao, clock) {
    @Transactional
    override fun verify(verification: KycVerificationEntity): KycVerificationEntity {
        startReview(verification)
        return doReview(verification, verification.case.identification)
    }

    private fun doReview(
        verification: KycVerificationEntity,
        identification: IdentificationEntity
    ): KycVerificationEntity {
        val files = identification.images.map { img -> toFile(img) }
        val infoExtractor = infoExtractorProvider.get()
        val info = infoExtractor.extract(files)

        val partyName = "${identification.party.firstName} ${identification.party.lastName}"
        val infoName = "${info.firstName} ${info.lastName}"
        val today = Date(clock.millis())
        val score = 100.0 * KycUtils.verifyName(partyName, infoName)
        var status = KycStatus.REJECTED
        var errorCode: String? = null
        var errorMessage: String? = null

        if (!info.valid) {
            errorCode = KycErrorCode.INVALID
            errorMessage = info.invalidityReason
        } else if (info.type != identification.type) {
            errorCode = KycErrorCode.TYPE_MISMATCH
        } else if (info.expiryDate != null && today.after(info.expiryDate)) {
            errorCode = KycErrorCode.EXPIRED
        } else {
            if (score < LOW_SCORE_THRESHOLD) {
                errorCode = KycErrorCode.LOW_SCORE
            } else {
                status = KycStatus.VERIFIED
            }
        }

        val copy = verification.copy(
            status = status,
            errorCode = errorCode,
            errorMessage = errorMessage,
            score = score.toInt(),
            modifiedAt = Date(clock.millis())
        )
        dao.save(copy)
        return copy
    }

    private fun toFile(img: IdentificationImageEntity): File {
        val path = img.path
            ?: throw ConflictException(
                Error(
                    code = ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE,
                    data = mapOf(
                        "imageId" to img.id,
                        "type" to img.imageType.name
                    )
                )
            )

        val extension = MimeUtils.getExtensionFromMimeType(img.mimeType)
        val file = Files.createTempFile(img.id + "-${img.imageType}", ".$extension").toFile()
        val storage = storageProvider.get()
        FileOutputStream(file).use { out ->
            storage.get(path, out)
        }
        return file
    }
}
