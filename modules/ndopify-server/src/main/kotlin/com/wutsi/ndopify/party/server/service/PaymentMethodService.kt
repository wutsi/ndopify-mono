package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreatePaymentMethodRequest
import com.wutsi.ndopify.party.dto.PaymentMethodStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.dto.SearchPaymentMethodRequest
import com.wutsi.ndopify.party.server.dao.PaymentMethodRepository
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.platform.momo.MoMoGatewayProvider
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
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

    fun findById(id: String): PaymentMethodEntity {
        return dao.findById(id).orElseThrow {
            NotFoundException(
                error = Error(
                    code = ErrorCode.PAYMENT_METHOD_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
        }
    }

    fun findByIdOrNull(id: String): PaymentMethodEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findByParty(party: PartyEntity): List<PaymentMethodEntity> {
        return dao.findByParty(party)
    }

    fun search(request: SearchPaymentMethodRequest): List<PaymentMethodEntity> {
        val spec = Specification<PaymentMethodEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.partyId?.let { partyId ->
                predicates.add(cb.equal(root.get<PartyEntity>("party").get<Long>("id"), partyId))
            }
            if (request.types.isNotEmpty()) {
                predicates.add(root.get<PaymentMethodType>("type").`in`(request.types))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<PaymentMethodStatus>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    @Transactional
    fun create(party: PartyEntity, request: CreatePaymentMethodRequest): PaymentMethodEntity {
        ensureMobileMoneyIsValid(request)
        ensurePaymentMethodNotExists(party, request)

        val now = Date(clock.millis())
        return dao.save(
            PaymentMethodEntity(
                party = party,
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
                ?: throw BadRequestException(error = Error(code = ErrorCode.PAYMENT_METHOD_NUMBER_NOT_VALID))
        }
    }

    private fun ensurePaymentMethodNotExists(party: PartyEntity, request: CreatePaymentMethodRequest) {
        val existing = dao.findByPartyAndTypeAndNumber(party, request.type, request.number)
        if (existing != null) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.PAYMENT_METHOD_ALREADY_EXISTS,
                )
            )
        }
    }
}
