package com.wutsi.ndopify.match.server.dao

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface MatchRepository : CrudRepository<MatchEntity, Long>, JpaSpecificationExecutor<MatchEntity> {
    fun findByMandateAndListing(mandate: MandateEntity, listing: ListingEntity): MatchEntity?
}
