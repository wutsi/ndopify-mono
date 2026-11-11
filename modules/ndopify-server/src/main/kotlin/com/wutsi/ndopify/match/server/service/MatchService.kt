package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.error.dto.Error
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.Parameter
import com.wutsi.ndopify.error.dto.ParameterType
import com.wutsi.ndopify.error.server.exception.BadRequestException
import com.wutsi.ndopify.error.server.exception.ConflictException
import com.wutsi.ndopify.error.server.exception.NotFoundException
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.service.MandateService
import com.wutsi.ndopify.match.dto.CloseMatchRequest
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.dto.SearchMatchRequest
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationRequest
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import com.wutsi.ndopify.util.jpa.OffsetLimitPageRequest
import jakarta.persistence.criteria.Predicate
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
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
    private val mandateService: MandateService,

    @param:Value("\${ndopify.match.min-score}") private val minScore: Double,
    @param:Value("\${ndopify.match.nearby-radius-km}") private val nearbyRadiusKm: Double,
    @param:Value("\${ndopify.match.nearby-max-neighborhoods}") private val nearbyMaxNeighborhoods: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchService::class.java)

        // Not proposed (or the proposal expired unanswered) nor dismissed: the agent may still propose or dismiss it
        val OPEN_STATUSES = setOf(MatchStatus.ACTIVE, MatchStatus.SHORTLISTED, MatchStatus.EXPIRED)

        // The own matches that show the agent's inventory still has nothing for the search
        val NON_VIABLE_OWN_STATUSES = setOf(MatchStatus.DISMISSED, MatchStatus.REJECTED)
    }

    fun findById(id: Long): MatchEntity {
        return dao.findById(id).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MATCH_NOT_FOUND))
    }

    /**
     * The matches of the mandates [SearchMatchRequest.mandateIds] and / or of the listings
     * [SearchMatchRequest.listingIds] (both when both are given), in [SearchMatchRequest.statuses] (default: ACTIVE),
     * best score first, all mandates together. Only while the mandate is APPROVED and the listing PUBLISHED: a closed search, or a rented listing, keeps its rows but isn't
     * listed.
     */
    fun search(request: SearchMatchRequest): List<MatchEntity> {
        val statuses = request.statuses.ifEmpty { listOf(MatchStatus.ACTIVE) }
        val spec = Specification<MatchEntity> { root, _, cb ->
            val mandate = root.get<MandateEntity>("mandate")
            val listing = root.get<ListingEntity>("listing")
            val predicates = mutableListOf<Predicate>(
                cb.equal(mandate.get<MandateStatus>("status"), MandateStatus.APPROVED),
                cb.equal(listing.get<ListingStatus>("status"), ListingStatus.PUBLISHED),
                root.get<Any>("status").`in`(statuses),
            )
            if (request.mandateIds.isNotEmpty()) {
                predicates.add(mandate.get<Long>("id").`in`(request.mandateIds))
            }
            if (request.listingIds.isNotEmpty()) {
                predicates.add(listing.get<Long>("id").`in`(request.listingIds))
            }
            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(
            Sort.Order.asc("coBrokerage"),
            Sort.Order.desc("score"),
            Sort.Order.asc("id"),
        )
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
     * Hides an ACTIVE, SHORTLISTED or EXPIRED match from the agent: it's never re-scored nor re-created. 409 MATCH_NOT_ACTIVE
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

    /**
     * Closes the deal of an ACCEPTED match. 404 MATCH_NOT_FOUND, 409 MATCH_NOT_ACCEPTED, 409 LISTING_NOT_PUBLISHED when
     * the listing side will be closed and its listing isn't PUBLISHED, 400 invalid price.
     */
    @Transactional
    fun close(matchId: Long, request: CloseMatchRequest): List<MandateEntity> {
        val match = findById(matchId)
        if (match.status != MatchStatus.ACCEPTED) {
            throw ConflictException(Error(ErrorCode.MATCH_NOT_ACCEPTED))
        }

        val search = match.mandate
        val listingMandate = match.listing.mandate
        val closeListing = listingMandate.agentId == search.agentId &&
            listingMandate.status != MandateStatus.CLOSED &&
            listingMandate.status != MandateStatus.CANCELLED
        if (closeListing && match.listing.status != ListingStatus.PUBLISHED) {
            throw ConflictException(Error(ErrorCode.LISTING_NOT_PUBLISHED))
        }
        if (request.finalPrice.amount < 1) {
            throw BadRequestException(
                Error(
                    code = ErrorCode.HTTP_INVALID_PARAMETER,
                    parameter = Parameter(name = "finalPrice", type = ParameterType.PARAMETER_TYPE_PAYLOAD),
                ),
            )
        }
        return mandateService.closeDeal(
            searchMandateId = search.id!!,
            counterpartMandateId = listingMandate.id!!.takeIf { closeListing },
            matchId = matchId,
            finalPrice = request.finalPrice,
        )
    }

    /**
     * Matches a search mandate against its agent's inventory, then, when no viable own match is left to offer, against
     * the other agents' published listings (co-brokerage). Returns the number of ACTIVE matches stored.
     */
    fun matchMandate(mandateId: Long): Int {
        // With its neighbourhoods: not in a transaction, the lazy collection couldn't be read later
        val mandate = mandateDao.findWithNeighborhoodsById(mandateId)
            ?: throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
        if (mandate.mandateType != MandateType.SEARCH || mandate.status != MandateStatus.APPROVED) {
            LOGGER.info(">>> Mandate#$mandateId - NO_MATCH mandate_status=${mandate.status},  mandate_type=${mandate.mandateType}")
            return 0
        }
        val agentId = mandate.agentId ?: return 0

        val distances = distances(mandate)
        val today = today(mandate.tenantId!!)

        // 1. The agent's own inventory
        val own = listingDao.findByAgentIdAndStatus(agentId, ListingStatus.PUBLISHED)
        val ownCount = matchAll(own.map { listing -> Triple(mandate, listing, distances) }, today)
        if (ownCount > 0 || hasViableOwnMatch(mandate.id!!)) {
            return ownCount
        }

        // 2. No own match left to offer: fall back to the other agents' listings (co-brokerage)
        val candidates = listingDao.findByCityIdAndListingTypeAndStatusAndAgentIdNot(
            mandate.cityId,
            mandate.listingType,
            ListingStatus.PUBLISHED,
            agentId,
        )
        if (candidates.isEmpty()) {
            LOGGER.info(">>> Mandate#$mandateId - NO_MATCH own_listing_count=${own.size}")
            return 0
        }
        LOGGER.info(">>> Mandate#$mandateId - CO_BROKERAGE candidate_listing_ids=${candidates.map { it.id }}")
        return matchAll(candidates.map { listing -> Triple(mandate, listing, distances) }, today)
    }

    // A PUBLISHED own match that neither the agent nor the customer turned down
    private fun hasViableOwnMatch(mandateId: Long): Boolean =
        dao.existsViableOwnMatch(
            mandateId = mandateId,
            statuses = NON_VIABLE_OWN_STATUSES,
            listingStatus = ListingStatus.PUBLISHED,
        )

    /**
     * Re-matches every APPROVED SEARCH mandate with an agent, as [matchMandate]: catches up on a run lost after an
     * approval or a publication (failed, server stopped, executor queue full), and re-scores against today (a listing's
     * availability gets closer to a search's moving deadline). Safe to re-run: only ACTIVE rows change, and
     * `notifiedAt` is kept, so the agent isn't told twice. A mandate failing is logged and skipped. Returns the number
     * of ACTIVE matches stored.
     */
    fun matchAll(): Int {
        val ids = mandateDao.findIdsWithAgent(MandateType.SEARCH, MandateStatus.APPROVED)
        return ids.sumOf { id ->
            try {
                matchMandate(id)
            } catch (ex: Exception) {
                LOGGER.warn("Unable to match Mandate#$id", ex)
                0
            }
        }
    }

    /** Matches a published listing against its agent's open searches. Returns the number of ACTIVE matches stored. */
    fun matchListing(listingId: Long): Int {
        val listing = listingDao.findById(listingId).orElse(null)
            ?: throw NotFoundException(error = Error(code = ErrorCode.LISTING_NOT_FOUND))
        if (listing.status != ListingStatus.PUBLISHED) {
            LOGGER.info(">>> Listing#$listingId - NO_MATCH listing_status=${listing.status}")
            return 0
        }

        val mandates = mandateDao.findByAgentIdAndMandateTypeAndStatus(
            listing.agent.id!!,
            MandateType.SEARCH,
            MandateStatus.APPROVED,
        )
        if (mandates.isEmpty()) {
            LOGGER.info(">>> Listing#$listingId - NO_MATCH mandate_count=${mandates.size}")
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
                val score = matcher.score(mandate, listing, distances, today)
                if (store(mandate, listing, score)) {
                    LOGGER.info(">>> Mandate#${mandate.id} Listing#${listing.id} - MATCH score=${score?.total}")
                    count++
                } else {
                    LOGGER.info(">>> Mandate#${mandate.id} Listing#${listing.id} - NO_MATCH Match score=${score?.total}")
                }
            } catch (_: ObjectOptimisticLockingFailureException) {
                conflicts++ // The agent dismissed it meanwhile
            } catch (_: DataIntegrityViolationException) {
                conflicts++ // Another run inserted the same pair meanwhile
            }
        }
        return count
    }

    // Returns true when an ACTIVE match is stored
    private fun store(mandate: MandateEntity, listing: ListingEntity, score: MatchScore?): Boolean {
        val coBrokerage = listing.agent.id != mandate.agentId
        val existing = dao.findByMandateAndListing(mandate, listing)
        if (existing != null && existing.status != MatchStatus.ACTIVE) {
            return false
        }
        if (score == null || score.total < minScore) {
            // A co-brokerage row was valid when created and its own-inventory situation is unchanged: kept (sticky)
            if (existing != null && !coBrokerage) {
                dao.delete(existing)
            }
            return false
        }

        val now = Date(clock.millis())
        val match = existing ?: MatchEntity(
            mandate = mandate,
            listing = listing,
            agentId = mandate.agentId!!,
            coBrokerage = coBrokerage,
            status = MatchStatus.ACTIVE,
            createdAt = now,
        )
        dao.save(
            match.copy(
                coBrokerage = coBrokerage,
                score = score.total,
                typeScore = score.type,
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
