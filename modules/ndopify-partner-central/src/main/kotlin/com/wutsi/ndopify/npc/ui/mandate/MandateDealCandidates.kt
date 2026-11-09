package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.customer.dto.CustomerSummary
import com.wutsi.ndopify.listing.dto.ListingSummary
import com.wutsi.ndopify.mandate.dto.MandateSummary
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException

/** A listing's sent match and, when its mandate is the agent's, that mandate and its customer. */
data class ClientCandidate(
    val match: Match,
    val mandate: MandateSummary?, // Null when the match's mandate isn't the agent's.
    val customer: CustomerSummary?, // Null when the customer can't be read.
)

/** A search's match and its listing, null when it can't be read or isn't returned. */
data class ListingCandidate(
    val match: Match,
    val listing: ListingSummary?,
)

/**
 * The deal candidates behind a mandate's matches, shared by the mandate page and the closing wizard: [clients] turns a
 * listing's matches into the clients (their mandates and customers) the agent can deal with, [listings] turns a search's
 * matches into the listings it was matched to. HTTP concerns only, no formatting or labels.
 *
 * The primary reads (a listing's matches, their mandates; a search's matches) propagate their failure: the caller
 * decides what to show (the mandate page leaves the section out). A secondary read (customers, listings) failing only
 * leaves that field null on its row, never the whole list — the mandate page shows the mandate's number for a nameless
 * client, and no listing for a match whose listing is gone.
 */
@Service
class MandateDealCandidates(
    private val matchClient: NdMatchClient,
    private val mandateClient: NdMandateClient,
    private val customerClient: NdCustomerClient,
    private val listingClient: NdListingClient,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDealCandidates::class.java)
    }

    /**
     * One candidate per match of [listingId], in the matches' order: the agent's own mandates among the matches' carry
     * their customer; a match of another agent's mandate is left with no mandate, so its customer is never asked.
     */
    fun clients(listingId: Long, statuses: List<MatchStatus>, agentId: Long): List<ClientCandidate> {
        val matches = matchClient.searchByListings(listOf(listingId), statuses)
        val mandates = mandateClient.searchByIds(matches.map { it.mandateId }.distinct(), agentId)
            .filter { it.agentId == agentId }
            .associateBy { it.id }
        val customers = customersById(mandates.values.map { it.customerId }.distinct())
        return matches.map { match ->
            val mandate = mandates[match.mandateId]
            ClientCandidate(match, mandate, mandate?.let { customers[it.customerId] })
        }
    }

    /** One candidate per match of [mandateId], in the matches' order, each with its listing when it can be had. */
    fun listings(mandateId: Long, statuses: List<MatchStatus>): List<ListingCandidate> {
        val matches = matchClient.search(listOf(mandateId), statuses)
        val listings = if (matches.isEmpty()) {
            emptyMap()
        } else {
            listingsById(matches.map { it.listingId }.distinct())
        }
        return matches.map { match -> ListingCandidate(match, listings[match.listingId]) }
    }

    private fun customersById(ids: List<Long>): Map<Long, CustomerSummary> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            try {
                customerClient.search(ids).mapNotNull { customer -> customer.id?.let { it to customer } }.toMap()
            } catch (ex: RestClientException) {
                LOGGER.warn("No customers $ids: ${ex.message}")
                emptyMap()
            }
        }

    private fun listingsById(ids: List<Long>): Map<Long, ListingSummary> =
        if (ids.isEmpty()) {
            emptyMap()
        } else {
            try {
                listingClient.searchByIds(ids).associateBy { it.id }
            } catch (ex: RestClientException) {
                LOGGER.warn("No listings $ids: ${ex.message}")
                emptyMap()
            }
        }
}
