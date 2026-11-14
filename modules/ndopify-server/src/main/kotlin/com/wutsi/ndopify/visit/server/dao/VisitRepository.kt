package com.wutsi.ndopify.visit.server.dao

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.Date

/** Number of visits of a listing that count, by kind. */
interface ListingVisitCount {
    val listingId: Long
    val total: Long
}

/** Number of visits of a mandate that count, by kind. */
interface MandateVisitCount {
    val mandateId: Long
    val total: Long
}

@Repository
interface VisitRepository : CrudRepository<VisitEntity, Long>, JpaSpecificationExecutor<VisitEntity> {
    fun existsByMatchIdAndStatusIn(matchId: Long, statuses: Collection<VisitStatus>): Boolean

    @Query(
        "SELECT v.listingId AS listingId, COUNT(v) AS total FROM VisitEntity v " +
            "WHERE v.listingId IN :listingIds AND (" +
            "(v.status = :requested AND (v.createdAt > :expireBefore OR v.requestedAt > :now)) OR " +
            "(v.status = :confirmed AND v.scheduledAt >= :checkInBefore) OR " +
            "v.status = :checkedIn) " +
            "GROUP BY v.listingId"
    )
    fun countUpcomingByListingIds(
        listingIds: Collection<Long>,
        requested: VisitStatus,
        confirmed: VisitStatus,
        checkedIn: VisitStatus,
        expireBefore: Date,
        checkInBefore: Date,
        now: Date,
    ): List<ListingVisitCount>

    @Query(
        "SELECT v.listingId AS listingId, COUNT(v) AS total FROM VisitEntity v " +
            "WHERE v.listingId IN :listingIds AND v.status = :status GROUP BY v.listingId"
    )
    fun countByListingIdsAndStatus(
        listingIds: Collection<Long>,
        status: VisitStatus,
    ): List<ListingVisitCount>

    @Query(
        "SELECT v.mandateId AS mandateId, COUNT(v) AS total FROM VisitEntity v " +
            "WHERE v.mandateId IN :mandateIds AND (" +
            "(v.status = :requested AND (v.createdAt > :expireBefore OR v.requestedAt > :now)) OR " +
            "(v.status = :confirmed AND v.scheduledAt >= :checkInBefore) OR " +
            "v.status = :checkedIn) " +
            "GROUP BY v.mandateId"
    )
    fun countUpcomingByMandateIds(
        mandateIds: Collection<Long>,
        requested: VisitStatus,
        confirmed: VisitStatus,
        checkedIn: VisitStatus,
        expireBefore: Date,
        checkInBefore: Date,
        now: Date,
    ): List<MandateVisitCount>

    @Query(
        "SELECT v.mandateId AS mandateId, COUNT(v) AS total FROM VisitEntity v " +
            "WHERE v.mandateId IN :mandateIds AND v.status = :status GROUP BY v.mandateId"
    )
    fun countByMandateIdsAndStatus(
        mandateIds: Collection<Long>,
        status: VisitStatus,
    ): List<MandateVisitCount>

    // The confirmed visits that start before [before]: the agent's no-show (4h past the time)
    fun findByStatusAndScheduledAtBefore(status: VisitStatus, before: Date): List<VisitEntity>

    // Expiry needs both bounds: the minimum age (48 h) and the requested slot
    fun findByStatusAndCreatedAtBeforeAndRequestedAtBefore(
        status: VisitStatus,
        createdBefore: Date,
        requestedBefore: Date,
    ): List<VisitEntity>

    // The confirmed visits starting in [from, to] whose reminder wasn't sent
    fun findByStatusAndScheduledAtBetweenAndReminderSentAtIsNull(
        status: VisitStatus,
        from: Date,
        to: Date,
    ): List<VisitEntity>

    // Another agent's visits on a listing, soonest first: the co-brokerage block
    fun findByListingIdAndAgentIdNotAndStatusInOrderByScheduledAtAscIdAsc(
        listingId: Long,
        agentId: Long,
        statuses: Collection<VisitStatus>,
    ): List<VisitEntity>

    // The co-brokerage visits on an agent's live listings, soonest first: the listing agent's home and mandate views
    @Query(
        "SELECT v FROM VisitEntity v WHERE v.agentId <> :agentId AND v.status IN :statuses AND " +
            "v.listingId IN (SELECT l.id FROM ListingEntity l WHERE l.agent.id = :agentId AND l.status IN :listingStatuses) " +
            "ORDER BY v.scheduledAt ASC, v.id ASC"
    )
    fun findCoBrokerageByListingAgent(
        agentId: Long,
        statuses: Collection<VisitStatus>,
        listingStatuses: Collection<ListingStatus>,
    ): List<VisitEntity>

    // The REQUESTED/CONFIRMED visits the ended mandate makes moot: its own (search side) and its listing's
    @Query(
        "SELECT v FROM VisitEntity v WHERE v.status IN :statuses AND (" +
            "v.mandateId = :mandateId OR v.listingId IN (" +
            "SELECT l.id FROM ListingEntity l WHERE l.mandate.id = :mandateId))"
    )
    fun findOpenByMandateOrListingMandate(
        mandateId: Long,
        statuses: Collection<VisitStatus>,
    ): List<VisitEntity>

    // The catch-up sweep: the open visits whose search mandate, or whose listing's mandate, already ended
    @Query(
        "SELECT v FROM VisitEntity v WHERE v.status IN :visitStatuses AND (" +
            "v.mandateId IN (SELECT m.id FROM MandateEntity m WHERE m.status IN :mandateStatuses) OR " +
            "v.listingId IN (SELECT l.id FROM ListingEntity l WHERE l.mandate.status IN :mandateStatuses))"
    )
    fun findOpenByEndedMandate(
        visitStatuses: Collection<VisitStatus>,
        mandateStatuses: Collection<MandateStatus>,
        page: Pageable,
    ): List<VisitEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VisitEntity v WHERE v.id = :id")
    fun findByIdForUpdate(id: Long): VisitEntity?
}
