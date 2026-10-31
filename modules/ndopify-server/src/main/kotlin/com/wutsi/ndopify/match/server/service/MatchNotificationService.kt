package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.mail.MatchNotificationMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

/**
 * Tells the agents of the current tenant about their new matches: the ACTIVE ones not notified yet, while the mandate
 * is APPROVED and the listing PUBLISHED. One e-mail per agent ([MatchNotificationMailet]), then its matches are marked
 * notified, each agent on its own: a failed e-mail is logged, its matches left to the next run, and the other agents
 * still sent.
 */
@Service
class MatchNotificationService(
    private val dao: MatchRepository,
    private val agentService: AgentService,
    private val mailet: MatchNotificationMailet,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchNotificationService::class.java)
    }

    /** Returns the number of agents e-mailed. */
    fun notifyAgents(): Int {
        val pending = dao.findByStatusAndNotifiedAtIsNullAndMandateStatusAndListingStatus(
            MatchStatus.ACTIVE,
            MandateStatus.APPROVED,
            ListingStatus.PUBLISHED,
        )
        var count = 0
        pending.groupBy { match -> match.agentId }.forEach { (agentId, matches) ->
            try {
                val agent = agentService.findByIdOrNull(agentId) ?: return@forEach
                mailet.send(agent, matches)
                dao.markNotified(matches.map { match -> match.id!! }, Date(clock.millis()))
                count++
            } catch (ex: Exception) {
                // An agent failing must not prevent the others to be notified
                LOGGER.warn("Agent#$agentId - ${matches.size} match(es) not notified", ex)
            }
        }
        return count
    }
}
