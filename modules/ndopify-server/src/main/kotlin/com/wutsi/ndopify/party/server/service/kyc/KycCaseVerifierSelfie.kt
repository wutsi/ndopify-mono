package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier.Companion.LOW_SCORE_THRESHOLD
import com.wutsi.ndopify.party.server.service.KycInfoExtractorProvider
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class KycCaseVerifierSelfie(
    clock: Clock,
    storageProvider: StorageServiceProvider,
    infoExtractorProvider: KycInfoExtractorProvider,
    logger: KVLogger,
) : AbstractImageBasedKycCaseVerifier(clock, storageProvider, infoExtractorProvider, logger) {
    override fun getVerificationType(): KycVerificationType = KycVerificationType.SELFIE

    override fun getImageTypes(): List<KycImageType> = listOf(KycImageType.FRONT, KycImageType.SELFIE)

    override fun match(
        verification: KycVerificationEntity,
        info: KycInfo,
    ): KycVerificationEntity {
        val today = Date(clock.millis())
        val score = 100.0 * info.confidence
        var status = KycStatus.REJECTED
        var errorCode: String? = null
        var errorMessage: String? = null

        if (!info.valid) {
            errorCode = KycErrorCode.INVALID
            errorMessage = info.invalidityReason
        } else {
            if (score < LOW_SCORE_THRESHOLD) {
                errorCode = KycErrorCode.LOW_SCORE
            } else {
                status = KycStatus.VERIFIED
            }
        }

        return verification.copy(
            status = status,
            score = score.toInt(),
            errorCode = errorCode,
            errorMessage = errorMessage,
            modifiedAt = today
        )
    }
}
