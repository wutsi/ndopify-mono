package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.event.AgentCreatedEvent
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.party.dto.CreatePartyRequest
import com.wutsi.ndopify.party.dto.CreatePaymentMethodRequest
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.PaymentMethodType
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.dto.UpdatePartyRequest
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.party.server.service.PaymentMethodService
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserService
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class AgentService(
    private val dao: AgentRepository,
    private val clock: Clock,
    private val partyService: PartyService,
    private val kycService: KycService,
    private val paymentMethodService: PaymentMethodService,
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val mailet: WelcomeMailet,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun search(request: SearchAgentRequest): List<AgentEntity> {
        val spec = Specification<AgentEntity> { root, query, cb ->
            query.distinct(true)

            val predicates = mutableListOf<Predicate>()

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            if (request.partyIds.isNotEmpty()) {
                predicates.add(root.get<PartyEntity>("party").get<Long>("id").`in`(request.partyIds))
            }
            request.cityId?.let { cityId ->
                predicates.add(cb.equal(root.get<Long>("cityId"), cityId))
            }
            if (request.neighborhoodIds.isNotEmpty()) {
                predicates.add(root.join<AgentEntity, Long>("neighborhoodIds").`in`(request.neighborhoodIds))
            }
            if (request.kycStatus.isNotEmpty()) {
                predicates.add(root.get<PartyEntity>("party").get<KycStatus>("kycStatus").`in`(request.kycStatus))
            }
            request.mobileMoneyNumber?.let { mobileMoneyNumber ->
                val subquery = query.subquery(Long::class.java)
                val paymentMethod = subquery.from(PaymentMethodEntity::class.java)
                subquery.select(paymentMethod.get<PartyEntity>("party").get<Long>("id"))
                subquery.where(cb.equal(paymentMethod.get<String>("number"), mobileMoneyNumber))
                predicates.add(root.get<PartyEntity>("party").get<Long>("id").`in`(subquery))
            }

            cb.and(*predicates.toTypedArray())
        }

        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, Sort.by("id"))).content
    }

    fun findByIdOrNull(id: Long): AgentEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findById(id: Long): AgentEntity {
        return findByIdOrNull(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.AGENT_NOT_FOUND))
    }

    @Transactional
    fun create(request: CreateAgentRequest): AgentEntity {
        // Party
        val party = partyService.findOrCreate(
            CreatePartyRequest(
                firstName = request.firstName,
                lastName = request.lastName,
                email = request.email,
            )
        )
        ensureAgentNotAlreadyExists(party)

        // User: global, so reused if the person is already a member of another tenant
        val user = userService.findByEmailOrCreate(party.email)
        val membership = membershipService.findOrCreate(user, party)
        membershipService.grantAccess(membership, ApplicationCode.PARTNER_CENTRAL)

        // Payment method
        paymentMethodService.create(
            party,
            CreatePaymentMethodRequest(
                type = PaymentMethodType.MOBILE_MONEY,
                number = request.mobileMoneyNumber,
            )
        )

        // KYC case
        kycService.create(party, IdentificationType.NATIONAL_ID)

        // Agent
        val now = Date(clock.millis())
        val agent = dao.save(
            AgentEntity(
                party = party,
                whatsappNumber = request.whatsappNumber,
                agentType = request.agentType,
                experienceLevel = request.experienceLevel,
                cityId = request.cityId,
                neighborhoodIds = request.neighborhoodIds,
                biography = request.biography,
                createdAt = now,
                modifiedAt = now,
            )
        )

        // Listeners run after commit (see AgentEventListener)
        eventPublisher.publishEvent(AgentCreatedEvent(agentId = agent.id!!, tenantId = agent.tenantId!!))
        return agent
    }

    @Transactional
    fun update(id: Long, request: UpdateAgentRequest): AgentEntity {
        val agent = findById(id)

        // Update party
        if (request.firstName != null || request.lastName != null) {
            val updateParty = UpdatePartyRequest(
                firstName = request.firstName,
                lastName = request.lastName,
            )
            partyService.update(agent.party, updateParty)
        }

        // Update agent
        val now = Date(clock.millis())
        return dao.save(
            agent.copy(
                whatsappNumber = request.whatsappNumber ?: agent.whatsappNumber,
                agentType = request.agentType ?: agent.agentType,
                experienceLevel = request.experienceLevel ?: agent.experienceLevel,
                cityId = request.cityId ?: agent.cityId,
                neighborhoodIds = request.neighborhoodIds ?: agent.neighborhoodIds,
                biography = request.biography ?: agent.biography,
                modifiedAt = now,
            )
        )
    }

    /**
     * Sends the welcome email of an agent whose KYC is still pending. Called by `AgentEventListener` on its own
     * thread, with `TenantContext` set: the transaction gives the mailet a session to load the case's party.
     */
    @Transactional
    fun sendWelcomeEmail(agentId: Long) {
        val agent = findById(agentId)
        if (agent.party.kycStatus != KycStatus.PENDING) {
            return
        }

        val kyc = kycService.search(
            SearchKycCaseRequest(
                partyId = agent.party.id,
                statuses = listOf(KycStatus.PENDING),
                limit = 1
            )
        ).firstOrNull()

        kyc?.let {
            mailet.send(kyc)
        }
    }

    private fun ensureAgentNotAlreadyExists(party: PartyEntity) {
        val agent = dao.findByParty(party)
        if (agent != null) {
            throw ConflictException(
                error = Error(
                    code = ErrorCode.AGENT_ALREADY_EXISTS,
                    data = mapOf(
                        "email" to agent.party.email,
                    ),
                )
            )
        }
    }
}
