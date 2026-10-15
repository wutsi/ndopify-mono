package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.SearchAssignmentRequest
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.AssignmentClosedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.security.server.service.AccessTokenService
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
class AssignmentService(
    private val dao: AssignmentRepository,
    private val mandateDao: MandateRepository,
    private val agentService: AgentService,
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val accessTokenService: AccessTokenService,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    fun findById(id: Long): AssignmentEntity {
        return dao.findById(id).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.ASSIGNMENT_NOT_FOUND))
    }

    /** Newest first: an agent's inbox shows its latest offers on top. */
    fun search(request: SearchAssignmentRequest): List<AssignmentEntity> {
        val spec = Specification<AssignmentEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            if (request.agentIds.isNotEmpty()) {
                predicates.add(root.get<Long>("agentId").`in`(request.agentIds))
            }
            if (request.mandateIds.isNotEmpty()) {
                predicates.add(root.get<MandateEntity>("mandate").get<Long>("id").`in`(request.mandateIds))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<Any>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    @Transactional
    fun accept(id: Long): AssignmentEntity {
        val assignment = findPendingForAssignee(id)
        val now = Date(clock.millis())
        mandateDao.save(
            assignment.mandate.copy(
                agentId = assignment.agentId,
                status = MandateStatus.ASSIGNED,
                modifiedAt = now,
            )
        )
        return dao.save(assignment.copy(status = AssignmentStatus.ACCEPTED, modifiedAt = now))
    }

    @Transactional
    fun decline(id: Long): AssignmentEntity {
        val assignment = findPendingForAssignee(id)
        val declined = dao.save(
            assignment.copy(status = AssignmentStatus.DECLINED, modifiedAt = Date(clock.millis()))
        )

        // The mandate is free to be offered to the next agent, after commit, by a listener of this event
        eventPublisher.publishEvent(
            AssignmentClosedEvent(
                assignmentId = declined.id!!,
                mandateId = declined.mandate.id!!,
                tenantId = TenantContext.get()!!,
            )
        )
        return declined
    }

    // 404 → 401/403 → 409: someone else's offer is refused before its status is revealed
    private fun findPendingForAssignee(id: Long): AssignmentEntity {
        val assignment = findById(id)
        ensureAssignee(assignment)
        return ensurePending(assignment)
    }

    /**
     * Only the agent the mandate is offered to can answer the offer: the caller's user (JWT), through its membership
     * of the current tenant, must have the agent's party. 401 without a token, 403 otherwise.
     */
    private fun ensureAssignee(assignment: AssignmentEntity) {
        val principal = accessTokenService.getPrincipal()
        val partyId = userService.findByIdOrNull(principal.getUserId())
            ?.let { user -> membershipService.findByUserOrNull(user) } // Tenant-filtered (@TenantId)
            ?.party?.id
        val agent = agentService.findById(assignment.agentId)
        if (partyId == null || agent.party.id != partyId) {
            throw ForbiddenException(error = Error(code = ErrorCode.ASSIGNMENT_ACCESS_DENIED))
        }
    }

    private fun ensurePending(assignment: AssignmentEntity): AssignmentEntity {
        // Accepted / declined late (after the TTL) counts as expired, even if the expiry job didn't run yet
        val late = assignment.expiresAt.before(Date(clock.millis()))
        if (assignment.status != AssignmentStatus.PENDING || late) {
            throw ConflictException(error = Error(code = ErrorCode.ASSIGNMENT_NOT_PENDING))
        }
        return assignment
    }
}
