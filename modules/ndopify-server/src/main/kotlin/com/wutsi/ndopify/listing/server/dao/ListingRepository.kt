package com.wutsi.ndopify.listing.server.dao

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.dto.ListingType
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ListingRepository : CrudRepository<ListingEntity, Long>, JpaSpecificationExecutor<ListingEntity> {
    fun existsByMandate(mandate: MandateEntity): Boolean

    fun findByAgentIdAndStatus(agentId: Long, status: ListingStatus): List<ListingEntity>

    // The co-brokerage candidates of a search: another agent's published listings in its city and listing type
    fun findByCityIdAndListingTypeAndStatusAndAgentIdNot(
        cityId: Long,
        listingType: ListingType,
        status: ListingStatus,
        agentId: Long,
    ): List<ListingEntity>

    fun findByMandate(mandate: MandateEntity): ListingEntity?

    fun findByStatusAndMandateStatusIn(
        status: ListingStatus,
        mandateStatuses: List<MandateStatus>,
        pageable: Pageable,
    ): List<ListingEntity>
}
