# Plan: Dispatching mandates to agents

## Context

`MandateService.create` starts a mandate as `PENDING_ASSIGNMENT`, but nothing assigns it to an agent yet.

Already on `main`:

- `T_ASSIGNMENT` (`V6_1`, incl. `expires_at`), `AssignmentEntity` (with a `score` and a non-null `expiresAt`),
  `AssignmentStatus` (`PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`), the assignment DTOs (`Assignment`,
  `AssignmentSummary`, `Get/SearchAssignment*`), `db/test/clean.sql` deleting `T_ASSIGNMENT`.
- `AgentEntity.lastAssignedAt` / `Agent.lastAssignedAt`, `SearchAgentRequest.kycStatus`, and a `MandateDispatcher`
  skeleton that already finds the candidates.
- `AgentEntity.joinedAt` / `Agent.joinedAt`: set once, on the agent's first VERIFIED KYC case, by
  `AgentKycEventListener` (`KycCaseCompletedEvent` now carries `partyId` + `status`). Not used by the dispatch.
- `TenantEntity.timeZone` / `Tenant.timeZone` (IANA id; `T_TENANT.time_zone` in `V1_2`, default `Africa/Douala`):
  the night hours below are computed in it.

## Decision

**Sequential, exclusive offers**: the mandate is offered to the best-scoring agent only, for a TTL (1h,
configurable). If the agent declines or lets it expire, it is offered to the next best agent; agents who declined or
ignored a mandate are never offered it again.

**No offer at night** (configurable hours): mandates created or released at night wait for the morning, and an offer's
TTL only runs during the day. **After N days** (configurable) without an agent, the mandate goes to
`MANUAL_ASSIGNMENT`, for a human to assign.

Rejected alternative: broadcast to the top N agents, first to accept wins. Faster claims, but agents race each other
and it needs an atomic claim. Revisit only if acceptance latency becomes a problem.

## Algorithm

### 1. Hard filters (candidates)

- KYC `VERIFIED`, in the mandate's `cityId` *(done)* — always: no agent of another city is ever a candidate
- Not the customer's own party *(done)*
- Not `DECLINED` / `EXPIRED` for this mandate *(done)*
- Nothing is dispatched if the mandate already has a `PENDING` assignment (mandate row locked)
- Not at capacity: mandates being worked on (`ASSIGNED`, `PENDING_APPROVAL`) < `max-load` — not `APPROVED` /
  `PUBLISHED`, which can last weeks

### 2. Score (0..1, stored in `T_ASSIGNMENT.score`)

| Factor              | Formula                                                                               | Weight |
|---------------------|---------------------------------------------------------------------------------------|--------|
| Neighbourhood match | Graded by proximity, see the table below                                              | 0.35   |
| Fairness (rotation) | `min(hoursSince(lastAssignedAt) / 48, 1)`, 1 if never offered                         | 0.25   |
| Rating              | `(rating − 1) / 4` — 1..5 stars, new agents start at 3 → 0.5                          | 0.15   |
| Responsiveness      | `(accepted + 1) / (answered + 2)` — smoothed acceptance rate, new agents start at 0.5 | 0.10   |
| Capacity            | `1 − activeLoad / maxLoad`                                                            | 0.15   |
| Tie-break           | random jitter ±`jitter` (0.01; 0 in tests)                                            | —      |

```
score = 0.35·neighbourhood + 0.25·fairness + 0.15·rating + 0.10·responsiveness + 0.15·capacity + jitter
```

**Rating** — new `AgentEntity.rating`, 1..5 (stars), **default 3.0**: the midpoint, so an unrated agent scores a
neutral 0.5, like a new agent's responsiveness. Every agent is unrated today, so the factor is neutral (adds 0.075 to
everyone) until ratings exist; it starts ranking agents as soon as they differ. The 0.15 comes from neighbourhood
(−0.05), fairness (−0.05) and responsiveness (−0.05): proximity and rotation stay the 2 strongest factors, and
responsiveness, partly a quality signal too, overlaps with the rating.

How a rating gets its value (customer reviews once a mandate is closed, an admin…) is out of scope: this plan only
adds the field, its default, and its use in the score. It's **read-only** in the API (`Agent.rating`), never in
`UpdateAgentRequest`: an agent mustn't rate itself.

`ExperienceLevel` is deliberately ignored for now (can be added later as a 5th weighted term).

### Nearby neighbourhoods (fallback when nobody covers the mandate's)

The neighbourhood factor falls back on the nearest neighbourhoods, using `LocationService.searchNearest` (distance
between the neighbourhoods' coordinates), restricted to **the mandate's city**, instead of scoring 0 every agent who
doesn't cover exactly the mandate's neighbourhoods:

| The agent covers…                                 | Neighbourhood score                          |
|---------------------------------------------------|----------------------------------------------|
| some of the mandate's neighbourhoods              | `0.5 + 0.5 × coverage` → **0.5..1**          |
| a neighbourhood within `nearby-radius-km` of them | `0.5 × (1 − distance / radius)` → **0..0.5** |
| nothing nearby                                    | 0                                            |
| *(the mandate has no neighbourhoods)*             | 0.5                                          |

The radius bounds *how far*, `nearby-max-neighborhoods` (20) bounds *how many*: `searchNearest` returns them nearest
first, so the cap only drops the farthest ones, whose agents would score ~0 on this factor anyway. Both only widen the
agent search; the cost (one spatial query + an `IN` of ≤ 20 ids) is negligible.

The bands don't overlap: an agent covering one of the mandate's neighbourhoods always outscores one who's merely
nearby, while fairness / responsiveness / capacity still rank agents within a band.

Locations are assumed always imported and geocoded (GeoNames), so every neighbourhood has coordinates.

The nearby neighbourhoods are those of the mandate's city only (`parentId = mandate.cityId`): a neighbourhood of a
neighbouring city is never "nearby", however close. The candidates are also searched by neighbourhood (mandate's +
nearby ones, still in the mandate's city), merged with the city's, so that the agents covering them can't be cut off
by the city search's `limit = 100`. Both searches are ordered least recently offered first
(`lastAssignedAt` ascending, never offered first), so past 100 agents in a city the cut drops the agents fairness
would rank last — not, as an id order would, every agent who joined after the first 100.

Rejected alternative: strict tiers (exact neighbourhood → nearby → city, next tier only when the previous is empty).
Fairness and responsiveness would only work inside a tier, so the few agents covering a popular neighbourhood would
get all its mandates.

### 3. Lifecycle

```
dispatch(mandate):
  lock mandate (PESSIMISTIC_WRITE); return if status != PENDING_ASSIGNMENT or a PENDING assignment exists
  if createdAt + manual-after-days <= now: status = MANUAL_ASSIGNMENT; return   // even at night
  if night: return                                    // MandateJobs.dispatch retries in the morning
  best = candidates.maxBy(score) ?: return null      // nobody left → MandateJobs.dispatch retries later
  save Assignment(PENDING, score, expiresAt = now + TTL of daytime)
  agent.lastAssignedAt = now                          // on OFFER, not on accept
  publish AssignmentCreatedEvent(assignmentId, tenantId)

accept(assignment):    → ACCEPTED; mandate.agentId = agentId; mandate.status = ASSIGNED
decline(assignment):   → DECLINED; after commit: dispatch(mandate)
MandateJobs.expire:    PENDING past expiresAt → EXPIRED; after commit: dispatch(mandate)
MandateJobs.dispatch:  PENDING_ASSIGNMENT mandates without a PENDING offer → dispatch(mandate)
```

`lastAssignedAt` is set **when the offer is made**: an agent who ignores offers still moves down the rotation, and
it spreads concurrent offers across agents (no need to count PENDING offers in the load).

### 4. Night & manual assignment

- **Night** = `[night.start, night.end)` (default `21:00` → `07:00`) in the **tenant's time zone**
  (`TenantEntity.timeZone`); it may span midnight. The night hours stay global config: "night" is 21:00 → 07:00
  local time for every tenant, wherever it is. `night.start == night.end` means no
  night.
- **No offer at night**: `dispatch` returns without offering, the mandate stays `PENDING_ASSIGNMENT`.
  `MandateJobs.dispatch` (every 15 min) dispatches the mandates waiting without a PENDING offer: the ones that came in
  at night (first run after `night.end`), but also the ones that had **no candidate** (retried until one shows up,
  ex: a newly verified agent).
- **The TTL only runs during the day**: `expiresAt = now + ttl` of daytime, so an offer made at 20:30 with a 1h TTL
  expires at 07:30 the next morning, not at 21:30 when the agent can't be expected to answer. An offer therefore
  never expires — and nothing is re-dispatched — at night.
- **Manual assignment**: when `dispatch` finds a mandate created more than `manual-after-days` (calendar days, nights
  included) ago, it moves it to `MANUAL_ASSIGNMENT` instead of offering it, and publishes
  `MandateManualAssignmentEvent`. It's checked in `dispatch`, so it happens on every path: the job, a decline, an
  expiry. A PENDING offer is never interrupted: the mandate only moves once it has expired or been declined, so an
  offer can't be accepted on a `MANUAL_ASSIGNMENT` mandate.
- `MANUAL_ASSIGNMENT` is appended to `MandateStatus` (persisted by ordinal: never inserted before existing values).
  How a human then assigns it (admin console) is out of scope.

## 1. Configuration — `application.yml`

```yaml
ndopify:
    mandate:
        assignment:
            enabled: false                 # Master switch: creation listener + both jobs. true only once agents are notified (PR 3)
            ops-endpoints:
                enabled: false             # POST /v1/assignments/dispatch and /{id}/expire: unauthenticated, off in prod
            jitter: 0.01                   # Random tie-break; 0 in tests, so scores are deterministic
            ttl-minutes: 60          # An agent has 1h to accept an offer, then it's EXPIRED and re-dispatched
            max-load: 5              # Max mandates an agent can work on at the same time
            nearby-radius-km: 3            # Neighbourhoods this close to the mandate's are "nearby"
            nearby-max-neighborhoods: 20   # Only the nearest ones (searchNearest returns them nearest first)
            manual-after-days: 3           # Without an agent after 3 days, the mandate goes to MANUAL_ASSIGNMENT
            night: # No offer, and the TTL is paused, from start to end (tenant's local time)
                start: "21:00"
                end: "07:00"
            expire:
                cron: "0 */5 * * * ?"      # Every 5 minutes
            dispatch:
                cron: "0 */15 * * * ?"     # Every 15 minutes: mandates that came at night, or had no candidate
```

Scoring weights stay constants in `MandateDispatcher` for now.

`src/test/resources/application-qa.yml` (the tests' profile) sets `enabled: true`, `ops-endpoints.enabled: true` and
`jitter: 0`. Local dev turns them on in its own config when needed.

**`enabled: false`** gates `MandateEventListener` (dispatch on creation, §8), `AssignmentEventListener` (re-dispatch
after decline / expiry) and both `MandateJobs`. It must stay off until agents are notified (PR 3): an offer nobody
hears about expires, and an expired agent is excluded from that mandate **forever**, so running dispatch without
notification burns through a city's agents. It's also checked on first enable: every `PENDING_ASSIGNMENT` mandate
older than `manual-after-days` goes to `MANUAL_ASSIGNMENT` on the first job run (one event each).

## 2. Migration — amend `V6_1__mandate-assignment.sql`

Committed, but no database is deployed yet, so it's amended rather than followed by a new migration (drop and
recreate the local schema). `expires_at` is already there (`NOT NULL`, like `AssignmentEntity.expiresAt`); it adds:

```sql
    version                 BIGINT NOT NULL DEFAULT 0,
    ...
    INDEX IDX_ASSIGNMENT_mandate_status (mandate_id, status),
    INDEX IDX_ASSIGNMENT_agent_status (agent_id, status),
    INDEX IDX_ASSIGNMENT_status_expires (status, expires_at),
```

Next to the `last_assigned_at` it already adds to `T_AGENT`:

```sql
-- 1..5 stars; 3 = unrated (neutral in the dispatch score)
ALTER TABLE T_AGENT ADD COLUMN rating DOUBLE NOT NULL DEFAULT 3.0;
```

Defaulted, so every existing agent and the test fixtures inserting into `T_AGENT` without it get 3.0.

`AssignmentStatus` is persisted by ordinal (like `KycStatus`): only ever append new values.

## 3. Entities — `AssignmentEntity`, `AgentEntity`

`AssignmentEntity` (`expiresAt` already exists, non-null):

```kotlin
    // Optimistic locking: of 2 concurrent transitions of the same offer (double-click accept/decline, accept vs
    // expire), the 2nd write fails instead of silently overwriting the 1st
    @Version
    val version: Long? = null,
```

`ControllerErrorHandler` maps Spring's `ObjectOptimisticLockingFailureException` to 409 `ASSIGNMENT_NOT_PENDING`
(the offer changed under the caller: it's no longer the PENDING one it read).

`AgentEntity` (+ `Agent` DTO in `ndopify-dto`, + `AgentMapper`; **not** in `UpdateAgentRequest`):

```kotlin
    val rating: Double = AgentEntity.DEFAULT_RATING, // 1..5 stars

    companion object {
        const val DEFAULT_RATING = 3.0
    }
```

`MandateStatus` (`ndopify-dto`) — appended, never inserted (persisted by ordinal):

```kotlin
enum class MandateStatus {
    UNKNOWN,
    PENDING_ASSIGNMENT,
    ASSIGNED,
    PENDING_APPROVAL,
    APPROVED,
    PUBLISHED,
    CLOSED,
    CANCELLED,
    MANUAL_ASSIGNMENT,
}
```

## 4. Repositories

```kotlin
interface AgentAssignmentCount {
    val agentId: Long
    val status: AssignmentStatus
    val total: Long
}

@Repository
interface AssignmentRepository : CrudRepository<AssignmentEntity, Long>, JpaSpecificationExecutor<AssignmentEntity> {
    fun findByMandateAndStatusIn(mandate: MandateEntity, status: List<AssignmentStatus>): List<AssignmentEntity>

    fun existsByMandateAndStatus(mandate: MandateEntity, status: AssignmentStatus): Boolean

    fun findByStatusAndExpiresAtBefore(status: AssignmentStatus, date: Date, page: Pageable): List<AssignmentEntity>

    // One query for all the candidates, instead of one per agent
    @Query(
        "SELECT a.agentId AS agentId, a.status AS status, COUNT(a) AS total FROM AssignmentEntity a " +
            "WHERE a.agentId IN :agentIds GROUP BY a.agentId, a.status"
    )
    fun countByAgentIds(agentIds: Collection<Long>): List<AgentAssignmentCount>
}
```

```kotlin
interface AgentMandateCount {
    val agentId: Long
    val total: Long
}

// MandateRepository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM MandateEntity m WHERE m.id = :id")
fun findByIdForUpdate(id: Long): MandateEntity?

@Query(
    "SELECT m.agentId AS agentId, COUNT(m) AS total FROM MandateEntity m " +
        "WHERE m.agentId IN :agentIds AND m.status IN :statuses GROUP BY m.agentId"
)
fun countByAgentIdsAndStatusIn(agentIds: Collection<Long>, statuses: Collection<MandateStatus>): List<AgentMandateCount>

// Mandates waiting for an agent without a PENDING offer: came at night, or had no candidate. Oldest first
@Query(
    "SELECT m.id FROM MandateEntity m WHERE m.status = :status AND NOT EXISTS (" +
        "SELECT a FROM AssignmentEntity a WHERE a.mandate = m AND a.status = :assignmentStatus) " +
        "ORDER BY m.createdAt, m.id"
)
fun findIdsWithoutAssignment(
    status: MandateStatus,
    assignmentStatus: AssignmentStatus,
    page: Pageable,
): List<Long>
```

## 5. `DispatchHours` — night & daytime TTL

Its own component, so that the dispatcher tests mock it, and its edge cases (midnight, TTL across the night, time
zones) are unit tested alone. The night hours are global config; the time zone is the **tenant's**, passed on each
call.

```kotlin
@Service
class DispatchHours(
    @param:Value("\${ndopify.mandate.assignment.night.start}") nightStart: String,
    @param:Value("\${ndopify.mandate.assignment.night.end}") nightEnd: String,
) {
    private val start = LocalTime.parse(nightStart)
    private val end = LocalTime.parse(nightEnd)

    fun isNight(instant: Instant, zoneId: ZoneId): Boolean {
        if (start == end) {
            return false
        }
        val time = instant.atZone(zoneId).toLocalTime()
        return if (start > end) {
            time >= start || time < end // Spans midnight: 21:00 → 07:00
        } else {
            time >= start && time < end
        }
    }

    /** [from] + [duration] counted during the day only, in [zoneId]: the clock stops at night. */
    fun addDaytime(from: Instant, duration: Duration, zoneId: ZoneId): Instant {
        if (start == end) {
            return from.plus(duration)
        }
        var cursor = from.atZone(zoneId)
        var remaining = duration
        while (true) {
            if (isNight(cursor.toInstant(), zoneId)) {
                cursor = next(cursor, end) // Morning
            }
            val night = next(cursor, start)
            val available = Duration.between(cursor, night)
            if (remaining <= available) {
                return cursor.plus(remaining).toInstant()
            }
            remaining -= available
            cursor = night
        }
    }

    // Next occurrence of [time] strictly after [from]
    private fun next(from: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val today = from.with(time)
        return if (today.isAfter(from)) today else today.plusDays(1)
    }
}
```

## 5b. `MandateDispatcher`

```kotlin
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
    @param:Value("\${ndopify.mandate.assignment.ttl-minutes}") private val ttlMinutes: Long,
    @param:Value("\${ndopify.mandate.assignment.manual-after-days}") private val manualAfterDays: Long,
    @param:Value("\${ndopify.mandate.assignment.max-load}") private val maxLoad: Int,
    @param:Value("\${ndopify.mandate.assignment.nearby-radius-km}") private val nearbyRadiusKm: Double,
    @param:Value("\${ndopify.mandate.assignment.nearby-max-neighborhoods}") private val nearbyMaxNeighborhoods: Int,
    @param:Value("\${ndopify.mandate.assignment.jitter}") private val jitter: Double,
) {
    companion object {
        const val WEIGHT_NEIGHBORHOOD = 0.35
        const val WEIGHT_FAIRNESS = 0.25
        const val WEIGHT_RATING = 0.15
        const val WEIGHT_RESPONSIVENESS = 0.10
        const val WEIGHT_CAPACITY = 0.15
        const val MIN_RATING = 1.0
        const val MAX_RATING = 5.0
        const val FAIRNESS_WINDOW_HOURS = 48.0

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
    fun dispatch(mandateId: Long): AssignmentEntity? {
        // Locked, so that 2 concurrent dispatches (ex: a decline + the expiry job) can't offer it twice
        val mandate = mandateDao.findByIdForUpdate(mandateId) ?: return null
        if (mandate.status != MandateStatus.PENDING_ASSIGNMENT ||
            assignmentDao.existsByMandateAndStatus(mandate, AssignmentStatus.PENDING)
        ) {
            return null
        }

        // Too long without an agent: handed over to a human. Checked before the night, so it's never delayed
        val now = Date(clock.millis())
        if (mandate.createdAt.time + manualAfterDays * 86_400_000 <= now.time) {
            mandateDao.save(mandate.copy(status = MandateStatus.MANUAL_ASSIGNMENT, modifiedAt = now))
            eventPublisher.publishEvent(MandateManualAssignmentEvent(mandate.id!!, TenantContext.get()!!))
            return null
        }

        // No offer at night, in the tenant's time zone: MandateJobs.dispatch picks the mandate up in the morning
        val zoneId = ZoneId.of(tenantService.findById(mandate.tenantId!!).timeZone)
        if (dispatchHours.isNight(now.toInstant(), zoneId)) {
            return null
        }

        val distances = distances(mandate)
        val best = score(mandate, findCandidates(mandate, distances), distances).maxByOrNull { it.second }
            ?: return null
        val assignment = assignmentDao.save(
            AssignmentEntity(
                mandate = mandate,
                agentId = best.first.id!!,
                status = AssignmentStatus.PENDING,
                score = best.second,
                // The TTL only runs during the day: offered at 20:30 for 1h, it expires at 07:30
                expiresAt = Date.from(
                    dispatchHours.addDaytime(
                        now.toInstant(),
                        Duration.ofMinutes(ttlMinutes),
                        zoneId
                    )
                ),
                createdAt = now,
                modifiedAt = now,
            )
        )
        // On offer, not on accept: an agent who ignores offers still moves down the rotation
        agentService.markAssigned(best.first, now)

        // Notifies the agent asynchronously, after commit — same pattern as AgentCreatedEvent
        eventPublisher.publishEvent(AssignmentCreatedEvent(assignment.id!!, TenantContext.get()!!))
        return assignment
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
        ).associate { (location, distance) -> location.id!! to distance }
        return nearby + mandate.neighborhoodIds.associateWith { 0.0 }
    }

    private fun findCandidates(mandate: MandateEntity, distances: Map<Long, Double>): List<AgentEntity> {
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
                limit = 100,
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
                    limit = 100,
                ),
                AgentService.SORT_LEAST_RECENTLY_ASSIGNED,
            )
        }
        return (nearby + inCity)
            .distinctBy { it.id }
            .filter { agent -> agent.id !in excludeIds && agent.party.id != customer.party.id }
    }

    private fun score(
        mandate: MandateEntity,
        candidates: List<AgentEntity>,
        distances: Map<Long, Double>,
    ): List<Pair<AgentEntity, Double>> {
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val agentIds = candidates.map { it.id!! }
        val counts = assignmentDao.countByAgentIds(agentIds).groupBy { it.agentId }
        val loads = mandateDao.countByAgentIdsAndStatusIn(agentIds, ACTIVE_STATUSES)
            .associate { it.agentId to it.total }
        val now = clock.millis()

        return candidates
            .filter { agent -> (loads[agent.id] ?: 0) < maxLoad } // At capacity: not a candidate
            .map { agent ->
                val score = WEIGHT_NEIGHBORHOOD * neighborhood(mandate, agent, distances) +
                    WEIGHT_FAIRNESS * fairness(agent, now) +
                    WEIGHT_RATING * rating(agent) +
                    WEIGHT_RESPONSIVENESS * responsiveness(counts[agent.id].orEmpty()) +
                    WEIGHT_CAPACITY * (1.0 - (loads[agent.id] ?: 0).toDouble() / maxLoad) +
                    (if (jitter > 0) Random.nextDouble(-jitter, jitter) else 0.0) // 0 in tests: deterministic
                agent to score
            }
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
        val hours = (now - last.time) / 3_600_000.0
        return min(hours / FAIRNESS_WINDOW_HOURS, 1.0)
    }

    // 1..5 stars → 0..1. Unrated (default 3.0) → 0.5. Clamped, in case a bad value gets in
    private fun rating(agent: AgentEntity): Double =
        ((agent.rating.coerceIn(MIN_RATING, MAX_RATING) - MIN_RATING) / (MAX_RATING - MIN_RATING))

    // Smoothed acceptance rate, so that a new agent starts at 0.5. PENDING offers aren't answered yet
    private fun responsiveness(counts: List<AgentAssignmentCount>): Double {
        val accepted = counts.filter { it.status == AssignmentStatus.ACCEPTED }.sumOf { it.total }
        val answered = counts.filter { it.status != AssignmentStatus.PENDING }.sumOf { it.total }
        return (accepted + 1.0) / (answered + 2.0)
    }
}
```

`SearchNearestLocationRequest` / `LocationService.searchNearest` — new optional `parentId` filter, named like
`SearchLocationRequest.parentId`:

```kotlin
data class SearchNearestLocationRequest(
    @param:BindParam("id") val ids: List<Long> = emptyList(), // the locations to search around
    val type: LocationType? = null, // defaults to the type(s) of the locations searched around
    @param:BindParam("parent-id") val parentId: Long? = null, // only the children of this location (ex: a city)
    val radius: Double = 1.0, // in km
    val limit: Int = 20,
    val offset: Int = 0,
)
```

```sql
-- SQL_NEAREST: the parent filter is appended (and :parentId bound) only when set
WHERE l.type IN (:types)
  AND l.id NOT IN (:excludedIds)
  AND l.latitude IS NOT NULL AND l.longitude IS NOT NULL
  AND l.parent_fk = :parentId   -- only if request.parentId != null
```

Exposed on `GET /v1/locations/nearest?parent-id=…` too; `SearchNearestLocationEndpointTest` gets a case for it.

`AgentService` — the dispatcher doesn't write to another domain's DAO directly; `search` takes an optional sort
(server-side only, not in `SearchAgentRequest`):

```kotlin
companion object {
    // MySQL sorts NULL first in ASC: never-offered agents come first
    val SORT_LEAST_RECENTLY_ASSIGNED: Sort = Sort.by(Sort.Order.asc("lastAssignedAt"), Sort.Order.asc("id"))
}

fun search(request: SearchAgentRequest, sort: Sort = Sort.by("id")): List<AgentEntity>

@Transactional
fun markAssigned(agent: AgentEntity, date: Date): AgentEntity =
    dao.save(agent.copy(lastAssignedAt = date, modifiedAt = date))
```

## 6. `AssignmentService` — dispatch / expire / accept / decline

```kotlin
@Service
class AssignmentService(
    private val dao: AssignmentRepository,
    private val mandateDao: MandateRepository,
    private val mandateService: MandateService,
    private val dispatcher: MandateDispatcher,
    private val agentService: AgentService,
    private val userService: UserService,
    private val membershipService: MembershipService,
    private val accessTokenService: AccessTokenService,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(AssignmentService::class.java)
    }

    fun findById(id: Long): AssignmentEntity =
        dao.findById(id).orElseThrow { NotFoundException(Error(code = ErrorCode.ASSIGNMENT_NOT_FOUND)) }

    /** Newest first: an agent's inbox shows its latest offers on top. */
    fun search(request: SearchAssignmentRequest): List<AssignmentEntity> {
        val spec = Specification<AssignmentEntity> { root, _, cb ->
            val predicates = mutableListOf<Predicate>()

            if (request.ids.isNotEmpty()) {
                predicates.add(root.get<Long>("id").`in`(request.ids))
            }
            if (request.agentIds.isNotEmpty()) {
                predicates.add(root.get<Long>("agentId").`in`(request.agentIds))
            }
            if (request.mandateIds.isNotEmpty()) {
                predicates.add(root.get<MandateEntity>("mandate").get<Long>("id").`in`(request.mandateIds))
            }
            if (request.statuses.isNotEmpty()) {
                predicates.add(root.get<Any>("status").`in`(request.statuses))
            }

            cb.and(*predicates.toTypedArray())
        }

        val sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        return dao.findAll(spec, OffsetLimitPageRequest(request.offset, request.limit, sort)).content
    }

    /** Dispatches one mandate now. Returns the PENDING assignment, or null if nothing was offered. */
    fun dispatch(mandateId: Long): AssignmentEntity? {
        mandateService.findById(mandateId) // 404 MANDATE_NOT_FOUND, rather than a silent null
        return dispatcher.dispatch(mandateId)
    }

    @Transactional
    fun accept(id: Long): AssignmentEntity {
        val assignment = findPendingForAssignee(id)
        val now = Date(clock.millis())
        mandateDao.save(
            assignment.mandate.copy(
                agentId = assignment.agentId,
                status = MandateStatus.ASSIGNED,
                modifiedAt = now,
            )
        )
        return dao.save(assignment.copy(status = AssignmentStatus.ACCEPTED, modifiedAt = now))
    }

    @Transactional
    fun decline(id: Long): AssignmentEntity =
        close(findPendingForAssignee(id), AssignmentStatus.DECLINED)

    /** Expires a PENDING offer now, even before its TTL; its mandate is re-dispatched after commit. */
    @Transactional
    fun expire(id: Long): AssignmentEntity =
        close(ensurePending(findById(id), ignoreTtl = true), AssignmentStatus.EXPIRED)

    /**
     * Expires the PENDING offers past their TTL; their mandates are re-dispatched after commit. Not @Transactional:
     * each save commits on its own, so one failing offer doesn't roll back the others.
     */
    fun expireDue(): Int {
        val now = Date(clock.millis())
        val due = dao.findByStatusAndExpiresAtBefore(AssignmentStatus.PENDING, now, PageRequest.of(0, 100))
        var expired = 0
        due.forEach { assignment ->
            try {
                close(assignment, AssignmentStatus.EXPIRED)
                expired++
            } catch (ex: Exception) {
                LOGGER.warn("Unable to expire Assignment#${assignment.id}", ex)
            }
        }
        return expired
    }

    /**
     * Dispatches the mandates waiting without a PENDING offer. Not @Transactional: each [MandateDispatcher.dispatch]
     * runs (and commits) in its own transaction, so one failing mandate doesn't roll back the others.
     * Known limit: the 100 oldest are taken each run; if over 100 have no candidate, they fill every run (until they go
     * MANUAL_ASSIGNMENT) and newer ones wait. Fine at today's volume; a `lastDispatchedAt` fixes it later.
     */
    fun dispatchPending(): Int {
        val ids = mandateDao.findIdsWithoutAssignment(
            MandateStatus.PENDING_ASSIGNMENT,
            AssignmentStatus.PENDING,
            PageRequest.of(0, 100),
        )
        var dispatched = 0
        ids.forEach { id ->
            try {
                dispatcher.dispatch(id)?.let { dispatched++ }
            } catch (ex: Exception) {
                LOGGER.warn("Unable to dispatch Mandate#$id", ex)
            }
        }
        return dispatched
    }

    // DECLINED or EXPIRED. The mandate is offered to the next agent AFTER COMMIT (AssignmentEventListener): a failing
    // re-dispatch never fails, nor rolls back, the decline / expiry. MandateJobs.dispatch catches any it missed
    private fun close(assignment: AssignmentEntity, status: AssignmentStatus): AssignmentEntity {
        val closed = dao.save(assignment.copy(status = status, modifiedAt = Date(clock.millis())))
        eventPublisher.publishEvent(AssignmentClosedEvent(closed.id!!, closed.mandate.id!!, TenantContext.get()!!))
        return closed
    }

    // 404 → 403 → 409: someone else's offer is refused before its status is revealed
    private fun findPendingForAssignee(id: Long): AssignmentEntity {
        val assignment = findById(id)
        ensureAssignee(assignment)
        return ensurePending(assignment)
    }

    /**
     * Only the agent the mandate is offered to can answer the offer: the caller's user (JWT), through its membership
     * of the current tenant, must have the agent's party. 401 without a token, 403 otherwise.
     */
    private fun ensureAssignee(assignment: AssignmentEntity) {
        val principal = accessTokenService.getPrincipal() // 401 AUTH_UNAUTHORIZED without a token
        val partyId = userService.findByIdOrNull(principal.getUserId())
            ?.let { user -> membershipService.findByUserOrNull(user) } // Tenant-filtered (@TenantId)
            ?.party?.id
        val agent = agentService.findById(assignment.agentId)
        if (partyId == null || agent.party.id != partyId) {
            throw ForbiddenException(Error(code = ErrorCode.ASSIGNMENT_ACCESS_DENIED))
        }
    }

    private fun ensurePending(assignment: AssignmentEntity, ignoreTtl: Boolean = false): AssignmentEntity {
        // Accepted / declined late (after the TTL) counts as expired, even if the job didn't run yet
        val late = !ignoreTtl && assignment.expiresAt.before(Date(clock.millis()))
        if (assignment.status != AssignmentStatus.PENDING || late) {
            throw ConflictException(Error(code = ErrorCode.ASSIGNMENT_NOT_PENDING))
        }
        return assignment
    }
}
```

`dispatch` isn't `@Transactional` itself: `MandateDispatcher.dispatch` is, on another bean, so its proxy applies.

## 6b. `AssignmentEndpoints` — `/v1/assignments`

```kotlin
@RestController
@RequestMapping("/v1/assignments")
class AssignmentEndpoints(
    private val service: AssignmentService,
    private val mapper: AssignmentMapper,
) {
    // Ex: an agent's pending offers: GET /v1/assignments?agent-id=12&status=PENDING
    @GetMapping
    fun search(@ModelAttribute request: SearchAssignmentRequest): SearchAssignmentResponse {
        val assignments = service.search(request.copy(limit = OffsetLimitPageRequest.limit(request.limit)))
        return SearchAssignmentResponse(assignments = assignments.map { mapper.toAssignmentSummary(it) })
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): GetAssignmentResponse {
        val assignment = service.findById(id)
        return GetAssignmentResponse(assignment = mapper.toAssignment(assignment))
    }

    @PostMapping("/{id}/accept")
    fun accept(@PathVariable id: Long) {
        service.accept(id)
    }

    @PostMapping("/{id}/decline")
    fun decline(@PathVariable id: Long) {
        service.decline(id)
    }
}

/**
 * Ops endpoints, off unless `ndopify.mandate.assignment.ops-endpoints.enabled`: they're unauthenticated, and every
 * expiry excludes that agent from the mandate forever — open in prod, a script could exhaust a city's agents in
 * minutes. On in tests (`application-qa.yml`) and local dev; in prod once admin roles exist.
 */
@RestController
@RequestMapping("/v1/assignments")
@ConditionalOnProperty("ndopify.mandate.assignment.ops-endpoints.enabled", havingValue = "true")
class AssignmentOpsEndpoints(
    private val service: AssignmentService,
) {
    // Offers the mandate now. The same rules apply: nothing offered at night, MANUAL_ASSIGNMENT if overdue, nothing
    // if it already has a PENDING offer
    @PostMapping("/dispatch")
    fun dispatch(@RequestBody @Valid request: DispatchMandateRequest): DispatchMandateResponse {
        val assignment = service.dispatch(request.mandateId)
        return DispatchMandateResponse(assignmentId = assignment?.id)
    }

    // Expires the offer now, even before its TTL; the mandate goes to the next agent after commit
    @PostMapping("/{id}/expire")
    fun expire(@PathVariable id: Long) {
        service.expire(id)
    }
}
```

| Endpoint                            | Errors                                                                                                                                                                 |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `GET /v1/assignments`               | — (`id`, `agent-id`, `mandate-id`, `status`, `limit`, `offset`)                                                                                                        |
| `GET /v1/assignments/{id}`          | 404 `ASSIGNMENT_NOT_FOUND`                                                                                                                                             |
| `POST /v1/assignments/dispatch` *(ops)* | 404 `MANDATE_NOT_FOUND`                                                                                                                                                |
| `POST /v1/assignments/{id}/expire` *(ops)* | 404 `ASSIGNMENT_NOT_FOUND`, 409 `ASSIGNMENT_NOT_PENDING`                                                                                                               |
| `POST /v1/assignments/{id}/accept`  | 404 `ASSIGNMENT_NOT_FOUND`, 401 `AUTH_UNAUTHORIZED` (no token), 403 `ASSIGNMENT_ACCESS_DENIED` (not the assigned agent), 409 `ASSIGNMENT_NOT_PENDING` (incl. past TTL) |
| `POST /v1/assignments/{id}/decline` | same as accept                                                                                                                                                         |

`Assignment`, `AssignmentSummary`, `GetAssignmentResponse`, `SearchAssignmentRequest`, `SearchAssignmentResponse` are
already drafted. One change: add `expiresAt` to `AssignmentSummary` — an agent's list of PENDING offers needs each
one's deadline, without a `GET` per offer.

`AssignmentMapper`:

```kotlin
@Service
class AssignmentMapper {
    fun toAssignment(entity: AssignmentEntity) = Assignment(
        id = entity.id ?: -1,
        mandateId = entity.mandate.id ?: -1,
        agentId = entity.agentId,
        status = entity.status,
        score = entity.score,
        createdAt = entity.createdAt,
        modifiedAt = entity.modifiedAt,
        expiresAt = entity.expiresAt,
    )

    fun toAssignmentSummary(entity: AssignmentEntity) = AssignmentSummary(
        id = entity.id ?: -1,
        mandateId = entity.mandate.id ?: -1,
        agentId = entity.agentId,
        status = entity.status,
        score = entity.score,
        createdAt = entity.createdAt,
        modifiedAt = entity.modifiedAt,
        expiresAt = entity.expiresAt,
    )
}
```

New DTOs (`ndopify-dto`, `mandate.dto`):

```kotlin
data class DispatchMandateRequest(
    val mandateId: Long = -1,
)

data class DispatchMandateResponse(
    val assignmentId: Long? = null, // null: nothing offered (night, no candidate, MANUAL_ASSIGNMENT, already offered)
)
```

**Accept / decline are restricted to the assigned agent** (`AssignmentService.ensureAssignee`): the JWT's user →
its membership of the current tenant → its party, which must be the assignment's agent's party. Checked in the
service (not the endpoint), after the 404 and before the 409, so a caller learns nothing about someone else's offer.

Caveat: JWTs are still unsigned (`Algorithm.none()`, see CLAUDE.md), so a caller can forge another user's token.
The check is the right authorization logic, but only becomes a real protection once tokens are signed — that's part
of the authentication work, out of scope here. `dispatch`, `expire`, `get` and `search` stay open (`permitAll`) for
now; `dispatch` / `expire` are off in prod (`AssignmentOpsEndpoints`, `ops-endpoints.enabled`) until admin roles
exist.

The `Clock` is the existing bean already injected in `PartyService` (`Date(clock.millis())`), so tests can mock
`clock.millis()` to cross the TTL boundary, as `UpdateAgentEndpointTest` does. `MandateDispatcher` uses it
too, so night / manual-assignment tests pin the time (`DispatchHours` takes the instant as a parameter).

`dispatchPending` lives here rather than in `MandateDispatcher`: calling `dispatch` from the same bean would bypass
its `@Transactional` proxy.

## 7. `MandateJobs` — mirrors `KycJobs`

```kotlin
@Service
@ConditionalOnProperty("ndopify.mandate.assignment.enabled", havingValue = "true")
class MandateJobs(
    private val service: AssignmentService,
    private val tenantService: TenantService,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MandateJobs::class.java)
    }

    @Scheduled(cron = "\${ndopify.mandate.assignment.expire.cron}")
    fun expire() = forEachTenant("expire") { service.expireDue() }

    // The mandates that came at night (first run after night.end), or had no candidate yet
    @Scheduled(cron = "\${ndopify.mandate.assignment.dispatch.cron}")
    fun dispatch() = forEachTenant("dispatch") { service.dispatchPending() }

    private fun forEachTenant(name: String, block: () -> Int) {
        tenantService.search(true).forEach { tenant ->
            val tenantId = tenant.id!!
            try {
                TenantContext.set(tenantId)
                val count = block()
                LOGGER.info("Tenant#$tenantId - MandateJobs.$name() completed: $count mandate(s)")
            } catch (ex: Exception) {
                // A tenant failing must not prevent the others to be processed
                LOGGER.warn("Tenant#$tenantId - MandateJobs.$name() failed", ex)
            } finally {
                TenantContext.remove()
            }
        }
    }
}
```

## 8. Wiring

- `MandateService.create` publishes a `MandateCreatedEvent(mandateId, tenantId)`; an
  `@Async @TransactionalEventListener(AFTER_COMMIT)` listener calls `dispatcher.dispatch(event.mandateId)` (setting
  `TenantContext` + `KVLogger` like the mail listeners), so a dispatch failure never rolls back the customer's mandate.
  Gated by `ndopify.mandate.assignment.enabled`.
- `AssignmentClosedEvent(assignmentId, mandateId, tenantId)`, published by `AssignmentService.close` (decline,
  expire); `AssignmentEventListener`, `@Async @TransactionalEventListener(AFTER_COMMIT, fallbackExecution = true)`,
  calls `dispatcher.dispatch(event.mandateId)` (same `TenantContext` / `KVLogger` handling, failures logged).
  `fallbackExecution = true` because `expireDue` isn't transactional: without it, Spring silently drops an event
  published outside a transaction. Gated by `enabled`.
- `AssignmentCreatedEvent` + listener notifying the agent (e-mail for now, `AssignmentMailet`, MJML template).
- `MandateManualAssignmentEvent`, in `mandate/server/event/`, like `AgentCreatedEvent`:

  ```kotlin
  /**
   * Published by `MandateDispatcher.dispatch`, inside its transaction, when a mandate is moved to
   * MANUAL_ASSIGNMENT. Carries ids rather than the entity, so listeners reload what they need. [tenantId] is the
   * mandate's tenant, since listeners run on a thread that has none.
   */
  data class MandateManualAssignmentEvent(
      val mandateId: Long,
      val tenantId: Long,
  )
  ```

  Listeners must be `@TransactionalEventListener(AFTER_COMMIT)` (+ `@Async`), so nothing fires for a rolled-back
  dispatch. None in this plan: alerting (ops, customer) comes later and plugs into this event.
- New error codes: `ASSIGNMENT_NOT_FOUND`, `ASSIGNMENT_NOT_PENDING`, `ASSIGNMENT_ACCESS_DENIED`.
- New endpoints (`AssignmentEndpoints`, §6b): `GET /v1/assignments` (search, DTOs already drafted),
  `GET /v1/assignments/{id}`, `POST /v1/assignments/dispatch`, `POST /v1/assignments/{id}/expire`,
  `POST /v1/assignments/{id}/accept`, `POST /v1/assignments/{id}/decline`.

## 9. Tests & docs

- `MandateDispatcherTest` (unit): each score factor (no neighbourhoods → 0.5, never offered → fairness 1, new agent →
  responsiveness 0.5, unrated (3.0) → rating 0.5, 5 → 1, 1 → 0, out of range clamped, a better-rated agent wins
  all else equal, at capacity → excluded), self-assignment excluded, DECLINED/EXPIRED excluded, already PENDING →
  no dispatch, no candidate → null, `lastAssignedAt` set on offer.
- `MandateDispatcherTest`, nearby neighbourhoods (`LocationService.searchNearest` mocked): covering the mandate's
  neighbourhood beats a nearby agent, nearer beats farther, `nearby-max-neighborhoods` passed as the limit, beyond the
  radius → 0, `parentId = mandate.cityId` passed to
  `searchNearest`, an agent of another city is never a candidate, an agent found by both searches is scored once,
  both searches sorted `SORT_LEAST_RECENTLY_ASSIGNED`; capacity counts `ASSIGNED` / `PENDING_APPROVAL` only (an
  agent with 5 `PUBLISHED` is still a candidate). `jitter = 0` in every test.
- `DispatchHoursTest` (unit, instants passed in): 20:59 day, 21:00 night, 06:59 night, 07:00 day; a window not spanning
  midnight; `start == end` → never night; `addDaytime`: 10:00 + 1h → 11:00, 20:30 + 1h → 07:30 next day, 23:00 +
  1h → 08:00, 20:00 + 12h → spans 2 nights; the same instant is night in one zone and day in another (ex:
  `Africa/Douala` vs `America/Toronto`).
- `MandateDispatcherTest`, night & manual: at night → no offer, mandate untouched; past `manual-after-days` →
  `MANUAL_ASSIGNMENT` + `MandateManualAssignmentEvent` (mandate + tenant ids) published, no offer, even at night;
  exactly `manual-after-days` after creation → manual (boundary); with a PENDING offer → untouched even if overdue;
  `expiresAt`
  from `addDaytime`; the tenant's `timeZone` is the one passed to `DispatchHours`.
- `AcceptAssignmentEndpointTest` / `DeclineAssignmentEndpointTest` (extend `AuthorizationAwareEndpointIntegrationTest`:
  user `11`, tenant `1`; fixture: the assigned agent's party is user 11's membership party): happy path, unknown →
  404, no token (`anonymousUser = true`) → 401, another agent's offer → 403 (and its status unchanged), user without
  a membership / party in the tenant → 403, someone else's offer that's no longer PENDING → 403 (not 409), not
  PENDING → 409, past TTL → 409, decline re-dispatches to the next agent (after commit: wait for the async
  listener), a failing re-dispatch still declines (200), 2 concurrent accepts / accept + decline → one wins, the other
  409 (`@Version`). Fixtures under `db/test/mandate/`.
- `GetAssignmentEndpointTest`: every field mapped (incl. `mandateId`, `expiresAt`); unknown → 404; an assignment of
  another tenant → 404 (`@TenantId`).
- `SearchAssignmentEndpointTest`: by `id`, `agent-id`, `mandate-id`, `status` (several values), combined; newest
  first; `limit` / `offset`; another tenant's assignments never returned.
- `AssignmentOpsEndpointsTest`: with `ops-endpoints.enabled=false`, `dispatch` / `expire` → 404 (no bean).
- `ExpireAssignmentEndpointTest`: a PENDING offer before its TTL → EXPIRED + re-dispatched to the next agent; past
  its TTL → also expired (not 409); unknown → 404; not PENDING → 409.
- `DispatchMandateEndpointTest`: returns the PENDING assignment to the best agent; unknown mandate → 404; already
  offered / at night / no candidate → `assignmentId = null`; overdue → `MANUAL_ASSIGNMENT`, `assignmentId = null`.
- `MandateJobsTest`: `expire()` (`expireDue`): expired offers → EXPIRED + re-dispatched, per tenant, one failing
  re-dispatch doesn't stop the others, the `AssignmentClosedEvent` published outside a transaction is still handled
  (`fallbackExecution`); `dispatch()` offers the mandates without a
  PENDING offer (oldest first), skips the ones with one, and one failing mandate doesn't stop the others.
- `CreateMandateEndpointTest`: a mandate is dispatched after creation; with `enabled=false`, it isn't.
- `SearchAgentEndpointTest` / `AgentService`: `SORT_LEAST_RECENTLY_ASSIGNED` puts never-offered agents first, then
  oldest `lastAssignedAt`.
- Update `CLAUDE.md`: mandate dispatch, `T_ASSIGNMENT`, and fix "There are no `@Scheduled` jobs" (`KycJobs` and
  `MandateJobs` exist).

## Implementation steps

Each step ends green (`mvn test -Dtest=…` on what it touches); tests are written first. Work on a branch
(`feature/mandate-dispatch`), not `main`.

### Step 1 — DTOs (`ndopify-dto`)

- `SearchNearestLocationRequest.parentId` (`@BindParam("parent-id")`).
- `ErrorCode.ASSIGNMENT_NOT_FOUND`, `ErrorCode.ASSIGNMENT_NOT_PENDING`, `ErrorCode.ASSIGNMENT_ACCESS_DENIED`.
- `DispatchMandateRequest`, `DispatchMandateResponse` (§6b); `AssignmentSummary.expiresAt`.
- `MandateStatus.MANUAL_ASSIGNMENT`, appended last.
- `Agent.rating` (read-only: not in `UpdateAgentRequest`).
- Review the drafted assignment DTOs (`Assignment`, `AssignmentSummary`, `Get/SearchAssignment*`) against §8's
  endpoints.
- The root `pom.xml` pins the released `ndopify-dto.version` (`0.0.37`): point it at the DTO module's current
  snapshot and `mvn install` the DTO module so the server builds against it.

**Verify:** `cd modules/ndopify-dto && mvn install`

### Step 2 — Nearest locations of a city (`refdata`)

- Test first: `SearchNearestLocationEndpointTest` — with `parent-id`, only that city's neighbourhoods come back
  (fixture `SearchNearestLocationEndpoint.sql`: a close neighbourhood of another city, excluded).
- `LocationService.searchNearest`: append `AND l.parent_fk = :parentId` and bind it only when set.

**Verify:** `mvn test -Dtest=SearchNearestLocationEndpointTest`

### Step 3 — Schema & entity

- Amend `V6_1__mandate-assignment.sql`: `version` + the 3 indexes (§2). Drop and recreate the local schema, since
  `V6_1` is already applied locally.
- `AssignmentEntity.version` (`@Version`); `ControllerErrorHandler`:
  `ObjectOptimisticLockingFailureException` → 409 `ASSIGNMENT_NOT_PENDING`.
- `V6_1` also adds `T_AGENT.rating` (default 3.0); `AgentEntity.rating`, `Agent.rating`, `AgentMapper`;
  `GetAgentEndpointTest` asserts it (3.0 for a fixture without it); `UpdateAgentEndpointTest` checks the rating is
  left unchanged.

**Verify:** any existing endpoint test boots (Flyway migrates): `mvn test -Dtest=SearchAgentEndpointTest`

### Step 4 — Repositories & agent

- `AssignmentRepository`: `existsByMandateAndStatus`, `findByStatusAndExpiresAtBefore`, `countByAgentIds` +
  `AgentAssignmentCount` (§4).
- `MandateRepository`: `findByIdForUpdate`, `countByAgentIdsAndStatusIn` + `AgentMandateCount`,
  `findIdsWithoutAssignment`.
- `AgentService.markAssigned`, `AgentService.search(request, sort)` + `SORT_LEAST_RECENTLY_ASSIGNED` (test in
  `SearchAgentEndpointTest`).
- `application.yml`: the `ndopify.mandate.assignment.*` block (§1), `enabled` / `ops-endpoints.enabled` false;
  `application-qa.yml`: both true, `jitter: 0`.

No tests of their own: they're exercised by steps 5–7.

### Step 4b — `DispatchHours`

- Test first: `DispatchHoursTest` (§9), including 2 time zones.
- `DispatchHours` (§5).

**Verify:** `mvn test -Dtest=DispatchHoursTest`

### Step 5 — `MandateDispatcher` (core)

- Test first: `MandateDispatcherTest` (unit, mocked repositories / services / `LocationService`), every case of §9:
  filters, each score factor, neighbourhood bands, `parentId = mandate.cityId` and `limit` passed to
  `searchNearest`, the 2 agent searches merged without duplicates, no PENDING twice, `lastAssignedAt` set on offer,
  `AssignmentCreatedEvent` published, plus the night & manual cases (`DispatchHours` mocked).
- Implement §5b, replacing the current skeleton; `MandateManualAssignmentEvent` (§8).

**Verify:** `mvn test -Dtest=MandateDispatcherTest`

### Step 6 — Assignment endpoints (`AssignmentService` + `AssignmentEndpoints`)

- Test first: `DispatchMandateEndpointTest`, `ExpireAssignmentEndpointTest`, `AcceptAssignmentEndpointTest`,
  `DeclineAssignmentEndpointTest`, `GetAssignmentEndpointTest`, `SearchAssignmentEndpointTest`, fixtures under
  `db/test/mandate/`; TTL and night crossed with a mocked `Clock` (as in `UpdateAgentEndpointTest`).
- `AssignmentService` (§6), `AssignmentMapper`, `AssignmentEndpoints` + `AssignmentOpsEndpoints` (§6b),
  `AssignmentClosedEvent` + `AssignmentEventListener` (§8).

**Verify:** `mvn test -Dtest='*AssignmentEndpointTest,DispatchMandateEndpointTest,AssignmentOpsEndpointsTest'`

### Step 7 — Jobs (`MandateJobs`)

- Test first: `MandateJobsTest` — `expire()`: expired PENDING → EXPIRED and re-dispatched to the next agent;
  `dispatch()`: mandates without a PENDING offer are offered; per tenant, a tenant failing doesn't stop the others.
- `AssignmentService.expireDue` / `dispatchPending`, `MandateJobs` (§7).

**Verify:** `mvn test -Dtest=MandateJobsTest`

### Step 8 — Dispatch on creation

- Test first: `CreateMandateEndpointTest` — after creation, the mandate has a PENDING assignment to the best agent
  (wait for the async listener, as the mail tests do); with no candidate, it stays `PENDING_ASSIGNMENT`.
- `MandateCreatedEvent(mandateId, tenantId)` published by `MandateService.create`; `MandateEventListener`
  (`@Async @TransactionalEventListener(AFTER_COMMIT)`, `TenantContext` + `KVLogger` set/flushed/removed) calls
  `dispatcher.dispatch(event.mandateId)`; failures logged, never rethrown.

**Verify:** `mvn test -Dtest=CreateMandateEndpointTest`

### Step 9 — Notify the agent

- Test first: every offer e-mails the agent (`smtp.waitForIncomingEmail`), whatever triggered it: creation,
  decline or expiry.
- `AssignmentCreatedEvent` listener → `AssignmentService.sendOfferEmail` (`@Transactional`) → `AssignmentMailet`,
  MJML template under `mail/mandate/` (French): mandate summary, deadline (`expiresAt`), link to Partner Central.

**Verify:** `mvn test -Dtest='*AssignmentEndpointTest,CreateMandateEndpointTest'`

### Step 10 — Docs & gates

- `CLAUDE.md`: mandate dispatch (algorithm, `T_ASSIGNMENT`, config), assignment endpoints, `@TenantId` list
  (+ `AssignmentEntity`), and fix "There are no `@Scheduled` jobs" (`KycJobs`, `MandateJobs`).
- `mvn antrun:run@ktlint-format`, then the full module build with the coverage gate.

**Verify:** `cd modules/ndopify-server && mvn verify`

### PR slices

1. **PR 1** — Steps 1–2: `parentId` on nearest locations (independent, small).
2. **PR 2** — Steps 3–8: dispatch (incl. night & manual assignment), accept/decline, jobs, dispatch on creation.
   Ships **dark**: `ndopify.mandate.assignment.enabled=false` in prod.
3. **PR 3** — Steps 9–10: agent notification + docs.
4. **Enable** (config only) once PR 3 is live and the notification channel is decided (WhatsApp, see Open
   questions). Expect every `PENDING_ASSIGNMENT` mandate older than 3 days to go `MANUAL_ASSIGNMENT` on the first
   run.

## Decisions

- Sequential exclusive offers, TTL 1h (`ndopify.mandate.assignment.ttl-minutes`).
- Experience ignored in scoring for now.
- Agent rating (1..5, default 3.0 = unrated, neutral) weighs 0.15; weights rebalanced to 0.35 neighbourhood / 0.25
  fairness / 0.15 rating / 0.10 responsiveness / 0.15 capacity.
- `lastAssignedAt` updated on offer, not on accept.
- An accept after the TTL is refused even if the expiry job hasn't run yet.
- `max-load`, the crons and the jitter are config too; weights are constants.
- Nearby neighbourhoods are a graded score (not strict tiers), radius `nearby-radius-km` (3 km), capped at the
  `nearby-max-neighborhoods` (20) nearest, and **always in the
  mandate's city**: neither the nearby neighbourhoods nor the candidates ever come from another city.
- Locations are assumed always imported and geocoded.
- Accept / decline only by the assigned agent (JWT user → membership → party = agent's party): 401 / 403.
- No offer at night (`21:00` → `07:00`, configurable), in the **tenant's time zone** (`TenantEntity.timeZone`); the
  TTL only runs during the day.
- `MANUAL_ASSIGNMENT` after `manual-after-days` (3) **calendar days** since creation (nights and week-ends included),
  checked on every dispatch; a PENDING offer is never interrupted. A `MandateManualAssignmentEvent` is fired.
- Mandates without a PENDING offer (night, no candidate) are retried by `MandateJobs.dispatch` every 15 min.
- `@Version` on `AssignmentEntity`: concurrent transitions of one offer → one wins, the other 409.
- Re-dispatch after a decline / expiry happens after commit (`AssignmentClosedEvent`), never inside it.
- Shipped dark behind `ndopify.mandate.assignment.enabled`; ops endpoints (`dispatch`, `expire`) behind
  `ops-endpoints.enabled`, off in prod.
- Candidates ordered least recently offered first, so the 100-agent cut never starves newer agents.
- Capacity counts `ASSIGNED` / `PENDING_APPROVAL` only, not `APPROVED` / `PUBLISHED`.
- Tie-break jitter is config (`jitter`), 0 in tests.

## Open questions

- `MANUAL_ASSIGNMENT`: which listeners of `MandateManualAssignmentEvent` (ops alert, customer e-mail)?
- Are 3 km / 20 neighbourhoods right for Douala / Yaoundé? Count the neighbourhoods within 3 km of a typical one.
- Rating: where will it come from (customer review after a mandate, admin)? Once computed from reviews, use a
  smoothed average (ex: `(sum + 3·k) / (count + k)`) so that one 5-star review doesn't beat 20 4-star ones.
- **Launch blocker — notification channel**: agents mostly live on WhatsApp; by e-mail only, most 1h offers will
  likely expire, and since expiry excludes the agent forever, most mandates would end up `MANUAL_ASSIGNMENT`. Decide
  before enabling: WhatsApp (`AgentEntity.whatsappNumber`), or e-mail with a longer `ttl-minutes` (3–4h) meanwhile.
- Capacity: confirm `APPROVED` / `PUBLISHED` shouldn't count (changed from the first draft).

## Out of scope

- Broadcast / first-to-accept dispatch.
- Experience-based scoring, tunable weights per tenant.
- Setting the rating: reviews, their API, and the rating computation.
- Partner Central UI for agents to see/accept/decline offers.
- Admin console UI for assigning `MANUAL_ASSIGNMENT` mandates.
