package com.wutsi.ndopify.agent.server.cron

import com.wutsi.ndopify.agent.dto.SearchIdentityChangeRequest
import com.wutsi.ndopify.agent.server.service.IdentityChangeService
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.time.measureTime

@Service
class IdentityChangeJobs(
    private val service: IdentityChangeService
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(IdentityChangeJobs::class.java)
    }

    @Scheduled(cron = "\${ndopify.agent.cron.identity-change-verify.expression}")
    fun verify() {
        LOGGER.info("Verifying identity change requests...")
        var total = 0
        var errors = 0
        val duration = measureTime {
            val changes = service.search(
                request = SearchIdentityChangeRequest(
                    status = KycStatus.PENDING,
                    limit = 1000
                ),
                tenantId = null,
            )
            total = changes.size

            changes.forEach { change ->
                try {
                    LOGGER.info("Verifying identity change request ${change.id}")
                    service.verify(change)
                } catch (ex: Exception) {
                    errors++
                    LOGGER.error("Unable to verify identity change request ${change.id}", ex)
                }
            }
        }
        LOGGER.info("Done. $total change(s) verified in ${duration.inWholeSeconds}s, with $errors error(s).")
    }
}
