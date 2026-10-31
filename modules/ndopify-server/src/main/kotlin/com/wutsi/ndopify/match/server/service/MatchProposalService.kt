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
import com.wutsi.ndopify.match.server.event.MatchProposedEvent
import com.wutsi.ndopify.match.server.mail.MatchProposedMailet
import com.wutsi.ndopify.platform.logger.KVLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.Date

/**
 * A match from the agent to its customer: proposed by the agent (ACTIVE or SHORTLISTED → PROPOSED, several in one
 * e-mail), then accepted or rejected by the customer (→ ACCEPTED / REJECTED, final). The matcher never touches a row
 * once it's no longer ACTIVE. Who may do what is checked by `MatchEndpoints`.
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
     * Proposes matches of one search mandate (see [findOfOneMandate]), ACTIVE or SHORTLISTED, to its customer, who's
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

    /** The customer accepts a PROPOSED match. 409 MATCH_NOT_PROPOSED otherwise. */
    fun accept(id: Long): MatchEntity {
        val match = findProposed(id)
        val now = Date(clock.millis())
        return dao.save(match.copy(status = MatchStatus.ACCEPTED, answeredAt = now, modifiedAt = now))
    }

    /**
     * The customer rejects a PROPOSED match, for good. Keeps its reason (`reasonCode`, OTHER when none), its comment
     * (blank: none) and the price it said no to (`listingPrice`), to tell a price drop later. 409 MATCH_NOT_PROPOSED otherwise.
     */
    fun reject(id: Long, request: RejectMatchRequest): MatchEntity {
        val match = findProposed(id)
        val now = Date(clock.millis())
        return dao.save(
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

    private fun findProposed(id: Long): MatchEntity {
        val match = matchService.findById(id)
        if (match.status != MatchStatus.PROPOSED) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_PROPOSED))
        }
        return match
    }
}
