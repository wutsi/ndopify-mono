package com.wutsi.ndopify.npc.ui.listing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.Listing
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.client.ndopify.NdListingClient
import com.wutsi.ndopify.npc.client.ndopify.NdMandateClient
import com.wutsi.ndopify.npc.client.ndopify.NdMatchClient
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.test.assertEquals

class PublishedListingAccessTest {
    private val listingClient = mock<NdListingClient>()
    private val matchClient = mock<NdMatchClient>()
    private val mandateClient = mock<NdMandateClient>()
    private val access = PublishedListingAccess(listingClient, matchClient, mandateClient)

    private val listing = ListingViewsTest.COMPLETE.copy(status = ListingStatus.PUBLISHED)
    private val match = Match(id = 9009, mandateId = 204, listingId = 501, coBrokerage = true, status = MatchStatus.ACTIVE)
    private val search = Mandate(id = 204, agentId = AGENT.id, mandateType = MandateType.SEARCH, status = MandateStatus.APPROVED)

    private fun stub(
        listing: Listing = this.listing,
        match: Match = this.match,
        search: Mandate = this.search,
    ) {
        doReturn(listing).whenever(listingClient).get(501)
        doReturn(match).whenever(matchClient).get(match.id)
        doReturn(search).whenever(mandateClient).get(match.mandateId)
    }

    private fun notFound() = HttpClientErrorException(HttpStatus.NOT_FOUND)

    @Test
    fun owner() {
        stub()

        assertEquals(PublishedListingAccess.Owner(listing), access.load(501, AGENT, null))
        verify(matchClient, never()).get(any())
    }

    @Test
    fun `another agent's published listing - read-only without a match`() {
        stub(listing = listing.copy(agentId = 999))

        assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, null))
        verify(matchClient, never()).get(any())
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `another agent's rented and sold listings - read-only`() {
        for (status in listOf(ListingStatus.RENTED, ListingStatus.SOLD)) {
            val other = listing.copy(agentId = 999, status = status)
            doReturn(other).whenever(listingClient).get(501)

            assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, null))
        }
        verify(matchClient, never()).get(any())
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `another agent's non-public listing - locked`() {
        val statuses = listOf(
            ListingStatus.DRAFT,
            ListingStatus.PENDING_VERIFICATION,
            ListingStatus.UNDER_VERIFICATION,
            ListingStatus.VERIFIED,
            ListingStatus.PUBLISHING,
        )
        for (status in statuses) {
            val other = listing.copy(agentId = 999, status = status)
            doReturn(other).whenever(listingClient).get(501)

            assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, null))
        }
        verify(matchClient, never()).get(any())
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `a valid co-brokerage match - read-only with its context`() {
        stub(listing = listing.copy(agentId = 999))

        assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999), match), access.load(501, AGENT, 9009))
    }

    @Test
    fun `a stale or bogus match - ignored, still read-only`() {
        val cases = listOf(
            match.copy(coBrokerage = false),
            match.copy(listingId = 502),
            match.copy(status = MatchStatus.DISMISSED),
            match.copy(status = MatchStatus.REJECTED),
        )
        for (candidate in cases) {
            stub(listing = listing.copy(agentId = 999), match = candidate)

            assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, 9009))
        }
    }

    @Test
    fun `a match of another agent or a non-search mandate - ignored`() {
        stub(listing = listing.copy(agentId = 999), search = search.copy(agentId = 999))
        assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, 9009))

        stub(listing = listing.copy(agentId = 999), search = search.copy(mandateType = MandateType.LISTING))
        assertEquals(PublishedListingAccess.ReadOnly(listing.copy(agentId = 999)), access.load(501, AGENT, 9009))
    }

    @Test
    fun `a match or mandate read failure - ignored, still read-only`() {
        val other = listing.copy(agentId = 999)
        stub(listing = other)

        doThrow(notFound()).whenever(matchClient).get(9009)
        assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(matchClient).get(9009)
        assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

        doThrow(ResourceAccessException("down")).whenever(matchClient).get(9009)
        assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

        stub(listing = other)
        doThrow(notFound()).whenever(mandateClient).get(204)
        assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))

        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).get(204)
        assertEquals(PublishedListingAccess.ReadOnly(other), access.load(501, AGENT, 9009))
    }

    @Test
    fun `a valid match on a rented listing - the context stays`() {
        val rented = listing.copy(agentId = 999, status = ListingStatus.RENTED)
        stub(listing = rented)

        assertEquals(PublishedListingAccess.ReadOnly(rented, match), access.load(501, AGENT, 9009))
    }

    @Test
    fun `a non-positive id - locked without a read`() {
        assertEquals(PublishedListingAccess.Locked, access.load(0, AGENT, 9009))
        verify(listingClient, never()).get(any())
    }

    @Test
    fun `unknown status - locked`() {
        stub(listing = listing.copy(status = ListingStatus.UNKNOWN))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `listing not found - locked`() {
        doThrow(notFound()).whenever(listingClient).get(501)

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `listing failure - unavailable`() {
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(listingClient).get(501)

        assertEquals(PublishedListingAccess.Unavailable, access.load(501, AGENT, 9009))
    }

    @Test
    fun `listing network failure - unavailable`() {
        doThrow(ResourceAccessException("down")).whenever(listingClient).get(501)

        assertEquals(PublishedListingAccess.Unavailable, access.load(501, AGENT, 9009))
    }
}
