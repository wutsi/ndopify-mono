package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class KycVerifierManual(
    dao: KycVerificationRepository,
    clock: Clock,
) : AbstractKycVerifier(dao, clock) {
    @Transactional
    override fun verify(verification: KycVerificationEntity): KycVerificationEntity {
        return manualReview(verification)
    }
}
