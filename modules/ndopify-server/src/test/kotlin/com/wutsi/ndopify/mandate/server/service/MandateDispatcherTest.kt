package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AgentAssignmentCount
import com.wutsi.ndopify.mandate.server.dao.AgentMandateCount
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.AssignmentCreatedEvent
import com.wutsi.ndopify.mandate.server.event.MandateManualAssignmentEvent
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationRequest
import com.wutsi.ndopify.refdata.server.domain.LocationEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MandateDispatcherTest {
    companion object {
        const val TENANT_ID = 1L
        const val CITY_ID = 2370201L
        const val MANDATE_ID = 1L
        const val CUSTOMER_PARTY_ID = 100L

        // Neighbourhoods of the city: N1, N2 picked by the customer; N3 at 1.5 km, N4 at 0.6 km of them; N9 far
        const val N1 = 11L
        const val N2 = 12L
        const val N3 = 13L
        const val N4 = 14L
        const val N9 = 19L

        // The score of an agent covering all the mandate's neighbourhoods, never offered, unrated, new and idle:
        // 0.35·1 + 0.25·1 + 0.15·0.5 + 0.10·0.5 + 0.15·1
        const val PERFECT_NEWCOMER = 0.875
        const val DELTA = 0.000001

        val DOUALA: ZoneId = ZoneId.of("Africa/Douala")
        const val HOUR = 3_600_000L
        const val DAY = 24 * HOUR
    }

    private val assignmentDao = mock<AssignmentRepository>()
    private val mandateDao = mock<MandateRepository>()
    private val agentService = mock<AgentService>()
    private val customerService = mock<CustomerService>()
    private val locationService = mock<LocationService>()
    private val tenantService = mock<TenantService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = mock<Clock>()

    // Noon in Douala: daytime
    private var now = time("2026-10-15T12:00")
    private var mandate = MandateEntity(
        id = MANDATE_ID,
        tenantId = TENANT_ID,
        customerId = 10L,
        cityId = CITY_ID,
        neighborhoodIds = listOf(N1),
        status = MandateStatus.PENDING_ASSIGNMENT,
        createdAt = Date(now - HOUR),
    )
    private val customer = CustomerEntity(id = 10L, party = PartyEntity(id = CUSTOMER_PARTY_ID))
    private var inCity = listOf<AgentEntity>() // What the city search returns
    private var nearby = listOf<AgentEntity>() // What the neighbourhood search returns
    private var nearest = listOf(N3 to 1.5, N4 to 0.6) // What searchNearest returns (never the mandate's own)

    private fun dispatcher(jitter: Double = 0.0) = MandateDispatcher(
        assignmentDao = assignmentDao,
        mandateDao = mandateDao,
        agentService = agentService,
        customerService = customerService,
        locationService = locationService,
        dispatchHours = DispatchHours("21:00", "07:00"),
        tenantService = tenantService,
        eventPublisher = eventPublisher,
        clock = clock,
        logger = DefaultKVLogger(),
        ttlMinutes = 60,
        manualAfterDays = 3,
        maxLoad = 5,
        nearbyRadiusKm = 3.0,
        nearbyMaxNeighborhoods = 20,
        jitter = jitter,
    )

    @BeforeTest
    fun setUp() {
        doAnswer { now }.whenever(clock).millis()
        doAnswer { mandate }.whenever(mandateDao).findByIdForUpdate(MANDATE_ID)
        doReturn(false).whenever(assignmentDao).existsByMandateAndStatus(any(), any())
        doReturn(emptyList<AssignmentEntity>()).whenever(assignmentDao).findByMandateAndStatusIn(any(), any())
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(TenantEntity(id = TENANT_ID, timeZone = DOUALA.id)).whenever(tenantService).findById(TENANT_ID)
        doAnswer { inv ->
            val request = inv.getArgument<SearchAgentRequest>(0)
            if (request.neighborhoodIds.isEmpty()) inCity else nearby
        }.whenever(agentService).search(any(), any())
        doAnswer {
            nearest.map { (id, distance) -> LocationEntity(id = id, type = LocationType.NEIGHBORHOOD) to distance }
        }.whenever(locationService).searchNearest(any())
        doReturn(emptyList<AgentAssignmentCount>()).whenever(assignmentDao).countByAgentIds(any())
        doReturn(emptyList<AgentMandateCount>()).whenever(mandateDao).countByAgentIdsAndStatusIn(any(), any())
        doAnswer { inv -> inv.getArgument<AssignmentEntity>(0).copy(id = 99L) }.whenever(assignmentDao).save(any())
    }

    // --- Helpers

    private fun time(local: String): Long = LocalDateTime.parse(local).atZone(DOUALA).toInstant().toEpochMilli()

    private fun agent(
        id: Long,
        neighborhoodIds: List<Long> = listOf(N1),
        lastAssignedAt: Date? = null,
        rating: Double = AgentEntity.DEFAULT_RATING,
        partyId: Long = 1000 + id,
    ) = AgentEntity(
        id = id,
        party = PartyEntity(id = partyId),
        cityId = CITY_ID,
        neighborhoodIds = neighborhoodIds,
        lastAssignedAt = lastAssignedAt,
        rating = rating,
    )

    private fun offers(agentId: Long, accepted: Long = 0, declined: Long = 0, expired: Long = 0, pending: Long = 0) =
        listOf(
            AssignmentStatus.ACCEPTED to accepted,
            AssignmentStatus.DECLINED to declined,
            AssignmentStatus.EXPIRED to expired,
            AssignmentStatus.PENDING to pending,
        ).filter { it.second > 0 }.map { (status, total) -> Count(agentId, status, total) }

    private fun loads(vararg loads: Pair<Long, Long>) {
        doReturn(loads.map { (agentId, total) -> Load(agentId, total) })
            .whenever(mandateDao).countByAgentIdsAndStatusIn(any(), any())
    }

    private data class Count(
        override val agentId: Long,
        override val status: AssignmentStatus,
        override val total: Long,
    ) : AgentAssignmentCount

    private data class Load(override val agentId: Long, override val total: Long) : AgentMandateCount

    /** Dispatches the mandate to a single candidate, and returns the score it got. */
    private fun scoreOf(agent: AgentEntity): Double {
        inCity = listOf(agent)
        val assignment = dispatcher().dispatch(MANDATE_ID)
        assertNotNull(assignment, "Agent#${agent.id} not offered the mandate")
        return assignment.score
    }

    private fun assertNothingOffered(result: AssignmentEntity?) {
        assertNull(result)
        verify(assignmentDao, never()).save(any())
        verify(agentService, never()).markAssigned(any(), any())
        verify(eventPublisher, never()).publishEvent(any<Any>())
    }

    private fun searchRequests(): List<SearchAgentRequest> {
        val requests = argumentCaptor<SearchAgentRequest>()
        verify(agentService, org.mockito.Mockito.atLeastOnce()).search(requests.capture(), any())
        return requests.allValues
    }

    // --- Nothing to dispatch

    @Test
    fun `mandate not found - nothing offered`() {
        assertNothingOffered(dispatcher().dispatch(999L))
    }

    @Test
    fun `mandate no longer waiting for an agent - nothing offered`() {
        listOf(MandateStatus.ASSIGNED, MandateStatus.MANUAL_ASSIGNMENT, MandateStatus.CANCELLED).forEach { status ->
            mandate = mandate.copy(status = status)
            inCity = listOf(agent(20))

            assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
        }
        verify(mandateDao, never()).save(any())
    }

    @Test
    fun `mandate already offered - never offered twice`() {
        inCity = listOf(agent(20))
        doReturn(true).whenever(assignmentDao).existsByMandateAndStatus(any(), eq(AssignmentStatus.PENDING))

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
    }

    @Test
    fun `the mandate is locked before being checked`() {
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        verify(mandateDao).findByIdForUpdate(MANDATE_ID)
        verify(mandateDao, never()).findById(any())
    }

    @Test
    fun `no candidate - nothing offered`() {
        inCity = emptyList()

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
    }

    // --- Candidates (hard filters)

    @Test
    fun `candidates - verified agents of the mandate's city, least recently offered first`() {
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        val sorts = argumentCaptor<org.springframework.data.domain.Sort>()
        verify(agentService, org.mockito.Mockito.times(2)).search(any(), sorts.capture())
        assertTrue(sorts.allValues.all { it == AgentService.SORT_LEAST_RECENTLY_ASSIGNED })
        searchRequests().forEach { request ->
            assertEquals(CITY_ID, request.cityId) // Never an agent of another city
            assertEquals(listOf(KycStatus.VERIFIED), request.kycStatus)
            assertEquals(100, request.limit)
        }
    }

    @Test
    fun `candidates - the agents of the mandate's and the nearby neighbourhoods are searched too`() {
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        val byNeighborhood = searchRequests().single { it.neighborhoodIds.isNotEmpty() }
        assertEquals(setOf(N1, N3, N4), byNeighborhood.neighborhoodIds.toSet())
    }

    @Test
    fun `candidates - an agent cut off by the city search is still found by its neighbourhood`() {
        inCity = listOf(agent(20, neighborhoodIds = listOf(N9)))
        nearby = listOf(agent(30, neighborhoodIds = listOf(N1)))

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    @Test
    fun `candidates - an agent found by both searches is scored once`() {
        inCity = listOf(agent(20), agent(30, neighborhoodIds = listOf(N9)))
        nearby = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        val ids = argumentCaptor<Collection<Long>>()
        verify(assignmentDao).countByAgentIds(ids.capture())
        assertEquals(listOf(20L, 30L), ids.firstValue.sorted())
    }

    @Test
    fun `candidates - no neighbourhood search when the mandate has no neighbourhood`() {
        mandate = mandate.copy(neighborhoodIds = emptyList())
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        assertTrue(searchRequests().all { it.neighborhoodIds.isEmpty() })
        verify(locationService, never()).searchNearest(any())
    }

    @Test
    fun `candidates - the customer is never offered its own mandate`() {
        inCity = listOf(agent(20, partyId = CUSTOMER_PARTY_ID))

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
    }

    @Test
    fun `candidates - the customer is skipped, the next agent is offered`() {
        inCity = listOf(agent(20, partyId = CUSTOMER_PARTY_ID), agent(30, neighborhoodIds = listOf(N9)))

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    @Test
    fun `candidates - agents who declined or ignored the mandate are never offered it again`() {
        inCity = listOf(agent(20), agent(30), agent(40, neighborhoodIds = listOf(N9)))
        doReturn(
            listOf(
                AssignmentEntity(mandate = mandate, agentId = 20, status = AssignmentStatus.DECLINED),
                AssignmentEntity(mandate = mandate, agentId = 30, status = AssignmentStatus.EXPIRED),
            )
        ).whenever(assignmentDao).findByMandateAndStatusIn(
            any(),
            eq(listOf(AssignmentStatus.DECLINED, AssignmentStatus.EXPIRED)),
        )

        assertEquals(40L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    @Test
    fun `candidates - all declined or ignored - nothing offered`() {
        inCity = listOf(agent(20))
        doReturn(listOf(AssignmentEntity(mandate = mandate, agentId = 20, status = AssignmentStatus.EXPIRED)))
            .whenever(assignmentDao).findByMandateAndStatusIn(any(), any())

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
    }

    @Test
    fun `candidates - an agent at capacity is excluded`() {
        inCity = listOf(agent(20), agent(30, neighborhoodIds = listOf(N9)))
        loads(20L to 5L)

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    @Test
    fun `candidates - every agent at capacity - nothing offered`() {
        inCity = listOf(agent(20))
        loads(20L to 7L)

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
    }

    @Test
    fun `candidates - the load only counts the mandates being worked on`() {
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        val statuses = argumentCaptor<Collection<MandateStatus>>()
        verify(mandateDao).countByAgentIdsAndStatusIn(eq(listOf(20L)), statuses.capture())
        assertEquals(setOf(MandateStatus.ASSIGNED, MandateStatus.PENDING_APPROVAL), statuses.firstValue.toSet())
    }

    // --- Score: neighbourhood (0.35)

    @Test
    fun `score - a perfect newcomer`() {
        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `neighbourhood - the mandate has none - neutral`() {
        mandate = mandate.copy(neighborhoodIds = emptyList())

        // 0.5 instead of 1
        assertEquals(PERFECT_NEWCOMER - 0.35 * 0.5, scoreOf(agent(20, neighborhoodIds = listOf(N9))), DELTA)
    }

    @Test
    fun `neighbourhood - partial coverage`() {
        mandate = mandate.copy(neighborhoodIds = listOf(N1, N2))

        // 0.5 + 0.5 × 1/2 = 0.75
        assertEquals(PERFECT_NEWCOMER - 0.35 * 0.25, scoreOf(agent(20, neighborhoodIds = listOf(N1))), DELTA)
    }

    @Test
    fun `neighbourhood - full coverage of several`() {
        mandate = mandate.copy(neighborhoodIds = listOf(N1, N2))

        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20, neighborhoodIds = listOf(N2, N1, N9))), DELTA)
    }

    @Test
    fun `neighbourhood - nearby - graded by distance`() {
        // N3 at 1.5 km of a 3 km radius: 0.5 × (1 − 0.5) = 0.25
        assertEquals(PERFECT_NEWCOMER - 0.35 * 0.75, scoreOf(agent(20, neighborhoodIds = listOf(N3))), DELTA)
    }

    @Test
    fun `neighbourhood - nearby - the nearest of the agent's neighbourhoods counts`() {
        // N4 at 0.6 km: 0.5 × (1 − 0.2) = 0.4
        assertEquals(PERFECT_NEWCOMER - 0.35 * 0.6, scoreOf(agent(20, neighborhoodIds = listOf(N3, N4))), DELTA)
    }

    @Test
    fun `neighbourhood - nothing nearby - 0`() {
        assertEquals(PERFECT_NEWCOMER - 0.35, scoreOf(agent(20, neighborhoodIds = listOf(N9))), DELTA)
    }

    @Test
    fun `neighbourhood - an agent without neighbourhood - 0`() {
        assertEquals(PERFECT_NEWCOMER - 0.35, scoreOf(agent(20, neighborhoodIds = emptyList())), DELTA)
    }

    @Test
    fun `neighbourhood - covering the mandate's beats being nearby, even with a worse rotation`() {
        inCity = listOf(
            agent(20, neighborhoodIds = listOf(N4)), // Nearby, never offered
            agent(30, neighborhoodIds = listOf(N1), lastAssignedAt = Date(now - 12 * HOUR)),
        )

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    @Test
    fun `neighbourhood - nearer beats farther`() {
        inCity = listOf(agent(20, neighborhoodIds = listOf(N3)), agent(30, neighborhoodIds = listOf(N4)))

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    @Test
    fun `neighbourhood - nearby neighbourhoods of the mandate's city only, within the radius, the nearest 20`() {
        mandate = mandate.copy(neighborhoodIds = listOf(N1, N2))
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        val request = argumentCaptor<SearchNearestLocationRequest>()
        verify(locationService).searchNearest(request.capture())
        assertEquals(listOf(N1, N2), request.firstValue.ids)
        assertEquals(LocationType.NEIGHBORHOOD, request.firstValue.type)
        assertEquals(CITY_ID, request.firstValue.parentId)
        assertEquals(3.0, request.firstValue.radius)
        assertEquals(20, request.firstValue.limit)
    }

    // --- Score: fairness (0.25)

    @Test
    fun `fairness - never offered - 1`() {
        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20, lastAssignedAt = null)), DELTA)
    }

    @Test
    fun `fairness - just offered - 0`() {
        assertEquals(PERFECT_NEWCOMER - 0.25, scoreOf(agent(20, lastAssignedAt = Date(now))), DELTA)
    }

    @Test
    fun `fairness - offered 12h ago - a quarter of the window`() {
        assertEquals(PERFECT_NEWCOMER - 0.25 * 0.75, scoreOf(agent(20, lastAssignedAt = Date(now - 12 * HOUR))), DELTA)
    }

    @Test
    fun `fairness - offered more than 48h ago - 1`() {
        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20, lastAssignedAt = Date(now - 72 * HOUR))), DELTA)
    }

    @Test
    fun `fairness - the least recently offered wins, all else equal`() {
        inCity = listOf(
            agent(20, lastAssignedAt = Date(now - 2 * HOUR)),
            agent(30, lastAssignedAt = Date(now - 20 * HOUR)),
            agent(40, lastAssignedAt = Date(now - 10 * HOUR)),
        )

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    // --- Score: rating (0.15)

    @Test
    fun `rating - unrated - neutral`() {
        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20, rating = 3.0)), DELTA)
    }

    @Test
    fun `rating - 5 stars - 1`() {
        assertEquals(PERFECT_NEWCOMER + 0.15 * 0.5, scoreOf(agent(20, rating = 5.0)), DELTA)
    }

    @Test
    fun `rating - 1 star - 0`() {
        assertEquals(PERFECT_NEWCOMER - 0.15 * 0.5, scoreOf(agent(20, rating = 1.0)), DELTA)
    }

    @Test
    fun `rating - 4 stars - 0,75`() {
        assertEquals(PERFECT_NEWCOMER + 0.15 * 0.25, scoreOf(agent(20, rating = 4.0)), DELTA)
    }

    @Test
    fun `rating - out of range - clamped`() {
        assertEquals(PERFECT_NEWCOMER + 0.15 * 0.5, scoreOf(agent(20, rating = 9.0)), DELTA)
    }

    @Test
    fun `rating - below range - clamped`() {
        assertEquals(PERFECT_NEWCOMER - 0.15 * 0.5, scoreOf(agent(20, rating = -2.0)), DELTA)
    }

    @Test
    fun `rating - the better rated wins, all else equal`() {
        inCity = listOf(agent(20, rating = 3.5), agent(30, rating = 4.8), agent(40, rating = 2.0))

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    // --- Score: responsiveness (0.10)

    @Test
    fun `responsiveness - new agent - 0,5`() {
        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `responsiveness - smoothed acceptance rate`() {
        // (3 + 1) / (3 + 1 + 0 + 2) = 4/6
        doReturn(offers(20, accepted = 3, declined = 1)).whenever(assignmentDao).countByAgentIds(any())

        assertEquals(PERFECT_NEWCOMER + 0.10 * (4.0 / 6 - 0.5), scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `responsiveness - ignored offers count as unanswered ones`() {
        // (0 + 1) / (2 + 2) = 0.25
        doReturn(offers(20, declined = 1, expired = 1)).whenever(assignmentDao).countByAgentIds(any())

        assertEquals(PERFECT_NEWCOMER + 0.10 * (0.25 - 0.5), scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `responsiveness - pending offers are not answered yet`() {
        doReturn(offers(20, pending = 4)).whenever(assignmentDao).countByAgentIds(any())

        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `responsiveness - the counts of the other agents don't leak`() {
        doReturn(offers(30, accepted = 10)).whenever(assignmentDao).countByAgentIds(any())

        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `responsiveness - the most responsive wins, all else equal`() {
        inCity = listOf(agent(20), agent(30))
        doReturn(offers(20, accepted = 1, declined = 5) + offers(30, accepted = 5, declined = 1))
            .whenever(assignmentDao).countByAgentIds(any())

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    // --- Score: capacity (0.15)

    @Test
    fun `capacity - idle - 1`() {
        assertEquals(PERFECT_NEWCOMER, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `capacity - 2 mandates of 5 - 0,6`() {
        loads(20L to 2L)

        assertEquals(PERFECT_NEWCOMER - 0.15 * 0.4, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `capacity - one below the max - still a candidate`() {
        loads(20L to 4L)

        assertEquals(PERFECT_NEWCOMER - 0.15 * 0.8, scoreOf(agent(20)), DELTA)
    }

    @Test
    fun `capacity - the least loaded wins, all else equal`() {
        inCity = listOf(agent(20), agent(30))
        loads(20L to 3L, 30L to 1L)

        assertEquals(30L, dispatcher().dispatch(MANDATE_ID)?.agentId)
    }

    // --- Score: tie-break

    @Test
    fun `jitter - within its bounds`() {
        inCity = listOf(agent(20))

        repeat(20) {
            val score = dispatcher(jitter = 0.01).dispatch(MANDATE_ID)!!.score
            assertTrue(score >= PERFECT_NEWCOMER - 0.01 && score <= PERFECT_NEWCOMER + 0.01, "score=$score")
        }
    }

    // --- The offer

    @Test
    fun `offer - the best scoring agent gets a PENDING offer for the TTL`() {
        inCity = listOf(agent(20, neighborhoodIds = listOf(N3)), agent(30), agent(40, neighborhoodIds = listOf(N9)))

        val result = dispatcher().dispatch(MANDATE_ID)

        val saved = argumentCaptor<AssignmentEntity>()
        verify(assignmentDao).save(saved.capture())
        val assignment = saved.firstValue
        assertEquals(MANDATE_ID, assignment.mandate.id)
        assertEquals(30L, assignment.agentId)
        assertEquals(AssignmentStatus.PENDING, assignment.status)
        assertEquals(PERFECT_NEWCOMER, assignment.score, DELTA)
        assertEquals(now, assignment.createdAt.time)
        assertEquals(now, assignment.modifiedAt.time)
        assertEquals(now + HOUR, assignment.expiresAt.time)
        assertNull(assignment.tenantId) // Set by Hibernate from the tenant context

        assertEquals(99L, result?.id)
    }

    @Test
    fun `offer - the agent moves down the rotation`() {
        val best = agent(20)
        inCity = listOf(best)

        dispatcher().dispatch(MANDATE_ID)

        verify(agentService).markAssigned(best, Date(now))
    }

    @Test
    fun `offer - the agent is notified after commit`() {
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        verify(eventPublisher).publishEvent(AssignmentCreatedEvent(assignmentId = 99L, tenantId = TENANT_ID))
    }

    @Test
    fun `offer - the mandate itself is not changed`() {
        inCity = listOf(agent(20))

        dispatcher().dispatch(MANDATE_ID)

        verify(mandateDao, never()).save(any())
    }

    @Test
    fun `offer - the TTL only runs during the day`() {
        now = time("2026-10-15T20:30")
        mandate = mandate.copy(createdAt = Date(now - HOUR))
        inCity = listOf(agent(20))

        val assignment = dispatcher().dispatch(MANDATE_ID)

        // 30 min before the night, 30 min in the morning
        assertEquals(time("2026-10-16T07:30"), assignment?.expiresAt?.time)
    }

    // --- Night

    @Test
    fun `night - nothing offered, the mandate waits for the morning`() {
        now = time("2026-10-15T22:00")
        mandate = mandate.copy(createdAt = Date(now - HOUR))
        inCity = listOf(agent(20))

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
        verify(mandateDao, never()).save(any())
        verify(agentService, never()).search(any(), any())
    }

    @Test
    fun `night - before the morning`() {
        now = time("2026-10-16T06:59")
        mandate = mandate.copy(createdAt = Date(now - HOUR))
        inCity = listOf(agent(20))

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
    }

    @Test
    fun `night - offered from the morning`() {
        now = time("2026-10-16T07:00")
        mandate = mandate.copy(createdAt = Date(now - 10 * HOUR))
        inCity = listOf(agent(20))

        assertNotNull(dispatcher().dispatch(MANDATE_ID))
    }

    @Test
    fun `night - in the tenant's time zone`() {
        now = time("2026-10-15T22:00") // Night in Douala, 17:00 in Toronto
        mandate = mandate.copy(createdAt = Date(now - HOUR))
        doReturn(TenantEntity(id = TENANT_ID, timeZone = "America/Toronto")).whenever(tenantService).findById(TENANT_ID)
        inCity = listOf(agent(20))

        val assignment = dispatcher().dispatch(MANDATE_ID)

        assertNotNull(assignment)
        assertEquals(now + HOUR, assignment.expiresAt.time)
        verify(tenantService).findById(TENANT_ID)
    }

    // --- Manual assignment

    @Test
    fun `manual - without an agent for too long, handed over to a human`() {
        mandate = mandate.copy(createdAt = Date(now - 3 * DAY - 1))
        inCity = listOf(agent(20))

        val result = dispatcher().dispatch(MANDATE_ID)

        assertNull(result)
        val saved = argumentCaptor<MandateEntity>()
        verify(mandateDao).save(saved.capture())
        assertEquals(MandateStatus.MANUAL_ASSIGNMENT, saved.firstValue.status)
        assertEquals(now, saved.firstValue.modifiedAt.time)
        assertEquals(MANDATE_ID, saved.firstValue.id)
        verify(eventPublisher).publishEvent(MandateManualAssignmentEvent(mandateId = MANDATE_ID, tenantId = TENANT_ID))
        verify(assignmentDao, never()).save(any())
        verify(agentService, never()).search(any(), any())
    }

    @Test
    fun `manual - exactly after the delay`() {
        mandate = mandate.copy(createdAt = Date(now - 3 * DAY))
        inCity = listOf(agent(20))

        assertNull(dispatcher().dispatch(MANDATE_ID))
        verify(mandateDao).save(any())
    }

    @Test
    fun `manual - just before the delay, still offered`() {
        mandate = mandate.copy(createdAt = Date(now - 3 * DAY + 1))
        inCity = listOf(agent(20))

        assertNotNull(dispatcher().dispatch(MANDATE_ID))
        verify(mandateDao, never()).save(any())
    }

    @Test
    fun `manual - even at night`() {
        now = time("2026-10-15T23:00")
        mandate = mandate.copy(createdAt = Date(now - 4 * DAY))

        assertNull(dispatcher().dispatch(MANDATE_ID))
        verify(mandateDao).save(any())
        verify(eventPublisher).publishEvent(MandateManualAssignmentEvent(mandateId = MANDATE_ID, tenantId = TENANT_ID))
    }

    @Test
    fun `manual - even without candidate`() {
        mandate = mandate.copy(createdAt = Date(now - 4 * DAY))
        inCity = emptyList()

        dispatcher().dispatch(MANDATE_ID)

        verify(mandateDao).save(any())
    }

    @Test
    fun `manual - a pending offer is never interrupted`() {
        mandate = mandate.copy(createdAt = Date(now - 4 * DAY))
        doReturn(true).whenever(assignmentDao).existsByMandateAndStatus(any(), eq(AssignmentStatus.PENDING))

        assertNothingOffered(dispatcher().dispatch(MANDATE_ID))
        verify(mandateDao, never()).save(any())
    }
}
