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

@Repository
interface VisitRepository : CrudRepository<VisitEntity, Long>, JpaSpecificationExecutor<VisitEntity> {
    fun existsByMatchIdAndStatusIn(matchId: Long, statuses: Collection<VisitStatus>): Boolean

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
