package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.KycVerifier
import java.time.Clock
import java.util.Date

abstract class AbstractKycVerifier(
    protected val dao: KycVerificationRepository,
    protected val clock: Clock,
) : KycVerifier {
    protected fun startReview(verification: KycVerificationEntity): KycVerificationEntity {
        val copy = verification.copy(
            status = KycStatus.IN_PROGRESS,
            modifiedAt = Date(clock.millis())
        )
        dao.save(copy)
        return copy
    }

    protected fun manualReview(verification: KycVerificationEntity): KycVerificationEntity {
        val copy = verification.copy(
            status = KycStatus.REQUIRES_MANUAL_REVIEW,
            modifiedAt = Date(clock.millis())
        )
        dao.save(copy)
        return copy
    }
}
