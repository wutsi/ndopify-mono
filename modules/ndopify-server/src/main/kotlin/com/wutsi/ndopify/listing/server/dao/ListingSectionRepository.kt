package com.wutsi.ndopify.listing.server.dao

import com.wutsi.ndopify.listing.dto.ListingSection
import com.wutsi.ndopify.listing.server.domain.ListingSectionEntity
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ListingSectionRepository : CrudRepository<ListingSectionEntity, Long> {
    fun findByListingId(listingId: Long): List<ListingSectionEntity>

    fun findByListingIdAndSection(listingId: Long, section: ListingSection): ListingSectionEntity?
}
