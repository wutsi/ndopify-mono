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
    fun `co-broker - a visible match of one of the agent's searches`() {
        val other = listing.copy(agentId = 999)
        stub(listing = other)

        assertEquals(PublishedListingAccess.CoBroker(other, match), access.load(501, AGENT, 9009))
    }

    @Test
    fun `no match id - locked`() {
        stub(listing = listing.copy(agentId = 999))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, null))
    }

    @Test
    fun `unknown status - locked`() {
        stub(listing = listing.copy(status = ListingStatus.UNKNOWN))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `not a co-brokerage match - locked`() {
        stub(listing = listing.copy(agentId = 999), match = match.copy(coBrokerage = false))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `match of another listing - locked`() {
        stub(listing = listing.copy(agentId = 999), match = match.copy(listingId = 502))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `dismissed match - locked`() {
        stub(listing = listing.copy(agentId = 999), match = match.copy(status = MatchStatus.DISMISSED))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `rejected match - locked`() {
        stub(listing = listing.copy(agentId = 999), match = match.copy(status = MatchStatus.REJECTED))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `another agent's search - locked`() {
        stub(listing = listing.copy(agentId = 999), search = search.copy(agentId = 999))

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `unpublished listing for a co-broker - locked`() {
        stub(listing = listing.copy(agentId = 999, status = ListingStatus.RENTED))

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

    @Test
    fun `match not found - locked`() {
        stub(listing = listing.copy(agentId = 999))
        doThrow(notFound()).whenever(matchClient).get(9009)

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `match failure - unavailable`() {
        stub(listing = listing.copy(agentId = 999))
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(matchClient).get(9009)

        assertEquals(PublishedListingAccess.Unavailable, access.load(501, AGENT, 9009))
    }

    @Test
    fun `mandate not found - locked`() {
        stub(listing = listing.copy(agentId = 999))
        doThrow(notFound()).whenever(mandateClient).get(204)

        assertEquals(PublishedListingAccess.Locked, access.load(501, AGENT, 9009))
    }

    @Test
    fun `mandate failure - unavailable`() {
        stub(listing = listing.copy(agentId = 999))
        doThrow(HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).whenever(mandateClient).get(204)

        assertEquals(PublishedListingAccess.Unavailable, access.load(501, AGENT, 9009))
    }
}
