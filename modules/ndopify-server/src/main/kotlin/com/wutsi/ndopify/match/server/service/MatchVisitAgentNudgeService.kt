package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

/**
 * Nudges the agents whose customers accepted a match but still have no visit: one message per ACCEPTED match with no
 * open visit ([MatchVisitReminderService.OPEN_VISIT_STATUSES]) answered at least `delay-hours` ago, whose mandate is
 * APPROVED and listing PUBLISHED, oldest answer first, at most `batch-size` per run. The row is marked
 * (`agentVisitNudgedAt`) only after the notifier returns, so a failure retries on the next run; an agent without a
 * channel is marked too, so it can't clog the batch. A match answered, closed or visited meanwhile fails the re-check or
 * its `@Version` and is skipped. One nudge per match.
 */
@Service
class MatchVisitAgentNudgeService(
    private val dao: MatchRepository,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val notifier: MatchVisitAgentNudgeNotifier,
    private val tx: TransactionTemplate,
    private val clock: Clock,
    @param:Value("\${ndopify.match.agent-visit-nudge.delay-hours}") private val delayHours: Long,
    @param:Value("\${ndopify.match.agent-visit-nudge.batch-size}") private val batchSize: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchVisitAgentNudgeService::class.java)
        private const val HOUR_MILLIS = 60L * 60 * 1000
    }

    /** Returns the number of agents reached. */
    fun nudgeDue(): Int {
        val now = Date(clock.millis())
        val matches = dao.findAgentVisitNudgeDue(
            status = MatchStatus.ACCEPTED,
            before = Date(now.time - delayHours * HOUR_MILLIS),
            mandateStatus = MandateStatus.APPROVED,
            listingStatus = ListingStatus.PUBLISHED,
            visitStatuses = MatchVisitReminderService.OPEN_VISIT_STATUSES,
            page = PageRequest.of(0, batchSize),
        )
        return matches.count { match ->
            try {
                tx.execute { nudge(match.id!!, now) } ?: false
            } catch (ex: ObjectOptimisticLockingFailureException) {
                LOGGER.info("Match#${match.id} - Changed while nudging, left as is")
                false
            } catch (ex: Exception) {
                LOGGER.warn("Match#${match.id} - Unable to nudge", ex)
                false
            }
        }
    }

    // In its own transaction: reloaded and re-checked, since the selection is a snapshot and the customer may have
    // booked a visit meanwhile.
    private fun nudge(id: Long, now: Date): Boolean {
        val current = dao.findById(id).orElse(null) ?: return false
        if (current.status != MatchStatus.ACCEPTED || current.agentVisitNudgedAt != null ||
            current.mandate.status != MandateStatus.APPROVED ||
            current.listing.status != ListingStatus.PUBLISHED ||
            dao.existsOpenVisit(id, MatchVisitReminderService.OPEN_VISIT_STATUSES)
        ) {
            return false
        }

        val reached = notifier.send(
            current,
            customerService.findById(current.mandate.customerId),
            agentService.findById(current.agentId),
        )
        // Marked even when unreachable: no channel to retry, and leaving it null would keep it at the front of the batch
        dao.save(current.copy(agentVisitNudgedAt = now, modifiedAt = now))
        return reached
    }
}
