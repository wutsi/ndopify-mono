package com.wutsi.ndopify.match.server.dao

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.visit.dto.VisitStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** Number of matches of a listing in a given status. */
interface ListingMatchCount {
    val listingId: Long
    val total: Long
}

/** Number of matches of a mandate in the given statuses. */
interface MandateMatchCount {
    val mandateId: Long
    val total: Long
}

@Repository
interface MatchRepository : CrudRepository<MatchEntity, Long>, JpaSpecificationExecutor<MatchEntity> {
    fun findByMandateAndListing(mandate: MandateEntity, listing: ListingEntity): MatchEntity?

    /**
     * Whether the search still has one of its agent's own listings to offer: PUBLISHED, and not turned down by the
     * agent (DISMISSED) or the customer (REJECTED). When it has none, co-brokerage may fill in.
     */
    @Query(
        "SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END FROM MatchEntity m " +
            "WHERE m.mandate.id = :mandateId AND m.coBrokerage = FALSE " +
            "AND m.status NOT IN :statuses AND m.listing.status = :listingStatus"
    )
    fun existsViableOwnMatch(
        mandateId: Long,
        statuses: Collection<MatchStatus>,
        listingStatus: ListingStatus,
    ): Boolean

    // The matches an agent left untouched for too long (MatchAutoProposalService): oldest first, tenant-filtered
    @Query(
        "SELECT m FROM MatchEntity m WHERE m.status = :status AND m.createdAt <= :before " +
            "AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus " +
            "ORDER BY m.createdAt ASC, m.id ASC"
    )
    fun findStalled(
        @Param("status") status: MatchStatus,
        @Param("before") before: Date,
        @Param("mandateStatus") mandateStatus: MandateStatus,
        @Param("listingStatus") listingStatus: ListingStatus,
        page: Pageable,
    ): List<MatchEntity>

    // A visit request locks the match, so that two requests for the same match can't both pass the open-visit check
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MatchEntity m WHERE m.id = :id")
    fun findByIdForUpdate(id: Long): MatchEntity?

    @Query(
        "SELECT m.listing.id AS listingId, COUNT(m) AS total FROM MatchEntity m " +
            "WHERE m.listing.id IN :listingIds AND m.status = :status GROUP BY m.listing.id"
    )
    fun countByListingIdsAndStatus(
        listingIds: Collection<Long>,
        status: MatchStatus,
    ): List<ListingMatchCount>

    @Query(
        "SELECT m.mandate.id AS mandateId, COUNT(m) AS total FROM MatchEntity m " +
            "WHERE m.mandate.id IN :mandateIds AND m.status IN :statuses GROUP BY m.mandate.id"
    )
    fun countByMandateIdsAndStatusIn(
        mandateIds: Collection<Long>,
        statuses: Collection<MatchStatus>,
    ): List<MandateMatchCount>

    // Not co-brokerage: this alert is about the agent's own inventory (its own slice covers co-brokerage)
    fun findByStatusAndNotifiedAtIsNullAndMandateStatusAndListingStatusAndCoBrokerageFalse(
        status: MatchStatus,
        mandateStatus: MandateStatus,
        listingStatus: ListingStatus,
    ): List<MatchEntity>

    // The co-brokerage matches the search agent has not been told about yet
    @Query(
        "SELECT m FROM MatchEntity m WHERE m.coBrokerage = TRUE AND m.status = :status " +
            "AND m.notifiedAt IS NULL AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus"
    )
    fun findCoBrokerageToNotify(
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

    // The accepted matches with no visit yet that are due for a reminder, oldest answer first (MatchVisitReminderService).
    // VisitEntity is named in the query rather than imported: the match domain doesn't depend on the visit one.
    @Query(
        "SELECT m FROM MatchEntity m WHERE m.status = :status AND m.visitRemindedAt IS NULL " +
            "AND m.answeredAt <= :before AND m.mandate.status = :mandateStatus " +
            "AND m.listing.status = :listingStatus " +
            "AND NOT EXISTS (SELECT v.id FROM VisitEntity v WHERE v.matchId = m.id AND v.status IN :visitStatuses) " +
            "ORDER BY m.answeredAt ASC, m.id ASC"
    )
    fun findVisitReminderDue(
        @Param("status") status: MatchStatus,
        @Param("before") before: Date,
        @Param("mandateStatus") mandateStatus: MandateStatus,
        @Param("listingStatus") listingStatus: ListingStatus,
        @Param("visitStatuses") visitStatuses: Collection<VisitStatus>,
        page: Pageable,
    ): List<MatchEntity>

    // Whether the match has an open visit: the reminder doesn't go out then. VisitEntity is named in the query rather
    // than imported, as above.
    @Query(
        "SELECT CASE WHEN COUNT(v) > 0 THEN TRUE ELSE FALSE END FROM VisitEntity v " +
            "WHERE v.matchId = :matchId AND v.status IN :statuses"
    )
    fun existsOpenVisit(@Param("matchId") matchId: Long, @Param("statuses") statuses: Collection<VisitStatus>): Boolean

    // The accepted matches whose agent wasn't nudged yet and still has no visit (MatchVisitAgentNudgeService)
    @Query(
        "SELECT m FROM MatchEntity m WHERE m.status = :status AND m.agentVisitNudgedAt IS NULL " +
            "AND m.answeredAt <= :before AND m.mandate.status = :mandateStatus " +
            "AND m.listing.status = :listingStatus " +
            "AND NOT EXISTS (SELECT v.id FROM VisitEntity v WHERE v.matchId = m.id AND v.status IN :visitStatuses) " +
            "ORDER BY m.answeredAt ASC, m.id ASC"
    )
    fun findAgentVisitNudgeDue(
        @Param("status") status: MatchStatus,
        @Param("before") before: Date,
        @Param("mandateStatus") mandateStatus: MandateStatus,
        @Param("listingStatus") listingStatus: ListingStatus,
        @Param("visitStatuses") visitStatuses: Collection<VisitStatus>,
        page: Pageable,
    ): List<MatchEntity>
}
