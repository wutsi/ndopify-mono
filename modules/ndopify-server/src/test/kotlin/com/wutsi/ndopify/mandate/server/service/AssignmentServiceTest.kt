package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.ForbiddenException
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.AssignmentAcceptedEvent
import com.wutsi.ndopify.mandate.server.event.AssignmentClosedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.util.Date
import java.util.Optional
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** The batch methods behind `MandateJobs`, which no endpoint exposes. */
class AssignmentServiceTest {
    private val dao = mock<AssignmentRepository>()
    private val mandateDao = mock<MandateRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = mock<Clock>()
    private val dispatcher = mock<MandateDispatcher>()
    private val agentService = mock<AgentService>()
    private val offerNotifier = mock<AssignmentOfferNotifier>()
    private val service = AssignmentService(
        dao = dao,
        mandateDao = mandateDao,
        agentService = agentService,
        customerService = mock(),
        accessService = mock(),
        eventPublisher = eventPublisher,
        clock = clock,
        mailet = mock(),
        offerNotifier = offerNotifier,
        dispatcher = dispatcher,
    )

    private val now = 1_800_000_000_000L

    private fun offer(id: Long, mandateId: Long) = AssignmentEntity(
        id = id,
        mandate = MandateEntity(id = mandateId, status = MandateStatus.PENDING_ASSIGNMENT),
        agentId = 20L,
        status = AssignmentStatus.PENDING,
        expiresAt = Date(now + 60_000),
    )

    private fun offered(id: Long, mandateId: Long) = DispatchResult.Offered(
        assignment = offer(id, mandateId),
        diagnostics = DispatchDiagnostics(candidateCount = 1, excluded = emptyMap(), eligible = emptyList()),
    )

    @BeforeTest
    fun setUp() {
        TenantContext.set(1L)
        doReturn(now).whenever(clock).millis()
        doAnswer { it.getArgument<AssignmentEntity>(0) }.whenever(dao).save(any())
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    // --- expireDue

    @Test
    fun `expireDue - the offers past their TTL are expired, and their mandate re-dispatched`() {
        doReturn(listOf(offer(1, 10), offer(2, 20)))
            .whenever(dao).findByStatusAndExpiresAtBefore(eq(AssignmentStatus.PENDING), eq(Date(now)), any())

        assertEquals(2, service.expireDue())

        val saved = argumentCaptor<AssignmentEntity>()
        verify(dao, org.mockito.Mockito.times(2)).save(saved.capture())
        assertEquals(listOf(AssignmentStatus.EXPIRED, AssignmentStatus.EXPIRED), saved.allValues.map { it.status })
        assertEquals(listOf(now, now), saved.allValues.map { it.modifiedAt.time })
        verify(eventPublisher).publishEvent(AssignmentClosedEvent(assignmentId = 1, mandateId = 10, tenantId = 1))
        verify(eventPublisher).publishEvent(AssignmentClosedEvent(assignmentId = 2, mandateId = 20, tenantId = 1))
    }

    @Test
    fun `expireDue - a failing offer doesn't stop the others`() {
        doReturn(listOf(offer(1, 10), offer(2, 20)))
            .whenever(dao).findByStatusAndExpiresAtBefore(any(), any(), any())
        doThrow(IllegalStateException("boom")).doAnswer { it.getArgument<AssignmentEntity>(0) }
            .whenever(dao).save(any())

        assertEquals(1, service.expireDue())

        verify(eventPublisher).publishEvent(AssignmentClosedEvent(assignmentId = 2, mandateId = 20, tenantId = 1))
    }

    @Test
    fun `expireDue - a batch at a time`() {
        doReturn(emptyList<AssignmentEntity>()).whenever(dao).findByStatusAndExpiresAtBefore(any(), any(), any())

        assertEquals(0, service.expireDue())

        verify(dao).findByStatusAndExpiresAtBefore(
            AssignmentStatus.PENDING,
            Date(now),
            PageRequest.of(0, AssignmentService.BATCH_SIZE),
        )
    }

    // --- sendOfferNotification

    @Test
    fun `sendOfferNotification - a pending offer goes to the notifier`() {
        val assignment = offer(1, 10)
        val agent = AgentEntity(id = 20L)
        doReturn(Optional.of(assignment)).whenever(dao).findById(1L)
        doReturn(agent).whenever(agentService).findById(20L)

        service.sendOfferNotification(1L)

        verify(offerNotifier).send(assignment, agent)
    }

    @Test
    fun `sendOfferNotification - a closed offer is not notified`() {
        doReturn(Optional.of(offer(1, 10).copy(status = AssignmentStatus.EXPIRED))).whenever(dao).findById(1L)

        service.sendOfferNotification(1L)

        verifyNoInteractions(offerNotifier)
    }

    // --- acceptForAgent / declineForAgent (the Telegram path: no JWT)

    @Test
    fun `acceptForAgent - accepts the offer of that agent`() {
        doReturn(Optional.of(offer(1, 10))).whenever(dao).findById(1L)

        val accepted = service.acceptForAgent(agentId = 20L, id = 1L)

        assertEquals(AssignmentStatus.ACCEPTED, accepted.status)
        verify(eventPublisher).publishEvent(AssignmentAcceptedEvent(assignmentId = 1L, tenantId = 1L))
    }

    @Test
    fun `acceptForAgent - another agent's offer is forbidden, before the status`() {
        doReturn(Optional.of(offer(1, 10).copy(status = AssignmentStatus.ACCEPTED))).whenever(dao).findById(1L)

        val ex = assertFailsWith<ForbiddenException> { service.acceptForAgent(agentId = 30L, id = 1L) }

        assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, ex.error.code)
        verify(dao, never()).save(any())
    }

    @Test
    fun `acceptForAgent - an offer past its TTL is not pending`() {
        doReturn(Optional.of(offer(1, 10).copy(expiresAt = Date(now - 1)))).whenever(dao).findById(1L)

        val ex = assertFailsWith<ConflictException> { service.acceptForAgent(agentId = 20L, id = 1L) }

        assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, ex.error.code)
    }

    @Test
    fun `declineForAgent - declines the offer of that agent`() {
        doReturn(Optional.of(offer(1, 10))).whenever(dao).findById(1L)

        val declined = service.declineForAgent(agentId = 20L, id = 1L)

        assertEquals(AssignmentStatus.DECLINED, declined.status)
        verify(eventPublisher).publishEvent(AssignmentClosedEvent(assignmentId = 1L, mandateId = 10L, tenantId = 1L))
    }

    // --- dispatchPending

    @Test
    fun `dispatchPending - the mandates waiting without an offer are dispatched`() {
        doReturn(listOf(3L, 4L, 5L)).whenever(mandateDao).findIdsWithoutAssignment(any(), any(), any())
        doReturn(offered(30, 3)).whenever(dispatcher).dispatchResult(3L)
        doReturn(DispatchResult.Skipped(DispatchReason.NIGHT)).whenever(dispatcher).dispatchResult(4L)
        doReturn(offered(50, 5)).whenever(dispatcher).dispatchResult(5L)

        assertEquals(2, service.dispatchPending())

        verify(mandateDao).findIdsWithoutAssignment(
            MandateStatus.PENDING_ASSIGNMENT,
            AssignmentStatus.PENDING,
            PageRequest.of(0, AssignmentService.BATCH_SIZE),
        )
    }

    @Test
    fun `dispatchPending - a failing mandate doesn't stop the others`() {
        doReturn(listOf(3L, 4L)).whenever(mandateDao).findIdsWithoutAssignment(any(), any(), any())
        doThrow(IllegalStateException("boom")).whenever(dispatcher).dispatchResult(3L)
        doReturn(offered(40, 4)).whenever(dispatcher).dispatchResult(4L)

        assertEquals(1, service.dispatchPending())

        verify(dispatcher).dispatchResult(4L)
    }

    @Test
    fun `dispatchPending - the mandates without an offer are not counted`() {
        doReturn(listOf(3L, 4L)).whenever(mandateDao).findIdsWithoutAssignment(any(), any(), any())
        doReturn(DispatchResult.Skipped(DispatchReason.NIGHT)).whenever(dispatcher).dispatchResult(3L)
        doReturn(DispatchResult.Skipped(DispatchReason.NO_CANDIDATE)).whenever(dispatcher).dispatchResult(4L)

        assertEquals(0, service.dispatchPending())
    }
}
