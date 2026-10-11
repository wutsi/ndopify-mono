package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierMoMo
import com.wutsi.ndopify.party.server.service.kyc.KycCaseVerifierSelfie
import org.springframework.stereotype.Service

@Service
class KycCaseVerifierProvider(
    private val id: KycCaseVerifierIdentification,
    private val selfie: KycCaseVerifierSelfie,
    private val momo: KycCaseVerifierMoMo,
) {
    fun get(verificationType: KycVerificationType): KycCaseVerifier? {
        return when (verificationType) {
            KycVerificationType.IDENTIFICATION -> id
            KycVerificationType.PAYMENT_METHOD -> momo
            KycVerificationType.SELFIE -> selfie
            else -> null
        }
    }
}
