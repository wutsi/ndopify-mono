package com.wutsi.ndopify.visit.server.job

import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.visit.server.service.VisitService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * The visits' jobs, for each active tenant: a request not scheduled in 48 h expires, a confirmed visit in the next 24 h is
 * reminded, and a confirmed visit whose agent didn't check in 4 h after the time is a no-show. A scheduler thread has no
 * tenant of its own: each tenant is processed with it set in [TenantContext], removed afterwards since the threads are
 * reused.
 */
@Service
class VisitJobs(
    private val service: VisitService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(VisitJobs::class.java)
    }

    /** Expires the visit requests the agent didn't schedule in time. */
    @Scheduled(cron = "\${ndopify.visit.expire.cron}")
    fun expire() {
        forEachTenant("expire", "visit(s)") { service.expireDue() }
    }

    /** Reminds both sides of the visits starting within 24 h. */
    @Scheduled(cron = "\${ndopify.visit.reminder.cron}")
    fun remind() {
        forEachTenant("remind", "visit(s)") { service.remindDue() }
    }

    /** Marks the visits whose agent didn't check in. */
    @Scheduled(cron = "\${ndopify.visit.no-show.cron}")
    fun noShow() {
        forEachTenant("noShow", "visit(s)") { service.markAgentNoShows() }
    }

    private fun forEachTenant(job: String, unit: String, run: () -> Int) {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                val count = run()
                LOGGER.info("Tenant#$tenantId - VisitJobs.$job() completed: $count $unit")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - VisitJobs.$job() failed", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }
}
