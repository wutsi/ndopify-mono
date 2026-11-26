package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.CoBrokerageMatchMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

/**
 * Tells the search agents of the current tenant about their new co-brokerage matches: the ACTIVE ones for another
 * agent's listing, not notified yet, while the mandate is APPROVED and the listing PUBLISHED. One e-mail per search
 * agent ([CoBrokerageMatchMailet]), then its matches are marked notified, each agent on its own: a failed e-mail is
 * logged, its matches left to the next run, and the other agents still sent.
 *
 * Each agent is sent in its own transaction, which reloads its matches: the mailet reads their lazy associations (ex:
 * the mandate's neighbourhoods), and the selection's session is closed by then (the job runs outside any transaction).
 *
 * The listing agents are not told here: a co-brokerage interest reaches them through the visits and the proposals
 * themselves, never as a digest of other agents' searches.
 */
@Service
class CoBrokerageNotificationService(
    private val dao: MatchRepository,
    private val agentService: AgentService,
    private val mailet: CoBrokerageMatchMailet,
    private val tx: TransactionTemplate,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CoBrokerageNotificationService::class.java)
    }

    /** Returns the number of agents e-mailed. */
    fun notifySearchAgents(): Int = notify(
        pending = dao.findCoBrokerageToNotify(
            MatchStatus.ACTIVE,
            MandateStatus.APPROVED,
            ListingStatus.PUBLISHED,
        ),
        label = "Agent",
        recipientId = { match -> match.agentId },
        send = { agent, matches -> mailet.send(agent, matches) },
        mark = { ids, now ->
            dao.markNotified(ids, now)
            Unit
        },
    )

    private fun notify(
        pending: List<MatchEntity>,
        label: String,
        recipientId: (MatchEntity) -> Long?,
        send: (AgentEntity, List<MatchEntity>) -> Unit,
        mark: (Collection<Long>, Date) -> Unit,
    ): Int {
        var count = 0
        val groups: Map<Long?, List<MatchEntity>> = pending.groupBy { match -> recipientId(match) }
        groups.forEach { (agentId, matches) ->
            if (agentId == null) {
                return@forEach
            }
            try {
                val sent = tx.execute {
                    val agent = agentService.findByIdOrNull(agentId) ?: return@execute false
                    val ids = matches.map { match -> match.id!! }
                    send(agent, dao.findAllById(ids).sortedBy { match -> ids.indexOf(match.id) })
                    mark(ids, Date(clock.millis()))
                    true
                } ?: false
                if (sent) {
                    count++
                }
            } catch (ex: Exception) {
                // An agent failing must not prevent the others to be notified
                LOGGER.warn("$label#$agentId - ${matches.size} match(es) not notified", ex)
            }
        }
        return count
    }
}
