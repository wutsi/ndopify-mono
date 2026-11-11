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
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.match.server.mail.CoBrokerageInterestMailet
import com.wutsi.ndopify.match.server.mail.CoBrokerageMatchMailet
import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals

class CoBrokerageNotificationServiceTest {
    private val dao = mock<MatchRepository>()
    private val agentService = mock<AgentService>()
    private val mailet = mock<CoBrokerageMatchMailet>()
    private val interestMailet = mock<CoBrokerageInterestMailet>()
    private val clock = Clock.systemUTC()
    private val service = CoBrokerageNotificationService(dao, agentService, mailet, interestMailet, clock)

    private val agent20 = AgentEntity(id = 20L)
    private val agent21 = AgentEntity(id = 21L)
    private val listingAgent = AgentEntity(id = 30L)

    @Test
    fun `sends one email per search agent and marks the matches`() {
        val first = MatchEntity(id = 1L, agentId = 20L, status = MatchStatus.ACTIVE, coBrokerage = true)
        val second = first.copy(id = 2L)
        val third = first.copy(id = 3L, agentId = 21L)
        doReturn(listOf(first, second, third)).whenever(dao)
            .findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)
        doReturn(agent20).whenever(agentService).findByIdOrNull(20L)
        doReturn(agent21).whenever(agentService).findByIdOrNull(21L)

        assertEquals(2, service.notifySearchAgents())

        verify(mailet).send(agent20, listOf(first, second))
        verify(mailet).send(agent21, listOf(third))
        verify(dao).markNotified(eq(listOf(1L, 2L)), any())
        verify(dao).markNotified(eq(listOf(3L)), any())
    }

    @Test
    fun `a failed email leaves the matches for the next run`() {
        val first = MatchEntity(id = 1L, agentId = 20L, status = MatchStatus.ACTIVE, coBrokerage = true)
        val second = first.copy(id = 2L)
        val third = first.copy(id = 3L, agentId = 21L)
        doReturn(listOf(first, second, third)).whenever(dao)
            .findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)
        doReturn(agent20).whenever(agentService).findByIdOrNull(20L)
        doReturn(agent21).whenever(agentService).findByIdOrNull(21L)
        doThrow(RuntimeException("SMTP down")).whenever(mailet).send(eq(agent20), any())

        assertEquals(1, service.notifySearchAgents())

        verify(dao, never()).markNotified(eq(listOf(1L, 2L)), any())
        verify(dao).markNotified(eq(listOf(3L)), any())
    }

    @Test
    fun `an unknown agent is skipped without marking`() {
        doReturn(listOf(MatchEntity(id = 1L, agentId = 20L, status = MatchStatus.ACTIVE, coBrokerage = true)))
            .whenever(dao).findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)
        doReturn(null).whenever(agentService).findByIdOrNull(20L)

        assertEquals(0, service.notifySearchAgents())

        verify(mailet, never()).send(any(), any())
        verify(dao, never()).markNotified(any(), any())
    }

    @Test
    fun `nothing pending`() {
        doReturn(emptyList<MatchEntity>()).whenever(dao)
            .findCoBrokerageToNotify(MatchStatus.ACTIVE, MandateStatus.APPROVED, ListingStatus.PUBLISHED)

        assertEquals(0, service.notifySearchAgents())

        verify(mailet, never()).send(any(), any())
        verify(dao, never()).markNotified(any(), any())
    }

    @Test
    fun `sends one email per listing agent and marks the interest`() {
        val first = MatchEntity(id = 4L, agentId = 20L, status = MatchStatus.SHORTLISTED, coBrokerage = true,
            listing = ListingEntity(id = 101L, agent = AgentEntity(id = 30L)))
        val second = first.copy(id = 5L, status = MatchStatus.PROPOSED)
        doReturn(listOf(first, second)).whenever(dao)
            .findCoBrokerageInterestToNotify(any(), eq(MandateStatus.APPROVED), eq(ListingStatus.PUBLISHED))
        doReturn(listingAgent).whenever(agentService).findByIdOrNull(30L)

        assertEquals(1, service.notifyListingAgents())

        verify(interestMailet).send(listingAgent, listOf(first, second))
        verify(dao).markListingAgentNotified(eq(listOf(4L, 5L)), any())
    }

    @Test
    fun `a failed reverse email leaves the interest for the next run`() {
        val first = MatchEntity(id = 4L, agentId = 20L, status = MatchStatus.SHORTLISTED, coBrokerage = true,
            listing = ListingEntity(id = 101L, agent = AgentEntity(id = 30L)))
        doReturn(listOf(first)).whenever(dao)
            .findCoBrokerageInterestToNotify(any(), eq(MandateStatus.APPROVED), eq(ListingStatus.PUBLISHED))
        doReturn(listingAgent).whenever(agentService).findByIdOrNull(30L)
        doThrow(RuntimeException("SMTP down")).whenever(interestMailet).send(eq(listingAgent), any())

        assertEquals(0, service.notifyListingAgents())

        verify(dao, never()).markListingAgentNotified(any(), any())
    }

    @Test
    fun `an unknown listing agent is skipped without marking`() {
        val first = MatchEntity(id = 4L, agentId = 20L, status = MatchStatus.SHORTLISTED, coBrokerage = true,
            listing = ListingEntity(id = 101L, agent = AgentEntity(id = 30L)))
        doReturn(listOf(first)).whenever(dao)
            .findCoBrokerageInterestToNotify(any(), eq(MandateStatus.APPROVED), eq(ListingStatus.PUBLISHED))
        doReturn(null).whenever(agentService).findByIdOrNull(30L)

        assertEquals(0, service.notifyListingAgents())

        verify(interestMailet, never()).send(any(), any())
        verify(dao, never()).markListingAgentNotified(any(), any())
    }
}
