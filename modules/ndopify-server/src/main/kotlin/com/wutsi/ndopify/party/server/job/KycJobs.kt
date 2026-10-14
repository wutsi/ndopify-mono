package com.wutsi.ndopify.party.server.job

import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.SearchKycCaseRequest
import com.wutsi.ndopify.party.server.service.KycService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class KycJobs(
    private val service: KycService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycJobs::class.java)
    }

    /**
     * Verifies the SUBMITTED cases of each active tenant. KYC cases are tenant-scoped (`@TenantId`), and a scheduler
     * thread has no tenant of its own: each tenant's cases are processed with it set in [TenantContext], removed
     * afterwards since the scheduler threads are reused.
     */
    @Scheduled(cron = "\${ndopify.party.kyc.verify.cron}")
    fun verify() {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                verify(tenantId)
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be verified
                LOGGER.warn("Tenant#$tenantId - Unable to verify the KYC cases", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }

    private fun verify(tenantId: Long) {
        LOGGER.info("Tenant#$tenantId - Running KycJobs.verify()")

        // First, so that the interrupted verifications sent back to SUBMITTED are retried by this run
        try {
            val recovered = service.recoverInterrupted()
            if (recovered.isNotEmpty()) {
                LOGGER.warn("Tenant#$tenantId - ${recovered.size} interrupted verification(s) recovered")
            }
        } catch (ex: Exception) {
            LOGGER.warn("Tenant#$tenantId - Unable to recover the interrupted KYC verifications", ex)
        }

        // Only SUBMITTED cases: the others are still being uploaded, being verified, or already resolved
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
                // The status email is sent by KycEventListener, once the case is resolved
                service.verify(case.id)
                verified++
            } catch (ex: Exception) {
                LOGGER.warn("Tenant#$tenantId - Unable to verify KYC case ${case.id}", ex)
                errors++
            }
        }
        LOGGER.info("Tenant#$tenantId - KycJobs.verify() completed: $verified verified, $errors errors")
    }
}
