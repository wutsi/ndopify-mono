package com.wutsi.ndopify.npc.ui.mandate

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdCustomerClient
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.ui.listing.ListingViewsTest
import com.wutsi.ndopify.npc.ui.match.MatchFixtures
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures
import org.junit.jupiter.api.Test
import org.springframework.web.client.ResourceAccessException
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDealCandidatesTest {
    private val matchClient = mock<NdMatchClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val customerClient = mock<NdCustomerClient>()
    private val listingClient = mock<NdListingClient>()
    private val candidates = MandateDealCandidates(matchClient, mandateClient, customerClient, listingClient)

    private val listingId = 501L
    private val agentId = ProfileFixtures.AGENT.id

    @Test
    fun `clients - each match with its mandate and customer, another agent's left unnamed`() {
        doReturn(ListingViewsTest.PROPOSALS)
            .whenever(matchClient).searchByListings(listOf(listingId), MatchRules.SENT)
        doReturn(ListingViewsTest.SEARCHES)
            .whenever(mandateClient).searchByIds(listOf(207L, 204L, 305L), agentId)
        doReturn(ListingViewsTest.CUSTOMERS).whenever(customerClient).search(listOf(14L, 17L))

        val result = candidates.clients(listingId, MatchRules.SENT, agentId)

        verify(matchClient).searchByListings(listOf(listingId), MatchRules.SENT)
        verify(mandateClient).searchByIds(listOf(207L, 204L, 305L), agentId)
        verify(customerClient).search(listOf(14L, 17L))
        // One candidate per match, in the matches' order (the refused one included: it is only counted later).
        assertEquals(listOf(9001L, 9002L, 9003L, 9004L), result.map { it.match.id })
        assertEquals(207L, result[0].mandate?.id)
        assertEquals("Carine", result[0].customer?.party?.firstName)
        assertEquals(204L, result[1].mandate?.id)
        assertEquals("Sandrine", result[1].customer?.party?.firstName)
        // 305 is another agent's mandate: unnamed, its customer never asked.
        assertNull(result[2].mandate)
        assertNull(result[2].customer)
        assertEquals(204L, result[3].mandate?.id)
        assertEquals("Sandrine", result[3].customer?.party?.firstName)
    }

    @Test
    fun `clients - a mandate returned with another agent is filtered out`() {
        doReturn(ListingViewsTest.PROPOSALS)
            .whenever(matchClient).searchByListings(listOf(listingId), MatchRules.SENT)
        val other = ListingViewsTest.SEARCHES[1].copy(agentId = 999)
        doReturn(listOf(ListingViewsTest.SEARCHES[0], other))
            .whenever(mandateClient).searchByIds(any(), any())
        doReturn(ListingViewsTest.CUSTOMERS.take(1)).whenever(customerClient).search(listOf(14L))

        val result = candidates.clients(listingId, MatchRules.SENT, agentId)

        verify(customerClient).search(listOf(14L))
        assertNull(result.first { it.match.id == 9001L }.mandate)
        assertEquals(204L, result.first { it.match.id == 9002L }.mandate?.id)
    }

    @Test
    fun `clients - customers unavailable - the rows keep their mandate without a name`() {
        doReturn(ListingViewsTest.PROPOSALS)
            .whenever(matchClient).searchByListings(listOf(listingId), MatchRules.SENT)
        doReturn(ListingViewsTest.SEARCHES)
            .whenever(mandateClient).searchByIds(listOf(207L, 204L, 305L), agentId)
        doThrow(ResourceAccessException("down")).whenever(customerClient).search(any())

        val result = candidates.clients(listingId, MatchRules.SENT, agentId)

        assertEquals(4, result.size)
        assertEquals(204L, result.first { it.match.id == 9002L }.mandate?.id)
        assertNull(result.first { it.match.id == 9002L }.customer)
    }

    @Test
    fun `listings - each match with its listing, a missing one left without`() {
        doReturn(listOf(MatchFixtures.MATCH_A, MatchFixtures.MATCH_B))
            .whenever(matchClient).search(listOf(204L), listOf(MatchStatus.ACCEPTED))
        doReturn(listOf(MatchFixtures.LISTING_A)).whenever(listingClient).searchByIds(listOf(3012L, 3041L))

        val result = candidates.listings(204L, listOf(MatchStatus.ACCEPTED))

        verify(matchClient).search(listOf(204L), listOf(MatchStatus.ACCEPTED))
        verify(listingClient).searchByIds(listOf(3012L, 3041L))
        assertEquals(MatchFixtures.LISTING_A, result.first { it.match.id == 9001L }.listing)
        assertNull(result.first { it.match.id == 9003L }.listing)
    }

    @Test
    fun `listings - no search when there are no matches`() {
        doReturn(emptyList<Match>()).whenever(matchClient).search(listOf(204L), listOf(MatchStatus.ACCEPTED))

        val result = candidates.listings(204L, listOf(MatchStatus.ACCEPTED))

        assertTrue(result.isEmpty())
        verify(listingClient, never()).searchByIds(any())
    }

    @Test
    fun `listings - listings unavailable - every match keeps its row without a listing`() {
        doReturn(listOf(MatchFixtures.MATCH_A, MatchFixtures.MATCH_B))
            .whenever(matchClient).search(listOf(204L), listOf(MatchStatus.ACCEPTED))
        doThrow(ResourceAccessException("down")).whenever(listingClient).searchByIds(any())

        val result = candidates.listings(204L, listOf(MatchStatus.ACCEPTED))

        assertEquals(2, result.size)
        assertTrue(result.all { it.listing == null })
    }
}
