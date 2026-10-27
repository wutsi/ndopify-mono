package com.wutsi.ndopify.listing.server.job

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.listing.server.service.verifier.ListingVerifierService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class ListingJobs(
    private val service: ListingService,
    private val verifierService: ListingVerifierService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingJobs::class.java)
    }

    /**
     * Verifies the PENDING_VERIFICATION listings of each active tenant. Listings are tenant-scoped (`@TenantId`), and a
     * scheduler thread has no tenant of its own: each tenant's listings are processed with it set in [TenantContext],
     * removed afterwards since the scheduler threads are reused.
     */
    @Scheduled(cron = "\${ndopify.listing.verify.cron}")
    fun verify() {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                verify(tenantId)
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be verified
                LOGGER.warn("Tenant#$tenantId - Unable to verify the listings", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }

    private fun verify(tenantId: Long) {
        LOGGER.info("Tenant#$tenantId - Running ListingJobs.verify()")

        // Only PENDING_VERIFICATION listings: sent for verification, or sent back after a failed verification to be retried
        val listings = service.search(
            SearchListingRequest(
                statuses = listOf(ListingStatus.PENDING_VERIFICATION),
                limit = 100 // Process a max of 100 listings at a time to avoid overloading the system
            )
        )

        var verified = 0
        var errors = 0
        listings.forEach { listing ->
            try {
                verifierService.verify(listing)
                verified++
            } catch (ex: Exception) {
                LOGGER.warn("Tenant#$tenantId - Unable to verify Listing#${listing.id}", ex)
                errors++
            }
        }
        LOGGER.info("Tenant#$tenantId - ListingJobs.verify() completed: $verified verified, $errors errors")
    }
}
