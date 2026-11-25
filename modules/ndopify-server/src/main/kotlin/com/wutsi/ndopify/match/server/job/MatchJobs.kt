package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.match.server.service.CoBrokerageNotificationService
import com.wutsi.ndopify.match.server.service.MatchAutoProposalService
import com.wutsi.ndopify.match.server.service.MatchNotificationService
import com.wutsi.ndopify.match.server.service.MatchProposalService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.match.server.service.MatchVisitAgentNudgeService
import com.wutsi.ndopify.match.server.service.MatchVisitReminderService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * The matches' jobs, for each active tenant. Matches are tenant-scoped (`@TenantId`), and a scheduler thread has no
 * tenant of its own: each tenant is processed with it set in [TenantContext], removed afterwards since the scheduler
 * threads are reused.
 */
@Service
class MatchJobs(
    private val notificationService: MatchNotificationService,
    private val coBrokerageNotificationService: CoBrokerageNotificationService,
    private val proposalService: MatchProposalService,
    private val autoProposalService: MatchAutoProposalService,
    private val matchService: MatchService,
    private val reminderService: MatchVisitReminderService,
    private val agentNudgeService: MatchVisitAgentNudgeService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchJobs::class.java)
    }

    /** E-mails the agents about their new matches: their own, and the co-brokerage ones of another agent's listing. */
    @Scheduled(cron = "\${ndopify.match.notify.cron}")
    fun notifyAgents() {
        forEachTenant("notifyAgents", "agent(s)") {
            notificationService.notifyAgents() + coBrokerageNotificationService.notifySearchAgents()
        }
    }

    /** Re-matches the open searches: catches up on the runs lost, re-scores against today. */
    @Scheduled(cron = "\${ndopify.match.rematch.cron}")
    fun rematch() {
        forEachTenant("rematch", "match(es)") { matchService.matchAll() }
    }

    /** Expires the proposals the customers didn't answer in time. */
    @Scheduled(cron = "\${ndopify.match.expire.cron}")
    fun expire() {
        forEachTenant("expire", "match(es)") { proposalService.expireDue() }
    }

    /** Proposes the matches the agents left untouched for 24h: the customers receive them without waiting. */
    @Scheduled(cron = "\${ndopify.match.auto-propose.cron}")
    fun autoPropose() {
        forEachTenant("autoPropose", "match(es)") { autoProposalService.autoProposeDue() }
    }

    /** Reminds the customers who accepted a match but didn't book a visit. */
    @Scheduled(cron = "\${ndopify.match.visit-reminder.cron}")
    fun remindAcceptedWithoutVisit() {
        forEachTenant("remindAcceptedWithoutVisit", "match(es)") { reminderService.remindDue() }
    }

    /** Nudges the agents whose accepted match still has no visit after 48 h. */
    @Scheduled(cron = "\${ndopify.match.agent-visit-nudge.cron}")
    fun nudgeAcceptedWithoutVisitAgent() {
        forEachTenant("nudgeAcceptedWithoutVisitAgent", "match(es)") { agentNudgeService.nudgeDue() }
    }

    private fun forEachTenant(job: String, unit: String, run: () -> Int) {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                val count = run()
                LOGGER.info("Tenant#$tenantId - MatchJobs.$job() completed: $count $unit")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - MatchJobs.$job() failed", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }
}
