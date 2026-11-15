package com.wutsi.ndopify.npc.ui.visit

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.npc.client.ndopify.NdAgentClient
import com.wutsi.ndopify.npc.config.Parallel
import com.wutsi.ndopify.visit.dto.CoBrokerageVisitSummary
import com.wutsi.ndopify.visit.dto.VisitSummary
import org.slf4j.LoggerFactory
import org.springframework.web.client.RestClientException

/**
 * The other agents a page names on a visit, by id, one read per agent on a virtual thread: the listing agent of a
 * co-brokerage visit on the Espace agent and the mandate page, and the organizer of a hosted visit. They are
 * secondary: an agent that can't be read is left out, so the view keeps its "un autre agent" wording rather than
 * failing the page.
 */
object VisitAgents {
    private val LOGGER = LoggerFactory.getLogger(VisitAgents::class.java)

    /**
     * The listing agents of the agent's co-brokerage [visits], by id: the visits whose listing isn't [agent]'s own
     * inventory. None without [listings] or for an own-inventory visit.
     */
    fun ofVisits(
        client: NdAgentClient,
        parallel: Parallel,
        agent: Agent,
        visits: List<VisitSummary>?,
        listings: Map<Long, ListingSummary>?,
    ): Map<Long, Agent> =
        find(
            client,
            parallel,
            visits.orEmpty()
                .mapNotNull { listings?.get(it.listingId) }
                .filter { it.agentId > 0 && it.agentId != agent.id }
                .map { it.agentId },
            "agent for a visit",
        )

    /** The organizers of the co-brokerage [visits], by id; an unreadable one is left out. */
    fun ofCoBrokerage(
        client: NdAgentClient,
        parallel: Parallel,
        visits: List<CoBrokerageVisitSummary>?,
    ): Map<Long, Agent> =
        find(client, parallel, visits.orEmpty().map { it.agentId }, "agent for a co-brokerage visit")

    private fun find(
        client: NdAgentClient,
        parallel: Parallel,
        agentIds: Collection<Long>,
        what: String,
    ): Map<Long, Agent> {
        val calls = agentIds.filter { it > 0 }.distinct().associateWith { id ->
            parallel.async {
                try {
                    client.get(id)
                } catch (ex: RestClientException) {
                    LOGGER.warn("No $what $id: ${ex.message}")
                    null
                }
            }
        }
        return calls.mapNotNull { (id, call) ->
            val found = try {
                call.join()
            } catch (ex: Exception) {
                null
            }
            found?.let { id to it }
        }.toMap()
    }
}
