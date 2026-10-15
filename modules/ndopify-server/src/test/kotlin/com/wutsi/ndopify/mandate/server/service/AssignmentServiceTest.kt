package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.AssignmentClosedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import java.time.Clock
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The batch methods behind `MandateJobs`, which no endpoint exposes. */
class AssignmentServiceTest {
    private val dao = mock<AssignmentRepository>()
    private val mandateDao = mock<MandateRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = mock<Clock>()
    private val dispatcher = mock<MandateDispatcher>()
    private val service = AssignmentService(
        dao = dao,
        mandateDao = mandateDao,
        agentService = mock(),
        customerService = mock(),
        userService = mock(),
        membershipService = mock(),
        accessTokenService = mock(),
        eventPublisher = eventPublisher,
        clock = clock,
        mailet = mock(),
        offerMailet = mock(),
        dispatcher = dispatcher,
        mandateService = mock(),
    )

    private val now = 1_800_000_000_000L

    private fun offer(id: Long, mandateId: Long) = AssignmentEntity(
        id = id,
        mandate = MandateEntity(id = mandateId),
        agentId = 20L,
        status = AssignmentStatus.PENDING,
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

    // --- dispatchPending

    @Test
    fun `dispatchPending - the mandates waiting without an offer are dispatched`() {
        doReturn(listOf(3L, 4L, 5L)).whenever(mandateDao).findIdsWithoutAssignment(any(), any(), any())
        doReturn(offer(30, 3)).whenever(dispatcher).dispatch(3L)
        doReturn(null).whenever(dispatcher).dispatch(4L) // Night, no candidate...
        doReturn(offer(50, 5)).whenever(dispatcher).dispatch(5L)

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
        doThrow(IllegalStateException("boom")).whenever(dispatcher).dispatch(3L)
        doReturn(offer(40, 4)).whenever(dispatcher).dispatch(4L)

        assertEquals(1, service.dispatchPending())

        verify(dispatcher).dispatch(4L)
    }
}
