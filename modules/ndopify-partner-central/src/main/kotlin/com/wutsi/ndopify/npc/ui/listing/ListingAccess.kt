package com.wutsi.ndopify.npc.ui.listing

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClientException

/**
 * The signed-in agent's listing, for every listing page. ndopify-server doesn't check who reads or writes a listing,
 * so the pages do: another agent's listing (`Listing.agentId`) is [Locked], like one that isn't found or has a status
 * the pages don't know; one ndopify-server can't return (5xx, network) is [Unavailable], since retrying may help.
 */
class ListingAccess(private val listingClient: NdListingClient) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingAccess::class.java)
    }

    sealed interface Result

    data class Found(val listing: Listing) : Result

    data object Locked : Result

    data object Unavailable : Result

    fun load(id: Long, agent: Agent): Result =
        try {
            val listing = listingClient.get(id)
            if (listing.agentId == agent.id && listing.status != ListingStatus.UNKNOWN) Found(listing) else Locked
        } catch (ex: HttpClientErrorException) {
            LOGGER.warn("No listing $id: ${ex.statusCode}")
            Locked
        } catch (ex: RestClientException) {
            LOGGER.error("No listing $id", ex)
            Unavailable
        }
}
