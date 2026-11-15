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
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.party.server.domain.PartyEntity
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
import com.wutsi.ndopify.visit.server.event.VisitCancelledEvent
import com.wutsi.ndopify.visit.server.mail.CoBrokerageVisitMailet
import com.wutsi.ndopify.visit.server.mail.VisitCancelledMailet
import com.wutsi.ndopify.visit.server.mail.VisitCompletedMailet
import com.wutsi.ndopify.visit.server.mail.VisitConfirmedMailet
import com.wutsi.ndopify.visit.server.mail.VisitExpiredMailet
import com.wutsi.ndopify.visit.server.mail.VisitNoShowMailet
import com.wutsi.ndopify.visit.server.mail.VisitReminderMailet
import org.mockito.Mockito.verifyNoInteractions
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
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VisitServiceTest {
    private val dao = mock<VisitRepository>()
    private val matchDao = mock<MatchRepository>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val listingService = mock<ListingService>()
    private val tenantService = mock<TenantService>()
    private val requestedNotifier = mock<VisitRequestedNotifier>()
    private val confirmedMailet = mock<VisitConfirmedMailet>()
    private val completedMailet = mock<VisitCompletedMailet>()
    private val noShowMailet = mock<VisitNoShowMailet>()
    private val cancelledMailet = mock<VisitCancelledMailet>()
    private val reminderMailet = mock<VisitReminderMailet>()
    private val expiredMailet = mock<VisitExpiredMailet>()
    private val coBrokerageVisitMailet = mock<CoBrokerageVisitMailet>()
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
        requestedNotifier,
        confirmedMailet,
        completedMailet,
        noShowMailet,
        cancelledMailet,
        reminderMailet,
        expiredMailet,
        coBrokerageVisitMailet,
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

    // A, the search agent organizing the visit
    private val agent20 = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )

    // B, the listing's owner agent
    private val owner = AgentEntity(
        id = 30L,
        party = PartyEntity(id = 300L, firstName = "John", lastName = "Doe"),
    )
    private val coBrokerageListing = ListingEntity(id = 101L, agent = owner)
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Secret", lastName = "Client"),
    )

    @BeforeTest
    fun setUp() {
        doReturn(visit).whenever(dao).findByIdForUpdate(1L)
        doReturn(Optional.of(visit)).whenever(dao).findById(1L)
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn("1234").whenever(codeGenerator).generate()
        doReturn(coBrokerageListing).whenever(listingService).findById(101L)
        doReturn(agent20).whenever(agentService).findById(20L)
        doReturn(customer).whenever(customerService).findById(10L)
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
    fun `requested - the agent is notified`() {
        service.sendRequestedNotification(1L)

        verify(requestedNotifier).send(visit, customer, agent20, coBrokerageListing)
    }

    @Test
    fun `requested - nothing once no longer REQUESTED`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.EXPIRED))).whenever(dao).findById(1L)

        service.sendRequestedNotification(1L)

        verifyNoInteractions(requestedNotifier)
    }

    @Test
    fun `requested - nothing for an unknown visit`() {
        doReturn(Optional.empty<VisitEntity>()).whenever(dao).findById(999L)

        service.sendRequestedNotification(999L)

        verifyNoInteractions(requestedNotifier)
    }

    @Test
    fun `expire the requests at least 48h old whose slot passed`() {
        val other = visit.copy(id = 2L)
        doReturn(listOf(visit, other)).whenever(dao).findByStatusAndCreatedAtBeforeAndRequestedAtBefore(
            eq(VisitStatus.REQUESTED),
            eq(Date.from(Instant.parse("2026-11-29T09:00:00Z"))),
            eq(Date.from(Instant.parse("2026-12-01T09:00:00Z"))),
        )

        assertEquals(2, service.expireDue())

        assertEquals(VisitStatus.EXPIRED, saved().status)
    }

    @Test
    fun `a visit changed meanwhile is skipped when expiring`() {
        val other = visit.copy(id = 2L)
        doReturn(listOf(visit, other)).whenever(dao).findByStatusAndCreatedAtBeforeAndRequestedAtBefore(
            eq(VisitStatus.REQUESTED),
            any(),
            any(),
        )
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

    @Test
    fun `confirmed - the listing agent is mailed when the visit belongs to another agent`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))))
            .whenever(dao).findById(1L)

        service.sendConfirmedEmails(1L)

        verify(coBrokerageVisitMailet).sendConfirmed(any(), eq(coBrokerageListing), eq(agent20))
    }

    @Test
    fun `confirmed - an own-inventory visit does not mail the listing agent`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))))
            .whenever(dao).findById(1L)
        doReturn(ListingEntity(id = 101L, agent = agent20)).whenever(listingService).findById(101L)

        service.sendConfirmedEmails(1L)

        verify(coBrokerageVisitMailet, never()).sendConfirmed(any(), any(), any())
    }

    @Test
    fun `reminder - the listing agent is mailed when the visit belongs to another agent`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))))
            .whenever(dao).findById(1L)

        service.sendReminderEmails(1L)

        verify(coBrokerageVisitMailet).sendReminder(any(), eq(coBrokerageListing), eq(agent20))
    }

    @Test
    fun `cancelled after confirmed - the listing agent is mailed`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.CANCELLED, cancelledBy = VisitParty.AGENT, scheduledAt = date(2026, 12, 2, 10, 0))))
            .whenever(dao).findById(1L)

        service.sendCancelledEmail(1L)

        verify(coBrokerageVisitMailet).sendCancelled(any(), eq(coBrokerageListing), eq(agent20))
    }

    @Test
    fun `cancelled after the listing mandate ended - the listing agent is not mailed`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.CANCELLED, cancelledBy = VisitParty.SYSTEM, scheduledAt = date(2026, 12, 2, 10, 0))))
            .whenever(dao).findById(1L)
        doReturn(
            ListingEntity(
                id = 101L,
                mandate = MandateEntity(id = 11L, status = MandateStatus.CANCELLED),
                agent = owner,
            ),
        ).whenever(listingService).findById(101L)

        service.sendCancelledEmail(1L)

        verify(coBrokerageVisitMailet, never()).sendCancelled(any(), any(), any())
    }

    @Test
    fun `cancelled while requested - the listing agent was never told`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.CANCELLED, cancelledBy = VisitParty.CUSTOMER, scheduledAt = null)))
            .whenever(dao).findById(1L)

        service.sendCancelledEmail(1L)

        verify(coBrokerageVisitMailet, never()).sendCancelled(any(), any(), any())
    }

    @Test
    fun `customer no-show - the listing agent is mailed`() {
        doReturn(Optional.of(visit.copy(status = VisitStatus.NO_SHOW, noShowParty = VisitParty.CUSTOMER, noShowReason = VisitNoShowReason.CUSTOMER_ABSENT)))
            .whenever(dao).findById(1L)

        service.sendNoShowEmails(1L)

        verify(coBrokerageVisitMailet).sendNoShow(any(), eq(coBrokerageListing), eq(agent20))
    }

    @Test
    fun `agent no-show, completion and expiry - no listing-agent mail`() {
        listOf(
            visit.copy(status = VisitStatus.NO_SHOW, noShowParty = VisitParty.AGENT),
            visit.copy(status = VisitStatus.COMPLETED),
            visit.copy(status = VisitStatus.EXPIRED),
        ).forEach { current ->
            doReturn(Optional.of(current)).whenever(dao).findById(1L)
            service.sendNoShowEmails(1L)
            service.sendCompletedEmail(1L)
            service.sendExpiredEmails(1L)
        }

        verify(coBrokerageVisitMailet, never()).sendNoShow(any(), any(), any())
    }

    @Test
    fun `an ended mandate cancels the open visits the query returns`() {
        val requested = visit.copy(id = 2L, status = VisitStatus.REQUESTED, scheduledAt = null)
        val confirmed = visit.copy(id = 1L, status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))
        // The repository query already excludes CHECKED_IN/COMPLETED; VisitRepositoryTest pins that exclusion.
        doReturn(listOf(requested, confirmed)).whenever(dao)
            .findOpenByMandateOrListingMandate(eq(1L), any())

        assertEquals(2, service.cancelForEndedMandate(1L))

        val saved = argumentCaptor<VisitEntity>()
        verify(dao, times(2)).save(saved.capture())
        saved.allValues.forEach {
            assertEquals(VisitStatus.CANCELLED, it.status)
            assertEquals(VisitParty.SYSTEM, it.cancelledBy)
            assertNotNull(it.cancelledAt)
            assertNull(it.cancellationReason)
        }
        verify(eventPublisher, times(2)).publishEvent(any<VisitCancelledEvent>())
    }

    @Test
    fun `the catch-up sweep cancels the open visits of ended mandates`() {
        val requested = visit.copy(id = 3L, status = VisitStatus.REQUESTED, scheduledAt = null)
        val confirmed = visit.copy(id = 1L, status = VisitStatus.CONFIRMED, scheduledAt = date(2026, 12, 2, 10, 0))
        doReturn(listOf(requested, confirmed)).whenever(dao)
            .findOpenByEndedMandate(any(), any(), any())

        assertEquals(2, service.cancelEndedMandateVisits(100))

        val saved = argumentCaptor<VisitEntity>()
        verify(dao, times(2)).save(saved.capture())
        saved.allValues.forEach {
            assertEquals(VisitStatus.CANCELLED, it.status)
            assertEquals(VisitParty.SYSTEM, it.cancelledBy)
            assertNotNull(it.cancelledAt)
            assertNull(it.cancellationReason)
        }
        verify(eventPublisher, times(2)).publishEvent(any<VisitCancelledEvent>())
    }

    @Test
    fun `the co-brokerage read covers the agent's live listings`() {
        val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L))
        doReturn(listOf(visit)).whenever(dao).findCoBrokerageByListingAgent(eq(20L), any(), any())

        assertEquals(listOf(visit), service.findCoBrokerageForAgent(agent))

        verify(dao).findCoBrokerageByListingAgent(
            20L,
            setOf(VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN),
            setOf(ListingStatus.PUBLISHED, ListingStatus.RENTED, ListingStatus.SOLD),
        )
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
