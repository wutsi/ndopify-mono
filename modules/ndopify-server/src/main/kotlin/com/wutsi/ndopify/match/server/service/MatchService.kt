package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.SearchMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationRequest
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Finds, scores and stores the listings of an agent's inventory matching its SEARCH mandates. See
 * `docs/superpowers/plans/2026-10-31-search-listing-matching.md`.
 *
 * Only an APPROVED SEARCH mandate with an agent is matched, against the PUBLISHED listings of that agent. One row per
 * (mandate, listing): a row that isn't ACTIVE (dismissed, kept, proposed...) is never touched; an ACTIVE one is
 * re-scored, or deleted when no longer matching. A run isn't one transaction: each save is its own, so a conflict (the agent dismissed the match meanwhile)
 * only skips that pair, and so does a duplicate insert by a concurrent run (ex: a listing published while
 * its agent's search is approved).
 */
@Service
class MatchService(
    private val dao: MatchRepository,
    private val mandateDao: MandateRepository,
    private val listingDao: ListingRepository,
    private val matcher: Matcher,
    private val locationService: LocationService,
    private val tenantService: TenantService,
    private val clock: Clock,
    private val logger: KVLogger,

    @param:Value("\${ndopify.match.min-score}") private val minScore: Double,
    @param:Value("\${ndopify.match.nearby-radius-km}") private val nearbyRadiusKm: Double,
    @param:Value("\${ndopify.match.nearby-max-neighborhoods}") private val nearbyMaxNeighborhoods: Int,
) {
    companion object {
        // Not yet proposed nor dismissed: the agent may still propose or dismiss it
        val OPEN_STATUSES = setOf(MatchStatus.ACTIVE, MatchStatus.SHORTLISTED)
    }

    fun findById(id: Long): MatchEntity {
        return dao.findById(id).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MATCH_NOT_FOUND))
    }

    /**
     * The matches of the mandates [SearchMatchRequest.mandateIds], in [SearchMatchRequest.statuses] (default: ACTIVE),
     * best score first, all mandates together. Only while the mandate is APPROVED and the listing PUBLISHED: a closed search, or a rented listing, keeps its rows but isn't
     * listed.
     */
    fun search(request: SearchMatchRequest): List<MatchEntity> {
        val statuses = request.statuses.ifEmpty { listOf(MatchStatus.ACTIVE) }
        val spec = Specification<MatchEntity> { root, _, cb ->
            val mandate = root.get<MandateEntity>("mandate")
            val predicates = mutableListOf<Predicate>(
                mandate.get<Long>("id").`in`(request.mandateIds),
                cb.equal(mandate.get<MandateStatus>("status"), MandateStatus.APPROVED),
                cb.equal(root.get<ListingEntity>("listing").get<ListingStatus>("status"), ListingStatus.PUBLISHED),
                root.get<Any>("status").`in`(statuses),
            )
            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(Sort.Order.desc("score"), Sort.Order.asc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    /**
     * The agent keeps an ACTIVE match, to propose it later (SHORTLISTED): no longer re-scored, like any match that
     * isn't ACTIVE. 409 MATCH_NOT_ACTIVE otherwise.
     */
    fun shortlist(id: Long): MatchEntity {
        val match = findById(id)
        if (match.status != MatchStatus.ACTIVE) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_ACTIVE))
        }
        return dao.save(match.copy(status = MatchStatus.SHORTLISTED, modifiedAt = Date(clock.millis())))
    }

    /**
     * Hides an ACTIVE or SHORTLISTED match from the agent: it's never re-scored nor re-created. 409 MATCH_NOT_ACTIVE
     * otherwise. Keeps the listing's price the agent said no to (`listingPrice`), to tell a price drop later.
     */
    fun dismiss(id: Long): MatchEntity {
        val match = findById(id)
        if (match.status !in OPEN_STATUSES) {
            throw ConflictException(error = Error(code = ErrorCode.MATCH_NOT_ACTIVE))
        }
        return dao.save(
            match.copy(
                status = MatchStatus.DISMISSED,
                listingPrice = match.listing.price,
                modifiedAt = Date(clock.millis()),
            )
        )
    }

    /** Matches a search mandate against its agent's inventory. Returns the number of ACTIVE matches stored. */
    fun matchMandate(mandateId: Long): Int {
        // With its neighbourhoods: not in a transaction, the lazy collection couldn't be read later
        val mandate = mandateDao.findWithNeighborhoodsById(mandateId)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
        if (mandate.mandateType != MandateType.SEARCH || mandate.status != MandateStatus.APPROVED) {
            logger.add("mandate_status", mandate.status)
            return 0
        }
        val agentId = mandate.agentId ?: return 0

        val listings = listingDao.findByAgentIdAndStatus(agentId, ListingStatus.PUBLISHED)
        logger.add("listing_count", listings.size)
        if (listings.isEmpty()) {
            return 0
        }

        val distances = distances(mandate)
        val today = today(mandate.tenantId!!)
        return matchAll(listings.map { listing -> Triple(mandate, listing, distances) }, today)
    }

    /** Matches a published listing against its agent's open searches. Returns the number of ACTIVE matches stored. */
    fun matchListing(listingId: Long): Int {
        val listing = listingDao.findById(listingId).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
        if (listing.status != ListingStatus.PUBLISHED) {
            logger.add("listing_status", listing.status)
            return 0
        }

        val mandates = mandateDao.findByAgentIdAndMandateTypeAndStatus(
            listing.agent.id!!,
            MandateType.SEARCH,
            MandateStatus.APPROVED,
        )
        logger.add("mandate_count", mandates.size)
        if (mandates.isEmpty()) {
            return 0
        }

        val today = today(listing.tenantId!!)
        return matchAll(mandates.map { mandate -> Triple(mandate, listing, distances(mandate)) }, today)
    }

    private fun matchAll(pairs: List<Triple<MandateEntity, ListingEntity, Map<Long, Double>>>, today: LocalDate): Int {
        var count = 0
        var conflicts = 0
        pairs.forEach { (mandate, listing, distances) ->
            try {
                if (store(mandate, listing, matcher.score(mandate, listing, distances, today))) {
                    count++
                }
            } catch (ex: ObjectOptimisticLockingFailureException) {
                conflicts++ // The agent dismissed it meanwhile
            } catch (ex: DataIntegrityViolationException) {
                conflicts++ // Another run inserted the same pair meanwhile
            }
        }
        logger.add("match_count", count)
        logger.add("conflicts", conflicts)
        return count
    }

    // Returns true when an ACTIVE match is stored
    private fun store(mandate: MandateEntity, listing: ListingEntity, score: MatchScore?): Boolean {
        val existing = dao.findByMandateAndListing(mandate, listing)
        if (existing != null && existing.status != MatchStatus.ACTIVE) {
            return false
        }
        if (score == null || score.total < minScore) {
            existing?.let { dao.delete(it) }
            return false
        }

        val now = Date(clock.millis())
        val match = existing ?: MatchEntity(
            mandate = mandate,
            listing = listing,
            agentId = mandate.agentId!!,
            status = MatchStatus.ACTIVE,
            createdAt = now,
        )
        dao.save(
            match.copy(
                score = score.total,
                neighborhoodScore = score.neighborhood,
                priceScore = score.price,
                roomsScore = score.rooms,
                amenitiesScore = score.amenities,
                availabilityScore = score.availability,
                furnitureScore = score.furniture,
                distanceKm = score.distanceKm,
                missingAmenities = score.missingAmenities,
                listingPrice = listing.price,
                modifiedAt = now,
            )
        )
        return true
    }

    // Distance (km) from each neighbourhood of the mandate's city to the nearest of the mandate's: 0 for its own
    private fun distances(mandate: MandateEntity): Map<Long, Double> {
        if (mandate.neighborhoodIds.isEmpty()) {
            return emptyMap()
        }
        val nearby = locationService.searchNearest(
            SearchNearestLocationRequest(
                ids = mandate.neighborhoodIds,
                type = LocationType.NEIGHBORHOOD,
                parentId = mandate.cityId, // Never a neighbourhood of another city, however close
                radius = nearbyRadiusKm,
                limit = nearbyMaxNeighborhoods,
            )
        ).associate { (location, distance) -> location.id to distance }
        return nearby + mandate.neighborhoodIds.associateWith { 0.0 }
    }

    private fun today(tenantId: Long): LocalDate =
        LocalDate.now(clock.withZone(ZoneId.of(tenantService.findById(tenantId).timeZone)))
}
