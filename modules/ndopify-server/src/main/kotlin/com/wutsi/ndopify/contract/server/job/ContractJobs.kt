package com.wutsi.ndopify.contract.server.job

import com.wutsi.ndopify.contract.server.service.ContractService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * The contracts' jobs, for each active tenant. Contracts are tenant-scoped (`@TenantId`), and a scheduler thread has no
 * tenant of its own: each tenant is processed with it set in [TenantContext], removed afterwards since the scheduler
 * threads are reused.
 */
@Service
class ContractJobs(
    private val service: ContractService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ContractJobs::class.java)
    }

    /** Expires the contracts not signed before their deadline, and the signed ones past their end date. */
    @Scheduled(cron = "\${ndopify.contract.expire.cron}")
    fun expire() {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                val count = service.expireDue()
                LOGGER.info("Tenant#$tenantId - ContractJobs.expire() completed: $count contract(s)")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - ContractJobs.expire() failed", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }
}
