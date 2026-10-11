package com.wutsi.ndopify.party.server.service.kyc

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.dao.PaymentMethodRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.party.server.service.KycCaseVerifier
import com.wutsi.ndopify.party.server.service.KycCaseVerifier.Companion.LOW_SCORE_THRESHOLD
import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date
import java.util.UUID

@Service
class KycCaseVerifierMoMo(
    private val verificationDao: KycVerificationRepository,
    private val paymentMethodDao: PaymentMethodRepository,
    private val clock: Clock,
    private val gatewayProvider: MoMoGatewayProvider,
) : KycCaseVerifier {
    @Transactional
    override fun verify(case: KycCaseEntity): List<KycVerificationEntity> {
        val paymentMethods = getPaymentMethods(case)
        if (paymentMethods.isEmpty()) {
            return emptyList()
        }

        return paymentMethods.map { method -> verify(method, case) }
    }

    private fun getPaymentMethods(case: KycCaseEntity): List<PaymentMethodEntity> {
        return paymentMethodDao.findByParty(case.party)
            .filter { method -> method.status == PaymentMethodStatus.PENDING_VERIFICATION }
            .filter { method -> method.type == PaymentMethodType.MOBILE_MONEY }
    }

    private fun verify(paymentMethod: PaymentMethodEntity, case: KycCaseEntity): KycVerificationEntity {
        val verification = start(paymentMethod, case)
        return match(paymentMethod, verification)
    }

    private fun start(paymentMethod: PaymentMethodEntity, case: KycCaseEntity): KycVerificationEntity {
        val now = Date(clock.millis())
        return verificationDao.save(
            KycVerificationEntity(
                id = UUID.randomUUID().toString(),
                case = case,
                type = KycVerificationType.PAYMENT_METHOD,
                status = KycStatus.IN_PROGRESS,
                paymentMethod = paymentMethod,
                createdAt = now,
                modifiedAt = now
            )
        )
    }

    private fun match(paymentMethod: PaymentMethodEntity, verification: KycVerificationEntity): KycVerificationEntity {
        val gateway = gatewayProvider.getByPhoneNumber(paymentMethod.number)
        if (gateway == null) {
            return verificationDao.save(
                verification.copy(
                    status = KycStatus.REQUIRES_MANUAL_REVIEW,
                    modifiedAt = Date(clock.millis())
                )
            )
        }

        val match = gateway.kycMatch(
            MoMoKycMatchRequest(
                phoneNumber = paymentMethod.number,
                holderName = paymentMethod.party.fullName(),
                countryCode = ""
            )
        )

        val score = match.holderNameScore * 100.0
        var status = KycStatus.REJECTED
        var errorCode: String? = null
        if (!match.active) {
            errorCode = KycErrorCode.INACTIVE
        } else {
            if (score < LOW_SCORE_THRESHOLD) {
                errorCode = KycErrorCode.LOW_SCORE
            } else {
                status = KycStatus.VERIFIED
            }
        }

        return verificationDao.save(
            verification.copy(
                score = score.toInt(),
                status = status,
                errorCode = errorCode,
                modifiedAt = Date(clock.millis())
            )
        )
    }
}
