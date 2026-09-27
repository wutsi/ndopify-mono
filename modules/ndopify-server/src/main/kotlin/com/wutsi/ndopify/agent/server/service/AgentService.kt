package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.dto.AgentStatus
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.dto.UpdateIdentyRequest
import com.wutsi.ndopify.agent.dto.UpdateImageRequest
import com.wutsi.ndopify.agent.dto.UpdateMobileMoneyRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.agent.server.dao.IdentityChangeRepository
import com.wutsi.ndopify.agent.server.dao.MobileChangeRepository
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.domain.IdentityChangeEntity
import com.wutsi.ndopify.agent.server.domain.MobileChangeEntity
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.refdata.dto.KycStatus
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class AgentService(
    private val dao: AgentRepository,
    private val mobileChangeDao: MobileChangeRepository,
    private val identityChangeDao: IdentityChangeRepository,
    private val clock: Clock,
) {
    @Transactional
    fun create(request: CreateAgentRequest, tenantId: Long): AgentEntity {
        val identityKycStatus = KycStatus.UNKNOWN
        val mobileMoneyKycStatus = KycStatus.UNKNOWN

        return dao.save(
            AgentEntity(
                tenantId = tenantId,
                userId = request.userId,
                agentType = request.agentType,
                firstName = request.firstName,
                lastName = request.lastName,
                biography = request.biography,
                agencyName = request.agencyName,
                cityId = request.cityId,
                neighborhoodIds = request.neighborhoodIds,
                identityKycStatus = identityKycStatus,
                mobileMoneyKycStatus = mobileMoneyKycStatus,
                status = computeStatus(identityKycStatus, mobileMoneyKycStatus),
            )
        )
    }

    @Transactional
    fun update(id: Long, request: UpdateAgentRequest, tenantId: Long): AgentEntity {
        val agent = findById(id, tenantId)

        return dao.save(
            agent.copy(
                agentType = request.agentType,
                biography = request.biography,
                agencyName = request.agencyName,
                cityId = request.cityId,
                neighborhoodIds = request.neighborhoodIds,
                modifiedAt = Date(),
            )
        )
    }

    @Transactional
    fun updatePhoto(id: Long, request: UpdateImageRequest, tenantId: Long): AgentEntity {
        val agent = findById(id, tenantId)

        return dao.save(
            agent.copy(
                photoUrl = request.url,
                modifiedAt = Date(),
            )
        )
    }

    @Transactional
    fun updateAgencyLogo(id: Long, request: UpdateImageRequest, tenantId: Long): AgentEntity {
        val agent = findById(id, tenantId)

        return dao.save(
            agent.copy(
                agencyLogoUrl = request.url,
                modifiedAt = Date(),
            )
        )
    }

    @Transactional
    fun updateMobileMoney(id: Long, request: UpdateMobileMoneyRequest, tenantId: Long): AgentEntity {
        val agent = findById(id, tenantId)

        ensureMobileMoneyNumberIsAvailable(request.mobileNumber, tenantId, agentId = id)

        // Set phone number and gateway if not already set. If already set, we will create a change request for review.
        val updatedAgent = if (agent.mobileMoneyNumber.isNullOrEmpty()) {
            val mobileMoneyKycStatus = KycStatus.PENDING
            dao.save(
                agent.copy(
                    mobileMoneyNumber = request.mobileNumber,
                    mobileMoneyGateway = request.gateway,
                    mobileMoneyKycStatus = mobileMoneyKycStatus,
                    status = computeStatus(agent.identityKycStatus, mobileMoneyKycStatus),
                    modifiedAt = Date(),
                )
            )
        } else {
            agent
        }

        // Store the change request for review
        val mobileChange = mobileChangeDao.save(
            MobileChangeEntity(
                agent = updatedAgent,
                tenantId = tenantId,
                oldMobileNumber = agent.mobileMoneyNumber,
                newMobileNumber = request.mobileNumber,
                newGateway = request.gateway,
                status = KycStatus.PENDING
            )
        )

        // Link the change to the agent
        dao.save(updatedAgent.copy(mobileChange = mobileChange))
        return updatedAgent
    }

    @Transactional
    fun updateIdentity(id: Long, request: UpdateIdentyRequest, tenantId: Long): AgentEntity {
        val agent = findById(id, tenantId)

        val updatedAgent = if (agent.firstName.isEmpty() && agent.lastName.isEmpty()) {
            val identityKycStatus = KycStatus.PENDING
            dao.save(
                agent.copy(
                    firstName = request.firstName,
                    lastName = request.lastName,
                    identityKycStatus = identityKycStatus,
                    status = computeStatus(identityKycStatus, agent.mobileMoneyKycStatus),
                    modifiedAt = Date(),
                )
            )
        } else {
            agent
        }

        identityChangeDao.save(
            IdentityChangeEntity(
                agent = updatedAgent,
                tenantId = tenantId,
                oldFirstName = agent.firstName,
                oldLastName = agent.lastName,
                newFirstName = request.firstName,
                newLastName = request.lastName,
                identityType = request.identityType,
                documentPage1Url = request.documentPage1Url,
                documentPage2Url = request.documentPage2Url,
                status = KycStatus.PENDING,
            )
        )

        return updatedAgent
    }

    @Transactional
    fun apply(request: MobileChangeEntity): Boolean {
        if (request.status != KycStatus.VERIFIED) {
            return false
        }
        if (request.newMobileNumber != request.oldMobileNumber) { // Extra-Precaution
            return false
        }

        val agent = request.agent
        val mobileMoneyKycStatus = KycStatus.VERIFIED
        dao.save(
            agent.copy(
                mobileMoneyKycStatus = mobileMoneyKycStatus,
                mobileMoneyNumber = request.newMobileNumber,
                mobileMoneyGateway = request.newGateway,
                mobileChange = null,
                modifiedAt = Date(clock.millis()),
                status = computeStatus(agent.identityKycStatus, mobileMoneyKycStatus),
            )
        )
        return true
    }

    private fun computeStatus(identityKycStatus: KycStatus, mobileMoneyKycStatus: KycStatus): AgentStatus =
        when {
            identityKycStatus == KycStatus.VERIFIED && mobileMoneyKycStatus == KycStatus.VERIFIED -> AgentStatus.ACTIVE
            identityKycStatus == KycStatus.VERIFIED -> AgentStatus.RESTRICTED
            else -> AgentStatus.LIMITED
        }

    private fun ensureMobileMoneyNumberIsAvailable(mobileNumber: String, tenantId: Long, agentId: Long) {
        val spec = Specification<AgentEntity> { root, _, cb ->
            cb.and(
                cb.equal(root.get<Long>("tenantId"), tenantId),
                cb.equal(root.get<String>("mobileMoneyNumber"), mobileNumber),
                cb.equal(root.get<Any>("mobileMoneyKycStatus"), KycStatus.VERIFIED),
                cb.notEqual(root.get<Long>("id"), agentId),
            )
        }

        if (dao.exists(spec)) {
            throw ConflictException(error = Error(code = ErrorCode.AGENT_MOBILE_MONEY_NUMBER_ALREADY_ASSIGNED))
        }
    }

    fun search(request: SearchAgentRequest, tenantId: Long): List<AgentEntity> {
        val spec = Specification<AgentEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            predicates.add(cb.equal(root.get<Long>("tenantId"), tenantId))

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            request.userId?.let { userId ->
                predicates.add(cb.equal(root.get<Long>("userId"), userId))
            }
            request.cityId?.let { cityId ->
                predicates.add(cb.equal(root.get<Long>("cityId"), cityId))
            }
            request.status?.let { status ->
                predicates.add(cb.equal(root.get<Any>("status"), status))
            }
            request.mobileMoneyNumber?.let { mobileMoneyNumber ->
                predicates.add(cb.equal(root.get<String>("mobileMoneyNumber"), mobileMoneyNumber))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, Sort.by("id"))
            .drop(request.offset)
            .take(request.limit)
    }

    fun findByIdOrNull(id: Long, tenantId: Long): AgentEntity? {
        return dao.findById(id).orElse(null)?.takeIf { it.tenantId == tenantId }
    }

    fun findById(id: Long, tenantId: Long): AgentEntity {
        return findByIdOrNull(id, tenantId)
            ?: throw NotFoundException(error = Error(code = ErrorCode.AGENT_NOT_FOUND))
    }
}
