package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.dto.SearchMobileChangeRequest
import com.wutsi.ndopify.agent.dto.UpdateMobileChangeRequest
import com.wutsi.ndopify.agent.server.dao.MobileChangeRepository
import com.wutsi.ndopify.agent.server.domain.MobileChangeEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.platform.momo.MoMoGatewayFactory
import com.wutsi.ndopify.platform.momo.model.MoMoKycMatchRequest
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.AccessTokenService
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Clock
import java.util.Date

@Service
class MobileChangeService(
    private val dao: MobileChangeRepository,
    private val tenantService: TenantService,
    private val agentService: AgentService,
    private val momoGatewayFactory: MoMoGatewayFactory,
    private val accessTokenService: AccessTokenService,
    private val clock: Clock,
) {
    companion object {
        const val MAX_RETRIES = 3
    }

    fun findById(id: Long, tenantId: Long): MobileChangeEntity {
        return findByIdOrNull(id, tenantId)
            ?: throw NotFoundException(Error(code = ErrorCode.MOBILE_CHANGE_REQUEST_NOT_FOUND))
    }

    fun findByIdOrNull(id: Long, tenantId: Long): MobileChangeEntity? {
        return dao.findById(id).orElse(null)?.takeIf { it.tenantId == tenantId }
    }

    fun search(request: SearchMobileChangeRequest, tenantId: Long?): List<MobileChangeEntity> {
        val spec = Specification<MobileChangeEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (tenantId != null) {
                predicates.add(cb.equal(root.get<Long>("tenantId"), tenantId))
            }

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            request.agentId?.let { agentId ->
                predicates.add(cb.equal(root.get<Any>("agent").get<Long>("id"), agentId))
            }
            request.status?.let { status ->
                predicates.add(cb.equal(root.get<Any>("status"), status))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    @Transactional
    fun update(id: Long, request: UpdateMobileChangeRequest, tenantId: Long): MobileChangeEntity {
        // START
        val change = findById(id, tenantId)
        if (change.status != KycStatus.REQUIRES_MANUAL_REVIEW) {
            throw ConflictException(
                error = Error(ErrorCode.MOBILE_CHANGE_REQUEST_NOT_FOR_MANUAL_REVIEW)
            )
        }

        // UPDATE
        change.holderName = request.holderName
        change.status = request.status
        change.errorCode = request.errorCode
        change.failureReason = request.failureReason
        change.verifiedAt = Date(clock.millis())
        change.verifyByUserId = accessTokenService.getPrincipal().getUserId()
        dao.save(change)

        // APPLY THE CHANGE
        if (change.status == KycStatus.VERIFIED) {
            agentService.apply(change)
        }

        return change
    }

    @Transactional
    fun verify(id: Long, tenantId: Long): MobileChangeEntity {
        // START
        val change = findById(id, tenantId)
        return verify(change)
    }

    @Transactional
    fun verify(change: MobileChangeEntity): MobileChangeEntity {
        // START
        if (change.status != KycStatus.PENDING) {
            throw ConflictException(
                error = Error(ErrorCode.MOBILE_CHANGE_REQUEST_ALREADY_PROCEEDED)
            )
        }
        change.status = KycStatus.IN_PROGRESS
        change.verifiedAt = Date(clock.millis())
        change.verifyByUserId = null // Automatic review - no user involved
        change.retries = change.retries?.let { it + 1 } ?: 0
        dao.save(change)

        // VERIFY
        val agent = change.agent
        try {
            val gateway = momoGatewayFactory.get(change.newGateway)
            if (gateway == null) {
                change.status = KycStatus.REQUIRES_MANUAL_REVIEW
                change.errorCode = KycErrorCode.AUTO_REVIEW_NOT_SUPPORTED
            } else {
                val tenant = tenantService.findById(change.tenantId)
                val result = gateway.kycMatch(
                    MoMoKycMatchRequest(
                        phoneNumber = agent.mobileMoneyNumber ?: "",
                        holderName = agent.firstName + " " + agent.lastName,
                        countryCode = tenant.countryCode,
                    )
                )

                change.holderName = result.holderName
                if (!result.active) {
                    change.status = KycStatus.REJECTED
                    change.errorCode = KycErrorCode.ACCOUNT_NOT_ACTIVE
                } else if (result.countryCodeScore < 1.0) {
                    change.status = KycStatus.REJECTED
                    change.errorCode = KycErrorCode.COUNTRY_NOT_VALID
                } else {
                    when {
                        result.holderNameScore >= 0.9 -> {
                            change.status = KycStatus.VERIFIED
                            change.errorCode = null
                            change.failureReason = null
                        }

                        result.holderNameScore >= 0.75 -> {
                            change.status = KycStatus.REQUIRES_MANUAL_REVIEW
                            change.errorCode = KycErrorCode.NAME_MISMATCH
                        }

                        else -> {
                            change.status = KycStatus.REJECTED
                            change.errorCode = KycErrorCode.NAME_MISMATCH
                        }
                    }
                }
            }
        } catch (ex: IOException) {
            change.status = if (change.retries > MAX_RETRIES) KycStatus.REQUIRES_MANUAL_REVIEW else KycStatus.PENDING
            change.errorCode = KycErrorCode.GATEWAY_ERROR
            change.failureReason = ex.message
        } finally {
            dao.save(change)
        }

        // APPLY THE CHANGE
        if (change.status == KycStatus.VERIFIED) {
            agentService.apply(change)
        }

        return change
    }
}
