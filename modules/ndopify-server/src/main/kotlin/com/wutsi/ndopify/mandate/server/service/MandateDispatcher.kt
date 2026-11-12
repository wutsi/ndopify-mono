package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.agent.dto.SearchAgentRequest
import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.agent.server.service.AgentService
import com.wutsi.ndopify.customer.server.service.CustomerService
import com.wutsi.ndopify.mandate.dto.AssignmentStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.dao.AgentAssignmentCount
import com.wutsi.ndopify.mandate.server.dao.AssignmentRepository
import com.wutsi.ndopify.mandate.server.dao.MandateRepository
import com.wutsi.ndopify.mandate.server.domain.AssignmentEntity
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.mandate.server.event.AssignmentCreatedEvent
import com.wutsi.ndopify.mandate.server.event.MandateManualAssignmentEvent
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.platform.logger.KVLogger
import com.wutsi.ndopify.refdata.dto.LocationType
import com.wutsi.ndopify.refdata.dto.SearchNearestLocationRequest
import com.wutsi.ndopify.refdata.server.service.LocationService
import com.wutsi.ndopify.refdata.server.service.TenantService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.util.Date
import kotlin.math.min
import kotlin.random.Random

/**
 * Offers a mandate waiting for an agent to the best scoring candidate, exclusively, for a TTL. See
 * `docs/superpowers/plans/2026-10-14-mandate-dispatch.md` for the algorithm and the reasons behind it.
 */
@Service
class MandateDispatcher(
    private val assignmentDao: AssignmentRepository,
    private val mandateDao: MandateRepository,
    private val agentService: AgentService,
    private val customerService: CustomerService,
    private val locationService: LocationService,
    private val dispatchHours: DispatchHours,
    private val tenantService: TenantService,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
    private val logger: KVLogger,

    @param:Value("\${ndopify.mandate.assignment.ttl-minutes}") private val ttlMinutes: Long,
    @param:Value("\${ndopify.mandate.assignment.manual-after-days}") private val manualAfterDays: Long,
    @param:Value("\${ndopify.mandate.assignment.max-load}") private val maxLoad: Int,
    @param:Value("\${ndopify.mandate.assignment.max-pending}") private val maxPending: Long,
    @param:Value("\${ndopify.mandate.assignment.nearby-radius-km}") private val nearbyRadiusKm: Double,
    @param:Value("\${ndopify.mandate.assignment.nearby-max-neighborhoods}") private val nearbyMaxNeighborhoods: Int,
    @param:Value("\${ndopify.mandate.assignment.jitter}") private val jitter: Double,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateDispatcher::class.java)

        const val WEIGHT_NEIGHBORHOOD = 0.35
        const val WEIGHT_FAIRNESS = 0.25
        const val WEIGHT_RATING = 0.15
        const val WEIGHT_RESPONSIVENESS = 0.10
        const val WEIGHT_CAPACITY = 0.15
        const val MIN_RATING = 1.0
        const val MAX_RATING = 5.0
        const val FAIRNESS_WINDOW_HOURS = 48.0
        const val MAX_CANDIDATES = 100
        private const val DAY_MILLIS = 86_400_000L
        private const val HOUR_MILLIS = 3_600_000.0

        // Expected races and missing mandates: the DISPATCH_SUMMARY / NO_OFFER details only matter when debugging
        private val QUIET_REASONS = setOf(
            DispatchReason.MANDATE_NOT_FOUND,
            DispatchReason.NOT_PENDING,
            DispatchReason.ALREADY_PENDING,
        )

        // Mandates an agent is actively working on, counted against its max-load. Not APPROVED / PUBLISHED: a listing
        // can stay published for weeks, and 5 of them would lock the agent out of new mandates
        val ACTIVE_STATUSES = listOf(
            MandateStatus.ASSIGNED,
            MandateStatus.PENDING_APPROVAL,
        )
    }

    /**
     * Offers the mandate to the best candidate. Returns the PENDING assignment, or null when the mandate
     * isn't waiting for an agent, already has a pending offer, is moved to MANUAL_ASSIGNMENT, it's night, or it has
     * no candidate left.
     */
    @Transactional
    fun dispatch(mandateId: Long): AssignmentEntity? = dispatchResult(mandateId).assignmentOrNull

    /**
     * Same as [dispatch], keeping the reason and the candidate details instead of only the offer. Called by
     * [AssignmentService.dispatchPending] to aggregate a batch summary; [dispatch] stays the public entry point.
     */
    @Transactional
    internal fun dispatchResult(mandateId: Long): DispatchResult {
        // Locked, so that 2 concurrent dispatches (ex: a decline + the expiry job) can't offer it twice
        val mandate = mandateDao.findByIdForUpdate(mandateId)
            ?: return skip(mandateId, DispatchReason.MANDATE_NOT_FOUND)
        if (mandate.status != MandateStatus.PENDING_ASSIGNMENT) {
            logger.add("mandate_status", mandate.status)
            return skip(mandateId, DispatchReason.NOT_PENDING, "mandate_status=${mandate.status}")
        }
        if (assignmentDao.existsByMandateAndStatus(mandate, AssignmentStatus.PENDING)) {
            logger.add("mandate_status", mandate.status)
            return skip(mandateId, DispatchReason.ALREADY_PENDING, "mandate_status=${mandate.status}")
        }

        // Too long without an agent: handed over to a human. Checked before the night, so it's never delayed
        val now = Date(clock.millis())
        if (mandate.createdAt.time + manualAfterDays * DAY_MILLIS <= now.time) {
            logger.add("mandate_expired", true)
            mandateDao.save(mandate.copy(status = MandateStatus.MANUAL_ASSIGNMENT, modifiedAt = now))
            eventPublisher.publishEvent(MandateManualAssignmentEvent(mandate.id!!, mandate.tenantId!!))
            return skip(mandateId, DispatchReason.OVERDUE_MANUAL, "manual_after_days=$manualAfterDays")
        }

        // No offer at night, in the tenant's time zone: the mandate is picked up again in the morning
        val zoneId = ZoneId.of(tenantService.findById(mandate.tenantId!!).timeZone)
        if (dispatchHours.isNight(now.toInstant(), zoneId)) {
            logger.add("is_night", true)
            return skip(mandateId, DispatchReason.NIGHT, "timezone=$zoneId")
        }

        val distances = distances(mandate)
        val candidates = findCandidates(mandate, distances)
        val excluded = candidates.excluded.toMutableMap()
        val scored = score(mandate, candidates.eligible, distances, excluded)
        val best = scored.maxByOrNull { it.total }
        val diagnostics = DispatchDiagnostics(candidates.searched, excluded, scored)
        if (best == null) {
            logger.add("no_candidate", true)
            LOGGER.info(
                ">>> Mandate#$mandateId - DISPATCH_SUMMARY candidates=${candidates.searched} " +
                    "excluded=$excluded eligible=0 offered=false",
            )
            return skip(mandateId, DispatchReason.NO_CANDIDATE, diagnostics = diagnostics)
        }

        logger.add("agent_id", best.agent.id)
        logger.add("agent_full_name", best.agent.party.fullName())
        val runnerUp = scored.filter { it.agent.id != best.agent.id }.maxByOrNull { it.total }
        LOGGER.info(
            ">>> Mandate#$mandateId - OFFER agent_id=${best.agent.id} score=${best.total}" +
                (
                    runnerUp?.let {
                        " runner_up=${it.agent.id}/${it.total} margin=${best.total - it.total}"
                    } ?: ""
                    ),
        )
        LOGGER.info(
            ">>> Mandate#$mandateId - DISPATCH_SUMMARY candidates=${candidates.searched} excluded=$excluded " +
                "eligible=${scored.size} offered=true agent_id=${best.agent.id}",
        )

        val assignment = assignmentDao.save(
            AssignmentEntity(
                mandate = mandate,
                agentId = best.agent.id!!,
                status = AssignmentStatus.PENDING,
                score = best.total,
                // The TTL only runs during the day: offered at 20:30 for 1h, it expires at 07:30
                expiresAt = Date.from(
                    dispatchHours.addDaytime(now.toInstant(), Duration.ofMinutes(ttlMinutes), zoneId)
                ),
                createdAt = now,
                modifiedAt = now,
            )
        )

        // On offer, not on accept: an agent who ignores offers still moves down the rotation
        agentService.markAssigned(best.agent, now)

        // Notifies the agent asynchronously, after commit
        eventPublisher.publishEvent(AssignmentCreatedEvent(assignment.id!!, mandate.tenantId))
        return DispatchResult.Offered(assignment, diagnostics)
    }

    // Logs why nothing was offered. Expected races stay at DEBUG; the actionable reasons are at INFO
    private fun skip(
        mandateId: Long,
        reason: DispatchReason,
        detail: String = "",
        diagnostics: DispatchDiagnostics? = null,
    ): DispatchResult.Skipped {
        logger.add("dispatch_reason", reason)
        val line = ">>> Mandate#$mandateId - NO_OFFER reason=$reason" + if (detail.isEmpty()) "" else " $detail"
        if (reason in QUIET_REASONS) {
            LOGGER.debug(line)
        } else {
            LOGGER.info(line)
        }
        return DispatchResult.Skipped(reason, detail, diagnostics)
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
                limit = nearbyMaxNeighborhoods, // The farthest are dropped first: they'd score ~0 anyway
            )
        ).associate { (location, distance) -> location.id to distance }
        return nearby + mandate.neighborhoodIds.associateWith { 0.0 }
    }

    // The candidates after the hard filters, the reasons they were dropped, and how many were merged before them
    private data class CandidatePool(
        val eligible: List<AgentEntity>,
        val excluded: Map<CandidateRuleOut, Int>,
        val searched: Int,
    )

    private fun findCandidates(mandate: MandateEntity, distances: Map<Long, Double>): CandidatePool {
        // Agents that already declined or ignored the mandate are never offered it again
        val excludeIds = assignmentDao.findByMandateAndStatusIn(
            mandate,
            listOf(AssignmentStatus.DECLINED, AssignmentStatus.EXPIRED),
        ).map { it.agentId }.toSet()

        val customer = customerService.findById(mandate.customerId)

        // Always in the mandate's city. The 2nd search makes sure the agents covering the mandate's or a nearby
        // neighbourhood aren't cut off by the 1st one's limit. Least recently offered first (never offered: NULL,
        // first in MySQL): past 100 agents, the cut drops the ones fairness would rank last anyway, never the newest
        val inCity = agentService.search(
            SearchAgentRequest(
                kycStatus = listOf(KycStatus.VERIFIED),
                cityId = mandate.cityId,
                limit = MAX_CANDIDATES,
            ),
            AgentService.SORT_LEAST_RECENTLY_ASSIGNED,
        )
        val nearby = if (distances.isEmpty()) {
            emptyList()
        } else {
            agentService.search(
                SearchAgentRequest(
                    kycStatus = listOf(KycStatus.VERIFIED),
                    cityId = mandate.cityId,
                    neighborhoodIds = distances.keys.toList(),
                    limit = MAX_CANDIDATES,
                ),
                AgentService.SORT_LEAST_RECENTLY_ASSIGNED,
            )
        }

        val merged = (nearby + inCity).distinctBy { it.id }
        val eligible = mutableListOf<AgentEntity>()
        val excluded = mutableMapOf<CandidateRuleOut, Int>()
        merged.forEach { agent ->
            // Same single `&&` as before: a declined customer only reports DECLINED_OR_EXPIRED
            val reason = when {
                agent.id in excludeIds -> CandidateRuleOut.DECLINED_OR_EXPIRED
                agent.party.id == customer.party.id -> CandidateRuleOut.SELF
                else -> null
            }
            if (reason == null) {
                eligible += agent
            } else {
                excluded.merge(reason, 1, Int::plus)
                LOGGER.debug(">>> Mandate#${mandate.id} Agent#${agent.id} - REJECTED reason=$reason")
            }
        }
        return CandidatePool(eligible, excluded, merged.size)
    }

    private fun score(
        mandate: MandateEntity,
        candidates: List<AgentEntity>,
        distances: Map<Long, Double>,
        excluded: MutableMap<CandidateRuleOut, Int>,
    ): List<CandidateScore> {
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val agentIds = candidates.map { it.id!! }
        val counts = assignmentDao.countByAgentIds(agentIds).groupBy { it.agentId }
        val loads = mandateDao.countByAgentIdsAndStatusIn(agentIds, ACTIVE_STATUSES)
            .associate { it.agentId to it.total }
        val now = clock.millis()

        val scored = mutableListOf<CandidateScore>()
        candidates.forEach { agent ->
            val load = loads[agent.id] ?: 0
            if (load >= maxLoad) { // At capacity: not a candidate
                excluded.merge(CandidateRuleOut.AT_CAPACITY, 1, Int::plus)
                LOGGER.debug(
                    ">>> Mandate#${mandate.id} Agent#${agent.id} - REJECTED reason=AT_CAPACITY load=$load max=$maxLoad",
                )
                return@forEach
            }
            // Offers still unanswered: not flooded with more until answering one, or until they expire
            val pendingOffers = pending(counts[agent.id].orEmpty())
            if (pendingOffers >= maxPending) {
                excluded.merge(CandidateRuleOut.MAX_PENDING, 1, Int::plus)
                LOGGER.debug(
                    ">>> Mandate#${mandate.id} Agent#${agent.id} - REJECTED reason=MAX_PENDING " +
                        "pending=$pendingOffers max=$maxPending",
                )
                return@forEach
            }

            val neighborhood = neighborhood(mandate, agent, distances)
            val fairness = fairness(agent, now)
            val rating = rating(agent)
            val responsiveness = responsiveness(counts[agent.id].orEmpty())
            val capacity = 1.0 - load.toDouble() / maxLoad
            // 0 in tests: deterministic
            val jitterValue = if (jitter > 0) Random.nextDouble(-jitter, jitter) else 0.0
            val total = WEIGHT_NEIGHBORHOOD * neighborhood +
                WEIGHT_FAIRNESS * fairness +
                WEIGHT_RATING * rating +
                WEIGHT_RESPONSIVENESS * responsiveness +
                WEIGHT_CAPACITY * capacity +
                jitterValue
            LOGGER.debug(
                ">>> Mandate#${mandate.id} Agent#${agent.id} - CANDIDATE score=$total neighborhood=$neighborhood " +
                    "fairness=$fairness rating=$rating responsiveness=$responsiveness capacity=$capacity " +
                    "jitter=$jitterValue",
            )
            scored += CandidateScore(
                agent = agent,
                total = total,
                neighborhood = neighborhood,
                fairness = fairness,
                rating = rating,
                responsiveness = responsiveness,
                capacity = capacity,
                jitter = jitterValue,
            )
        }
        return scored
    }

    // Covering the mandate's neighbourhoods: 0.5..1 by coverage. Covering a nearby one: 0..0.5 by distance.
    // Neutral when the customer didn't pick any
    private fun neighborhood(mandate: MandateEntity, agent: AgentEntity, distances: Map<Long, Double>): Double {
        if (mandate.neighborhoodIds.isEmpty()) {
            return 0.5
        }
        val covered = mandate.neighborhoodIds.count { it in agent.neighborhoodIds }
        if (covered > 0) {
            return 0.5 + 0.5 * covered / mandate.neighborhoodIds.size
        }
        val nearest = agent.neighborhoodIds.mapNotNull { distances[it] }.minOrNull() ?: return 0.0
        return 0.5 * (1.0 - nearest / nearbyRadiusKm)
    }

    // 0 right after an offer, back to 1 after FAIRNESS_WINDOW_HOURS. Never offered: 1
    private fun fairness(agent: AgentEntity, now: Long): Double {
        val last = agent.lastAssignedAt ?: return 1.0
        val hours = (now - last.time) / HOUR_MILLIS
        return min(hours / FAIRNESS_WINDOW_HOURS, 1.0)
    }

    // 1..5 stars → 0..1. Unrated (default 3.0) → 0.5. Clamped, in case a bad value gets in
    private fun rating(agent: AgentEntity): Double =
        (agent.rating.coerceIn(MIN_RATING, MAX_RATING) - MIN_RATING) / (MAX_RATING - MIN_RATING)

    private fun pending(counts: List<AgentAssignmentCount>): Long =
        counts.filter { it.status == AssignmentStatus.PENDING }.sumOf { it.total }

    // Smoothed acceptance rate, so that a new agent starts at 0.5. PENDING offers aren't answered yet
    private fun responsiveness(counts: List<AgentAssignmentCount>): Double {
        val accepted = counts.filter { it.status == AssignmentStatus.ACCEPTED }.sumOf { it.total }
        val answered = counts.filter { it.status != AssignmentStatus.PENDING }.sumOf { it.total }
        return (accepted + 1.0) / (answered + 2.0)
    }
}
