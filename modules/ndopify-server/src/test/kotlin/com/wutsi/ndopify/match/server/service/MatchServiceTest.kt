package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationRequest
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchServiceTest {
    companion object {
        const val TENANT_ID = 1L
        const val AGENT_ID = 10L
        const val CITY_ID = 100L
        const val MANDATE_ID = 1L
        const val LISTING_ID = 11L
        const val N1 = 1001L
        const val N2 = 1002L
    }

    private val matchDao = mock<MatchRepository>()
    private val mandateDao = mock<MandateRepository>()
    private val listingDao = mock<ListingRepository>()
    private val matcher = mock<Matcher>()
    private val locationService = mock<LocationService>()
    private val tenantService = mock<TenantService>()

    // 2026-10-31 23:30 UTC: already 2026-11-01 in Douala (UTC+1)
    private val clock = Clock.fixed(Instant.parse("2026-10-31T23:30:00Z"), ZoneId.of("UTC"))
    private val today = LocalDate.of(2026, 11, 1)

    private val service = MatchService(
        matchDao,
        mandateDao,
        listingDao,
        matcher,
        locationService,
        tenantService,
        clock,
        DefaultKVLogger(),
        0.5,
        3.0,
        20,
    )

    private val search = MandateEntity(
        id = MANDATE_ID,
        tenantId = TENANT_ID,
        customerId = 500L,
        agentId = AGENT_ID,
        mandateType = MandateType.SEARCH,
        status = MandateStatus.APPROVED,
        cityId = CITY_ID,
        neighborhoodIds = listOf(N1),
    )
    private val listing1 = ListingEntity(
        id = LISTING_ID,
        tenantId = TENANT_ID,
        agent = AgentEntity(id = AGENT_ID),
        status = ListingStatus.PUBLISHED,
        price = 240_000,
    )
    private val listing2 = listing1.copy(id = 12L, price = 200_000)

    private fun score(total: Double): MatchScore =
        // furniture carries the whole total, so that MatchScore.total == [total] for the test
        MatchScore(0.0, 0.0, 0.0, 0.0, 0.0, total / Matcher.WEIGHT_FURNITURE)

    @BeforeTest
    fun setUp() {
        doReturn(TenantEntity(id = TENANT_ID, timeZone = "Africa/Douala")).whenever(tenantService).findById(TENANT_ID)
        doReturn(search).whenever(mandateDao).findWithNeighborhoodsById(MANDATE_ID)
        doReturn(listOf(LocationEntity(id = N2) to 1.5)).whenever(locationService).searchNearest(any())
        doAnswer { it.arguments[0] }.whenever(matchDao).save(any<MatchEntity>())
    }

    @Test
    fun `matchMandate creates the matches`() {
        doReturn(listOf(listing1, listing2)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(score(0.9)).whenever(matcher).score(eq(search), eq(listing1), any(), eq(today))
        doReturn(score(0.6)).whenever(matcher).score(eq(search), eq(listing2), any(), eq(today))

        val count = service.matchMandate(MANDATE_ID)

        assertEquals(2, count)
        val captor = argumentCaptor<MatchEntity>()
        verify(matchDao, times(2)).save(captor.capture())
        val first = captor.firstValue
        assertEquals(null, first.id)
        assertEquals(search, first.mandate)
        assertEquals(listing1, first.listing)
        assertEquals(AGENT_ID, first.agentId)
        assertEquals(MatchStatus.ACTIVE, first.status)
        assertEquals(0.9, first.score, 0.000001)
        assertEquals(18.0, first.furnitureScore, 0.000001)
        assertEquals(0.0, first.neighborhoodScore)
        assertEquals(240_000L, first.listingPrice)
        assertEquals(listing2, captor.secondValue.listing)
        assertEquals(200_000L, captor.secondValue.listingPrice)
        assertEquals(0.6, captor.secondValue.score, 0.000001)
    }

    @Test
    fun `matchMandate skips scores below min-score`() {
        doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(score(0.4)).whenever(matcher).score(any(), any(), any(), any())

        assertEquals(0, service.matchMandate(MANDATE_ID))
        verify(matchDao, never()).save(any<MatchEntity>())
        verify(matchDao, never()).delete(any<MatchEntity>())
    }

    @Test
    fun `matchMandate updates an ACTIVE match`() {
        val existing = MatchEntity(
            id = 7L,
            mandate = search,
            listing = listing1,
            status = MatchStatus.ACTIVE,
            score = 0.7,
            listingPrice = 260_000,
            version = 3L,
        )
        doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(existing).whenever(matchDao).findByMandateAndListing(search, listing1)
        doReturn(score(0.8)).whenever(matcher).score(any(), any(), any(), any())

        assertEquals(1, service.matchMandate(MANDATE_ID))

        val captor = argumentCaptor<MatchEntity>()
        verify(matchDao).save(captor.capture())
        assertEquals(7L, captor.firstValue.id)
        assertEquals(3L, captor.firstValue.version)
        assertEquals(MatchStatus.ACTIVE, captor.firstValue.status)
        assertEquals(0.8, captor.firstValue.score, 0.000001)
        assertEquals(240_000L, captor.firstValue.listingPrice) // The price it's scored at now
        assertEquals(existing.createdAt, captor.firstValue.createdAt)
    }

    @Test
    fun `matchMandate deletes an ACTIVE match no longer matching`() {
        val existing = MatchEntity(id = 7L, mandate = search, listing = listing1, status = MatchStatus.ACTIVE)
        doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(existing).whenever(matchDao).findByMandateAndListing(search, listing1)
        doReturn(null).whenever(matcher).score(any(), any(), any(), any())

        assertEquals(0, service.matchMandate(MANDATE_ID))
        verify(matchDao).delete(existing)
        verify(matchDao, never()).save(any<MatchEntity>())
    }

    @Test
    fun `matchMandate leaves a DISMISSED match`() {
        val existing = MatchEntity(id = 7L, mandate = search, listing = listing1, status = MatchStatus.DISMISSED)
        doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(existing).whenever(matchDao).findByMandateAndListing(search, listing1)
        doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())

        assertEquals(0, service.matchMandate(MANDATE_ID))
        verify(matchDao, never()).save(any<MatchEntity>())
        verify(matchDao, never()).delete(any<MatchEntity>())
    }

    @Test
    fun `matchMandate ignores a LISTING mandate`() {
        assertIgnored(search.copy(mandateType = MandateType.LISTING))
    }

    @Test
    fun `matchMandate ignores a mandate not APPROVED`() {
        assertIgnored(search.copy(status = MandateStatus.CLOSED))
    }

    @Test
    fun `matchMandate ignores a mandate without agent`() {
        assertIgnored(search.copy(agentId = null))
    }

    private fun assertIgnored(mandate: MandateEntity) {
        doReturn(mandate).whenever(mandateDao).findWithNeighborhoodsById(MANDATE_ID)

        assertEquals(0, service.matchMandate(MANDATE_ID))
        verify(listingDao, never()).findByAgentIdAndStatus(any(), any())
        verify(matchDao, never()).save(any<MatchEntity>())
    }

    @Test
    fun `matchMandate with empty inventory`() {
        doReturn(emptyList<ListingEntity>()).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)

        assertEquals(0, service.matchMandate(MANDATE_ID))
        verify(matchDao, never()).save(any<MatchEntity>())
    }

    @Test
    fun `matchMandate of an unknown mandate`() {
        doReturn(null).whenever(mandateDao).findWithNeighborhoodsById(MANDATE_ID)

        assertFailsWith<NotFoundException> { service.matchMandate(MANDATE_ID) }
    }

    @Test
    fun `a conflict skips the pair`() {
        doReturn(listOf(listing1, listing2)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())
        doThrow(ObjectOptimisticLockingFailureException(MatchEntity::class.java, 7L))
            .doAnswer { it.arguments[0] }
            .whenever(matchDao).save(any<MatchEntity>())

        assertEquals(1, service.matchMandate(MANDATE_ID))

        val captor = argumentCaptor<MatchEntity>()
        verify(matchDao, times(2)).save(captor.capture())
        assertEquals(listing2, captor.secondValue.listing)
    }

    @Test
    fun `a duplicate insert skips the pair`() {
        // Another run (ex: matchListing while matchMandate runs) inserted the same pair meanwhile
        doReturn(listOf(listing1, listing2)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())
        doThrow(DataIntegrityViolationException("Duplicate entry"))
            .doAnswer { it.arguments[0] }
            .whenever(matchDao).save(any<MatchEntity>())

        assertEquals(1, service.matchMandate(MANDATE_ID))

        val captor = argumentCaptor<MatchEntity>()
        verify(matchDao, times(2)).save(captor.capture())
        assertEquals(listing2, captor.secondValue.listing)
    }

    @Test
    fun distances() {
        doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())

        service.matchMandate(MANDATE_ID)

        verify(locationService).searchNearest(
            SearchNearestLocationRequest(
                ids = listOf(N1),
                type = LocationType.NEIGHBORHOOD,
                parentId = CITY_ID,
                radius = 3.0,
                limit = 20,
            )
        )
        verify(matcher).score(search, listing1, mapOf(N2 to 1.5, N1 to 0.0), today)
    }

    @Test
    fun `no distances without neighborhood`() {
        val mandate = search.copy(neighborhoodIds = emptyList())
        doReturn(mandate).whenever(mandateDao).findWithNeighborhoodsById(MANDATE_ID)
        doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())

        service.matchMandate(MANDATE_ID)

        verify(locationService, never()).searchNearest(any())
        verify(matcher).score(mandate, listing1, emptyMap(), today)
    }

    @Test
    fun `matchListing matches the agent's open searches`() {
        val search2 = search.copy(id = 2L)
        doReturn(Optional.of(listing1)).whenever(listingDao).findById(LISTING_ID)
        doReturn(listOf(search, search2)).whenever(mandateDao)
            .findByAgentIdAndMandateTypeAndStatus(AGENT_ID, MandateType.SEARCH, MandateStatus.APPROVED)
        doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())

        assertEquals(2, service.matchListing(LISTING_ID))

        verify(matcher).score(eq(search), eq(listing1), any(), eq(today))
        verify(matcher).score(eq(search2), eq(listing1), any(), eq(today))
    }

    @Test
    fun `matchListing ignores a listing not PUBLISHED`() {
        doReturn(Optional.of(listing1.copy(status = ListingStatus.DRAFT))).whenever(listingDao).findById(LISTING_ID)

        assertEquals(0, service.matchListing(LISTING_ID))
        verify(mandateDao, never()).findByAgentIdAndMandateTypeAndStatus(any(), any(), any())
    }

    @Test
    fun `matchListing of a listing whose agent has no open search`() {
        doReturn(Optional.of(listing1)).whenever(listingDao).findById(LISTING_ID)
        doReturn(emptyList<MandateEntity>()).whenever(mandateDao)
            .findByAgentIdAndMandateTypeAndStatus(AGENT_ID, MandateType.SEARCH, MandateStatus.APPROVED)

        assertEquals(0, service.matchListing(LISTING_ID))
        verify(matcher, never()).score(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `matchListing of an unknown listing`() {
        doReturn(Optional.empty<ListingEntity>()).whenever(listingDao).findById(LISTING_ID)

        assertFailsWith<NotFoundException> { service.matchListing(LISTING_ID) }
    }
}
