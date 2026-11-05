package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.visit.dto.VisitStatus
import com.wutsi.ndopify.visit.server.dao.VisitRepository
import com.wutsi.ndopify.visit.server.service.VisitPolicyProperties
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

@Service
class ListingStatsService(
    private val matchDao: MatchRepository,
    private val visitDao: VisitRepository,
    private val policy: VisitPolicyProperties,
    private val clock: Clock,
) {
    /** One row per distinct id, in request order, zeros when the listing has no match nor visit. */
    fun search(listingIds: List<Long>): List<ListingStats> {
        val ids = listingIds.distinct()
        val interested = matchDao.countByListingIdsAndStatus(ids, MatchStatus.ACCEPTED)
            .associate { it.listingId to it.total }

        val now = clock.millis()
        val upcoming = visitDao.countUpcomingByListingIds(
            ids,
            VisitStatus.REQUESTED,
            VisitStatus.CONFIRMED,
            VisitStatus.CHECKED_IN,
            Date(now - policy.expireAfter.toMillis()),
            Date(now - policy.checkInAfter.toMillis()),
        ).associate { it.listingId to it.total }
        val completed = visitDao.countByListingIdsAndStatus(ids, VisitStatus.COMPLETED)
            .associate { it.listingId to it.total }

        return ids.map { id ->
            ListingStats(
                listingId = id,
                interested = interested[id]?.toInt() ?: 0,
                upcomingVisits = upcoming[id]?.toInt() ?: 0,
                completedVisits = completed[id]?.toInt() ?: 0,
            )
        }
    }
}
