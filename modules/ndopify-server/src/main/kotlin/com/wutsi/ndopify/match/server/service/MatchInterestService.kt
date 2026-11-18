package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.event.MatchInterestAcceptedEvent
import com.wutsi.ndopify.platform.logger.KVLogger
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A customer's interest in a listing, asked for on the listing's public page, on a search it already has: records the
 * match ACCEPTED (see [MatchService.acceptInterest]; a search that isn't approved yet is accepted at once too) and
 * tells the search's agent once, when the row moved to ACCEPTED.
 *
 * Returns null when nothing can be accepted (a search without an agent yet, a listing no longer published, the
 * customer's own property). A search without an agent keeps the listing as its `originListingId`
 * (`MandateService.create`), and `proposeOrigin` accepts it once the search is approved: the interest isn't lost.
 */
@Service
class MatchInterestService(
    private val matchService: MatchService,
    private val agentService: AgentService,
    private val customerService: CustomerService,
    private val mandateService: MandateService,
    private val notifier: MatchInterestNotifier,
    private val eventPublisher: ApplicationEventPublisher,
    private val logger: KVLogger,
) {
    /** Returns the match's id, or null when nothing could be accepted (see the class KDoc). */
    @Transactional
    fun registerInterest(mandateId: Long, listingId: Long): Long? {
        val interest = matchService.acceptInterest(mandateId, listingId) ?: return null
        val match = interest.match
        logger.add("match_id", match.id)
        logger.add("match_changed", interest.changed)
        if (interest.changed) {
            notify(match)
            eventPublisher.publishEvent(MatchInterestAcceptedEvent(match.id!!, match.tenantId!!))
        }
        return match.id
    }

    /**
     * The mandate an approval just made APPROVED had a search seeded from a listing: accepts the match of that listing
     * (no agent notification, the approval's own message is enough) and e-mails the customer its booking link. Null
     * when it has no origin, the listing is gone or no longer published, or it's the customer's own property.
     */
    @Transactional
    fun proposeOrigin(mandateId: Long): Long? {
        val mandate = mandateService.findByIdOrNull(mandateId) ?: return null
        val listingId = mandate.originListingId ?: return null
        val interest = matchService.acceptInterest(mandateId, listingId) ?: return null
        val match = interest.match
        logger.add("origin_match_id", match.id)
        logger.add("origin_match_changed", interest.changed)
        if (interest.changed) {
            eventPublisher.publishEvent(MatchInterestAcceptedEvent(match.id!!, match.tenantId!!))
        }
        return match.id
    }

    // A failed notification never fails the interest: the row is accepted, and the agent sees it on its search
    private fun notify(match: MatchEntity) {
        try {
            notifier.send(
                agent = agentService.findById(match.mandate.agentId!!),
                customer = customerService.findById(match.mandate.customerId),
                mandate = match.mandate,
                listing = match.listing,
            )
        } catch (ex: Exception) {
            logger.setException(ex)
        }
    }
}
