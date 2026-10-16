package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
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
import com.wutsi.ndopify.mandate.server.event.AssignmentAcceptedEvent
import com.wutsi.ndopify.mandate.server.event.AssignmentClosedEvent
import com.wutsi.ndopify.mandate.server.mail.MandateAssignedMailet
import com.wutsi.ndopify.mandate.server.mail.MandateOfferMailet
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.platform.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.security.server.service.AccessTokenService
import com.wutsi.ndopify.security.server.service.MembershipService
import com.wutsi.ndopify.security.server.service.UserService
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
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
    private val customerService: CustomerService,
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val accessTokenService: AccessTokenService,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val mailet: MandateAssignedMailet,
    private val offerMailet: MandateOfferMailet,
    private val dispatcher: MandateDispatcher,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssignmentService::class.java)
        const val BATCH_SIZE = 100
    }

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
        val accepted = dao.save(assignment.copy(status = AssignmentStatus.ACCEPTED, modifiedAt = now))

        // The customer is told who its agent is, after commit, by a listener of this event
        eventPublisher.publishEvent(
            AssignmentAcceptedEvent(assignmentId = accepted.id!!, tenantId = TenantContext.get()!!)
        )
        return accepted
    }

    /**
     * E-mails the customer the agent its mandate is assigned to. Called by `AssignmentEventListener` on its own
     * thread, with `TenantContext` set: the transaction gives the mailet a session to load the parties.
     */
    @Transactional
    fun sendAssignedEmail(id: Long) {
        val assignment = findById(id)
        if (assignment.status != AssignmentStatus.ACCEPTED) {
            return
        }

        val mandate = assignment.mandate
        mailet.send(
            mandate = mandate,
            customer = customerService.findById(mandate.customerId),
            agent = agentService.findById(assignment.agentId),
        )
    }

    @Transactional
    fun decline(id: Long): AssignmentEntity =
        close(findPendingForAssignee(id), AssignmentStatus.DECLINED)

    /** Expires a PENDING offer now, even before its TTL; its mandate is re-dispatched after commit. */
    @Transactional
    fun expire(id: Long): AssignmentEntity =
        close(ensurePending(findById(id), ignoreTtl = true), AssignmentStatus.EXPIRED)

    /**
     * Expires the PENDING offers past their TTL; their mandates are re-dispatched after commit. Not @Transactional:
     * each save commits on its own, so one failing offer doesn't roll back the others.
     */
    fun expireDue(): Int {
        val now = Date(clock.millis())
        val due = dao.findByStatusAndExpiresAtBefore(AssignmentStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE))
        var expired = 0
        due.forEach { assignment ->
            try {
                close(assignment, AssignmentStatus.EXPIRED)
                expired++
            } catch (ex: Exception) {
                LOGGER.warn("Unable to expire Assignment#${assignment.id}", ex)
            }
        }
        return expired
    }

    /**
     * Dispatches the mandates waiting for an agent without a PENDING offer: came at night, had no candidate, or their
     * re-dispatch failed. Not @Transactional: each [MandateDispatcher.dispatch] runs (and commits) in its own
     * transaction, so one failing mandate doesn't roll back the others.
     * Known limit: the oldest [BATCH_SIZE] are taken each run; if more have no candidate, they fill every run (until
     * they go MANUAL_ASSIGNMENT) and newer ones wait. Fine at today's volume; a `lastDispatchedAt` fixes it later.
     */
    fun dispatchPending(): Int {
        val ids = mandateDao.findIdsWithoutAssignment(
            MandateStatus.PENDING_ASSIGNMENT,
            AssignmentStatus.PENDING,
            PageRequest.of(0, BATCH_SIZE),
        )
        var dispatched = 0
        ids.forEach { id ->
            try {
                dispatcher.dispatch(id)?.let { dispatched++ }
            } catch (ex: Exception) {
                LOGGER.warn("Unable to dispatch Mandate#$id", ex)
            }
        }
        return dispatched
    }

    /**
     * E-mails the agent the mandate it's offered. Called by `AssignmentEventListener` on its own thread, with
     * `TenantContext` set: the transaction gives the mailet a session to load the parties.
     */
    @Transactional
    fun sendOfferEmail(id: Long) {
        val assignment = findById(id)
        if (assignment.status != AssignmentStatus.PENDING) {
            return // Already answered or expired: nothing to offer anymore
        }
        offerMailet.send(assignment, agentService.findById(assignment.agentId))
    }

    // DECLINED or EXPIRED. The mandate is offered to the next agent AFTER COMMIT (AssignmentEventListener): a failing
    // re-dispatch never fails, nor rolls back, the decline / expiry. MandateJobs.dispatch catches any it missed
    private fun close(assignment: AssignmentEntity, status: AssignmentStatus): AssignmentEntity {
        val closed = dao.save(assignment.copy(status = status, modifiedAt = Date(clock.millis())))
        eventPublisher.publishEvent(
            AssignmentClosedEvent(
                assignmentId = closed.id!!,
                mandateId = closed.mandate.id!!,
                tenantId = TenantContext.get()!!,
            )
        )
        return closed
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

    private fun ensurePending(assignment: AssignmentEntity, ignoreTtl: Boolean = false): AssignmentEntity {
        // Accepted / declined late (after the TTL) counts as expired, even if the expiry job didn't run yet
        val late = !ignoreTtl && assignment.expiresAt.before(Date(clock.millis()))
        if (assignment.status != AssignmentStatus.PENDING || late) {
            throw ConflictException(error = Error(code = ErrorCode.ASSIGNMENT_NOT_PENDING))
        }
        return assignment
    }
}
