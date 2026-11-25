package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.dto.PropertyType
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.dao.ListingSectionRepository
import com.wutsi.ndopify.listing.server.domain.ImageKind
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingSectionEntity
import org.springframework.stereotype.Service
import java.util.Date

/**
 * A listing's checklist. A section is VERIFIED once the agent saved it (the landlord's answers alone don't count),
 * PHOTOS once there are [MIN_PHOTOS] images, none REJECTED and at least one of them a [ImageKind.PHOTO] (a plan or a
 * map counts toward the five, not as the photo). REMARKS is optional. A building sold as a whole has no utilities, so
 * its checklist has no AMENITIES section.
 */
@Service
class ListingSectionService(
    private val dao: ListingSectionRepository,
    private val imageDao: ListingImageRepository,
) {
    companion object {
        const val MIN_PHOTOS = 5

        val SECTIONS = ListingSection.entries.filter { section -> section != ListingSection.UNKNOWN }
        val BUILDING_SECTIONS = SECTIONS - ListingSection.AMENITIES
        val OPTIONAL_SECTIONS = listOf(ListingSection.REMARKS)

        /** A building sold as a whole: it has no utilities, no bathrooms and no parkings. */
        fun soldBuilding(listing: ListingEntity): Boolean =
            listing.listingType == ListingType.SALE && listing.propertyType == PropertyType.BUILDING
    }

    fun sections(listing: ListingEntity): List<ListingSection> =
        if (soldBuilding(listing)) BUILDING_SECTIONS else SECTIONS

    fun summaries(listing: ListingEntity): List<ListingSectionSummary> {
        val listingId = listing.id ?: -1
        val verifiedAt = dao.findByListingId(listingId).associate { entity -> entity.section to entity.verifiedAt }
        return sections(listing).map { section ->
            val verified = if (section == ListingSection.PHOTOS) {
                photosVerified(listingId)
            } else {
                verifiedAt.containsKey(section)
            }
            ListingSectionSummary(
                section = section,
                status = if (verified) ListingSectionStatus.VERIFIED else ListingSectionStatus.TO_VERIFY,
                required = section !in OPTIONAL_SECTIONS,
                verifiedAt = verifiedAt[section],
            )
        }
    }

    /** Records that the agent saved the section now. */
    fun verify(listingId: Long, section: ListingSection, now: Date): ListingSectionEntity {
        val entity = dao.findByListingIdAndSection(listingId, section)
        return dao.save(
            entity?.copy(verifiedAt = now)
                ?: ListingSectionEntity(listingId = listingId, section = section, verifiedAt = now)
        )
    }

    private fun photosVerified(listingId: Long): Boolean {
        val images = imageDao.findByListingIdOrderById(listingId)
        return images.size >= MIN_PHOTOS &&
            images.none { image -> image.status == ImageStatus.REJECTED } &&
            images.any { image -> image.status != ImageStatus.REJECTED && image.kind == ImageKind.PHOTO }
    }
}
