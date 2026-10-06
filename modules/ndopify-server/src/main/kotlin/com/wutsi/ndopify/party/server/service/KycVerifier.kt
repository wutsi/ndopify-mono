package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.domain.KycVerificationEntity

interface KycVerifier {
    companion object {
        const val LOW_SCORE_THRESHOLD = 90.0
    }

    fun verify(verification: KycVerificationEntity): KycVerificationEntity
}
