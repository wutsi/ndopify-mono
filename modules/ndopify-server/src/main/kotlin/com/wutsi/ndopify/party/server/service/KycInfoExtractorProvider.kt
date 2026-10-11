package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorNull
import com.wutsi.ndopify.party.server.service.kyc.KycInfoExtractorSelfie
import org.springframework.stereotype.Service

@Service
class KycInfoExtractorProvider(
    private val ai: KycInfoExtractorIdentification,
    private val selfie: KycInfoExtractorSelfie,
    private val none: KycInfoExtractorNull,
) {
    fun get(verificationType: KycVerificationType): KycInfoExtractor {
        when (verificationType) {
            KycVerificationType.IDENTIFICATION -> return ai
            KycVerificationType.SELFIE -> return selfie
            else -> return none
        }
    }
}
