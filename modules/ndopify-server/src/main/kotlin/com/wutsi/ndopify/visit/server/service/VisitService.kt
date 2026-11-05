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
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import com.wutsi.ndopify.visit.dto.CancelVisitRequest
import com.wutsi.ndopify.visit.dto.CheckInVisitRequest
import com.wutsi.ndopify.visit.dto.CheckOutVisitRequest
import com.wutsi.ndopify.visit.dto.CreateVisitRequest
import com.wutsi.ndopify.visit.dto.NoShowVisitRequest
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.SearchVisitRequest
import com.wutsi.ndopify.visit.dto.VisitFeedbackRequest
import com.wutsi.ndopify.visit.dto.VisitInterest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.event.VisitCancelledEvent
import com.wutsi.ndopify.visit.server.event.VisitCompletedEvent
import com.wutsi.ndopify.visit.server.event.VisitConfirmedEvent
import com.wutsi.ndopify.visit.server.event.VisitExpiredEvent
import com.wutsi.ndopify.visit.server.event.VisitNoShowEvent
import com.wutsi.ndopify.visit.server.event.VisitReminderEvent
import com.wutsi.ndopify.visit.server.event.VisitRequestedEvent
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import com.wutsi.ndopify.visit.server.mail.VisitCompletedMailet
import com.wutsi.ndopify.visit.server.mail.VisitConfirmedMailet
import com.wutsi.ndopify.visit.server.mail.VisitExpiredMailet
import com.wutsi.ndopify.visit.server.mail.VisitNoShowMailet
import com.wutsi.ndopify.visit.server.mail.VisitReminderMailet
import com.wutsi.ndopify.visit.server.mail.VisitRequestedMailet
import jakarta.persistence.criteria.Predicate
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.util.Date

/**
 * The visit's lifecycle: requested by the customer, scheduled by the agent, checked in and out. Who may do what is
 * checked by [VisitAccessService] (from `VisitEndpoints`); this service checks the state of the entities.
 */
@Service
@EnableConfigurationProperties(VisitPolicyProperties::class)
class VisitService(
    private val dao: VisitRepository,
    private val matchDao: MatchRepository,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val listingService: ListingService,
    private val tenantService: TenantService,
    private val requestedMailet: VisitRequestedMailet,
    private val confirmedMailet: VisitConfirmedMailet,
    private val completedMailet: VisitCompletedMailet,
    private val noShowMailet: VisitNoShowMailet,
    private val cancelledMailet: VisitCancelledMailet,
    private val reminderMailet: VisitReminderMailet,
    private val expiredMailet: VisitExpiredMailet,
    private val codeGenerator: VisitCodeGenerator,
    private val eventPublisher: ApplicationEventPublisher,
    private val tx: TransactionTemplate,
    private val clock: Clock,
    private val logger: KVLogger,
    private val policy: VisitPolicyProperties,
) {
    companion object {
        // The statuses in which a match already has a visit: only one at a time
        val OPEN_STATUSES = setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)

        private val LOGGER = LoggerFactory.getLogger(VisitService::class.java)
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
     * confirmed within [VisitPolicyProperties.remindBefore] of it: the confirmation e-mail is the reminder.
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
                reminderSentAt = now.takeIf { request.scheduledAt.time - now.time < policy.remindBefore.toMillis() },
            ),
        )
        eventPublisher.publishEvent(VisitConfirmedEvent(visitId = saved.id!!, tenantId = visit.tenantId!!))
        return saved
    }

    /**
     * The agent checks the customer in. 409 VISIT_NOT_CONFIRMED (ex: cancelled meanwhile), then VISIT_CODE_LOCKED after
     * [VisitPolicyProperties.maxCodeAttempts] wrong codes (for good), then VISIT_OUTSIDE_WINDOW (from
     * [VisitPolicyProperties.checkInBefore] before to [VisitPolicyProperties.checkInAfter] after the time). A wrong
     * code is counted and kept even though the call fails: a right code after the last attempt is still locked.
     */
    @Transactional(noRollbackFor = [ConflictException::class])
    fun checkIn(id: Long, request: CheckInVisitRequest): VisitEntity {
        val now = Date(clock.millis())
        val visit = lock(id)
        if (visit.status != VisitStatus.CONFIRMED) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_NOT_CONFIRMED))
        }
        if (visit.codeAttempts >= policy.maxCodeAttempts) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_CODE_LOCKED))
        }
        ensureInWindow(visit, now, policy.checkInBefore.negated(), policy.checkInAfter)

        if (visit.code != request.code) {
            val attempts = visit.codeAttempts + 1
            dao.save(visit.copy(codeAttempts = attempts))
            throw ConflictException(
                error = Error(
                    code = if (attempts >= policy.maxCodeAttempts) {
                        ErrorCode.VISIT_CODE_LOCKED
                    } else {
                        ErrorCode.VISIT_CODE_INVALID
                    },
                ),
            )
        }

        val distance = distance(visit.listingId, request.latitude, request.longitude)
        return dao.save(
            visit.copy(
                status = VisitStatus.CHECKED_IN,
                checkedInAt = now,
                checkInDistanceMeters = distance,
                checkInFlagged = distance != null && distance > policy.flagDistanceMeters,
                checkInLatitude = request.latitude,
                checkInLongitude = request.longitude,
                checkInAccuracy = request.accuracyMeters,
            ),
        )
    }

    /**
     * The agent reports the customer's absence: 400 HTTP_INVALID_PARAMETER for the agent's own absence (a job marks it),
     * 409 VISIT_NOT_CONFIRMED, then VISIT_OUTSIDE_WINDOW (from [VisitPolicyProperties.noShowDelay] to
     * [VisitPolicyProperties.checkInAfter] after the time).
     */
    @Transactional
    fun noShow(id: Long, request: NoShowVisitRequest): VisitEntity {
        val now = Date(clock.millis())
        val visit = lock(id)
        if (request.reason != VisitNoShowReason.CUSTOMER_ABSENT && request.reason != VisitNoShowReason.CODE_REFUSED) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "reason", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
                ),
            )
        }
        if (visit.status != VisitStatus.CONFIRMED) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_NOT_CONFIRMED))
        }
        ensureInWindow(visit, now, policy.noShowDelay, policy.checkInAfter)

        val saved = dao.save(
            visit.copy(
                status = VisitStatus.NO_SHOW,
                noShowParty = VisitParty.CUSTOMER,
                noShowReason = request.reason,
                noShowLatitude = request.latitude,
                noShowLongitude = request.longitude,
                noShowAccuracy = request.accuracyMeters,
            ),
        )
        eventPublisher.publishEvent(VisitNoShowEvent(visitId = saved.id!!, tenantId = visit.tenantId!!))
        return saved
    }

    /**
     * The agent checks the customer out: 409 VISIT_NOT_CHECKED_IN, then VISIT_OUTSIDE_WINDOW until
     * [VisitPolicyProperties.checkOutMinimum] after the check-in. The comment is private to the agent and the admins.
     */
    @Transactional
    fun checkOut(id: Long, request: CheckOutVisitRequest): VisitEntity {
        val now = Date(clock.millis())
        val visit = lock(id)
        if (visit.status != VisitStatus.CHECKED_IN) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_NOT_CHECKED_IN))
        }
        if (now.time < visit.checkedInAt!!.time + policy.checkOutMinimum.toMillis()) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_OUTSIDE_WINDOW))
        }

        val saved = dao.save(
            visit.copy(
                status = VisitStatus.COMPLETED,
                checkedOutAt = now,
                agentComment = request.comment.trim(),
                checkOutLatitude = request.latitude,
                checkOutLongitude = request.longitude,
                checkOutAccuracy = request.accuracyMeters,
            ),
        )
        eventPublisher.publishEvent(VisitCompletedEvent(visitId = saved.id!!, tenantId = visit.tenantId!!))
        return saved
    }

    /**
     * The customer or the agent cancels a visit that hasn't started: 409 VISIT_CANNOT_BE_CANCELLED once checked in,
     * completed or already cancelled. The other side is e-mailed after commit.
     */
    @Transactional
    fun cancel(id: Long, request: CancelVisitRequest, cancelledBy: VisitParty): VisitEntity {
        val visit = lock(id)
        if (visit.status != VisitStatus.REQUESTED && visit.status != VisitStatus.CONFIRMED) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_CANNOT_BE_CANCELLED))
        }
        val saved = dao.save(
            visit.copy(
                status = VisitStatus.CANCELLED,
                cancelledBy = cancelledBy,
                cancellationReason = request.reason?.trim()?.ifEmpty { null },
                cancelledAt = Date(clock.millis()),
            ),
        )
        eventPublisher.publishEvent(VisitCancelledEvent(visitId = saved.id!!, tenantId = visit.tenantId!!))
        return saved
    }

    /**
     * The customer's feedback on a COMPLETED visit, once: 409 VISIT_NOT_COMPLETED, then VISIT_FEEDBACK_ALREADY_SENT. A
     * reason only when the property doesn't interest it (OTHER when none given).
     */
    @Transactional
    fun feedback(id: Long, request: VisitFeedbackRequest): VisitEntity {
        val visit = lock(id)
        if (visit.status != VisitStatus.COMPLETED) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_NOT_COMPLETED))
        }
        if (visit.interest != null) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_FEEDBACK_ALREADY_SENT))
        }

        val notInterested = request.interest == VisitInterest.NOT_INTERESTED
        return dao.save(
            visit.copy(
                interest = request.interest,
                feedbackReasonCode = if (notInterested) {
                    request.reasonCode?.takeIf { it != MatchRejectionReason.UNKNOWN } ?: MatchRejectionReason.OTHER
                } else {
                    null
                },
                feedbackComment = request.comment?.trim()?.ifEmpty { null },
            ),
        )
    }

    /**
     * Expires the REQUESTED visits older than [VisitPolicyProperties.expireAfter], each in its own transaction: a visit
     * scheduled meanwhile fails on its `@Version` and is skipped. E-mails both sides after commit. Returns how many
     * expired.
     */
    fun expireDue(): Int {
        val before = Date(clock.millis() - policy.expireAfter.toMillis())
        val due = dao.findByStatusAndCreatedAtBefore(VisitStatus.REQUESTED, before)
        return due.count { visit ->
            update(visit) { current ->
                val saved = dao.save(current.copy(status = VisitStatus.EXPIRED))
                eventPublisher.publishEvent(VisitExpiredEvent(visitId = saved.id!!, tenantId = saved.tenantId!!))
                saved
            }
        }
    }

    /**
     * Reminds both sides of the CONFIRMED visits starting within [VisitPolicyProperties.remindBefore] whose reminder
     * wasn't sent: `reminderSentAt` is set then, so a visit isn't reminded twice (nor one confirmed within that delay,
     * which was marked at confirmation). Each in its own transaction. Returns how many were reminded.
     */
    fun remindDue(): Int {
        val now = Date(clock.millis())
        val due = dao.findByStatusAndScheduledAtBetweenAndReminderSentAtIsNull(
            VisitStatus.CONFIRMED,
            now,
            Date(now.time + policy.remindBefore.toMillis()),
        )
        return due.count { visit ->
            update(visit) { current ->
                val saved = dao.save(current.copy(reminderSentAt = now))
                eventPublisher.publishEvent(VisitReminderEvent(visitId = saved.id!!, tenantId = saved.tenantId!!))
                saved
            }
        }
    }

    /**
     * Marks NO_SHOW the CONFIRMED visits whose agent didn't check in within [VisitPolicyProperties.checkInAfter] of the
     * time, each in its own transaction. `noShowParty = AGENT`, so both sides are e-mailed. Returns how many were
     * marked.
     */
    fun markAgentNoShows(): Int {
        val before = Date(clock.millis() - policy.checkInAfter.toMillis())
        val due = dao.findByStatusAndScheduledAtBefore(VisitStatus.CONFIRMED, before)
        return due.count { visit ->
            update(visit) { current ->
                val saved = dao.save(
                    current.copy(
                        status = VisitStatus.NO_SHOW,
                        noShowParty = VisitParty.AGENT,
                        noShowReason = VisitNoShowReason.AGENT_ABSENT,
                    ),
                )
                eventPublisher.publishEvent(VisitNoShowEvent(visitId = saved.id!!, tenantId = saved.tenantId!!))
                saved
            }
        }
    }

    // Runs [block] in its own transaction; a visit changed meanwhile is skipped, never overwritten
    private fun update(visit: VisitEntity, block: (VisitEntity) -> VisitEntity): Boolean =
        try {
            tx.execute { block(visit) }
            true
        } catch (ex: ObjectOptimisticLockingFailureException) {
            LOGGER.info("Visit#${visit.id} - Changed while processing, left as is")
            false
        } catch (ex: Exception) {
            LOGGER.warn("Visit#${visit.id} - Unable to process", ex)
            false
        }

    private fun lock(id: Long): VisitEntity =
        dao.findByIdForUpdate(id) ?: throw NotFoundException(error = Error(code = ErrorCode.VISIT_NOT_FOUND))

    // The visit's time, shifted by [before] (negative: before) and [after]
    private fun ensureInWindow(visit: VisitEntity, now: Date, before: Duration, after: Duration) {
        val scheduled = visit.scheduledAt!!.time
        if (now.time < scheduled + before.toMillis() || now.time > scheduled + after.toMillis()) {
            throw ConflictException(error = Error(code = ErrorCode.VISIT_OUTSIDE_WINDOW))
        }
    }

    private fun distance(listingId: Long, latitude: Double?, longitude: Double?): Int? {
        if (latitude == null || longitude == null) {
            return null
        }
        val listing = listingService.findById(listingId)
        val listingLatitude = listing.latitude ?: return null
        val listingLongitude = listing.longitude ?: return null
        return GeoDistance.meters(listingLatitude, listingLongitude, latitude, longitude)
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
            if (request.listingIds.isNotEmpty()) {
                predicates.add(root.get<Long>("listingId").`in`(request.listingIds))
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

    /** E-mails the customer that the visit is over. Called by `VisitEventListener`; nothing when not COMPLETED. */
    @Transactional
    fun sendCompletedEmail(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.COMPLETED) {
            return
        }
        logger.add("visit_id", visitId)
        completedMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            listing = listingService.findById(visit.listingId),
        )
    }

    /**
     * E-mails the no-show: the customer only when it caused it, both sides when the agent did. Called by
     * `VisitEventListener`; nothing when not NO_SHOW.
     */
    @Transactional
    fun sendNoShowEmails(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.NO_SHOW || visit.noShowParty == null) {
            return
        }
        logger.add("visit_id", visitId)
        noShowMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            agent = agentService.findById(visit.agentId),
            listing = listingService.findById(visit.listingId),
        )
    }

    /** E-mails the other side of a cancellation. Called by `VisitEventListener`; nothing when not CANCELLED. */
    @Transactional
    fun sendCancelledEmail(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.CANCELLED || visit.cancelledBy == null) {
            return
        }
        logger.add("visit_id", visitId)
        cancelledMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            agent = agentService.findById(visit.agentId),
            listing = listingService.findById(visit.listingId),
        )
    }

    /** E-mails both sides the 24 h reminder. Called by `VisitEventListener`; nothing when no longer CONFIRMED. */
    @Transactional
    fun sendReminderEmails(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.CONFIRMED || visit.scheduledAt == null) {
            return
        }
        logger.add("visit_id", visitId)
        reminderMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            agent = agentService.findById(visit.agentId),
            listing = listingService.findById(visit.listingId),
        )
    }

    /** E-mails both sides an expired request. Called by `VisitEventListener`; nothing when not EXPIRED. */
    @Transactional
    fun sendExpiredEmails(visitId: Long) {
        val visit = dao.findById(visitId).orElse(null) ?: return
        if (visit.status != VisitStatus.EXPIRED) {
            return
        }
        logger.add("visit_id", visitId)
        expiredMailet.send(
            visit = visit,
            customer = customerService.findById(visit.customerId),
            agent = agentService.findById(visit.agentId),
            listing = listingService.findById(visit.listingId),
        )
    }
}
