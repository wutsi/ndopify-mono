package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

/**
 * Auto-proposes the matches an agent left untouched for `after-hours`: the customer receives the same proposal e-mail
 * as a manual propose and can answer it. Each mandate is proposed in its own transaction
 * ([MatchProposalService.propose]), so a failure, or an agent answering meanwhile (optimistic lock), skips that mandate
 * only. Called by `MatchJobs.autoPropose`.
 */
@Service
class MatchAutoProposalService(
    private val dao: MatchRepository,
    private val proposalService: MatchProposalService,
    private val clock: Clock,
    @param:Value("\${ndopify.match.auto-propose.after-hours}") private val afterHours: Long,
    @param:Value("\${ndopify.match.auto-propose.batch-size}") private val batchSize: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchAutoProposalService::class.java)
        const val MAX_PER_PROPOSAL = 20 // ProposeMatchRequest.matchIds: @Size(max = 20)
        private const val HOUR_MILLIS = 3_600_000L
    }

    fun autoProposeDue(): Int {
        val before = Date(clock.millis() - afterHours * HOUR_MILLIS)
        val stalled = dao.findStalled(
            status = MatchStatus.ACTIVE,
            before = before,
            mandateStatus = MandateStatus.APPROVED,
            listingStatus = ListingStatus.PUBLISHED,
            page = PageRequest.of(0, batchSize),
        )
        return stalled.groupBy { it.mandate.id!! }.values.sumOf { matches -> propose(matches) }
    }

    private fun propose(matches: List<MatchEntity>): Int {
        val mandateId = matches.first().mandate.id!!
        val ids = matches.sortedByDescending { it.score }.take(MAX_PER_PROPOSAL).map { it.id!! }
        return try {
            proposalService.propose(ids)
            LOGGER.info("Mandate#$mandateId - Auto-proposed ${ids.size} match(es)")
            ids.size
        } catch (ex: Exception) {
            // The agent answered, the mandate closed, the listing was taken down... The next run sees the new state
            LOGGER.warn("Mandate#$mandateId - Unable to auto-propose its matches", ex)
            0
        }
    }
}
