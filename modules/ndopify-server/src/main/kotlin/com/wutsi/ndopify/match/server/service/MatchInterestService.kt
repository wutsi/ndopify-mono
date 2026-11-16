package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A customer's interest in a listing, asked for on the listing's public page, on a search it already has: stores the
 * match (see [MatchService.ensureInterest]), tells the search's agent once, and proposes it at once when the search is
 * already APPROVED.
 *
 * Returns null when the pair can't match (another city or listing type, a listing no longer published, a search without
 * an agent yet). A search without an agent keeps the listing as its `originListingId` (`MandateService.create`), and
 * `proposeOrigin` proposes it once the search is approved: the interest isn't lost.
 */
@Service
class MatchInterestService(
    private val matchService: MatchService,
    private val proposalService: MatchProposalService,
    private val agentService: AgentService,
    private val customerService: CustomerService,
    private val notifier: MatchInterestNotifier,
    private val logger: KVLogger,
) {
    /** Returns the match's id, or null when nothing could be matched (see the class KDoc). */
    @Transactional
    fun registerInterest(mandateId: Long, listingId: Long): Long? {
        val interest = matchService.ensureInterest(mandateId, listingId) ?: return null
        val match = interest.match
        logger.add("match_id", match.id)
        logger.add("match_created", interest.created)
        if (interest.created) {
            notify(match)
        }

        // An approved search has its agent: the customer's interest is proposed at once, not left to the agent nor to the
        // 24 h auto-proposal
        if (match.mandate.status == MandateStatus.APPROVED && match.status in MatchService.OPEN_STATUSES) {
            proposalService.propose(listOf(match.id!!))
            logger.add("proposed", true)
        }
        return match.id
    }

    // A failed notification never fails the interest: the match is stored, and the agent sees it on its search
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
