package com.wutsi.ndopify.party.server.mapper

import com.wutsi.ndopify.party.dto.PaymentMethod
import com.wutsi.ndopify.party.dto.PaymentMethodSummary
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import org.springframework.stereotype.Service

@Service
class PaymentMethodMapper {
    fun toPaymentMethod(entity: PaymentMethodEntity): PaymentMethod {
        return PaymentMethod(
            id = entity.id,
            partyId = entity.party.id ?: -1,
            number = entity.number,
            providerName = entity.providerName,
            holderName = entity.holderName,
            methodType = entity.type,
            verificationStatus = entity.status,
            expiresAt = entity.expiresAt,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }

    fun toPaymentMethodSummary(entity: PaymentMethodEntity): PaymentMethodSummary {
        return PaymentMethodSummary(
            id = entity.id,
            partyId = entity.party.id ?: -1,
            number = entity.number,
            methodType = entity.type,
            verificationStatus = entity.status,
            expiresAt = entity.expiresAt,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }
}
