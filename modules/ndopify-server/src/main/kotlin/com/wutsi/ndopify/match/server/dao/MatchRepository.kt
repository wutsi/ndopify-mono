package com.wutsi.ndopify.match.server.dao

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
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

    // A visit request locks the match, so that two requests for the same match can't both pass the open-visit check
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MatchEntity m WHERE m.id = :id")
    fun findByIdForUpdate(id: Long): MatchEntity?

    fun findByStatusAndNotifiedAtIsNullAndMandateStatusAndListingStatus(
        status: MatchStatus,
        mandateStatus: MandateStatus,
        listingStatus: ListingStatus,
    ): List<MatchEntity>

    // The matches to expire: proposed before, oldest first (MatchProposalService.expireDue)
    fun findByStatusAndProposedAtBeforeOrderByProposedAt(
        status: MatchStatus,
        before: Date,
        page: Pageable,
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
