package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.util.UrlUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Clock
import java.util.Date
import javax.imageio.ImageIO

@Service
class ListingImageVerifier(
    private val extractor: ListingImageInfoExtractor,
    private val imageDao: ListingImageRepository,
    private val tx: TransactionTemplate,
    private val clock: Clock,
) : ListingVerifier {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingImageVerifier::class.java)
        private val MAX_RETRIES = 3
        private val MIN_IMAGES = 5
    }

    override fun verify(listing: ListingEntity): ListingEntity {
        listing.id ?: throw IllegalStateException("Listing id is required")

        startImageVerification(listing)
        verifyImages(listing)
        return completeImageVerification(listing)
    }

    private fun startImageVerification(listing: ListingEntity) {
        val images = imageDao.findByListingIdAndStatus(listing.id!!, ImageStatus.PENDING_VERIFICATION)
        for (image in images) {
            tx.execute {
                imageDao.save(
                    image.copy(
                        status = ImageStatus.UNDER_VERIFICATION,
                        modifiedAt = Date(clock.millis()),
                    )
                )
            }
        }
    }

    private fun verifyImages(listing: ListingEntity) {
        val images = imageDao.findByListingIdAndStatus(listing.id!!, ImageStatus.UNDER_VERIFICATION)
        images.forEach { image ->
            try {
                verify(image)
            } catch (ex: Exception) {
                LOGGER.warn(">>> Listing#${listing.id} - Unable to verify image ${image.url}", ex)
                val retries = (image.retries ?: 0) + 1
                tx.execute {
                    imageDao.save(
                        image.copy(
                            status = if (retries <= MAX_RETRIES) ImageStatus.PENDING_VERIFICATION else ImageStatus.REJECTED,
                            retries = retries,
                            errorMessage = ex.message,
                            modifiedAt = Date(clock.millis()),
                        )
                    )
                }
            }
        }
    }

    private fun completeImageVerification(listing: ListingEntity): ListingEntity {
        val images = imageDao.findByListingIdOrderById(listing.id!!)
        val completed = images.none { image ->
            image.status == ImageStatus.PENDING_VERIFICATION || image.status == ImageStatus.UNDER_VERIFICATION
        }
        if (!completed) {
            return listing
        }

        val verified = images.filter { image -> image.status == ImageStatus.VERIFIED }
        return if (verified.size < MIN_IMAGES) {
            listing.copy(
                errors = listing.errors + ErrorCode.LISTING_NOT_ENOUGH_IMAGES,
                status = ListingStatus.REJECTED,
            )
        } else {
            listing
        }
    }

    private fun verify(image: ListingImageEntity) {
        LOGGER.info(">>> Listing#${image.listingId} - Verifying image ${image.url}")

        val file = UrlUtils.download(URL(image.url))
        val info = extractor.extract(file)
        val dimension = getDimensions(file)
        val updated = if (info.valid) {
            image.copy(
                status = ImageStatus.VERIFIED,
                title = info.title,
                description = info.description,
                width = dimension.first,
                height = dimension.second,
                modifiedAt = Date(clock.millis()),
            )
        } else {
            image.copy(
                status = ImageStatus.REJECTED,
                errorMessage = info.reason,
                modifiedAt = Date(clock.millis()),
            )
        }

        tx.execute { imageDao.save(updated) }
    }

    private fun getDimensions(file: File): Pair<Int?, Int?> {
        try {
            // null when no ImageIO reader can decode the file (ex: WEBP)
            val img = ImageIO.read(file) ?: return Pair(null, null)
            return Pair(img.getWidth(), img.getHeight())
        } catch (ex: IOException) {
            LOGGER.warn(">>> Listing#$file - Unable to extract image dimension of ${file.absolutePath}", ex)
            return Pair(null, null)
        }
    }
}
