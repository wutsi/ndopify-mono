package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.match.server.service.MatchNotificationService
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
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchJobs::class.java)
    }

    /** E-mails the agents about their new matches. */
    @Scheduled(cron = "\${ndopify.match.notify.cron}")
    fun notifyAgents() {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                val count = notificationService.notifyAgents()
                LOGGER.info("Tenant#$tenantId - MatchJobs.notifyAgents() completed: $count agent(s)")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - MatchJobs.notifyAgents() failed", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }
}
