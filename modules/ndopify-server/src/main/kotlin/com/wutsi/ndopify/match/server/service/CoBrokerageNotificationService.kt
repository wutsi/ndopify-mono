package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.CoBrokerageInterestMailet
import com.wutsi.ndopify.match.server.mail.CoBrokerageMatchMailet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

/**
 * Tells the search agents of the current tenant about their new co-brokerage matches: the ACTIVE ones for another
 * agent's listing, not notified yet, while the mandate is APPROVED and the listing PUBLISHED. One e-mail per search
 * agent ([CoBrokerageMatchMailet]), then its matches are marked notified, each agent on its own: a failed e-mail is
 * logged, its matches left to the next run, and the other agents still sent.
 *
 * The reverse: tells the listing agents that other agents' searches showed interest in their listings
 * ([CoBrokerageInterestMailet]) — the INTEREST statuses for an APPROVED mandate on a PUBLISHED listing, not told yet,
 * grouped by listing agent, each on its own for the same reason.
 */
@Service
class CoBrokerageNotificationService(
    private val dao: MatchRepository,
    private val agentService: AgentService,
    private val mailet: CoBrokerageMatchMailet,
    private val interestMailet: CoBrokerageInterestMailet,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(CoBrokerageNotificationService::class.java)

        // A search agent's interest worth telling the listing agent about
        private val INTEREST_STATUSES = setOf(
            MatchStatus.SHORTLISTED,
            MatchStatus.PROPOSED,
            MatchStatus.ACCEPTED,
            MatchStatus.EXPIRED,
        )
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

    /** Returns the number of listing agents e-mailed. */
    fun notifyListingAgents(): Int {
        val pending = dao.findCoBrokerageInterestToNotify(
            INTEREST_STATUSES,
            MandateStatus.APPROVED,
            ListingStatus.PUBLISHED,
        )

        // The interest mailet drops rows whose search agent no longer exists: never mark those as told
        val knownSearchAgents = pending.mapNotNull { match -> match.mandate.agentId }
            .distinct()
            .filter { agentId -> agentService.findByIdOrNull(agentId) != null }
            .toSet()
        val notifiable = pending.filter { match -> match.mandate.agentId in knownSearchAgents }

        return notify(
            pending = notifiable,
            label = "Listing agent",
            recipientId = { match -> match.listing.agent.id },
            send = { agent, matches -> interestMailet.send(agent, matches) },
            mark = { ids, now ->
                dao.markListingAgentNotified(ids, now)
                Unit
            },
        )
    }

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
                val agent = agentService.findByIdOrNull(agentId) ?: return@forEach
                send(agent, matches)
                mark(matches.map { match -> match.id!! }, Date(clock.millis()))
                count++
            } catch (ex: Exception) {
                // An agent failing must not prevent the others to be notified
                LOGGER.warn("$label#$agentId - ${matches.size} match(es) not notified", ex)
            }
        }
        return count
    }
}
