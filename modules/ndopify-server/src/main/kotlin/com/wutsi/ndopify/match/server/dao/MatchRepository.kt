package com.wutsi.ndopify.match.server.dao

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Date

@Repository
interface MatchRepository : CrudRepository<MatchEntity, Long>, JpaSpecificationExecutor<MatchEntity> {
    fun findByMandateAndListing(mandate: MandateEntity, listing: ListingEntity): MatchEntity?

    fun findByStatusAndNotifiedAtIsNullAndMandateStatusAndListingStatus(
        status: MatchStatus,
        mandateStatus: MandateStatus,
        listingStatus: ListingStatus,
    ): List<MatchEntity>

    /**
     * Sets [MatchEntity.notifiedAt]. A bulk update on purpose: it skips the `@Version` check, so a dismiss made
     * meanwhile neither fails it nor is overwritten, since only `notified_at` is set.
     */
    @Transactional
    @Modifying
    @Query("UPDATE MatchEntity m SET m.notifiedAt = :now WHERE m.id IN :ids")
    fun markNotified(@Param("ids") ids: Collection<Long>, @Param("now") now: Date): Int
}
