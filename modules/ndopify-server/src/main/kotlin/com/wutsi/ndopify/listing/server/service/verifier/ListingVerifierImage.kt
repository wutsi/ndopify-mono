package com.wutsi.ndopify.listing.server.service.verifier

import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.domain.ImageKind
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

/**
 * Verifies a listing's PENDING_VERIFICATION images through the vision extractor, then the listing itself: below
 * `ndopify.listing.min-images` VERIFIED images it answers LISTING_NOT_ENOUGH_IMAGES, else without a VERIFIED image of
 * kind PHOTO it answers LISTING_MISSING_PHOTO (a floor plan or a map doesn't replace a photo), else it passes.
 */
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
        images.forEach { image -> verify(listing, image) }

        val verified = dao.findByListingIdAndStatus(listing.id, ImageStatus.VERIFIED)
        return when {
            verified.size < minImages ->
                ListingVerification(success = false, errorCode = ErrorCode.LISTING_NOT_ENOUGH_IMAGES)
            verified.none { image -> image.kind == ImageKind.PHOTO } ->
                ListingVerification(success = false, errorCode = ErrorCode.LISTING_MISSING_PHOTO)
            else -> ListingVerification(success = true)
        }
    }

    private fun verify(listing: ListingEntity, image: ListingImageEntity) {
        LOGGER.info(">>> Listing#${image.listingId} - Verifying image ${image.url}")

        val file = UrlUtils.download(URL(image.url))
        val info = extractor.extract(file, listing)
        val dimension = getDimensions(file)
        val kind = ImageKind.parse(info?.kind)
        val verified = if (info?.valid == true) {
            image.copy(
                status = ImageStatus.VERIFIED,
                kind = kind,
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
                kind = kind,
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
