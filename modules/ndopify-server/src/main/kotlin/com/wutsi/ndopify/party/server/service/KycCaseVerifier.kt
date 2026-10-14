package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity

interface KycCaseVerifier {
    companion object {
        const val LOW_SCORE_THRESHOLD = 90.0
    }

    /**
     * Runs the checks of [case] and returns their results **unsaved**: [KycService] saves them. Never writes to the
     * DB: it runs outside any transaction, since it does I/O (storage, LLM, MoMo).
     */
    fun verify(case: KycCaseEntity): List<KycVerificationEntity>
}
