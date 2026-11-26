package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.mail.MatchNotificationMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

/**
 * Tells the agents of the current tenant about their new matches: the ACTIVE ones from their own inventory, not
 * notified yet, while the mandate is APPROVED and the listing PUBLISHED. Co-brokerage rows are left to the later
 * notification slice. One e-mail per agent ([MatchNotificationMailet]), then its matches are marked notified, each
 * agent on its own: a failed e-mail is logged, its matches left to the next run, and the other agents still sent.
 *
 * Each agent is sent in its own transaction, which reloads its matches: the mailet reads their lazy associations (ex:
 * the mandate's neighbourhoods), and the selection's session is closed by then (the job runs outside any transaction).
 */
@Service
class MatchNotificationService(
    private val dao: MatchRepository,
    private val agentService: AgentService,
    private val mailet: MatchNotificationMailet,
    private val tx: TransactionTemplate,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchNotificationService::class.java)
    }

    /** Returns the number of agents e-mailed. */
    fun notifyAgents(): Int {
        val pending = dao.findByStatusAndNotifiedAtIsNullAndMandateStatusAndListingStatusAndCoBrokerageFalse(
            MatchStatus.ACTIVE,
            MandateStatus.APPROVED,
            ListingStatus.PUBLISHED,
        )
        var count = 0
        pending.groupBy { match -> match.agentId }.forEach { (agentId, matches) ->
            try {
                val sent = tx.execute {
                    val agent = agentService.findByIdOrNull(agentId) ?: return@execute false
                    val ids = matches.map { match -> match.id!! }
                    mailet.send(agent, dao.findAllById(ids).sortedBy { match -> ids.indexOf(match.id) })
                    dao.markNotified(ids, Date(clock.millis()))
                    true
                } ?: false
                if (sent) {
                    count++
                }
            } catch (ex: Exception) {
                // An agent failing must not prevent the others to be notified
                LOGGER.warn("Agent#$agentId - ${matches.size} match(es) not notified", ex)
            }
        }
        return count
    }
}
