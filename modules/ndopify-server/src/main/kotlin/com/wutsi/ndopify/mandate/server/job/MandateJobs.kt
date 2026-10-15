package com.wutsi.ndopify.mandate.server.job

import com.wutsi.ndopify.mandate.server.service.AssignmentService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * The dispatch's jobs, for each active tenant. Assignments and mandates are tenant-scoped (`@TenantId`), and a scheduler thread has no tenant of its own: each tenant is processed
 * with it set in [TenantContext], removed afterwards since the scheduler threads are reused.
 */
@Service
class MandateJobs(
    private val service: AssignmentService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateJobs::class.java)
    }

    /** Expires the offers past their TTL; their mandates go to the next agent. */
    @Scheduled(cron = "\${ndopify.mandate.assignment.expire.cron}")
    fun expire() = forEachTenant("expire") { service.expireDue() }

    /** Offers the mandates waiting without an offer: came at night (first run after it), or had no candidate yet. */
    @Scheduled(cron = "\${ndopify.mandate.assignment.dispatch.cron}")
    fun dispatch() = forEachTenant("dispatch") { service.dispatchPending() }

    private fun forEachTenant(name: String, block: () -> Int) {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                val count = block()
                LOGGER.info("Tenant#$tenantId - MandateJobs.$name() completed: $count mandate(s)")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - MandateJobs.$name() failed", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }
}
