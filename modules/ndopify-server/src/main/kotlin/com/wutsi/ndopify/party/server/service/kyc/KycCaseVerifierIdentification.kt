package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier.Companion.LOW_SCORE_THRESHOLD
import com.wutsi.ndopify.party.server.service.KycInfoExtractorProvider
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.util.KycUtils
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class KycCaseVerifierIdentification(
    clock: Clock,
    storageProvider: StorageServiceProvider,
    infoExtractorProvider: KycInfoExtractorProvider,
    logger: KVLogger,
) : AbstractImageBasedKycCaseVerifier(clock, storageProvider, infoExtractorProvider, logger) {
    override fun getVerificationType(): KycVerificationType = KycVerificationType.IDENTIFICATION

    override fun getImageTypes(): List<KycImageType> = listOf(KycImageType.FRONT, KycImageType.BACK)

    override fun match(
        verification: KycVerificationEntity,
        info: KycInfo,
    ): KycVerificationEntity {
        val case = verification.case
        val partyName = case.party.fullName()
        val infoName = "${info.firstName} ${info.lastName}"
        val today = Date(clock.millis())
        val score = 100.0 * KycUtils.verifyName(infoName, partyName)
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

        return verification.copy(
            status = status,
            score = score.toInt(),
            errorCode = errorCode,
            errorMessage = errorMessage,
            modifiedAt = today
        )
    }
}
