package com.wutsi.ndopify.party.server.mapper

import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycCaseSummary
import com.wutsi.ndopify.party.dto.KycVerification
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import org.springframework.stereotype.Service

@Service
class KycCaseMapper {
    fun toKycCase(entity: KycCaseEntity): KycCase {
        return KycCase(
            id = entity.id,
            partyId = entity.party.id?.toString() ?: "",
            identificationId = entity.identification.id,
            paymentMethodId = entity.paymentMethod?.id,
            score = entity.score,
            status = entity.status,
            errorCode = entity.errorCode,
            verifications = entity.verifications.map { verification -> toKycVerification(verification) },
            createdAt = entity.createdAt,
            updatedAt = entity.modifiedAt,
        )
    }

    fun toKycCaseSummary(entity: KycCaseEntity): KycCaseSummary {
        return KycCaseSummary(
            id = entity.id,
            partyId = entity.party.id?.toString() ?: "",
            identificationId = entity.identification.id,
            paymentMethodId = entity.paymentMethod?.id,
            score = entity.score,
            status = entity.status,
            errorCode = entity.errorCode,
            createdAt = entity.createdAt,
            updatedAt = entity.modifiedAt,
        )
    }

    fun toKycVerification(entity: KycVerificationEntity): KycVerification {
        return KycVerification(
            id = entity.id,
            kycCaseId = entity.case.id,
            type = entity.type,
            status = entity.status,
            score = entity.score,
            errorCode = entity.errorCode,
            errorMessage = entity.errorMessage,
            createdAt = entity.createdAt,
            updatedAt = entity.modifiedAt,
        )
    }
}
