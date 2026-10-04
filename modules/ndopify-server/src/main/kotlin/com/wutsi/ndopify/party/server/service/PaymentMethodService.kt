package com.wutsi.ndopify.party.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dao.PaymentMethodRepository
import com.wutsi.ndopify.party.domain.PartyEntity
import com.wutsi.ndopify.party.domain.PaymentMethodEntity
import com.wutsi.ndopify.party.dto.CreatePaymentMethodRequest
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import com.wutsi.ndopify.refdata.dto.PaymentMethodStatus
import jakarta.transaction.Transactional
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class PaymentMethodService(
    private val dao: PaymentMethodRepository,
    private val momoGatewayProvider: MoMoGatewayProvider,
    private val clock: Clock,
) {
    companion object {
        val EOL_STATUSES = listOf(
            PaymentMethodStatus.DELETED,
            PaymentMethodStatus.REJECTED,
            PaymentMethodStatus.EXPIRED,
        )
    }

    fun findById(id: Long): PaymentMethodEntity {
        return dao.findById(id).orElseThrow {
            NotFoundException(
                error = Error(
                    code = ErrorCode.PAYMENT_METHOD_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
        }
    }

    fun findByTypeAndNumber(type: PaymentMethodType, number: String): PaymentMethodEntity {
        val hash = computeHash(type, number)
        return dao.findByHash(hash)
            ?: throw NotFoundException(
                error = Error(
                    code = ErrorCode.PAYMENT_METHOD_NOT_FOUND,
                )
            )
    }

    fun findByIdOrNull(id: Long): PaymentMethodEntity? {
        return dao.findById(id).orElse(null)
    }

    @Transactional
    fun create(party: PartyEntity, request: CreatePaymentMethodRequest): PaymentMethodEntity {
        val hash = computeHash(request.type, request.number)
        ensureHashUnique(hash)
        ensureMobileMoneyIsValid(request)

        val now = Date(clock.millis())
        return dao.save(
            PaymentMethodEntity(
                party = party,
                hash = hash,
                number = request.number,
                providerName = request.providerName,
                holderName = request.holderName,
                type = request.type,
                expiresAt = request.expiresAt,
                status = PaymentMethodStatus.PENDING_VERIFICATION,
                createdAt = now,
                modifiedAt = now,
            )
        )
    }

    @Transactional
    fun updateStatus(paymentMethod: PaymentMethodEntity, status: PaymentMethodStatus): PaymentMethodEntity {
        ensureNotEol(paymentMethod)

        val now = Date(clock.millis())
        return dao.save(
            paymentMethod.copy(
                status = status,
                modifiedAt = now,
            )
        )
    }

    private fun computeHash(type: PaymentMethodType, number: String): String {
        return DigestUtils.md5Hex("$type:${number.lowercase()}")
    }

    private fun ensureHashUnique(hash: String) {
        if (dao.findByHash(hash) != null) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.PAYMENT_METHOD_ALREADY_EXISTS,
                )
            )
        }
    }

    private fun ensureNotEol(paymentMethod: PaymentMethodEntity) {
        if (EOL_STATUSES.contains(paymentMethod.status)) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.PAYMENT_METHOD_EOL,
                    parameter = Parameter(value = paymentMethod.status.name)
                )
            )
        }
    }

    private fun ensureMobileMoneyIsValid(request: CreatePaymentMethodRequest) {
        if (request.type == PaymentMethodType.MOBILE_MONEY) {
            momoGatewayProvider.getByPhoneNumber(request.number)
                ?: throw ConflictException(error = Error(code = ErrorCode.AGENT_MOBILE_MONEY_NUMBER_INVALID))
        }
    }
}
