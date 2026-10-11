package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycImageEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier
import com.wutsi.ndopify.party.server.service.KycInfoExtractorProvider
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
import java.util.UUID

@Service
class KycCaseVerifierIdentification(
    private val verificationDao: KycVerificationRepository,
    private val clock: Clock,
    private val storageProvider: StorageServiceProvider,
    private val infoExtractorProvider: KycInfoExtractorProvider,
) : KycCaseVerifier {
    @Transactional
    override fun verify(case: KycCaseEntity): KycVerificationEntity? {
        val files = getFiles(case)
        if (files.isEmpty()) {
            return null
        }

        val verification = start(case)
        val info = extractInformations(files)
        return match(verification, info)
    }

    private fun start(case: KycCaseEntity): KycVerificationEntity {
        val now = Date(clock.millis())
        return verificationDao.save(
            KycVerificationEntity(
                id = UUID.randomUUID().toString(),
                case = case,
                type = KycVerificationType.IDENTIFICATION,
                status = KycStatus.IN_PROGRESS,
                createdAt = now,
                modifiedAt = now
            )
        )
    }

    private fun extractInformations(files: List<File>): KycInfo {
        val infoExtractor = infoExtractorProvider.get(KycVerificationType.IDENTIFICATION)
        return infoExtractor.extract(files)
    }

    private fun getFiles(case: KycCaseEntity): List<File> {
        val files = case.images
            .filter { img -> img.path != null }
            .filter { img -> (img.imageType == KycImageType.FRONT) || (img.imageType == KycImageType.BACK) }
            .map { img -> toFile(img) }
        return files
    }

    private fun toFile(img: KycImageEntity): File {
        val path = img.path
            ?: throw ConflictException(
                Error(
                    code = ErrorCode.KYC_IMAGE_NOT_FOUND,
                    message = "No identification image found for image ${img.id} of type ${img.imageType}"
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

    private fun match(
        verification: KycVerificationEntity,
        info: KycInfo,
    ): KycVerificationEntity {
        val case = verification.case
        val partyName = case.party.fullName()
        val infoName = "${info.firstName} ${info.lastName}"
        val today = Date(clock.millis())
        val score = 100.0 * KycUtils.verifyName(partyName, infoName)
        var status = KycStatus.REJECTED
        var errorCode: String? = null
        var errorMessage: String? = null

        if (!info.valid) {
            errorCode = KycErrorCode.INVALID
            errorMessage = info.invalidityReason
        } else if (info.type != case.identificationType) {
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

        return verificationDao.save(
            verification.copy(
                status = status,
                score = score.toInt(),
                errorCode = errorCode,
                errorMessage = errorMessage,
                modifiedAt = Date(clock.millis())
            )
        )
    }
}
