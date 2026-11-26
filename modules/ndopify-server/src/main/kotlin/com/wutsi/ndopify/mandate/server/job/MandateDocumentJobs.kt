package com.wutsi.ndopify.mandate.server.job

import com.wutsi.ndopify.mandate.server.service.document.MandateDocumentExtractionService
import com.wutsi.ndopify.platform.logger.DefaultKVLogger
import com.wutsi.ndopify.platform.logger.KVLoggerThreadLocal
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Reads the due mandate documents, for each active tenant: the same loop as [MandateJobs], since documents are
 * tenant-scoped (`@TenantId`) and a scheduler thread has no tenant of its own. Each tenant runs with it set in
 * [TenantContext] and a [DefaultKVLogger] installed, both removed afterwards.
 */
@Service
class MandateDocumentJobs(
    private val service: MandateDocumentExtractionService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDocumentJobs::class.java)
    }

    /** Extracts the documents not read yet, and the ones left `EXTRACTING` by an interrupted run. */
    @Scheduled(cron = "\${ndopify.mandate.document.extract.cron}")
    fun extract() {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            val logger = DefaultKVLogger()
            KVLoggerThreadLocal.set(logger)
            try {
                TenantContext.set(tenantId)
                logger.add("job", "MandateDocumentJobs.extract")
                logger.add("tenant_id", tenantId)
                val count = service.extractDue(tenant.timeZone)
                logger.add("count", count)
                LOGGER.info("Tenant#$tenantId - MandateDocumentJobs.extract() completed: $count document(s)")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                logger.setException(ex)
                LOGGER.warn("Tenant#$tenantId - MandateDocumentJobs.extract() failed", ex)
            } finally {
                logger.log()
                TenantContext.remove()
                KVLoggerThreadLocal.remove()
            }
        }
    }
}
