package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.ui.match.MatchRules
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

/**
 * Who may open a listing page: its own agent ([Owner], every page as before), or a co-broker whose visible search
 * holds a co-brokerage match on this published listing ([CoBroker]). ndopify-server doesn't check who reads a listing,
 * so the BFF owns the decision: anything else is [Locked], and a failure to read (5xx, network) is [Unavailable].
 *
 * The co-broker's match and its mandate are loaded one by one: a match of another listing, not a co-brokerage one, or
 * one the client dismissed, and another agent's mandate, are all [Locked]. The owner's mandate and customer are never
 * loaded here.
 */
class PublishedListingAccess(
    private val listingClient: NdListingClient,
    private val matchClient: NdMatchClient,
    private val mandateClient: NdMandateClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(PublishedListingAccess::class.java)
    }

    sealed interface Result

    data class Owner(val listing: Listing) : Result

    data class CoBroker(val listing: Listing, val match: Match) : Result

    data object Locked : Result

    data object Unavailable : Result

    fun load(id: Long, agent: Agent, matchId: Long?): Result {
        if (id <= 0) return Locked

        val listing = try {
            listingClient.get(id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No listing $id: ${ex.statusCode}")
            return Locked
        } catch (ex: RestClientException) {
            LOGGER.error("No listing $id", ex)
            return Unavailable
        }

        if (listing.status == ListingStatus.UNKNOWN) return Locked
        if (listing.agentId == agent.id) return Owner(listing)
        if (matchId == null || listing.status != ListingStatus.PUBLISHED) return Locked

        val match = try {
            matchClient.get(matchId)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No match $matchId: ${ex.statusCode}")
            return Locked
        } catch (ex: RestClientException) {
            LOGGER.error("No match $matchId", ex)
            return Unavailable
        }
        if (match.listingId != listing.id || !match.coBrokerage || match.status !in MatchRules.ORDER) return Locked

        val mandate = try {
            mandateClient.get(match.mandateId)
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No mandate ${match.mandateId}: ${ex.statusCode}")
            return Locked
        } catch (ex: RestClientException) {
            LOGGER.error("No mandate ${match.mandateId}", ex)
            return Unavailable
        }
        if (mandate.agentId != agent.id || mandate.mandateType != MandateType.SEARCH) return Locked

        return CoBroker(listing, match)
    }
}
