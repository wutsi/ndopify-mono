package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.event.ListingSentBackEvent
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

/**
 * Verifies an UNDER_VERIFICATION listing with each verifier: VERIFIED when none finds an error, else sent back to
 * DRAFT with their error codes, for the agent to fix and send again. A verifier failure retries up to MAX_RETRIES
 * times (left UNDER_VERIFICATION), then sends the listing back to DRAFT with INTERNAL_ERROR.
 *
 * A send-back publishes [ListingSentBackEvent] inside the transaction that saves it, so an after-commit listener
 * announces it to the agent; a retry left UNDER_VERIFICATION publishes nothing.
 */
@Service
class ListingVerifierService(
    private val dao: ListingRepository,
    private val tx: TransactionTemplate,
    private val clock: Clock,

    private val image: ListingVerifierImage,
    private val geo: ListingVerifierGeoLocalization,
    private val eventPublisher: ApplicationEventPublisher,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingVerifierService::class.java)
        const val MAX_RETRIES = 3
    }

    private val verifiers = listOf(
        image,
        geo
    )

    fun verify(listing: ListingEntity) {
        if (listing.status != ListingStatus.UNDER_VERIFICATION) {
            throw IllegalStateException("Listing#${listing.id}.status=${listing.status} - Cannot be verified")
        }

        val now = Date(clock.millis())
        try {
            val verifications = verifiers.map { verifier -> verifier.verify(listing) }
            val errorCodes = verifications.mapNotNull { verification -> verification.errorCode }
            tx.execute {
                val saved = dao.save(
                    listing.copy(
                        status = if (errorCodes.isEmpty()) ListingStatus.VERIFIED else ListingStatus.DRAFT,
                        errorCodes = errorCodes,
                        errorMessage = null,
                        retries = 0,
                        verifiedAt = now,
                        modifiedAt = now,
                    )
                )
                publishSentBack(saved)
            }
        } catch (ex: Exception) {
            LOGGER.warn("Listing#${listing.id} - Verification failed", ex)
            val retries = (listing.retries ?: 0) + 1
            tx.execute {
                val saved = dao.save(
                    listing.copy(
                        status = if (retries <= MAX_RETRIES) ListingStatus.UNDER_VERIFICATION else ListingStatus.DRAFT,
                        retries = if (retries <= MAX_RETRIES) retries else 0,
                        errorCodes = listOf(ErrorCode.INTERNAL_ERROR),
                        errorMessage = ex.message,
                        modifiedAt = now,
                    )
                )
                publishSentBack(saved)
            }
        }
    }

    // Only a listing left DRAFT is sent back: VERIFIED and a retry under verification notify nobody
    private fun publishSentBack(listing: ListingEntity) {
        if (listing.status != ListingStatus.DRAFT) {
            return
        }

        eventPublisher.publishEvent(
            ListingSentBackEvent(listingId = listing.id!!, tenantId = listing.tenantId!!)
        )
    }
}
