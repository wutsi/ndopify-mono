package com.wutsi.ndopify.match.server.mapper

import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.springframework.stereotype.Service

@Service
class MatchMapper {
    fun toMatch(entity: MatchEntity): Match {
        return Match(
            id = entity.id ?: -1,
            mandateId = entity.mandate.id ?: -1,
            listingId = entity.listing.id ?: -1,
            status = entity.status,
            score = entity.score,
            neighborhoodScore = entity.neighborhoodScore,
            priceScore = entity.priceScore,
            roomsScore = entity.roomsScore,
            amenitiesScore = entity.amenitiesScore,
            availabilityScore = entity.availabilityScore,
            furnitureScore = entity.furnitureScore,
            listingPrice = entity.listingPrice,
            createdAt = entity.createdAt,
            modifiedAt = entity.modifiedAt,
        )
    }
}
