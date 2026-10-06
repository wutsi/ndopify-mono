package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.party.server.service.KycVerifier
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
    private val dao: KycVerificationRepository,
    private val provider: MoMoGatewayProvider,
    private val clock: Clock,
) : KycVerifier {
    @Transactional
    override fun verify(verification: KycVerificationEntity): Boolean {
        val paymentMethod = verification.case.paymentMethod
        if (paymentMethod?.type != PaymentMethodType.MOBILE_MONEY) {
            return false
        }

        startReview(verification)
        return doReview(verification, paymentMethod)
    }

    private fun startReview(verification: KycVerificationEntity) {
        dao.save(
            verification.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(clock.millis())
            )
        )
    }

    private fun doReview(verification: KycVerificationEntity, paymentMethod: PaymentMethodEntity): Boolean {
        val gateway = provider.getByPhoneNumber(paymentMethod.number)
        return if (gateway == null) {
            manualReview(verification)
        } else {
            verify(verification, paymentMethod, gateway)
        }

    }

    private fun manualReview(verification: KycVerificationEntity): Boolean {
        dao.save(
            verification.copy(
                status = KycStatus.REQUIRES_MANUAL_REVIEW,
                modifiedAt = Date(clock.millis())
            )
        )
        return true
    }

    private fun verify(
        verification: KycVerificationEntity,
        paymentMethod: PaymentMethodEntity,
        gateway: MoMoGateway
    ): Boolean {
        val party = verification.case.party
        val match = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = paymentMethod.number,
                holderName = "${party.firstName} ${party.lastName}",
                countryCode = ""
            )
        )

        val score = match.holderNameScore * 100.0
        val status = if (match.active) KycStatus.REJECTED else KycStatus.VERIFIED
        val errorCode = if (match.active) KycErrorCode.INACTIVE else null
        dao.save(
            verification.copy(
                score = score.toInt(),
                status = status,
                errorCode = errorCode,
                modifiedAt = Date(clock.millis())
            )
        )
        return true
    }
}
