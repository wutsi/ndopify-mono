# Search ↔ Listing Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Find, score and store the listings of an agent's inventory that match each of its SEARCH mandates, and
expose them to the agent (list, get, dismiss).

**Architecture:** A new `match` domain in ndopify-server (`com.wutsi.ndopify.match.server`), which depends on
`mandate` and `listing` (neither depends on it). A pure `Matcher` rules out or scores one (search mandate, listing)
pair; `MatchService` runs it when a SEARCH mandate is APPROVED or a listing is PUBLISHED (async listeners on the
existing events) and upserts one `T_MATCH` row per pair. `MatchEndpoints` serves `/v1/matches`.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (Hibernate multi-tenancy), Flyway/MySQL, mockito-kotlin,
kotlin.test, `TestRestTemplate` endpoint tests.

**Spec:** the brainstorming conversation of 2026-10-31, captured in *Design* below (this plan is the design record).

## Global Constraints

- `mandate` and `listing` never depend on `match`: the match domain reacts to `MandateApprovedEvent` /
  `ListingPublishedEvent`, it isn't called by them.
- Tenancy by `@TenantId` on `MatchEntity`, never a hand-written `tenant_id` predicate.
- Only the agent's own inventory: the listing's agent is the search mandate's agent.
- Assertions with `kotlin.test`, mocks with mockito-kotlin; tests mirror the package.
- KDoc says what a class is for and its non-obvious rules (error codes in the order they're checked).
- Coverage gate of ndopify-server: `jacoco.threshold.line` 0.95, `jacoco.threshold.class` 0.90.
- A `ndopify-dto` change needs a DTO release before deploying (see `modules/ndopify-dto/CLAUDE.md`).
- Work on a branch: `git checkout -b feat/search-listing-matching`.

## Design

### Scope

In: the algorithm, `T_MATCH`, the triggers, `GET /v1/matches`, `GET /v1/matches/{id}`,
`POST /v1/matches/{id}/dismiss`, `POST /v1/matches/run` (ops).

Out, for the next plans: the agent alert (e-mail, `notifiedAt`), proposing to the customer and the customer's
accept / reject (statuses `PROPOSED`, `ACCEPTED`, `REJECTED`, the public-portal access), re-proposing after a price
drop (needs a price change on a PUBLISHED listing, impossible today: `ListingService.update` is DRAFT only),
visits, the Partner Central UI. The design below already leaves room for them: the matcher only ever creates,
re-scores or deletes `ACTIVE` rows.

### Statuses

`MatchStatus { UNKNOWN, ACTIVE, DISMISSED }`. `ACTIVE`: shown to the agent. `DISMISSED`: hidden by the agent, never
re-scored nor re-created (decision of 2026-10-31: it may come back after a price drop — out of scope here).

### Algorithm

Runs for a pair (search mandate S, listing L). S is matched only if `mandateType = SEARCH`, `status = APPROVED`
and `agentId != null` (an APPROVED mandate can't be updated, so S never changes after matching). L is a candidate
only if `status = PUBLISHED` and `L.agent.id = S.agentId`.

**1. Rule-outs** (`Matcher.score` returns `null`), in this order:

1. `L.mandate.customerId == S.customerId` (the customer's own property)
2. `listingType`, `propertyCategory` or `cityId` differ
3. `S.propertyType != null` and differs from `L.propertyType`
4. `currencyCode` or `pricePeriodicityType` differ (only when `S.price > 0`)
5. `S.price > 0` and `L.price > S.price × (1 + priceTolerance)` — `priceTolerance` = 0.10

**2. Partial scores** (each 0..1):

| Criterion    | Weight | Rule                                                                                                                                     |
|--------------|--------|------------------------------------------------------------------------------------------------------------------------------------------|
| Neighborhood | 0.30   | 1 if `S.neighborhoodIds` empty or contains `L.neighborhoodId`; `0.5 × (1 − d / nearbyRadiusKm)` if `L.neighborhoodId` is nearby at `d` km; else 0 |
| Price        | 0.25   | 1 if `S.price <= 0` or `L.price <= S.price`; else `1 − (L.price − S.price) / (S.price × priceTolerance)`, clamped to 0..1                 |
| Rooms        | 0.15   | `S.rooms` null/blank/unparseable → 1. Wanted `n` (`"5+"` → 5, or more). `L.rooms` null → 0. Exact, or `"5+"` and `L.rooms >= 5` → 1; one more → 0.7; one fewer → 0.3; else 0 |
| Amenities    | 0.15   | Requested = the amenity flags `true` on S (all the booleans but `trackInaccessibleByCar`). None requested → 1; else share of them `true` on L |
| Availability | 0.10   | Deadline from `S.movingPeriod`: `WITHIN_1_MONTH` → today + 1 month, `WITHIN_3_MONTHS` → today + 3 months, other → none (1). `L.availableFrom` null or ≤ deadline → 1; ≤ deadline + 1 month → 0.5; else 0 |
| Furniture    | 0.05   | 1 if `S.furnitureType` null/`UNKNOWN` or equals `L.furnitureType`; else 0                                                                |

`score = Σ weight × partial`. Kept if `score >= minScore` (0.5). "Nearby" distances come from
`LocationService.searchNearest` exactly as `MandateDispatcher.distances` does (S's neighborhoods, type
NEIGHBORHOOD, `parentId = S.cityId`, `nearbyRadiusKm` 3, `nearbyMaxNeighborhoods` 20; S's own at 0 km). "Today" is
the tenant's local date (`TenantEntity.timeZone`).

**Worked example** (pinned by `MatcherTest`): S = RENTAL, RESIDENTIAL, APARTMENT, city 100, neighborhood 1001, rooms
`"3"`, 250 000 MONTHLY XAF, wants `generatorSet`, `securedFenced`, `waterTank`, `WITHIN_3_MONTHS`, today 2026-11-01.

| Listing                                                                    | N   | P   | R   | A    | Av  | F | Score |
|----------------------------------------------------------------------------|-----|-----|-----|------|-----|---|-------|
| A: 1001, 240 000, 3 rooms, fence + tank, available 2026-12-01              | 1   | 1   | 1   | 0.67 | 1   | 1 | 0.95  |
| C: 1001, 200 000, 2 rooms, fence, available 2027-02-15                     | 1   | 1   | 0.3 | 0.33 | 0.5 | 1 | 0.745 |
| B: 1002 at 1.5 km, 265 000, 4 rooms, all 3 amenities, available now        | 0.25| 0.4 | 0.7 | 1    | 1   | 1 | 0.58  |

D (1001, 290 000: over budget + 10 %) and E (a HOUSE) are ruled out: `null`.

### Storing

One row per (S, L), unique. For each candidate pair:

- existing row not `ACTIVE` → left untouched;
- score `null` or `< minScore` → the `ACTIVE` row, if any, is deleted;
- else the row is created `ACTIVE`, or its scores updated.

Each row keeps `listingPrice`: L's price when last scored, or when dismissed (decision of 2026-10-31). A `DISMISSED`
row isn't re-scored, so it keeps the price the agent said no to: a later price below it is a price drop, the reason a
future plan re-proposes it (with `ListingPriceChangedEvent` → `matchListing`, once a PUBLISHED listing's price can
change). Exposed as `Match.listingPrice`.

Not deleted when L stops being PUBLISHED or S is closed: reads filter them out. The run isn't one transaction: each
save is its own, so an optimistic-lock conflict (the agent dismissed the match meanwhile) only skips that pair,
logged.

### Triggers

- `MandateApprovedEvent` → `MatchService.matchMandate(mandateId)` (no-op for a LISTING mandate).
- `ListingPublishedEvent` → `MatchService.matchListing(listingId)`: every APPROVED SEARCH mandate of the listing's
  agent.
- Both `@Async @TransactionalEventListener(AFTER_COMMIT)`, like `MandateEventListener`: a failure is only logged.
- `POST /v1/matches/run?mandate-id=` (`@OpsEndpoint`) re-runs a mandate by hand.

### API

| Endpoint                                            | Access                                   | Errors, in order                                               |
|-----------------------------------------------------|------------------------------------------|----------------------------------------------------------------|
| `GET /v1/matches?mandate-id=&status=&limit=&offset=` | `MandateAccessService.ensureCanRead(S)`  | 404 `MANDATE_NOT_FOUND`, 401, 403 `MANDATE_ACCESS_DENIED`      |
| `GET /v1/matches/{id}`                              | `ensureCanRead(match.mandate)`           | 404 `MATCH_NOT_FOUND`, 401, 403                                |
| `POST /v1/matches/{id}/dismiss`                     | `ensureCanUpdate(match.mandate)`         | 404 `MATCH_NOT_FOUND`, 401, 403, 409 `MATCH_NOT_ACTIVE`        |
| `POST /v1/matches/run?mandate-id=`                  | `@OpsEndpoint`                           | 404 `MANDATE_NOT_FOUND`                                        |

Search: `status` defaults to `ACTIVE`; only matches whose listing is PUBLISHED; best score first, then `id`. A
concurrent dismiss / re-score is a 409 `MATCH_NOT_ACTIVE` (`ControllerErrorHandler`'s optimistic-lock mapping).

## Review Focus

1. **The customer's own property** (the search customer also owns a listing of the same agent) → never matched.
   Test in Task 2.
2. **Sparse search or listing** (no budget `price = -1`, no rooms on a STUDIO, `"5+"`, no neighborhood, null
   `availableFrom`) → a score, no exception. Test in Task 2.
3. **Agent dismisses while a re-score runs** → the match stays `DISMISSED`, the rest of the run goes on. Test in
   Task 3.
4. **Listing rented / search closed after matching** → no longer listed, row kept. Test in Task 5.
5. **Nothing to match** (an approved search with an empty inventory, a listing whose agent has no open search,
   a LISTING mandate approved) → no row, no error. Test in Task 3.

---

### Task 1: ndopify-dto — match contracts

**Files:**
- Create in `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/`: `MatchStatus.kt`, `Match.kt`,
  `SearchMatchRequest.kt`, `SearchMatchResponse.kt`, `GetMatchResponse.kt`, `RunMatchResponse.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt` (next to `MANDATE_*`)
- Modify: `pom.xml` (root) — `<ndopify-dto.version>` to the DTO's current `-SNAPSHOT`

**Interfaces:**
- Produces:
  - `enum class MatchStatus { UNKNOWN, ACTIVE, DISMISSED }`
  - `data class Match(id: Long = -1, mandateId: Long = -1, listing: ListingSummary = ListingSummary(), status: MatchStatus = UNKNOWN, score: Double = 0.0, neighborhoodScore, priceScore, roomsScore, amenitiesScore, availabilityScore, furnitureScore: Double = 0.0, createdAt: Date = Date(), modifiedAt: Date = Date())`
  - `data class SearchMatchRequest(@param:BindParam("mandate-id") val mandateId: Long = -1, @param:BindParam("status") val statuses: List<MatchStatus> = emptyList(), val limit: Int = 20, val offset: Int = 0)`
  - `SearchMatchResponse(matches: List<Match>)`, `GetMatchResponse(match: Match)`, `RunMatchResponse(count: Int)`
  - `ErrorCode.MATCH_NOT_FOUND = "$PREFIX:match:not-found"`, `ErrorCode.MATCH_NOT_ACTIVE = "$PREFIX:match:not-active"`

- [ ] **Step 1: Write the DTOs and error codes** as above (style of `mandate/dto/SearchAssignmentRequest.kt`).
- [ ] **Step 2: Run** `cd modules/ndopify-dto && mvn install` — Expected: BUILD SUCCESS.
- [ ] **Step 3: Commit** `feat(dto): match contracts`

---

### Task 2: `Matcher` — rule-outs and scoring

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchScore.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` — under `ndopify:`
  ```yaml
  match:
      price-tolerance: 0.10           # A listing up to 10% over the budget is still a match, scored down
      min-score: 0.5                  # Below, the pair isn't stored
      nearby-radius-km: 3             # Neighbourhoods this close to the searched ones are "nearby"
      nearby-max-neighborhoods: 20
  ```
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Produces:
  - `data class MatchScore(val neighborhood: Double, val price: Double, val rooms: Double, val amenities: Double, val availability: Double, val furniture: Double) { val total: Double }` — `total` = the weighted sum.
  - `@Service class Matcher(@Value("\${ndopify.match.price-tolerance}") priceTolerance: Double, @Value("\${ndopify.match.nearby-radius-km}") nearbyRadiusKm: Double)` with
    `fun score(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>, today: LocalDate): MatchScore?` — `null` = ruled out. `distances`: neighborhood id → km, the searched ones at 0.
  - Weights as `companion object` constants, like `MandateDispatcher`: `WEIGHT_NEIGHBORHOOD = 0.30`, `WEIGHT_PRICE = 0.25`, `WEIGHT_ROOMS = 0.15`, `WEIGHT_AMENITIES = 0.15`, `WEIGHT_AVAILABILITY = 0.10`, `WEIGHT_FURNITURE = 0.05`.

- [ ] **Step 1: Write the failing tests** in `MatcherTest` (`Matcher(0.10, 3.0)`, fixtures from the worked example,
  `distances = mapOf(1001L to 0.0, 1002L to 1.5)`, today 2026-11-01). Compare doubles with `assertEquals(x, y, 0.005)`.
  - `listing A` → partials (1, 1, 1, 0.67, 1, 1), total 0.95
  - `listing C` → partials (1, 1, 0.3, 0.33, 0.5, 1), total 0.745
  - `listing B` → partials (0.25, 0.4, 0.7, 1, 1, 1), total 0.58
  - `ruled out`, one test each: price 290 000; HOUSE; SALE; other city; other `propertyCategory`; other currency;
    WEEKLY periodicity; `listing.mandate.customerId == search.customerId` → `null`
  - `no budget` (`search.price = -1`, listing 1 000 000, other currency) → not null, price 1
  - `no property type searched` → HOUSE not ruled out
  - `no neighborhood searched` → neighborhood 1; `listing without neighborhood` → 0; `neighborhood not nearby` → 0
  - rooms: `"5+"` with 7 → 1; `"3"` with `null` (STUDIO) → 0; `null` searched → 1; `"3"` with 5 → 0
  - `no amenity requested` → 1; `trackInaccessibleByCar` alone requested → 1 (not an amenity)
  - availability: `WITHIN_1_MONTH` and 2026-12-01 → 1; `AFTER_3_MONTHS` and 2027-06-01 → 1; `null availableFrom` → 1
  - furniture: `FULLY_FURNISHED` searched, `UNFURNISHED` listed → 0; `UNKNOWN` searched → 1
- [ ] **Step 2: Run** `cd modules/ndopify-server && mvn test -Dtest=MatcherTest` — Expected: compile FAIL.
- [ ] **Step 3: Implement** `MatchScore` and `Matcher` per *Design / Algorithm*. KDoc on `Matcher`: the rule-outs in
  order, and a pointer to this plan for the weights.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(match): matcher`

---

### Task 3: `T_MATCH`, `MatchEntity`, `MatchService` (run + store)

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V9_0__match.sql`
- Create: `.../match/server/domain/MatchEntity.kt`, `.../match/server/dao/MatchRepository.kt`,
  `.../match/server/service/MatchService.kt`
- Modify: `.../listing/server/dao/ListingRepository.kt` — add `findByAgentIdAndStatus`
- Modify: `.../mandate/server/dao/MandateRepository.kt` — add `findByAgentIdAndMandateTypeAndStatus`
- Modify: `modules/ndopify-server/src/test/resources/db/test/clean.sql` — `DELETE FROM T_MATCH;` first
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`

(`...` = `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify`)

**Interfaces:**
- Consumes: `Matcher.score`, `MatchScore` (Task 2); `MatchStatus` (Task 1).
- Produces:
  - `MatchEntity(id, @TenantId tenantId, @ManyToOne mandate: MandateEntity, @ManyToOne listing: ListingEntity, agentId: Long, status: MatchStatus, score, neighborhoodScore, priceScore, roomsScore, amenitiesScore, availabilityScore, furnitureScore: Double, createdAt, modifiedAt: Date, @Version version: Long?)`
  - `MatchRepository : CrudRepository<MatchEntity, Long>, JpaSpecificationExecutor<MatchEntity>` with `fun findByMandateAndListing(mandate: MandateEntity, listing: ListingEntity): MatchEntity?`
  - `ListingRepository.findByAgentIdAndStatus(agentId: Long, status: ListingStatus): List<ListingEntity>`
  - `MandateRepository.findByAgentIdAndMandateTypeAndStatus(agentId: Long, mandateType: MandateType, status: MandateStatus): List<MandateEntity>`
  - `MatchService.matchMandate(mandateId: Long): Int` and `MatchService.matchListing(listingId: Long): Int` — the number of `ACTIVE` rows stored by the run; 404 `MANDATE_NOT_FOUND` / `LISTING_NOT_FOUND` for an unknown id.

`V9_0__match.sql`: the columns of `MatchEntity` (snake_case), `UNIQUE (mandate_id, listing_id)`,
`INDEX IDX_MATCH_mandate_status (mandate_id, status)`, `INDEX IDX_MATCH_listing (listing_id)`, FKs to `T_MANDATE`,
`T_LISTING`, `T_AGENT`; same layout as `T_ASSIGNMENT` in `V7_1__mandate-assignment.sql`.

`MatchService` constructor: `MatchRepository`, `MandateRepository`, `ListingRepository`, `Matcher`,
`LocationService`, `TenantService`, `Clock`, `KVLogger`, `@Value` `min-score`, `nearby-radius-km`,
`nearby-max-neighborhoods`. **Not `@Transactional`** on the run methods (see *Design / Storing*): catch
`ObjectOptimisticLockingFailureException` per pair, log `conflicts`, go on.

- [ ] **Step 1: Write the failing tests** in `MatchServiceTest` (mocks, `Matcher` mocked to return fixed scores):
  - `matchMandate creates the matches` — 2 PUBLISHED listings scoring 0.9 and 0.6 → 2 `ACTIVE` rows saved with their
    partials, `agentId` = the mandate's; returns 2
  - `matchMandate skips scores below min-score` — 0.4 → nothing saved
  - `matchMandate updates an ACTIVE match` — existing row (id 7) → saved with id 7 and the new scores
  - `matchMandate deletes an ACTIVE match no longer matching` — existing `ACTIVE` + `null` score → `delete(existing)`
  - `matchMandate leaves a DISMISSED match` — existing `DISMISSED` → never saved nor deleted
  - `matchMandate ignores a mandate not to match` — LISTING type / not APPROVED / no agent → 0, no listing read
  - `matchMandate with empty inventory` → 0
  - `a conflict skips the pair` — `save` throws `ObjectOptimisticLockingFailureException` for the 1st listing → the
    2nd is still saved; returns 1
  - `distances` — `searchNearest` called with the mandate's neighborhoods, `NEIGHBORHOOD`, `parentId = cityId`,
    radius 3.0, limit 20; and not called when the mandate has none
  - `matchListing matches the agent's open searches` — 2 APPROVED SEARCH mandates of the listing's agent → `Matcher`
    called for both
  - `matchListing ignores a listing not PUBLISHED` → 0
- [ ] **Step 2: Run** `mvn test -Dtest=MatchServiceTest` — Expected: compile FAIL.
- [ ] **Step 3: Implement** the migration, entity, repositories and both run methods.
- [ ] **Step 4: Run** `mvn test -Dtest=MatchServiceTest` — Expected: PASS.
- [ ] **Step 5: Commit** `feat(match): store matches`

---

### Task 4: `MatchEventListener` — triggers

**Files:**
- Create: `.../match/server/event/MatchEventListener.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/event/MatchEventListenerTest.kt`

**Interfaces:**
- Consumes: `MatchService.matchMandate`, `MatchService.matchListing` (Task 3); `MandateApprovedEvent(mandateId,
  tenantId)`, `ListingPublishedEvent(listingId, tenantId)` (existing).
- Produces: `onMandateApproved(event: MandateApprovedEvent)`, `onListingPublished(event: ListingPublishedEvent)`.

- [ ] **Step 1: Write the failing tests** (style of `MandateEventListenerTest`): each handler calls the service with
  the event's id, with `TenantContext` set to the event's tenant during the call and removed after; a service
  exception is swallowed and the context still removed.
- [ ] **Step 2: Run** `mvn test -Dtest=MatchEventListenerTest` — Expected: compile FAIL.
- [ ] **Step 3: Implement** — same body as `MandateEventListener.onMandateCreated` (logger keys `event`,
  `tenant_id`, `mandate_id` / `listing_id`, `match_count`, `success`). KDoc: why async after commit.
- [ ] **Step 4: Run** — Expected: PASS.
- [ ] **Step 5: Commit** `feat(match): match on approval and publication`

---

### Task 5: `MatchEndpoints` — search, get, dismiss, run

**Files:**
- Modify: `.../match/server/service/MatchService.kt` — add `search`, `findById`, `dismiss`
- Create: `.../match/server/mapper/MatchMapper.kt`, `.../match/server/endpoint/MatchEndpoints.kt`
- Modify: `.../error/server/endpoint/ControllerErrorHandler.kt` — `MatchEntity` → `MATCH_NOT_ACTIVE` in the
  optimistic-lock mapping
- Modify: `modules/ndopify-server/CLAUDE.md` — a `match` entry in *Domains* (what it stores, the triggers, the
  statuses, a pointer to this plan)
- Test (in `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/`):
  `SearchMatchEndpointTest.kt`, `GetMatchEndpointTest.kt`, `DismissMatchEndpointTest.kt`, `RunMatchEndpointTest.kt`,
  `RunMatchOpsEndpointDisabledTest.kt`; fixtures `src/test/resources/db/test/match/<Name>Endpoint.sql`

**Interfaces:**
- Consumes: Tasks 1–3; `MandateAccessService.ensureCanRead/ensureCanUpdate`; `ListingMapper.toListingSummary`.
- Produces:
  - `MatchService.search(request: SearchMatchRequest): List<MatchEntity>`, `findById(id: Long): MatchEntity`,
    `dismiss(id: Long): MatchEntity`
  - `MatchMapper.toMatch(entity: MatchEntity): Match`
  - Endpoints per *Design / API*; `run` returns `RunMatchResponse(count = matchService.matchMandate(mandateId))`,
    404 `MANDATE_NOT_FOUND` for an unknown mandate.

Endpoint tests extend `AuthorizationAwareEndpointIntegrationTest` with `@Sql(["/db/test/clean.sql",
"/db/test/match/<Name>Endpoint.sql"])`, as `DeclineAssignmentEndpointTest`. Fixtures insert the rows directly in
`T_MATCH`, except `RunMatchEndpoint.sql` (the worked example's mandate + listings A–E, the run must find A, C, B).

- [ ] **Step 1: Write the failing tests**
  - `SearchMatchEndpointTest`: `ACTIVE` ones, best score first; `status=DISMISSED`; a match whose listing is RENTED
    isn't listed; another agent → 403 `MANDATE_ACCESS_DENIED`; no token → 401; unknown mandate → 404
    `MANDATE_NOT_FOUND`; `limit`/`offset`
  - `GetMatchEndpointTest`: the match with its partials and `listing.title`; unknown → 404 `MATCH_NOT_FOUND`;
    another tenant's → 404; another agent → 403
  - `DismissMatchEndpointTest`: → `DISMISSED`; already dismissed → 409 `MATCH_NOT_ACTIVE`; another agent → 403;
    unknown → 404; then `POST /v1/matches/run?mandate-id=` leaves it `DISMISSED`
  - `RunMatchEndpointTest`: run → `count = 3`; `GET /v1/matches?mandate-id=` returns listings A, C, B in that order;
    (the event triggers are unit-tested in Task 4)
  - `RunMatchOpsEndpointDisabledTest`: as `DispatchMandateOpsEndpointDisabledTest`
- [ ] **Step 2: Run** `mvn test -Dtest='*MatchEndpoint*'` — Expected: FAIL (404 on `/v1/matches`).
- [ ] **Step 3: Implement** the service methods, mapper, endpoints, handler mapping, `CLAUDE.md` entry.
- [ ] **Step 4: Run** `cd modules/ndopify-server && mvn verify` — Expected: BUILD SUCCESS (ktlint, all tests, JaCoCo).
- [ ] **Step 5: Commit** `feat(match): match endpoints`

---

### After merge

Push to `main` releases `ndopify-dto`; then set `<ndopify-dto.version>` to the released version ("fix
dependencies" commit) before ndopify-server can deploy.
