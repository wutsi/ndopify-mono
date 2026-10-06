package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.service.kyc.KycVerifierIdentification
import com.wutsi.ndopify.party.server.service.kyc.KycVerifierManual
import com.wutsi.ndopify.party.server.service.kyc.KycVerifierMoMo
import org.springframework.stereotype.Service

@Service
class KycVerifierProvider(
    private val momo: KycVerifierMoMo,
    private val id: KycVerifierIdentification,
    private val manual: KycVerifierManual,
) {
    fun get(verification: KycVerificationEntity): KycVerifier {
        return when (verification.type) {
            KycVerificationType.IDENTIFICATION -> id
            KycVerificationType.PAYMENT_METHOD -> when (verification.case.paymentMethod?.type) {
                PaymentMethodType.MOBILE_MONEY -> momo
                else -> manual
            }

            else -> manual
        }
    }
}
