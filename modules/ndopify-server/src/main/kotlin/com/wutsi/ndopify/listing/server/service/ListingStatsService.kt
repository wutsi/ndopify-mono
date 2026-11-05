package com.wutsi.ndopify.listing.server.service

import com.wutsi.ndopify.listing.dto.ListingStats
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import org.springframework.stereotype.Service

@Service
class ListingStatsService(
    private val matchDao: MatchRepository,
) {
    /** One row per distinct id, in request order, zeros when the listing has no match nor visit. */
    fun search(listingIds: List<Long>): List<ListingStats> {
        val ids = listingIds.distinct()
        val interested = matchDao.countByListingIdsAndStatus(ids, MatchStatus.ACCEPTED)
            .associate { it.listingId to it.total }
        return ids.map { id ->
            ListingStats(listingId = id, interested = interested[id]?.toInt() ?: 0)
        }
    }
}
