package com.wutsi.ndopify.agent.server.service

import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.dto.UpdateAgentRequest
import com.wutsi.ndopify.agent.server.dao.AgentRepository
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.event.AgentCreatedEvent
import com.wutsi.ndopify.agent.server.mail.WelcomeMailet
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
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.party.server.domain.PaymentMethodEntity
import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.party.server.service.PartyService
import com.wutsi.ndopify.party.server.service.PaymentMethodService
import com.wutsi.ndopify.refdata.dto.ApplicationCode
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
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
    companion object {
        // Used by the mandate dispatch. MySQL sorts NULL first in ASC: never-assigned agents come first
        val SORT_LEAST_RECENTLY_ASSIGNED: Sort = Sort.by(Sort.Order.asc("lastAssignedAt"), Sort.Order.asc("id"))
    }

    fun search(request: SearchAgentRequest, sort: Sort = Sort.by("id")): List<AgentEntity> {
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

        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    /** The agent was just offered a mandate: it moves down the dispatch rotation. */
    @Transactional
    fun markAssigned(agent: AgentEntity, date: Date): AgentEntity =
        dao.save(agent.copy(lastAssignedAt = date, modifiedAt = date))

    fun findByIdOrNull(id: Long): AgentEntity? {
        return dao.findById(id).orElse(null)
    }

    fun findByPartyIdOrNull(partyId: Long): AgentEntity? {
        return dao.findByPartyId(partyId)
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
        // The caller's copy is stale: saving it would overwrite the change made since (ex: in another tab)
        if (request.version != null && request.version != agent.version) {
            throw ConflictException(Error(ErrorCode.AGENT_VERSION_CONFLICT))
        }
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
        if (agent.party.kycStatus != KycStatus.PENDING_VERIFICATION) {
            return
        }

        val kyc = kycService.search(
            SearchKycCaseRequest(
                partyId = agent.party.id,
                statuses = listOf(KycStatus.PENDING_VERIFICATION),
                limit = 1
            )
        ).firstOrNull()

        kyc?.let {
            mailet.send(kyc)
        }
    }

    /**
     * The agent joins the platform on its first verified KYC. Called by `AgentKycEventListener`, inside the KYC
     * transaction. Parties that aren't agents (ex: customers) have nothing to update.
     */
    @Transactional
    fun onKycVerified(partyId: Long) {
        val agent = dao.findByPartyId(partyId) ?: return
        if (agent.joinedAt == null) {
            val now = Date(clock.millis())
            dao.save(agent.copy(joinedAt = now, modifiedAt = now))
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
