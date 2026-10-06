package com.wutsi.ndopify.party.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.KycVerificationType
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.server.dao.KycCaseRepository
import com.wutsi.ndopify.party.server.dao.KycVerificationRepository
import com.wutsi.ndopify.party.server.dao.PartyRepository
import com.wutsi.ndopify.party.server.domain.KycCaseEntity
import com.wutsi.ndopify.party.server.domain.KycVerificationEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class KycService(
    private val dao: KycCaseRepository,
    private val partyDao: PartyRepository,
    private val daoVerification: KycVerificationRepository,
    private val identificationService: IdentificationService,
    private val paymentMethodService: PaymentMethodService,
    private val verifierProvider: KycVerifierProvider,
    private val clock: Clock,
    private val logger: KVLogger,
) {
    fun findById(id: String): KycCaseEntity {
        return dao.findById(id).orElseThrow {
            NotFoundException(
                error = Error(
                    code = ErrorCode.KYC_CASE_NOT_FOUND,
                    parameter = Parameter(value = id)
                )
            )
        }
    }

    fun search(request: SearchKycCaseRequest): List<KycCaseEntity> {
        val spec = Specification<KycCaseEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            request.partyId?.let { partyId ->
                predicates.add(cb.equal(root.get<PartyEntity>("party").get<Long>("id"), partyId))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<KycStatus>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    @Transactional
    fun create(request: CreateKycCaseRequest): KycCaseEntity {
        val identification = identificationService.findById(request.identificationId)
        val paymentMethod = request.paymentMethodId?.let { id -> paymentMethodService.findById(id) }
        ensureSameParty(identification.party.id, paymentMethod)
        val now = Date(clock.millis())

        // Case
        val case = dao.save(
            KycCaseEntity(
                party = identification.party,
                identification = identification,
                paymentMethod = paymentMethod,
                status = KycStatus.PENDING,
                createdAt = now,
                modifiedAt = now,
            )
        )

        // Verifications
        daoVerification.save(
            KycVerificationEntity(
                case = case,
                type = KycVerificationType.IDENTIFICATION,
                status = KycStatus.PENDING,
                createdAt = now,
                modifiedAt = now,
            )
        )

        if (paymentMethod != null) {
            daoVerification.save(
                KycVerificationEntity(
                    case = case,
                    type = KycVerificationType.PAYMENT_METHOD,
                    status = KycStatus.PENDING,
                    createdAt = now,
                    modifiedAt = now,
                )
            )
        }

        return case
    }

    @Transactional
    fun verify(id: String): KycCaseEntity {
        // Start
        val case = findById(id)
        ensureStatusIsPending(case)
        startVerification(case)

        // Verifications
        case.verifications.forEach { verification ->
            val logPrefix = "verifier_${verification.type.name.lowercase()}"
            val verifier = verifierProvider.get(verification)
            verifier.verify(verification)

            logger.add("${logPrefix}_status", verification.status)
            logger.add("${logPrefix}_score", verification.score)
            logger.add("${logPrefix}_error_code", verification.errorCode)
            logger.add("${logPrefix}_error_message", verification.errorMessage)
        }

        // For the overall status and score, consider ONLY the identification verification.
        val idVerification = case.verifications.find { it.type == KycVerificationType.IDENTIFICATION }!!
        val status = idVerification.status
        val score = idVerification.score
        logger.add("kyc_case_status", status)
        logger.add("kyc_case_score", score)
        val copy = dao.save(
            case.copy(
                status = status,
                score = score,
                modifiedAt = Date(clock.millis()),
            )
        )

        // Update party KYC status
        partyDao.save(
            copy.party.copy(
                kycStatus = copy.status,
                modifiedAt = Date(clock.millis()),
            )
        )

        return copy
    }

    private fun startVerification(case: KycCaseEntity) {
        dao.save(
            case.copy(
                status = KycStatus.IN_PROGRESS,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    private fun ensureStatusIsPending(case: KycCaseEntity) {
        if (case.status != KycStatus.PENDING) {
            throw BadRequestException(
                error = Error(code = ErrorCode.KYC_CASE_NOT_PENDING)
            )
        }
    }

    private fun ensureSameParty(partyId: Long?, paymentMethod: PaymentMethodEntity?) {
        if (paymentMethod != null && paymentMethod.party.id != partyId) {
            throw BadRequestException(
                error = Error(code = ErrorCode.KYC_CASE_PARTY_MISMATCH)
            )
        }
    }
}
