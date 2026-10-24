package com.wutsi.ndopify.listing.server.dao

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface ListingRepository : CrudRepository<ListingEntity, Long>, JpaSpecificationExecutor<ListingEntity> {
    fun existsByMandate(mandate: MandateEntity): Boolean

    @Query("SELECT l.id FROM ListingEntity l WHERE l.mandate.id = :mandateId")
    fun findIdByMandateId(mandateId: Long): Long?
}
