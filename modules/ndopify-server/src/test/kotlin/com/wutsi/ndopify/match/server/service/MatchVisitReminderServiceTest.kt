package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.domain.CustomerEntity
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.MatchVisitReminderMailet
import com.wutsi.ndopify.party.server.domain.PartyEntity
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchVisitReminderServiceTest {
    private val dao = mock<MatchRepository>()
    private val customerService = mock<CustomerService>()
    private val agentService = mock<AgentService>()
    private val mailet = mock<MatchVisitReminderMailet>()
    private val clock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC)
    private val now = Date(clock.millis())
    private val tx = TransactionTemplate(
        mock<PlatformTransactionManager> {
            on { getTransaction(any()) } doReturn mock<TransactionStatus>()
        },
    )
    private val service = MatchVisitReminderService(dao, customerService, agentService, mailet, tx, clock, 3, 100)

    private val approvedSearch = MandateEntity(
        id = 1L,
        tenantId = 1L,
        customerId = 10L,
        agentId = 20L,
        status = MandateStatus.APPROVED,
    )
    private val publishedListing = ListingEntity(id = 101L, title = "Nice apartment", status = ListingStatus.PUBLISHED)
    private val accepted = MatchEntity(
        id = 1L,
        status = MatchStatus.ACCEPTED,
        answeredAt = Date(now.time - 3 * DAY_MILLIS),
        mandate = approvedSearch,
        listing = publishedListing,
        agentId = 20L,
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray.sponsible@gmail.com"),
    )
    private val agent = AgentEntity(id = 20L, party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"))

    @Test
    fun `reminds each due match, then marks it`() {
        givenDue(accepted)
        givenFound(accepted)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)

        assertEquals(1, service.remindDue())

        verify(mailet).send(accepted, customer, agent)
        verify(dao).save(accepted.copy(visitRemindedAt = now, modifiedAt = now))
    }

    @Test
    fun `an open visit - no reminder`() {
        givenDue(accepted)
        givenFound(accepted)
        doReturn(true).whenever(dao).existsOpenVisit(1L, MatchVisitReminderService.OPEN_VISIT_STATUSES)

        assertEquals(0, service.remindDue())

        verify(mailet, never()).send(any(), any(), any())
        verify(dao, never()).save(any())
    }

    @Test
    fun `already reminded - no reminder`() {
        givenDue(accepted)
        givenFound(accepted.copy(visitRemindedAt = Date(now.time - DAY_MILLIS)))

        assertEquals(0, service.remindDue())

        verify(mailet, never()).send(any(), any(), any())
        verify(dao, never()).save(any())
    }

    @Test
    fun `changed while selecting - skipped`() {
        givenDue(accepted)
        givenFound(accepted.copy(status = MatchStatus.REJECTED))

        assertEquals(0, service.remindDue())

        verify(mailet, never()).send(any(), any(), any())
        verify(dao, never()).save(any())
    }

    @Test
    fun `a failed send is left for the next run`() {
        givenDue(accepted)
        givenFound(accepted)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(eq(accepted), eq(customer), eq(agent))

        assertEquals(0, service.remindDue())

        verify(dao, never()).save(any())
    }

    @Test
    fun `no e-mail - marked without sending`() {
        val withoutEmail = customer.copy(party = customer.party.copy(email = ""))
        givenDue(accepted)
        givenFound(accepted)
        doReturn(withoutEmail).whenever(customerService).findById(10L)

        assertEquals(0, service.remindDue())

        verify(mailet, never()).send(any(), any(), any())
        verify(dao).save(accepted.copy(visitRemindedAt = now, modifiedAt = now))
    }

    @Test
    fun `the other matches still go out after one failure`() {
        val second = accepted.copy(id = 2L, listing = publishedListing.copy(id = 102L))
        givenDue(accepted, second)
        givenFound(accepted)
        givenFound(second)
        doReturn(customer).whenever(customerService).findById(10L)
        doReturn(agent).whenever(agentService).findById(20L)
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(eq(accepted), any(), any())

        assertEquals(1, service.remindDue())

        verify(mailet).send(second, customer, agent)
        verify(dao).save(second.copy(visitRemindedAt = now, modifiedAt = now))
        verify(dao, never()).save(accepted.copy(visitRemindedAt = now, modifiedAt = now))
    }

    private fun givenDue(vararg matches: MatchEntity) {
        doReturn(matches.toList()).whenever(dao).findVisitReminderDue(
            eq(MatchStatus.ACCEPTED),
            eq(Date(now.time - 3 * DAY_MILLIS)),
            eq(MandateStatus.APPROVED),
            eq(ListingStatus.PUBLISHED),
            eq(MatchVisitReminderService.OPEN_VISIT_STATUSES),
            eq(PageRequest.of(0, 100)),
        )
    }

    private fun givenFound(match: MatchEntity) {
        doReturn(Optional.of(match)).whenever(dao).findById(match.id!!)
        doReturn(false).whenever(dao).existsOpenVisit(match.id!!, MatchVisitReminderService.OPEN_VISIT_STATUSES)
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
