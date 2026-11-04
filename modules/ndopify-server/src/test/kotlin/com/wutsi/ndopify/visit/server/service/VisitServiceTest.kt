package com.wutsi.ndopify.visit.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.dto.ScheduleVisitRequest
import com.wutsi.ndopify.visit.dto.VisitNoShowReason
import com.wutsi.ndopify.visit.dto.VisitParty
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import com.wutsi.ndopify.visit.server.mail.VisitCompletedMailet
import com.wutsi.ndopify.visit.server.mail.VisitConfirmedMailet
import com.wutsi.ndopify.visit.server.mail.VisitExpiredMailet
import com.wutsi.ndopify.visit.server.mail.VisitNoShowMailet
import com.wutsi.ndopify.visit.server.mail.VisitReminderMailet
import com.wutsi.ndopify.visit.server.mail.VisitRequestedMailet
import org.springframework.context.ApplicationEventPublisher
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VisitServiceTest {
    private val dao = mock<VisitRepository>()
    private val matchDao = mock<MatchRepository>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val listingService = mock<ListingService>()
    private val tenantService = mock<TenantService>()
    private val requestedMailet = mock<VisitRequestedMailet>()
    private val confirmedMailet = mock<VisitConfirmedMailet>()
    private val completedMailet = mock<VisitCompletedMailet>()
    private val noShowMailet = mock<VisitNoShowMailet>()
    private val cancelledMailet = mock<VisitCancelledMailet>()
    private val reminderMailet = mock<VisitReminderMailet>()
    private val expiredMailet = mock<VisitExpiredMailet>()
    private val codeGenerator = mock<VisitCodeGenerator>()
    private val transactionManager = mock<PlatformTransactionManager>()
    private val tx = TransactionTemplate(transactionManager)
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val logger = DefaultKVLogger()

    // 2026-12-01 10:00 in Douala
    private val clock = Clock.fixed(Instant.parse("2026-12-01T09:00:00Z"), ZoneId.of("UTC"))

    private val service = VisitService(
        dao,
        matchDao,
        customerService,
        agentService,
        listingService,
        tenantService,
        requestedMailet,
        confirmedMailet,
        completedMailet,
        noShowMailet,
        cancelledMailet,
        reminderMailet,
        expiredMailet,
        codeGenerator,
        eventPublisher,
        tx,
        clock,
        logger,
        VisitPolicyProperties(),
    )

    private val tenant = TenantEntity(id = 1L, timeZone = "Africa/Douala")
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        mandateId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.REQUESTED,
        type = VisitType.ON_SITE,
    )

    @BeforeTest
    fun setUp() {
        doReturn(visit).whenever(dao).findByIdForUpdate(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("1234").whenever(codeGenerator).generate()
        doAnswer { it.arguments[0] }.whenever(dao).save(any())
        doReturn(mock<TransactionStatus>()).whenever(transactionManager).getTransaction(any())
    }

    @Test
    fun `confirmed less than 24h ahead marks the reminder as sent`() {
        service.schedule(1L, ScheduleVisitRequest(scheduledAt = date(2026, 12, 2, 8, 0)))

        val saved = saved()
        assertEquals(VisitStatus.CONFIRMED, saved.status)
        assertEquals("1234", saved.code)
        assertEquals(Date(clock.millis()), saved.reminderSentAt)
    }

    @Test
    fun `confirmed 24h ahead or more leaves the reminder unsent`() {
        service.schedule(1L, ScheduleVisitRequest(scheduledAt = date(2026, 12, 2, 10, 0)))

        assertNull(saved().reminderSentAt)
    }

    @Test
    fun `expire the requests older than 48h`() {
        val other = visit.copy(id = 2L)
        doReturn(listOf(visit, other)).whenever(dao).findByStatusAndCreatedAtBefore(eq(VisitStatus.REQUESTED), any())

        assertEquals(2, service.expireDue())

        assertEquals(VisitStatus.EXPIRED, saved().status)
    }

    @Test
    fun `a visit changed meanwhile is skipped when expiring`() {
        val other = visit.copy(id = 2L)
        doReturn(listOf(visit, other)).whenever(dao).findByStatusAndCreatedAtBefore(eq(VisitStatus.REQUESTED), any())
        doThrow(ObjectOptimisticLockingFailureException(VisitEntity::class.java, 1L))
            .whenever(dao).save(argThat { id == 1L })

        assertEquals(1, service.expireDue())

        assertEquals(VisitStatus.EXPIRED, saved().status)
    }

    @Test
    fun `remind the confirmed visits starting within 24h, once`() {
        val confirmed = visit.copy(status = VisitStatus.CONFIRMED)
        doReturn(listOf(confirmed)).whenever(dao)
            .findByStatusAndScheduledAtBetweenAndReminderSentAtIsNull(eq(VisitStatus.CONFIRMED), any(), any())

        assertEquals(1, service.remindDue())

        val saved = saved()
        assertEquals(VisitStatus.CONFIRMED, saved.status)
        assertEquals(Date(clock.millis()), saved.reminderSentAt)
    }

    @Test
    fun `a missing check-in 4h later is the agent's no-show`() {
        doReturn(listOf(visit.copy(status = VisitStatus.CONFIRMED))).whenever(dao)
            .findByStatusAndScheduledAtBefore(eq(VisitStatus.CONFIRMED), any())

        assertEquals(1, service.markAgentNoShows())

        val saved = saved()
        assertEquals(VisitStatus.NO_SHOW, saved.status)
        assertEquals(VisitParty.AGENT, saved.noShowParty)
        assertEquals(VisitNoShowReason.AGENT_ABSENT, saved.noShowReason)
    }

    private fun saved(): VisitEntity {
        val captor = argumentCaptor<VisitEntity>()
        verify(dao, atLeastOnce()).save(captor.capture())
        return captor.lastValue
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
