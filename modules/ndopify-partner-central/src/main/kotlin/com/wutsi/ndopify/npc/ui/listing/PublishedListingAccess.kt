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
 * Who may open a listing page: its own agent ([Owner], every page as before), or any agent on a listing that keeps its
 * public page ([ReadOnly], `ListingRules.PUBLIC_STATUSES`). ndopify-server doesn't check who reads a listing, so the
 * BFF owns the decision: anything else is [Locked], and a failure to read (5xx, network) is [Unavailable].
 *
 * A `?match=` is only a context hint: [matchId] is resolved best-effort to the co-brokerage match of one of the
 * agent's searches ([ReadOnly.match]), and a stale or bogus one — another listing's, not a co-brokerage one, one the
 * client dismissed, another agent's mandate, a non-search mandate, or a failed read — is ignored: the read-only page
 * still renders. The owner's mandate and customer are never loaded here.
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

    data class ReadOnly(val listing: Listing, val match: Match? = null) : Result

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
        if (listing.status !in ListingRules.PUBLIC_STATUSES) return Locked
        if (matchId == null) return ReadOnly(listing)

        return ReadOnly(listing, visibleMatch(matchId, listing, agent))
    }

    /** The match [matchId] when it is one of [agent]'s visible co-brokerage matches on [listing]; null otherwise. */
    private fun visibleMatch(matchId: Long, listing: Listing, agent: Agent): Match? {
        val match = try {
            matchClient.get(matchId)
        } catch (ex: RestClientException) {
            LOGGER.warn("No match $matchId: ${ex.message}")
            return null
        }
        if (match.listingId != listing.id || !match.coBrokerage || match.status !in MatchRules.ORDER) return null

        val mandate = try {
            mandateClient.get(match.mandateId)
        } catch (ex: RestClientException) {
            LOGGER.warn("No mandate ${match.mandateId}: ${ex.message}")
            return null
        }
        if (mandate.agentId != agent.id || mandate.mandateType != MandateType.SEARCH) return null

        return match
    }
}
