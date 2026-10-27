package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.util.Date

@Service
class ListingVerifierService(
    private val dao: ListingRepository,
    private val tx: TransactionTemplate,
    private val clock: Clock,

    private val image: ListingVerifierImage,
    private val geo: ListingVerifierGeoLocalization,
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
        if (listing.status != ListingStatus.PENDING_VERIFICATION) {
            throw IllegalStateException("Listing#${listing.id}.status=${listing.status} - Cannot be verified")
        }

        // Start verification
        val now = Date(clock.millis())
        val toVerify = tx.execute {
            dao.save(
                listing.copy(
                    status = ListingStatus.UNDER_VERIFICATION,
                    modifiedAt = now,
                )
            )
        }

        // Verify
        try {
            val verifications = verifiers.map { verifier -> verifier.verify(toVerify) }
            val errorCodes = verifications.mapNotNull { verification -> verification.errorCode }
            tx.execute {
                dao.save(
                    toVerify.copy(
                        status = if (errorCodes.isEmpty()) ListingStatus.VERIFIED else ListingStatus.REJECTED,
                        errorCodes = errorCodes,
                        errorMessage = null,
                        verifiedAt = now,
                        modifiedAt = now,
                    )
                )
            }
        } catch (ex: Exception) {
            LOGGER.warn("Listing#${toVerify.id} - Verification failed", ex)
            val retries = (toVerify.retries ?: 0) + 1
            tx.execute {
                dao.save(
                    toVerify.copy(
                        status = if (retries <= MAX_RETRIES) ListingStatus.PENDING_VERIFICATION else ListingStatus.REJECTED,
                        retries = if (retries <= MAX_RETRIES) retries else 0,
                        errorCodes = listOf(ErrorCode.INTERNAL_ERROR),
                        errorMessage = ex.message,
                        modifiedAt = now,
                    )
                )
            }
        }
    }
}
