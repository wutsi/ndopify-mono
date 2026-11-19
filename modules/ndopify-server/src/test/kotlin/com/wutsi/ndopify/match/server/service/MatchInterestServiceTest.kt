package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.event.MatchInterestAcceptedEvent
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import org.springframework.context.ApplicationEventPublisher
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MatchInterestServiceTest {
    private val matchService = mock<MatchService>()
    private val agentService = mock<AgentService>()
    private val customerService = mock<CustomerService>()
    private val notifier = mock<MatchInterestNotifier>()
    private val mandateService = mock<MandateService>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val service = MatchInterestService(
        matchService,
        agentService,
        customerService,
        mandateService,
        notifier,
        eventPublisher,
        DefaultKVLogger(),
    )

    private fun match(
        mandateStatus: MandateStatus = MandateStatus.APPROVED,
    ) = MatchEntity(
        id = 9L,
        tenantId = 1L,
        mandate = MandateEntity(id = 1L, tenantId = 1L, customerId = 10L, agentId = 20L, status = mandateStatus),
        listing = ListingEntity(id = 2L),
        status = MatchStatus.ACCEPTED,
    )

    @BeforeTest
    fun setUp() {
        doReturn(AgentEntity(id = 20L)).whenever(agentService).findById(20L)
        doReturn(CustomerEntity(id = 10L)).whenever(customerService).findById(10L)
    }

    @Test
    fun `a changed match is notified and its customer e-mailed`() {
        doReturn(Interest(match(), true)).whenever(matchService).acceptInterest(1L, 2L)

        assertEquals(9L, service.registerInterest(1L, 2L))

        verify(notifier).send(any(), any(), any(), any())
        verify(eventPublisher).publishEvent(MatchInterestAcceptedEvent(9L, 1L))
    }

    @Test
    fun `an already accepted match is neither notified nor e-mailed`() {
        doReturn(Interest(match(mandateStatus = MandateStatus.ASSIGNED), false)).whenever(matchService).acceptInterest(1L, 2L)

        service.registerInterest(1L, 2L)

        verify(notifier, never()).send(any(), any(), any(), any())
        verify(eventPublisher, never()).publishEvent(any<MatchInterestAcceptedEvent>())
    }

    @Test
    fun `a notifier failure does not fail the interest`() {
        doReturn(Interest(match(), true)).whenever(matchService).acceptInterest(1L, 2L)
        doThrow(RuntimeException("smtp down")).whenever(notifier).send(any(), any(), any(), any())

        assertNotNull(service.registerInterest(1L, 2L))
        verify(eventPublisher).publishEvent(MatchInterestAcceptedEvent(9L, 1L))
    }

    @Test
    fun `proposeOrigin accepts the origin and e-mails the customer, never the agent`() {
        doReturn(approvedMandate(originListingId = 2L)).whenever(mandateService).findByIdOrNull(1L)
        doReturn(Interest(match(), true)).whenever(matchService).acceptInterest(1L, 2L)

        assertEquals(9L, service.proposeOrigin(1L))

        verify(notifier, never()).send(any(), any(), any(), any())
        verify(eventPublisher).publishEvent(MatchInterestAcceptedEvent(9L, 1L))
    }

    @Test
    fun `no origin, nothing accepted`() {
        doReturn(approvedMandate(originListingId = null)).whenever(mandateService).findByIdOrNull(1L)

        assertNull(service.proposeOrigin(1L))

        verify(matchService, never()).acceptInterest(any(), any())
    }

    @Test
    fun `a sold listing is not accepted`() {
        doReturn(approvedMandate(originListingId = 2L)).whenever(mandateService).findByIdOrNull(1L)
        doReturn(null as Interest?).whenever(matchService).acceptInterest(1L, 2L)

        assertNull(service.proposeOrigin(1L))
    }

    private fun approvedMandate(originListingId: Long?) = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        originListingId = originListingId,
        status = MandateStatus.APPROVED,
    )
}
