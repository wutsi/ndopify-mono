package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity

interface KycCaseVerifier {
    companion object {
        const val LOW_SCORE_THRESHOLD = 90.0
    }

    fun verify(case: KycCaseEntity): List<KycVerificationEntity>
}
