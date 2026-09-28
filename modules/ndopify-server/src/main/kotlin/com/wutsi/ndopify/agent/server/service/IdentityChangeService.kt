package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.dto.SearchIdentityChangeRequest
import com.wutsi.ndopify.agent.dto.UpdateIdentityChangeRequest
import com.wutsi.ndopify.agent.server.dao.IdentityChangeRepository
import com.wutsi.ndopify.agent.server.domain.IdentityChangeEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.platform.identity.IdentityKycServiceProvider
import com.wutsi.ndopify.platform.identity.model.IdKycMatchRequest
import com.wutsi.ndopify.refdata.dto.IdentityStatus
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import com.wutsi.ndopify.refdata.dto.KycStatus
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.util.UrlUtils
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.net.URL
import java.time.Clock
import java.util.Date

@Service
class IdentityChangeService(
    private val dao: IdentityChangeRepository,
    private val tenantService: TenantService,
    private val agentService: AgentService,
    private val identityKycProvider: IdentityKycServiceProvider,
    private val accessTokenService: AccessTokenService,
    private val clock: Clock,
) {
    companion object {
        const val MAX_RETRIES = 3
    }

    fun findById(id: Long, tenantId: Long): IdentityChangeEntity {
        return findByIdOrNull(id, tenantId)
            ?: throw NotFoundException(Error(code = ErrorCode.IDENTITY_CHANGE_NOT_FOUND))
    }

    fun findByIdOrNull(id: Long, tenantId: Long): IdentityChangeEntity? {
        return dao.findById(id).orElse(null)?.takeIf { it.tenantId == tenantId }
    }

    fun search(request: SearchIdentityChangeRequest, tenantId: Long?): List<IdentityChangeEntity> {
        val spec = Specification<IdentityChangeEntity> { root, _, cb ->
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
    fun update(id: Long, request: UpdateIdentityChangeRequest, tenantId: Long): IdentityChangeEntity {
        // START
        val change = findById(id, tenantId)
        if (change.status != KycStatus.REQUIRES_MANUAL_REVIEW) {
            throw ConflictException(
                error = Error(ErrorCode.IDENTITY_CHANGE_NOT_FOR_MANUAL_REVIEW)
            )
        }

        // UPDATE
        dao.save(
            change.copy(
                status = request.status,
                errorCode = request.errorCode,
                failureReason = request.failureReason,
                holderName = request.holderName,
                verifiedAt = Date(clock.millis()),
                verifyByUserId = accessTokenService.getPrincipal().getUserId(),
            )
        )

        // APPLY THE CHANGE
        agentService.apply(change)

        return change
    }

    @Transactional
    fun verify(id: Long, tenantId: Long): IdentityChangeEntity {
        // START
        val change = findById(id, tenantId)
        return verify(change)
    }

    @Transactional
    fun verify(change: IdentityChangeEntity): IdentityChangeEntity {
        if (change.status != KycStatus.PENDING) {
            throw ConflictException(
                error = Error(ErrorCode.IDENTITY_CHANGE_ALREADY_PROCEEDED)
            )
        }

        // CANCEL
        val currentIdentityChangeId = change.agent.identityChange?.id
        if (currentIdentityChangeId != null && currentIdentityChangeId != change.id) {
            dao.save(
                change.copy(
                    status = KycStatus.CANCELLED
                )
            )
            return change
        }

        // START
        val retries = change.retries?.let { it + 1 } ?: 0
        dao.save(
            change.copy(
                status = KycStatus.IN_PROGRESS,
                verifiedAt = Date(clock.millis()),
                verifyByUserId = null, // Automatic review - no user involved
                retries = retries
            )
        )

        // VERIFY
        val agent = change.agent
        var status: KycStatus? = null
        var errorCode: String? = null
        try {
            val kyc = identityKycProvider.get(change.identityType)
            if (kyc == null) {
                return dao.save(
                    change.copy(
                        status = KycStatus.REQUIRES_MANUAL_REVIEW,
                        errorCode = KycErrorCode.AUTO_REVIEW_NOT_SUPPORTED
                    )
                )
            } else {
                val tenant = tenantService.findById(change.tenantId)
                val result = kyc.kycMatch(
                    IdKycMatchRequest(
                        holderName = agent.firstName + " " + agent.lastName,
                        countryCode = tenant.countryCode,
                        images = change.imageUrls.map { url -> UrlUtils.download(URL(url)) },
                        identityType = change.identityType,
                    )
                )

                if (result.status != IdentityStatus.VALID) {
                    status = KycStatus.REJECTED
                    errorCode = when (result.status) {
                        IdentityStatus.EXPIRED -> KycErrorCode.EXPIRED
                        IdentityStatus.SUSPENDED -> KycErrorCode.SUSPENDED
                        IdentityStatus.INVALID -> KycErrorCode.INVALID
                        else -> null
                    }
                } else if (result.countryCodeScore < 1.0) {
                    status = KycStatus.REJECTED
                    errorCode = KycErrorCode.COUNTRY_NOT_VALID
                } else if (result.documentTypeScore < 1.0) {
                    status = KycStatus.REJECTED
                    errorCode = KycErrorCode.INVALID
                } else {
                    when {
                        result.holderNameScore >= 0.9 -> {
                            status = KycStatus.VERIFIED
                        }

                        result.holderNameScore >= 0.75 -> {
                            status = KycStatus.REQUIRES_MANUAL_REVIEW
                            errorCode = KycErrorCode.NAME_MISMATCH
                        }

                        else -> {
                            status = KycStatus.REJECTED
                            errorCode = KycErrorCode.NAME_MISMATCH
                        }
                    }
                }
                dao.save(
                    change.copy(
                        status = status,
                        errorCode = errorCode,
                        failureReason = null,
                        holderName = result.holderName,
                        holderNameScore = result.holderNameScore,
                        countryCodeScore = result.countryCodeScore,
                        documentTypeScore = result.documentTypeScore,
                    )
                )

                // APPLY THE CHANGE
                agentService.apply(change)
            }
        } catch (ex: Exception) {
            val status = if (retries > MAX_RETRIES) KycStatus.REQUIRES_MANUAL_REVIEW else KycStatus.PENDING
            dao.save(
                change.copy(
                    status = status,
                    errorCode = KycErrorCode.GATEWAY_ERROR,
                    failureReason = ex.message
                )
            )
        }

        return change
    }
}
