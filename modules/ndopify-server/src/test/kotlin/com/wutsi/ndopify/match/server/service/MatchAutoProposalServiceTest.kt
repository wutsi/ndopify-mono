package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchAutoProposalServiceTest {
    private val dao = mock<MatchRepository>()
    private val proposalService = mock<MatchProposalService>()
    private val clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC)
    private val service = MatchAutoProposalService(
        dao = dao,
        proposalService = proposalService,
        clock = clock,
        afterHours = 24,
        batchSize = 100,
    )

    @Test
    fun `proposes stalled matches grouped by mandate, best score first`() {
        doReturn(
            listOf(
                match(id = 11, mandateId = 1, score = 0.5),
                match(id = 10, mandateId = 1, score = 0.9),
                match(id = 21, mandateId = 2, score = 0.7),
            ),
        ).whenever(dao).findStalled(
            status = MatchStatus.ACTIVE,
            before = Date.from(Instant.parse("2026-11-13T12:00:00Z")),
            mandateStatus = MandateStatus.APPROVED,
            listingStatus = ListingStatus.PUBLISHED,
            page = PageRequest.of(0, 100),
        )
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        assertEquals(3, service.autoProposeDue())

        verify(proposalService).propose(listOf(10L, 11L))
        verify(proposalService).propose(listOf(21L))
    }

    @Test
    fun `keeps the best 20 per mandate`() {
        doReturn((1L..25L).map { match(id = it, mandateId = 1, score = it.toDouble()) })
            .whenever(dao).findStalled(any(), any(), any(), any(), any())
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        assertEquals(20, service.autoProposeDue())

        verify(proposalService).propose((25L downTo 6L).toList())
    }

    @Test
    fun `the top 20 are the best ranks, not the best scores`() {
        doReturn(
            (1L..25L).map { match(id = it, mandateId = 1, score = (26L - it).toDouble(), rankingScore = it.toDouble()) },
        ).whenever(dao).findStalled(any(), any(), any(), any(), any())
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        assertEquals(20, service.autoProposeDue())

        verify(proposalService).propose((25L downTo 6L).toList())
    }

    @Test
    fun `equal ranks fall back to the relevance, then the id`() {
        doReturn(
            listOf(
                match(id = 12, mandateId = 1, score = 0.7, rankingScore = 0.8),
                match(id = 11, mandateId = 1, score = 0.9, rankingScore = 0.8),
                match(id = 13, mandateId = 1, score = 0.7, rankingScore = 0.8),
            ),
        ).whenever(dao).findStalled(any(), any(), any(), any(), any())
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        service.autoProposeDue()

        verify(proposalService).propose(listOf(11L, 12L, 13L))
    }

    @Test
    fun `an optimistic-lock conflict skips that mandate only`() {
        doReturn(listOf(match(id = 10, mandateId = 1, score = 0.9), match(id = 20, mandateId = 2, score = 0.8)))
            .whenever(dao).findStalled(any(), any(), any(), any(), any())
        doThrow(ObjectOptimisticLockingFailureException(MatchEntity::class.java, 10L))
            .whenever(proposalService).propose(listOf(10L))
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(listOf(20L))

        assertEquals(1, service.autoProposeDue())

        verify(proposalService).propose(listOf(20L))
    }

    private fun match(id: Long, mandateId: Long, score: Double, rankingScore: Double = score) = MatchEntity(
        id = id,
        mandate = MandateEntity(id = mandateId),
        listing = ListingEntity(id = 1000 + id),
        agentId = 20,
        status = MatchStatus.ACTIVE,
        score = score,
        rankingScore = rankingScore,
        createdAt = Date.from(Instant.parse("2026-11-13T10:00:00Z")),
    )
}
