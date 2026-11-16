package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchRejectionReason
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.RejectMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.event.MatchAnsweredEvent
import com.wutsi.ndopify.match.server.mail.MatchProposedMailet
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Optional
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatchProposalServiceTest {
    private val dao = mock<MatchRepository>()
    private val matchService = mock<MatchService>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val mailet = mock<MatchProposedMailet>()
    private val notifier = mock<MatchAnsweredNotifier>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val clock = Clock.fixed(Instant.parse("2026-11-15T10:00:00Z"), ZoneId.of("UTC"))
    private val service = MatchProposalService(
        dao, matchService, customerService, agentService, mailet, notifier, eventPublisher, clock, mock(), 7L,
    )

    private val mandate = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        status = MandateStatus.APPROVED,
    )
    private val listing = ListingEntity(id = 102L, title = "Small apartment")
    private val match = MatchEntity(
        id = 2L,
        tenantId = 1L,
        mandate = mandate,
        listing = listing,
        agentId = 20L,
        status = MatchStatus.PROPOSED,
    )
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L)

    @BeforeTest
    fun setUp() {
        doReturn(match).whenever(matchService).findById(2L)
        doAnswer { it.arguments[0] }.whenever(dao).save(any())
    }

    @Test
    fun `accept publishes the answer`() {
        service.accept(2L)

        assertEquals(MatchStatus.ACCEPTED, saved().status)
        val event = argumentCaptor<MatchAnsweredEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(
            MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 1L, status = MatchStatus.ACCEPTED),
            event.firstValue,
        )
    }

    @Test
    fun `reject publishes the answer with its reason`() {
        service.reject(2L, RejectMatchRequest(reasonCode = MatchRejectionReason.PRICE, reason = " Trop loin. "))

        val saved = saved()
        assertEquals(MatchStatus.REJECTED, saved.status)
        assertEquals(MatchRejectionReason.PRICE, saved.rejectionReasonCode)
        assertEquals("Trop loin.", saved.rejectionReason)
        val event = argumentCaptor<MatchAnsweredEvent>()
        verify(eventPublisher).publishEvent(event.capture())
        assertEquals(
            MatchAnsweredEvent(matchId = 2L, mandateId = 1L, tenantId = 1L, status = MatchStatus.REJECTED),
            event.firstValue,
        )
    }

    @Test
    fun `an answer to a match not proposed publishes nothing`() {
        doReturn(match.copy(status = MatchStatus.ACTIVE)).whenever(matchService).findById(2L)

        assertFailsWith<ConflictException> { service.accept(2L) }

        verify(eventPublisher, never()).publishEvent(any())
    }

    @Test
    fun `answered - the search agent is notified`() {
        doReturn(Optional.of(match.copy(status = MatchStatus.ACCEPTED))).whenever(dao).findById(2L)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)

        service.sendAnsweredNotification(2L)

        verify(notifier).send(match.copy(status = MatchStatus.ACCEPTED), customer, agent)
    }

    @Test
    fun `answered - a co-brokerage match notifies the search agent only`() {
        val listingMandate = MandateEntity(id = 11L, tenantId = 1L, agentId = 30L)
        val coBrokerage = match.copy(
            status = MatchStatus.ACCEPTED,
            agentId = 20L,
            listing = listing.copy(mandate = listingMandate),
        )
        doReturn(Optional.of(coBrokerage)).whenever(dao).findById(2L)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)

        service.sendAnsweredNotification(2L)

        verify(notifier).send(coBrokerage, customer, agent)
        verify(agentService, never()).findById(30L)
    }

    @Test
    fun `answered - nothing for a proposal not answered`() {
        doReturn(Optional.of(match)).whenever(dao).findById(2L)

        service.sendAnsweredNotification(2L)

        verifyNoInteractions(notifier)
    }

    @Test
    fun `answered - nothing for an unknown match`() {
        doReturn(Optional.empty<MatchEntity>()).whenever(dao).findById(999L)

        service.sendAnsweredNotification(999L)

        verifyNoInteractions(notifier)
    }

    private fun saved(): MatchEntity {
        val saved = argumentCaptor<MatchEntity>()
        verify(dao).save(saved.capture())
        return saved.firstValue
    }
}
