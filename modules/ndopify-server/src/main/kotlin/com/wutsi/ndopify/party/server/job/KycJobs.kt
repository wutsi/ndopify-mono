package com.wutsi.ndopify.party.server.job

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.server.service.KycMailet
import com.wutsi.ndopify.party.server.service.KycService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled

class KycJobs(
    private val service: KycService,
    private val mailet: KycMailet,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycJobs::class.java)
    }

    @Scheduled(cron = "\${ndopify.party.kyc.verify.cron}")
    fun verify() {
        LOGGER.info("Running KycJobs.verify()")

        val cases = service.search(
            SearchKycCaseRequest(
                statuses = listOf(KycStatus.SUBMITTED),
                limit = 100 // Process a max of 100 cases at a time to avoid overloading the system
            )
        )

        var verified = 0
        var errors = 0
        cases.forEach { case ->
            try {
                val xcase = service.verify(case)
                verified++

                mailet.send(xcase)
            } catch (ex: Exception) {
                LOGGER.warn("Unable to verify KYC case ${case.id}", ex)
                errors++
            }
        }
        LOGGER.info("KycJobs.verify() completed: $verified verified, $errors errors")
    }
}
