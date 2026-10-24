package com.wutsi.ndopify.listing.server.dao

import com.wutsi.ndopify.listing.dto.ImageStatus
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ListingImageRepository : CrudRepository<ListingImageEntity, Long> {
    fun findByListingIdOrderById(listingId: Long): List<ListingImageEntity>

    fun findByListingIdAndStatusInOrderById(listingId: Long, statuses: Collection<ImageStatus>): List<ListingImageEntity>

    fun findByIdAndListingId(id: Long, listingId: Long): ListingImageEntity?
}
