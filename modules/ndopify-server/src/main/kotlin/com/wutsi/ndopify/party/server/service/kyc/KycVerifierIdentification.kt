package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.platform.momo.MoMoGateway
import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class KycVerifierMoMo(
    dao: KycVerificationRepository,
    clock: Clock,
    private val gatewayProvider: MoMoGatewayProvider,
) : AbstractKycVerifier(dao, clock) {
    @Transactional
    override fun verify(verification: KycVerificationEntity): KycVerificationEntity {
        val paymentMethod = verification.case.paymentMethod
        if (paymentMethod?.type != PaymentMethodType.MOBILE_MONEY) {
            return verification
        }

        startReview(verification)
        return doReview(verification, paymentMethod)
    }

    private fun doReview(
        verification: KycVerificationEntity,
        paymentMethod: PaymentMethodEntity
    ): KycVerificationEntity {
        val gateway = gatewayProvider.getByPhoneNumber(paymentMethod.number)
        return if (gateway == null) {
            manualReview(verification)
        } else {
            verify(verification, paymentMethod, gateway)
        }
    }

    private fun verify(
        verification: KycVerificationEntity,
        paymentMethod: PaymentMethodEntity,
        gateway: MoMoGateway
    ): KycVerificationEntity {
        val party = verification.case.party
        val match = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = paymentMethod.number,
                holderName = "${party.firstName} ${party.lastName}",
                countryCode = ""
            )
        )

        val score = match.holderNameScore * 100.0
        var status: KycStatus?
        var errorCode: String?
        if (!match.active) {
            status = KycStatus.REJECTED
            errorCode = KycErrorCode.INACTIVE
        } else {
            status = KycStatus.VERIFIED
            errorCode = null
        }
        val copy = verification.copy(
            score = score.toInt(),
            status = status,
            errorCode = errorCode,
            modifiedAt = Date(clock.millis())
        )
        dao.save(copy)
        return copy
    }
}
