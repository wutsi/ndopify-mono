package com.wutsi.ndopify.agent.server.cron

import com.wutsi.ndopify.agent.dto.SearchMobileChangeRequest
import com.wutsi.ndopify.agent.server.service.MobileChangeService
import com.wutsi.ndopify.refdata.dto.KycStatus
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import kotlin.time.measureTime

@Service
class MobileChangeJobs(
    private val service: MobileChangeService
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MobileChangeJobs::class.java)
    }

    @Scheduled(cron = "\${ndopify.agent.cron.mobile-change-verify.expression}")
    fun verify() {
        LOGGER.info("Verifying mobile change requests...")
        var total = 0
        var errors = 0
        val duration = measureTime {
            val changes = service.search(
                request = SearchMobileChangeRequest(
                    status = KycStatus.PENDING,
                    limit = 1000
                ),
                tenantId = null,
            )
            total = changes.size

            changes.forEach { change ->
                try {
                    LOGGER.info("Verifying mobile change request ${change.id}")
                    service.verify(change)
                } catch (ex: Exception) {
                    errors++
                    LOGGER.error("Unable to verify mobile change request ${change.id}", ex)
                }
            }
        }
        LOGGER.info("Done. $total change(s) verified in ${duration.inWholeSeconds}s, with $errors error(s).")
    }
}
