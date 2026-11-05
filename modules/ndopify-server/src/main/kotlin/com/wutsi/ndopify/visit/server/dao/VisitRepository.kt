package com.wutsi.ndopify.visit.server.dao

import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import jakarta.persistence.LockModeType
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
            "(v.status = :requested AND v.createdAt > :expireBefore) OR " +
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
            "(v.status = :requested AND v.createdAt > :expireBefore) OR " +
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

    // The requests started before [before]: expired when not scheduled in time
    fun findByStatusAndCreatedAtBefore(status: VisitStatus, before: Date): List<VisitEntity>

    // The confirmed visits starting in [from, to] whose reminder wasn't sent
    fun findByStatusAndScheduledAtBetweenAndReminderSentAtIsNull(
        status: VisitStatus,
        from: Date,
        to: Date,
    ): List<VisitEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM VisitEntity v WHERE v.id = :id")
    fun findByIdForUpdate(id: Long): VisitEntity?
}
