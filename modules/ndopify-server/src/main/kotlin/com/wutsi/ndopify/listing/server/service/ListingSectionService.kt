package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.dto.ListingSectionStatus
import com.wutsi.ndopify.listing.dto.ListingSectionSummary
import com.wutsi.ndopify.listing.server.dao.ListingImageRepository
import com.wutsi.ndopify.listing.server.dao.ListingSectionRepository
import com.wutsi.ndopify.listing.server.domain.ListingSectionEntity
import org.springframework.stereotype.Service
import java.util.Date

/**
 * A listing's checklist. A section is VERIFIED once the agent saved it (the landlord's answers alone don't count),
 * PHOTOS once there are [MIN_PHOTOS] of them and none REJECTED. REMARKS is optional.
 */
@Service
class ListingSectionService(
    private val dao: ListingSectionRepository,
    private val imageDao: ListingImageRepository,
) {
    companion object {
        const val MIN_PHOTOS = 5

        val SECTIONS = ListingSection.entries.filter { section -> section != ListingSection.UNKNOWN }
        val OPTIONAL_SECTIONS = listOf(ListingSection.REMARKS)
    }

    fun summaries(listingId: Long): List<ListingSectionSummary> {
        val verifiedAt = dao.findByListingId(listingId).associate { entity -> entity.section to entity.verifiedAt }
        return SECTIONS.map { section ->
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
        val statuses = imageDao.findByListingIdOrderById(listingId).map { image -> image.status }
        return statuses.size >= MIN_PHOTOS && ImageStatus.REJECTED !in statuses
    }
}
