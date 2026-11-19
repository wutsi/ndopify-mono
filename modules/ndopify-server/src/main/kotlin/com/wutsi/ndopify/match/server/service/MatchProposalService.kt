package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.event.MatchAnsweredEvent
import com.wutsi.ndopify.match.server.event.MatchProposedEvent
import com.wutsi.ndopify.match.server.event.MatchResentEvent
import com.wutsi.ndopify.match.server.mail.MatchProposedMailet
import com.wutsi.ndopify.platform.logger.KVLogger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.util.Date

/**
 * A match from the agent to its customer: proposed by the agent (ACTIVE, SHORTLISTED or EXPIRED → PROPOSED, several in
 * one e-mail), then accepted or rejected by the customer (→ ACCEPTED / REJECTED, final), or left unanswered for
 * `ndopify.match.expire.days` (→ EXPIRED by `MatchJobs.expire`, which the agent may propose again). The agent may also
 * resend the link of a PROPOSED or ACCEPTED match: a new e-mail, the status untouched (`resend`). The matcher never
 * touches a row once it's no longer ACTIVE. Who may do what is checked by `MatchEndpoints`.
 */
@Service
class MatchProposalService(
    private val dao: MatchRepository,
    private val matchService: MatchService,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val mailet: MatchProposedMailet,
    private val answeredNotifier: MatchAnsweredNotifier,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val logger: KVLogger,

    @param:Value("\${ndopify.match.expire.days}") private val expireDays: Long,
    @param:Value("\${ndopify.match.resend.min-interval}") private val resendMinInterval: Duration,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchProposalService::class.java)
        const val BATCH_SIZE = 100
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    /**
     * The matches [ids], all of one search mandate, for a proposal: 404 MATCH_NOT_FOUND for the first one not found,
     * then 400 HTTP_INVALID_PARAMETER when they aren't all of the same mandate (one e-mail goes to one customer).
     */
    fun findOfOneMandate(ids: List<Long>): List<MatchEntity> {
        val matches = ids.distinct().map { id -> matchService.findById(id) }
        if (matches.map { it.mandate.id }.distinct().size > 1) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "matchIds", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
                ),
            )
        }
        return matches
    }

    /**
     * Proposes matches of one search mandate (see [findOfOneMandate]), ACTIVE, SHORTLISTED or EXPIRED, to its customer, who's
     * e-mailed them in one e-mail after commit ([MatchProposedEvent]). All or none. Keeps the price the customer is
     * shown (`listingPrice`). Then 409 MATCH_NOT_ACTIVE, MANDATE_NOT_APPROVED (the search is closed), then
     * LISTING_NOT_PUBLISHED (ex: rented meanwhile), for the first match concerned.
     */
    @Transactional
    fun propose(ids: List<Long>): List<MatchEntity> {
        val matches = findOfOneMandate(ids)
        matches.forEach { match ->
            if (match.status !in MatchService.OPEN_STATUSES) {
                throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_ACTIVE))
            }
            if (match.mandate.status != MandateStatus.APPROVED) {
                throw ConflictException(error = Error(code = ErrorCode.MANDATE_NOT_APPROVED))
            }
            if (match.listing.status != ListingStatus.PUBLISHED) {
                throw ConflictException(error = Error(code = ErrorCode.LISTING_NOT_PUBLISHED))
            }
        }

        val now = Date(clock.millis())
        val saved = matches.map { match ->
            dao.save(
                match.copy(
                    status = MatchStatus.PROPOSED,
                    proposedAt = now,
                    listingPrice = match.listing.price,
                    modifiedAt = now,
                )
            )
        }
        eventPublisher.publishEvent(
            MatchProposedEvent(matchIds = saved.map { it.id!! }, tenantId = saved.first().tenantId!!),
        )
        return saved
    }

    /**
     * Sends the match's link to its customer again, without changing its status: PROPOSED or ACCEPTED only, the search
     * APPROVED and the listing PUBLISHED. A PROPOSED match gets a fresh `proposedAt` (its 7-day window restarts); an
     * ACCEPTED one keeps its `answeredAt` and `proposedAt`. 409 MATCH_NOT_PROPOSED, MANDATE_NOT_APPROVED,
     * LISTING_NOT_PUBLISHED, then MATCH_RATE_LIMITED when the last resend is younger than `ndopify.match.resend.min-interval`.
     * E-mails the customer after commit ([MatchResentEvent]).
     */
    @Transactional
    fun resend(id: Long): MatchEntity {
        val match = matchService.findById(id)
        if (match.status != MatchStatus.PROPOSED && match.status != MatchStatus.ACCEPTED) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_PROPOSED))
        }
        if (match.mandate.status != MandateStatus.APPROVED) {
            throw ConflictException(error = Error(code = ErrorCode.MANDATE_NOT_APPROVED))
        }
        if (match.listing.status != ListingStatus.PUBLISHED) {
            throw ConflictException(error = Error(code = ErrorCode.LISTING_NOT_PUBLISHED))
        }
        val now = Date(clock.millis())
        if (match.resentAt?.after(Date(now.time - resendMinInterval.toMillis())) == true) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_RATE_LIMITED))
        }

        val saved = dao.save(
            match.copy(
                proposedAt = if (match.status == MatchStatus.PROPOSED) now else match.proposedAt,
                resentAt = now,
                modifiedAt = now,
            )
        )
        eventPublisher.publishEvent(
            MatchResentEvent(matchId = saved.id!!, tenantId = saved.tenantId!!),
        )
        return saved
    }

    /**
     * E-mails the customer the match's link again, PROPOSED or ACCEPTED only; called by `MatchEventListener`. Nothing
     * for an unknown match or another status (answered, expired meanwhile).
     */
    @Transactional
    fun sendResentEmail(matchId: Long) {
        val match = dao.findById(matchId).orElse(null) ?: return
        if (match.status != MatchStatus.PROPOSED && match.status != MatchStatus.ACCEPTED) {
            return
        }
        mailet.send(
            listOf(match),
            customerService.findById(match.mandate.customerId),
            agentService.findById(match.agentId),
        )
    }

    /**
     * The customer accepts a PROPOSED match. 409 MATCH_NOT_PROPOSED otherwise. After commit, its agent hears the answer
     * ([MatchAnsweredEvent]).
     */
    @Transactional
    fun accept(id: Long): MatchEntity {
        val match = findProposed(id)
        val now = Date(clock.millis())
        val saved = dao.save(match.copy(status = MatchStatus.ACCEPTED, answeredAt = now, modifiedAt = now))
        eventPublisher.publishEvent(
            MatchAnsweredEvent(
                matchId = saved.id!!,
                mandateId = saved.mandate.id!!,
                tenantId = saved.tenantId!!,
                status = saved.status,
            ),
        )
        return saved
    }

    /**
     * The customer rejects a PROPOSED match, for good. Keeps its reason (`reasonCode`, OTHER when none), its comment
     * (blank: none) and the price it said no to (`listingPrice`), to tell a price drop later. 409 MATCH_NOT_PROPOSED
     * otherwise. After commit, its agent hears the answer ([MatchAnsweredEvent]).
     */
    @Transactional
    fun reject(id: Long, request: RejectMatchRequest): MatchEntity {
        val match = findProposed(id)
        val now = Date(clock.millis())
        val saved = dao.save(
            match.copy(
                status = MatchStatus.REJECTED,
                answeredAt = now,
                rejectionReasonCode = request.reasonCode
                    ?.takeIf { it != MatchRejectionReason.UNKNOWN }
                    ?: MatchRejectionReason.OTHER,
                rejectionReason = request.reason?.trim()?.ifEmpty { null },
                listingPrice = match.listing.price,
                modifiedAt = now,
            )
        )
        eventPublisher.publishEvent(
            MatchAnsweredEvent(
                matchId = saved.id!!,
                mandateId = saved.mandate.id!!,
                tenantId = saved.tenantId!!,
                status = saved.status,
            ),
        )
        return saved
    }

    /**
     * Tells the match's agent that its customer answered it: called by `MatchEventListener` after commit, so nothing is
     * sent for a rolled-back answer. The agent is the search mandate's (`match.agentId`), never the listing's. Nothing
     * once the match isn't ACCEPTED or REJECTED (a second event, a match expired meanwhile).
     */
    @Transactional
    fun sendAnsweredNotification(matchId: Long) {
        val match = dao.findById(matchId).orElse(null) ?: return
        if (match.status != MatchStatus.ACCEPTED && match.status != MatchStatus.REJECTED) {
            return
        }
        answeredNotifier.send(
            match,
            customerService.findById(match.mandate.customerId),
            agentService.findById(match.agentId),
        )
    }

    /**
     * E-mails the customer an ACCEPTED match's link, through the accepted proposal e-mail; called by
     * `MatchEventListener` after the interest is committed. Nothing for an unknown match or another status (answered,
     * expired, reopened meanwhile).
     */
    @Transactional
    fun sendAcceptedEmail(matchId: Long) {
        val match = dao.findById(matchId).orElse(null) ?: return
        if (match.status != MatchStatus.ACCEPTED) {
            return
        }
        mailet.send(
            listOf(match),
            customerService.findById(match.mandate.customerId),
            agentService.findById(match.agentId),
        )
    }

    /**
     * E-mails the customer the matches proposed together. Called by `MatchEventListener`; leaves out those no longer
     * PROPOSED, nothing when none is.
     */
    @Transactional
    fun sendProposedEmail(matchIds: List<Long>) {
        val matches = dao.findAllById(matchIds).filter { it.status == MatchStatus.PROPOSED }
        logger.add("match_count", matches.size)
        val match = matches.firstOrNull() ?: return
        val customer = customerService.findById(match.mandate.customerId)
        val agent = agentService.findById(match.agentId)
        mailet.send(matches.sortedByDescending { it.score }, customer, agent)
    }

    /**
     * Expires the PROPOSED matches the customer hasn't answered within `expireDays`, oldest first, at most
     * [BATCH_SIZE] per run (the rest on the next one). Each save is its own transaction: a match answered meanwhile
     * fails on its `@Version` and is skipped, never overwritten. No e-mail. Returns how many expired.
     */
    fun expireDue(): Int {
        val now = Date(clock.millis())
        val matches = dao.findByStatusAndProposedAtBeforeOrderByProposedAt(
            status = MatchStatus.PROPOSED,
            before = Date(now.time - expireDays * DAY_MILLIS),
            page = PageRequest.of(0, BATCH_SIZE),
        )
        return matches.count { match ->
            try {
                dao.save(match.copy(status = MatchStatus.EXPIRED, modifiedAt = now))
                true
            } catch (ex: ObjectOptimisticLockingFailureException) {
                LOGGER.info("Match#${match.id} - Answered while expiring, left as is")
                false
            } catch (ex: Exception) {
                LOGGER.warn("Match#${match.id} - Unable to expire", ex)
                false
            }
        }
    }

    private fun findProposed(id: Long): MatchEntity {
        val match = matchService.findById(id)
        if (match.status != MatchStatus.PROPOSED) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_PROPOSED))
        }
        return match
    }
}
