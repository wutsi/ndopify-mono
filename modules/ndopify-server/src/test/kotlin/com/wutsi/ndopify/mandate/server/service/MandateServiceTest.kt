package com.wutsi.ndopify.mandate.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.mandate.dto.CancelMandateRequest
import com.wutsi.ndopify.mandate.dto.CloseMandateRequest
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.MandateClosedEvent
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.dto.Money
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.util.Date
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Closing and cancelling: the age and the event, that the endpoint tests can't see. */
class MandateServiceTest {
    private val dao = mock<MandateRepository>()
    private val assignmentDao = mock<AssignmentRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = mock<Clock>()
    private val service = MandateService(
        dao = dao,
        assignmentDao = assignmentDao,
        clock = clock,
        customerService = mock(),
        agentService = mock(),
        eventPublisher = eventPublisher,
        contractService = mock(),
        dispatcher = mock(),
        contractGenerator = mock(),
        storageProvider = mock(),
        tx = mock(),
        access = mock(),
        contractDurationMonths = 6,
    )

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    private fun mandate(status: MandateStatus, approvedAt: Date? = null) = MandateEntity(
        id = 1L,
        status = status,
        createdAt = Date(now - 30 * day),
        approvedAt = approvedAt,
    )

    @BeforeTest
    fun setUp() {
        TenantContext.set(1L)
        doReturn(now).whenever(clock).millis()
        doAnswer { it.getArgument<MandateEntity>(0) }.whenever(dao).save(any())
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `close - the age is the number of whole days since its approval`() {
        // 12 days and 23 hours
        doReturn(mandate(MandateStatus.APPROVED, Date(now - 13 * day + 3_600_000)))
            .whenever(dao).findByIdForUpdate(1L)

        val closed = service.close(1L, CloseMandateRequest(finalPrice = Money(amount = 100000)))

        assertEquals(MandateStatus.CLOSED, closed.status)
        assertEquals(12, closed.age)
        assertEquals(100000L, closed.finalPrice)
        assertEquals(Date(now), closed.closedAt)
        verify(eventPublisher).publishEvent(MandateClosedEvent(mandateId = 1L, tenantId = 1L))
    }

    @Test
    fun `close - approved today is 0 day old`() {
        doReturn(mandate(MandateStatus.APPROVED, Date(now - 60_000))).whenever(dao).findByIdForUpdate(1L)

        assertEquals(0, service.close(1L, CloseMandateRequest()).age)
    }

    @Test
    fun `close - without an approval date, the age is from its creation`() {
        doReturn(mandate(MandateStatus.APPROVED)).whenever(dao).findByIdForUpdate(1L)

        assertEquals(30, service.close(1L, CloseMandateRequest()).age)
    }

    @Test
    fun `close - not approved`() {
        doReturn(mandate(MandateStatus.ASSIGNED)).whenever(dao).findByIdForUpdate(1L)

        assertFailsWith<ConflictException> { service.close(1L, CloseMandateRequest()) }
        verify(dao, never()).save(any())
        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `cancel - publishes the event`() {
        doReturn(mandate(MandateStatus.ASSIGNED)).whenever(dao).findByIdForUpdate(1L)
        doReturn(emptyList<Any>()).whenever(assignmentDao).findByMandateAndStatusIn(any(), any())

        val cancelled = service.cancel(1L, CancelMandateRequest(cancellationReason = "  "))

        assertEquals(MandateStatus.CANCELLED, cancelled.status)
        assertEquals(null, cancelled.cancellationReason)
        assertEquals(null, cancelled.age)
        val event = argumentCaptor<MandateClosedEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(MandateClosedEvent(mandateId = 1L, tenantId = 1L), event.firstValue)
    }

    @Test
    fun `cancel - already closed`() {
        doReturn(mandate(MandateStatus.CLOSED)).whenever(dao).findByIdForUpdate(1L)

        assertFailsWith<ConflictException> { service.cancel(1L, CancelMandateRequest()) }
        verify(eventPublisher, never()).publishEvent(any())
    }
}
