package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.event.MatchProposedEvent
import com.wutsi.ndopify.match.server.mail.MatchProposedMailet
import com.wutsi.ndopify.platform.logger.KVLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Date

/**
 * A match from the agent to its customer: proposed by the agent (ACTIVE → PROPOSED), then accepted or rejected by the
 * customer (→ ACCEPTED / REJECTED, final). The matcher never touches a row once it's no longer ACTIVE. Who may do
 * what is checked by `MatchEndpoints`.
 */
@Service
class MatchProposalService(
    private val dao: MatchRepository,
    private val matchService: MatchService,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val mailet: MatchProposedMailet,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val logger: KVLogger,
) {
    /**
     * Proposes an ACTIVE match to the customer, who's e-mailed the listing after commit ([MatchProposedEvent]). Keeps
     * the price the customer is shown (`listingPrice`). 409 MATCH_NOT_ACTIVE, MANDATE_NOT_APPROVED (the search is
     * closed), then LISTING_NOT_PUBLISHED (ex: rented meanwhile).
     */
    @Transactional
    fun propose(id: Long): MatchEntity {
        val match = matchService.findById(id)
        if (match.status != MatchStatus.ACTIVE) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_ACTIVE))
        }
        if (match.mandate.status != MandateStatus.APPROVED) {
            throw ConflictException(error = Error(code = ErrorCode.MANDATE_NOT_APPROVED))
        }
        if (match.listing.status != ListingStatus.PUBLISHED) {
            throw ConflictException(error = Error(code = ErrorCode.LISTING_NOT_PUBLISHED))
        }

        val now = Date(clock.millis())
        val saved = dao.save(
            match.copy(
                status = MatchStatus.PROPOSED,
                proposedAt = now,
                listingPrice = match.listing.price,
                modifiedAt = now,
            )
        )
        eventPublisher.publishEvent(MatchProposedEvent(matchId = saved.id!!, tenantId = saved.tenantId!!))
        return saved
    }

    /** The customer accepts a PROPOSED match. 409 MATCH_NOT_PROPOSED otherwise. */
    fun accept(id: Long): MatchEntity {
        val match = findProposed(id)
        val now = Date(clock.millis())
        return dao.save(match.copy(status = MatchStatus.ACCEPTED, answeredAt = now, modifiedAt = now))
    }

    /**
     * The customer rejects a PROPOSED match, for good. Keeps its reason (blank: none) and the price it said no to
     * (`listingPrice`), to tell a price drop later. 409 MATCH_NOT_PROPOSED otherwise.
     */
    fun reject(id: Long, request: RejectMatchRequest): MatchEntity {
        val match = findProposed(id)
        val now = Date(clock.millis())
        return dao.save(
            match.copy(
                status = MatchStatus.REJECTED,
                answeredAt = now,
                rejectionReason = request.reason?.trim()?.ifEmpty { null },
                listingPrice = match.listing.price,
                modifiedAt = now,
            )
        )
    }

    /** E-mails the customer a proposed match. Called by `MatchEventListener`; nothing once no longer PROPOSED. */
    @Transactional
    fun sendProposedEmail(matchId: Long) {
        val match = dao.findById(matchId).orElse(null) ?: return
        if (match.status != MatchStatus.PROPOSED) {
            logger.add("match_status", match.status)
            return
        }
        val customer = customerService.findById(match.mandate.customerId)
        val agent = agentService.findById(match.agentId)
        mailet.send(match, customer, agent)
    }

    private fun findProposed(id: Long): MatchEntity {
        val match = matchService.findById(id)
        if (match.status != MatchStatus.PROPOSED) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_PROPOSED))
        }
        return match
    }
}
