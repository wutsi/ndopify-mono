package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.util.UrlUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Clock
import java.util.Date
import javax.imageio.ImageIO

@Service
class ListingVerifierImage(
    private val extractor: ListingImageInfoExtractor,
    private val dao: ListingImageRepository,
    private val tx: TransactionTemplate,
    private val clock: Clock,

    @param:Value("\${ndopify.listing.min-images}") private val minImages: Int,
) : ListingVerifier {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingVerifierImage::class.java)
    }

    override fun verify(listing: ListingEntity): ListingVerification {
        listing.id ?: throw IllegalStateException("Listing id is required")

        return verifyImages(listing)
    }

    private fun verifyImages(listing: ListingEntity): ListingVerification {
        val images = dao.findByListingIdAndStatus(listing.id!!, ImageStatus.PENDING_VERIFICATION)
        images.forEach { image -> verify(image) }

        val verified = dao.findByListingIdAndStatus(listing.id, ImageStatus.VERIFIED)
        return if (verified.size < minImages) {
            ListingVerification(success = false, errorCode = ErrorCode.LISTING_NOT_ENOUGH_IMAGES)
        } else {
            ListingVerification(success = true)
        }
    }

    private fun verify(image: ListingImageEntity) {
        LOGGER.info(">>> Listing#${image.listingId} - Verifying image ${image.url}")

        val file = UrlUtils.download(URL(image.url))
        val info = extractor.extract(file)
        val dimension = getDimensions(file)
        val verified = if (info?.valid == true) {
            image.copy(
                status = ImageStatus.VERIFIED,
                title = info.title,
                description = info.description,
                quality = info.quality,
                width = dimension.first,
                height = dimension.second,
                modifiedAt = Date(clock.millis()),
            )
        } else {
            image.copy(
                status = ImageStatus.REJECTED,
                errorMessage = info?.reason ?: "Unable to extract image info",
                modifiedAt = Date(clock.millis()),
            )
        }
        tx.execute { dao.save(verified) }
    }

    private fun getDimensions(file: File): Pair<Int?, Int?> {
        try {
            // null for a format ImageIO can't read (ex: WEBP)
            val img = ImageIO.read(file) ?: return Pair(null, null)
            return Pair(img.width, img.height)
        } catch (ex: IOException) {
            LOGGER.warn(">>> Listing#$file - Unable to extract image dimension of ${file.absolutePath}", ex)
            return Pair(null, null)
        }
    }
}
