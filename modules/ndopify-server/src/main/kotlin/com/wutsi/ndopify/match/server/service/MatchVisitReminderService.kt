package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.mail.MatchVisitReminderMailet
import com.wutsi.ndopify.visit.dto.VisitStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

/**
 * Reminds the customers who accepted a match but didn't book a visit: one e-mail per ACCEPTED match with no open visit
 * ([OPEN_VISIT_STATUSES]) whose customer answered it at least `delay-days` ago, whose mandate is APPROVED and listing
 * PUBLISHED, oldest answer first, at most `batch-size` per run. The row is marked (`visitRemindedAt`) only after a
 * successful send, so a failure retries on the next run; a customer without e-mail is marked without one, so it can't
 * clog the batch. A match answered, closed or visited meanwhile fails the re-check or its `@Version` and is skipped.
 * One reminder per match; the agent isn't told.
 */
@Service
class MatchVisitReminderService(
    private val dao: MatchRepository,
    private val customerService: CustomerService,
    private val agentService: AgentService,
    private val mailet: MatchVisitReminderMailet,
    private val tx: TransactionTemplate,
    private val clock: Clock,
    @param:Value("\${ndopify.match.visit-reminder.delay-days}") private val delayDays: Long,
    @param:Value("\${ndopify.match.visit-reminder.batch-size}") private val batchSize: Int,
) {
    companion object {
        val OPEN_VISIT_STATUSES = setOf(VisitStatus.REQUESTED, VisitStatus.CONFIRMED, VisitStatus.CHECKED_IN)
        private val LOGGER = LoggerFactory.getLogger(MatchVisitReminderService::class.java)
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }

    /** Returns the number of matches e-mailed. */
    fun remindDue(): Int {
        val now = Date(clock.millis())
        val matches = dao.findVisitReminderDue(
            status = MatchStatus.ACCEPTED,
            before = Date(now.time - delayDays * DAY_MILLIS),
            mandateStatus = MandateStatus.APPROVED,
            listingStatus = ListingStatus.PUBLISHED,
            visitStatuses = OPEN_VISIT_STATUSES,
            page = PageRequest.of(0, batchSize),
        )
        return matches.count { match ->
            try {
                tx.execute { remind(match.id!!, now) } ?: false
            } catch (ex: ObjectOptimisticLockingFailureException) {
                LOGGER.info("Match#${match.id} - Changed while reminding, left as is")
                false
            } catch (ex: Exception) {
                LOGGER.warn("Match#${match.id} - Unable to remind", ex)
                false
            }
        }
    }

    // In its own transaction: reloaded and re-checked, since the selection is a snapshot and the customer may have
    // booked a visit meanwhile.
    private fun remind(id: Long, now: Date): Boolean {
        val current = dao.findById(id).orElse(null) ?: return false
        if (current.status != MatchStatus.ACCEPTED ||
            current.visitRemindedAt != null ||
            current.mandate.status != MandateStatus.APPROVED ||
            current.listing.status != ListingStatus.PUBLISHED ||
            dao.existsOpenVisit(id, OPEN_VISIT_STATUSES)
        ) {
            return false
        }

        val customer = customerService.findById(current.mandate.customerId)
        val agent = agentService.findById(current.agentId)
        if (customer.party.email.isBlank()) {
            // Nothing to retry, and leaving it null would keep it at the front of the batch window forever
            dao.save(current.copy(visitRemindedAt = now, modifiedAt = now))
            return false
        }

        mailet.send(current, customer, agent)
        dao.save(current.copy(visitRemindedAt = now, modifiedAt = now))
        return true
    }
}
