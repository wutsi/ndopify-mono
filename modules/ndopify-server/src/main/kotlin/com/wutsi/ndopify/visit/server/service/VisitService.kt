package com.wutsi.ndopify.visit.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.SearchVisitRequest
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.event.VisitConfirmedEvent
import com.wutsi.ndopify.visit.server.event.VisitRequestedEvent
import com.wutsi.ndopify.visit.server.mail.VisitConfirmedMailet
import com.wutsi.ndopify.visit.server.mail.VisitRequestedMailet
import jakarta.persistence.criteria.Predicate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Date

/**
 * The visit's lifecycle: requested by the customer, scheduled by the agent, checked in and out. Who may do what is
 * checked by [VisitAccessService] (from `VisitEndpoints`); this service checks the state of the entities.
 */
@Service
class VisitService(
    private val dao: VisitRepository,
    private val matchDao: MatchRepository,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val listingService: ListingService,
    private val tenantService: TenantService,
    private val requestedMailet: VisitRequestedMailet,
    private val confirmedMailet: VisitConfirmedMailet,
    private val codeGenerator: VisitCodeGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val logger: KVLogger,
) {
    companion object {
        // The statuses in which a match already has a visit: only one at a time
        val OPEN_STATUSES = setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)

        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    fun findById(id: Long): VisitEntity {
        return dao.findById(id).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.VISIT_NOT_FOUND))
    }

    /**
     * The customer requests a visit of a match it accepted. The match is locked first, so that two requests for the same
     * match can't both pass the open-visit check. Then 400 HTTP_INVALID_PARAMETER for an UNKNOWN type, 400
     * VISIT_INVALID_SLOT for a date outside the allowed slots, then 409 MATCH_NOT_ACCEPTED, MANDATE_NOT_APPROVED,
     * LISTING_NOT_PUBLISHED, VISIT_WHATSAPP_MISSING (a VIDEO visit needs the customer's WhatsApp number) and
     * VISIT_ALREADY_EXISTS (an open visit for that match).
     */
    @Transactional
    fun create(request: CreateVisitRequest): VisitEntity {
        val now = Date(clock.millis())
        val match = matchDao.findByIdForUpdate(request.matchId)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MATCH_NOT_FOUND))

        if (request.type != VisitType.ON_SITE && request.type != VisitType.VIDEO) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "type", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
                ),
            )
        }

        val tenant = tenantService.findById(match.tenantId!!)
        if (!VisitSlots.isValid(request.requestedAt, tenant, now)) {
            throw BadRequestException(error = Error(code = ErrorCode.VISIT_INVALID_SLOT))
        }

        if (match.status != MatchStatus.ACCEPTED) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_ACCEPTED))
        }
        if (match.mandate.status != MandateStatus.APPROVED) {
            throw ConflictException(error = Error(code = ErrorCode.MANDATE_NOT_APPROVED))
        }
        if (match.listing.status != ListingStatus.PUBLISHED) {
            throw ConflictException(error = Error(code = ErrorCode.LISTING_NOT_PUBLISHED))
        }
        if (request.type == VisitType.VIDEO) {
            val customer = customerService.findById(match.mandate.customerId)
            if (customer.whatsappNumber.isNullOrBlank()) {
                throw ConflictException(error = Error(code = ErrorCode.VISIT_WHATSAPP_MISSING))
            }
        }
        if (dao.existsByMatchIdAndStatusIn(match.id!!, OPEN_STATUSES)) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_ALREADY_EXISTS))
        }

        val visit = dao.save(
            VisitEntity(
                matchId = match.id,
                mandateId = match.mandate.id!!,
                listingId = match.listing.id!!,
                agentId = match.agentId,
                customerId = match.mandate.customerId,
                status = VisitStatus.REQUESTED,
                type = request.type,
                requestedAt = request.requestedAt,
                note = request.note?.trim()?.ifEmpty { null },
                createdAt = now,
            )
        )
        eventPublisher.publishEvent(VisitRequestedEvent(visitId = visit.id!!, tenantId = match.tenantId!!))
        return visit
    }

    /**
     * The agent (or an admin) confirms the visit: 400 HTTP_INVALID_PARAMETER for an UNKNOWN type, 400 VISIT_INVALID_SLOT for
     * a date outside the allowed slots, then 409 VISIT_NOT_REQUESTED, VISIT_WHATSAPP_MISSING (a VIDEO visit needs the
     * customer's WhatsApp number). The code is generated here, and the reminder is marked as sent when the visit is
     * confirmed less than 24 h before it: the confirmation e-mail is the reminder.
     */
    @Transactional
    fun schedule(id: Long, request: ScheduleVisitRequest): VisitEntity {
        val now = Date(clock.millis())
        val visit = dao.findByIdForUpdate(id)
            ?: throw NotFoundException(error = Error(code = ErrorCode.VISIT_NOT_FOUND))

        val type = request.type ?: visit.type
        if (type != VisitType.ON_SITE && type != VisitType.VIDEO) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "type", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
                ),
            )
        }
        val tenant = tenantService.findById(visit.tenantId!!)
        if (!VisitSlots.isValid(request.scheduledAt, tenant, now)) {
            throw BadRequestException(error = Error(code = ErrorCode.VISIT_INVALID_SLOT))
        }
        if (visit.status != VisitStatus.REQUESTED) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_NOT_REQUESTED))
        }
        if (type == VisitType.VIDEO) {
            val customer = customerService.findById(visit.customerId)
            if (customer.whatsappNumber.isNullOrBlank()) {
                throw ConflictException(error = Error(code = ErrorCode.VISIT_WHATSAPP_MISSING))
            }
        }

        val saved = dao.save(
            visit.copy(
                status = VisitStatus.CONFIRMED,
                scheduledAt = request.scheduledAt,
                type = type,
                code = codeGenerator.generate(),
                codeAttempts = 0,
                reminderSentAt = now.takeIf { request.scheduledAt.time - now.time < DAY_MILLIS },
            ),
        )
        eventPublisher.publishEvent(VisitConfirmedEvent(visitId = saved.id!!, tenantId = visit.tenantId!!))
        return saved
    }

    /** Soonest first. */
    fun search(request: SearchVisitRequest): List<VisitEntity> {
        val spec = Specification<VisitEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.mandateIds.isNotEmpty()) {
                predicates.add(root.get<Long>("mandateId").`in`(request.mandateIds))
            }
            if (request.matchIds.isNotEmpty()) {
                predicates.add(root.get<Long>("matchId").`in`(request.matchIds))
            }
            if (request.agentIds.isNotEmpty()) {
                predicates.add(root.get<Long>("agentId").`in`(request.agentIds))
            }
            if (request.customerIds.isNotEmpty()) {
                predicates.add(root.get<Long>("customerId").`in`(request.customerIds))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<Any>("status").`in`(request.statuses))
            }
            request.scheduledFrom?.let { from ->
                predicates.add(cb.greaterThanOrEqualTo(root.get("scheduledAt"), Date.from(from)))
            }
            request.scheduledTo?.let { to ->
                predicates.add(cb.lessThanOrEqualTo(root.get("scheduledAt"), Date.from(to)))
            }
            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(Sort.Order.asc("scheduledAt"), Sort.Order.asc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    /** E-mails the agent the request. Called by `VisitEventListener`; nothing when the visit is no longer REQUESTED. */
    @Transactional
    fun sendRequestedEmail(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.REQUESTED) {
            return
        }
        logger.add("visit_id", visitId)
        requestedMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            agent = agentService.findById(visit.agentId),
            listing = listingService.findById(visit.listingId),
        )
    }

    /** E-mails both sides the confirmation. Called by `VisitEventListener`; nothing when no longer CONFIRMED. */
    @Transactional
    fun sendConfirmedEmails(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.CONFIRMED || visit.scheduledAt == null) {
            return
        }
        logger.add("visit_id", visitId)
        confirmedMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            agent = agentService.findById(visit.agentId),
            listing = listingService.findById(visit.listingId),
        )
    }
}
