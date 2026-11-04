package com.wutsi.ndopify.visit.server.mapper

import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.visit.dto.Visit
import com.wutsi.ndopify.visit.dto.VisitSummary
import com.wutsi.ndopify.visit.dto.VisitType
import com.wutsi.ndopify.visit.server.domain.VisitEntity
import org.springframework.stereotype.Service

/**
 * Maps a visit to its DTO. [showCode] is true only for its customer: the code is what the agent asks for at check-in, so
 * nobody else ever gets it. The exact address is the listing's, and only to the customer of a confirmed ON_SITE visit;
 * never for a VIDEO one.
 */
@Service
class VisitMapper {
    fun toVisit(visit: VisitEntity, listing: ListingEntity, showCode: Boolean): Visit {
        val showAddress = showCode && visit.type == VisitType.ON_SITE && visit.scheduledAt != null
        return Visit(
            id = visit.id ?: -1,
            matchId = visit.matchId,
            mandateId = visit.mandateId,
            listingId = visit.listingId,
            agentId = visit.agentId,
            customerId = visit.customerId,
            status = visit.status,
            type = visit.type,
            requestedAt = visit.requestedAt,
            note = visit.note,
            scheduledAt = visit.scheduledAt,
            code = visit.code.takeIf { showCode },
            address = address(listing).takeIf { showAddress },
            latitude = listing.latitude.takeIf { showAddress },
            longitude = listing.longitude.takeIf { showAddress },
            checkedInAt = visit.checkedInAt,
            checkInDistanceMeters = visit.checkInDistanceMeters,
            checkInFlagged = visit.checkInFlagged,
            checkedOutAt = visit.checkedOutAt,
            agentComment = visit.agentComment.takeIf { !showCode }, // Private to the agent and the admins
            interest = visit.interest,
            feedbackReasonCode = visit.feedbackReasonCode,
            feedbackComment = visit.feedbackComment,
            noShowParty = visit.noShowParty,
            noShowReason = visit.noShowReason,
            cancelledBy = visit.cancelledBy,
            cancellationReason = visit.cancellationReason,
            cancelledAt = visit.cancelledAt,
            createdAt = visit.createdAt,
            version = visit.version,
        )
    }

    fun toVisitSummary(visit: VisitEntity): VisitSummary {
        return VisitSummary(
            id = visit.id ?: -1,
            matchId = visit.matchId,
            mandateId = visit.mandateId,
            listingId = visit.listingId,
            agentId = visit.agentId,
            customerId = visit.customerId,
            status = visit.status,
            type = visit.type,
            requestedAt = visit.requestedAt,
            note = visit.note,
            scheduledAt = visit.scheduledAt,
            checkedInAt = visit.checkedInAt,
            checkInDistanceMeters = visit.checkInDistanceMeters,
            checkInFlagged = visit.checkInFlagged,
            checkedOutAt = visit.checkedOutAt,
            noShowParty = visit.noShowParty,
            noShowReason = visit.noShowReason,
            cancelledBy = visit.cancelledBy,
            cancellationReason = visit.cancellationReason,
            cancelledAt = visit.cancelledAt,
            createdAt = visit.createdAt,
            version = visit.version,
        )
    }

    private fun address(listing: ListingEntity): String? =
        listOfNotNull(
            listing.landmark?.trim()?.ifEmpty { null },
            listing.accessDirections?.trim()?.ifEmpty { null },
        ).joinToString(", ").ifEmpty { null }
}
