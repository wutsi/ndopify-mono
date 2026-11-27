package com.wutsi.ndopify.listing.server.job

import com.wutsi.ndopify.error.server.exception.ConflictException
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
     * Publishes the listings of each active tenant, in two passes. First the VERIFIED ones are sent for publication
     * (PUBLISHING, as `POST /v1/listings/{id}/publish` does): a verified listing is published without anybody having to
     * ask. The gate applies as for the endpoint, and a listing whose owner is not verified is skipped quietly, to be
     * retried on the next run. Then the PUBLISHING ones are published by [ListingPublisherService]: just sent by the
     * first pass (so a listing is published in the run that finds it VERIFIED), sent by the endpoint, or left
     * PUBLISHING by a failed attempt to be retried.
     */
    @Scheduled(cron = "\${ndopify.listing.publish.cron}")
    fun publish() {
        runVerified()
        run("publishing", ListingStatus.PUBLISHING) { listing -> publisherService.publish(listing) }
    }

    /**
     * Pages the VERIFIED listings by id and sends each for publication, stopping after [LIMIT] successful transitions
     * per tenant. A listing the gate blocks (a `ConflictException`) is counted and skipped without a warning: it is
     * retried on the next run, and does not consume the batch nor starve the listings after it.
     */
    private fun runVerified() {
        forEachTenant("publish") { tenantId -> runVerified(tenantId) }
    }

    private fun runVerified(tenantId: Long) {
        LOGGER.info("Tenant#$tenantId - Running ListingJobs.publish()")

        var lastId = 0L
        var published = 0
        var blocked = 0
        var errors = 0
        while (published < LIMIT) {
            val listings = service.findVerifiedAfter(lastId, LIMIT)
            if (listings.isEmpty()) {
                break
            }
            for (listing in listings) {
                lastId = listing.id!!
                try {
                    service.publish(listing)
                    published++
                } catch (ex: ConflictException) {
                    blocked++ // Not VERIFIED anymore, or the owner is not verified: retried next run, no warning
                } catch (ex: Exception) {
                    LOGGER.warn("Tenant#$tenantId - Unable to publish Listing#${listing.id}", ex)
                    errors++
                }
                if (published >= LIMIT) {
                    break // The limit stops the page: the listings after it wait for the next run
                }
            }
            if (listings.size < LIMIT) {
                break
            }
        }
        LOGGER.info(
            "Tenant#$tenantId - ListingJobs.publish() completed: $published published, $blocked blocked, $errors errors"
        )
    }

    /**
     * Takes off the market the PUBLISHED listings whose mandate is closed (CLOSED or CANCELLED), as
     * `ListingMandateEventListener` does on the closing: catches up on the mandates closed before that listener
     * existed, or by a path that didn't publish `MandateClosedEvent`.
     */
    @Scheduled(cron = "\${ndopify.listing.close.cron}")
    fun close() {
        run("close", { service.findPublishedOfClosedMandates(LIMIT) }) { listing ->
            service.onMandateClosed(listing.mandate.id!!)
        }
    }

    private fun run(job: String, status: ListingStatus, process: (ListingEntity) -> Unit) {
        run(job, { service.search(SearchListingRequest(statuses = listOf(status), limit = LIMIT)) }, process)
    }

    private fun run(job: String, find: () -> List<ListingEntity>, process: (ListingEntity) -> Unit) {
        forEachTenant(job) { tenantId -> run(tenantId, job, find, process) }
    }

    private fun forEachTenant(job: String, process: (Long) -> Unit) {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                process(tenantId)
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - Unable to $job the listings", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }

    private fun run(tenantId: Long, job: String, find: () -> List<ListingEntity>, process: (ListingEntity) -> Unit) {
        LOGGER.info("Tenant#$tenantId - Running ListingJobs.$job()")

        val listings = find()

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
