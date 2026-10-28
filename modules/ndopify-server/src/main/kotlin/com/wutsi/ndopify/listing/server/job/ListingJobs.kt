package com.wutsi.ndopify.listing.server.job

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.SearchListingRequest
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.service.ListingService
import com.wutsi.ndopify.listing.server.service.publisher.ListingPublisherService
import com.wutsi.ndopify.listing.server.service.verifier.ListingVerifierService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.service.TenantService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Listings are tenant-scoped (`@TenantId`), and a scheduler thread has no tenant of its own: each job processes each
 * active tenant's listings with it set in [TenantContext], removed afterwards since the scheduler threads are reused.
 */
@Service
class ListingJobs(
    private val service: ListingService,
    private val verifierService: ListingVerifierService,
    private val publisherService: ListingPublisherService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingJobs::class.java)
        private const val LIMIT = 100 // Process a max of 100 listings at a time to avoid overloading the system
    }

    /**
     * Verifies the UNDER_VERIFICATION listings of each active tenant: sent for verification, or left
     * UNDER_VERIFICATION by a failed or interrupted attempt to be retried.
     */
    @Scheduled(cron = "\${ndopify.listing.verify.cron}")
    fun verify() {
        run("verify", ListingStatus.UNDER_VERIFICATION) { listing -> verifierService.verify(listing) }
    }

    /**
     * Publishes the PUBLISHING listings of each active tenant: sent for publication (`POST /v1/listings/{id}/publish`),
     * or left PUBLISHING by a failed attempt to be retried.
     */
    @Scheduled(cron = "\${ndopify.listing.publish.cron}")
    fun publish() {
        run("publish", ListingStatus.PUBLISHING) { listing -> publisherService.publish(listing) }
    }

    private fun run(job: String, status: ListingStatus, process: (ListingEntity) -> Unit) {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                run(tenantId, job, status, process)
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - Unable to $job the listings", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }

    private fun run(tenantId: Long, job: String, status: ListingStatus, process: (ListingEntity) -> Unit) {
        LOGGER.info("Tenant#$tenantId - Running ListingJobs.$job()")

        val listings = service.search(
            SearchListingRequest(
                statuses = listOf(status),
                limit = LIMIT,
            )
        )

        var processed = 0
        var errors = 0
        listings.forEach { listing ->
            try {
                process(listing)
                processed++
            } catch (ex: Exception) {
                LOGGER.warn("Tenant#$tenantId - Unable to $job Listing#${listing.id}", ex)
                errors++
            }
        }
        LOGGER.info("Tenant#$tenantId - ListingJobs.$job() completed: $processed processed, $errors errors")
    }
}
