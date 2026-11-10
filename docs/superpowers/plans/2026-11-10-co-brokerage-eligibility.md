# Co-Brokerage Eligibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a signed search fall back to the published listings of *other* agents when its own agent's inventory has nothing that matches, keeping the agent's own inventory first.

**Architecture:** `Matcher` stays a pure pairwise scorer. `MatchService.matchMandate` runs the existing own-inventory pass, then — only when no viable own match exists — scores the published listings of other agents in the same city and listing type, storing them ACTIVE with `coBrokerage = true`. Nothing is deleted for a co-brokerage pair, and `GET /v1/matches` orders own inventory before co-brokerage, then by score. No new endpoint and no new configuration.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (Hibernate multi-tenancy), Flyway/MySQL, mockito-kotlin, `kotlin.test`, `TestRestTemplate` endpoint tests.

**Spec:** the brainstorming conversation of 2026-11-10, captured in *Design* below (this plan is the design record). This is slice #1 (*eligibility*) of the co-brokerage decomposition; closing, visits, notifications and commission are separate slices.

## Global Constraints

- Only an APPROVED `SEARCH` mandate with an agent is matched; `mandate` and `listing` never depend on `match`.
- `Matcher` keeps every rule-out and every weight; the fallback only decides *which pairs are offered to it*.
- Same `min-score`, `price-tolerance`, `nearby-radius-km` and tenant-local `today` as the own pass.
- Cross-agent candidates: same tenant, `status = PUBLISHED`, same `cityId`, same `listingType`, `agent.id != mandate.agentId`. The Matcher decides the rest (category, property type, budget, rooms, the customer's own property).
- A DISMISSED or REJECTED own match does not block the fallback. An ACTIVE, SHORTLISTED, PROPOSED, ACCEPTED or EXPIRED own match does.
- A co-brokerage row is never deleted by the matcher. An own-inventory row keeps the existing re-score / delete-when-no-longer-matching behaviour.
- Own inventory always sorts before co-brokerage, then best score, then id.
- No new endpoint, no new `ndopify.match.*` property. `coBrokerage` is persisted (`V10_5`) and exposed in `Match`.
- `ndopify-dto` changes need a DTO release before the server can be deployed (see `modules/ndopify-dto/CLAUDE.md`).
- Assertions with `kotlin.test`, mocks with mockito-kotlin; tests mirror the package.
- Coverage gate of ndopify-server: `jacoco.threshold.line` 0.95, `jacoco.threshold.class` 0.90.
- Work on a branch: `git checkout -b feat/match-co-brokerage`.
- Endpoint tests need a local MySQL (see the root `CLAUDE.md`).

## Design

**Per search S** (APPROVED, `SEARCH`, agent A):

1. **Own pass, unchanged.** Score S against A's PUBLISHED listings; upsert.
2. **Gate.** Let `ownCount` be the ACTIVE rows stored by the own pass. S falls back only when `ownCount == 0` **and** S has no viable own match:
   ```sql
   exists match(mandate = S, co_brokerage = FALSE, listing.status = PUBLISHED,
                status NOT IN (DISMISSED, REJECTED))
   ```
   Reading stored statuses (not re-scoring own inventory) is what lets a dismissed or customer-rejected own match stop blocking the fallback. It is also one indexed query.
3. **Candidates.** `listing.status = PUBLISHED`, `listing.cityId = S.cityId`, `listing.listingType = S.listingType`, `listing.agent.id != A`. This is a coarse prefilter only: `Matcher` still applies the category / type matrix, the budget, the rooms rule, the customer's-own-property rule and `min-score`.
4. **Score and store** with the same `distances` and `today`, ACTIVE, `coBrokerage = true`.
5. **Never delete** a co-brokerage row. Own rows keep the existing behaviour.
6. **Rank** `coBrokerage asc, score desc, id asc`.

**Triggers.** `MatchService.matchMandate` (mandate approval) and `MatchService.matchAll` (the daily `MatchJobs.rematch`, which calls `matchMandate`). `matchListing` is unchanged: another agent's publication does not fan out over every search in the tenant; the next rematch picks it up. `/v1/matches/run?mandate-id=` forces it.

**Worked example.** S has no own inventory → B1 (0.81) and C1 (0.74) are stored as co-brokerage. A publishes A1 (0.90) → the next rematch stores A1, and S lists A1 first, then B1 and C1. A dismisses A1 → it stops blocking the gate, but B1/C1 are already stored and stay. If A1's deal closes and its listing becomes RENTED, it drops out of the read filters and B1/C1 remain.

**No cap.** With sticky rows, a per-search cap would freeze S on the first N co-brokerage listings and hide better ones published later. The read is ranked and paginated instead.

**Two calls made while planning:**
- Global "never delete every ACTIVE row" was narrowed to *never delete co-brokerage rows*. Own rows keep the safety net that removes a pair that no longer matches (today only reachable if a PUBLISHED listing can change, which it cannot) — `RematchJobTest` and `MatchServiceTest` pin that behaviour.
- The gate is stored-status based rather than score based, so a customer's rejection or an agent's dismissal does not leave the search with nothing forever.

## Review Focus

1. **The only own match was dismissed or rejected** → the search still gets a co-brokerage listing (Task 3, endpoint scenarios 4 and 5).
2. **Own inventory appears after co-brokerage rows exist** → both are listed, own first; the co-brokerage rows stay (Task 3, endpoint scenario 3; Task 2 ordering).
3. **A same-agent, wrong-city or non-PUBLISHED listing** → never scored as a co-brokerage candidate (Task 3, endpoint scenario 1 and the candidate-query assertion in Task 3).
4. **A co-brokerage pair that no longer qualifies** (ex: other currency) → the row is left ACTIVE, not deleted (Task 3, endpoint scenario 6 and unit test "never deletes").
5. **The listing is rented/sold after a co-brokerage match** → the match is hidden by the read filter, the row kept (Task 3, endpoint scenario 7).

---

### Task 1: `ndopify-dto` — the `coBrokerage` flag

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/Match.kt`
- Modify: `pom.xml` (root) — `<ndopify-dto.version>`

**Interfaces:**
- Produces: `Match.coBrokerage: Boolean` (default `false`), read by every later task and by Partner Central.

- [ ] **Step 1: Add the field**

In `Match.kt`, after `listingId`:
```kotlin
// True when the listing belongs to another agent's inventory (co-brokerage fallback)
val coBrokerage: Boolean = false,
```

- [ ] **Step 2: Point the reactor at the DTO snapshot and install it**

Set `<ndopify-dto.version>` in the root `pom.xml` to the DTO's current `-SNAPSHOT` (`0.0.87-SNAPSHOT` today), then:
```bash
cd modules/ndopify-dto && mvn install
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Commit**
```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/Match.kt pom.xml
git commit -m "feat(dto): flag co-brokerage matches"
```

> After the DTO change reaches `main`, `ndopify-dto-main.yml` releases `0.0.87`; then set `<ndopify-dto.version>` to `0.0.87` in a "fix dependencies" commit so the other modules' CI can resolve it. If the server PR's CI cannot resolve the snapshot, merge the DTO change first.

---

### Task 2: persistence and read ordering

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V10_5__match-co-brokerage.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mapper/MatchMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/dao/ListingRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt` (`search` sort only)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/SearchMatchEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/match/SearchMatchEndpoint.sql`

**Interfaces:**
- Consumes: `Match.coBrokerage` (Task 1).
- Produces:
  - `MatchEntity.coBrokerage: Boolean`
  - `MatchRepository.existsViableOwnMatch(mandateId: Long, statuses: Collection<MatchStatus>, listingStatus: ListingStatus): Boolean`
  - `ListingRepository.findByCityIdAndListingTypeAndStatusAndAgentIdNot(cityId: Long, listingType: ListingType, status: ListingStatus, agentId: Long): List<ListingEntity>`
  - `MatchService.search` order `coBrokerage asc, score desc, id asc`.

- [ ] **Step 1: Write the failing endpoint test and fixture**

In `SearchMatchEndpoint.sql`, add a fifth search of agent 20 and two matches: one own with a low score, one co-brokerage with a high score. Append:
```sql
(5, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF')
```
to the `T_MANDATE` insert (keep the existing rows untouched), then append a separate insert:
```sql
-- 11: own inventory (below the co-brokerage score)   12: another agent's listing
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, co_brokerage) VALUES
    (11, 1, 5, 104, 20, 'ACTIVE', 0.4, 150000, FALSE),
    (12, 1, 5, 109, 20, 'ACTIVE', 0.99, 150000, TRUE);
```

Add to `SearchMatchEndpointTest`:
```kotlin
@Test
fun `own inventory first, then co-brokerage`() {
    val matches = rest.getForEntity("/v1/matches?mandate-id=5", SearchMatchResponse::class.java).body!!.matches

    assertEquals(listOf(11L, 12L), matches.map { it.id })
    assertEquals(listOf(false, true), matches.map { it.coBrokerage })
    assertEquals(0.99, matches[1].score) // Higher score, but another agent's listing still comes second
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchMatchEndpointTest`
Expected: FAIL — `Match` has no `coBrokerage` / the ordering is by score only. (Requires local MySQL.)

- [ ] **Step 3: Add the migration**

`V10_5__match-co-brokerage.sql`:
```sql
ALTER TABLE T_MATCH
    ADD COLUMN co_brokerage BOOLEAN NOT NULL DEFAULT FALSE;
```

- [ ] **Step 4: Add the entity field**

In `MatchEntity.kt`, after `agentId` (all the existing constructions use named arguments, so the default keeps them compiling):
```kotlin
// The listing belongs to another agent's inventory: the search had no own match (co-brokerage fallback)
val coBrokerage: Boolean = false,
```

- [ ] **Step 5: Map it**

In `MatchMapper.toMatch`, add `coBrokerage = entity.coBrokerage,`.

- [ ] **Step 6: Add the two repository queries**

`MatchRepository.kt`:
```kotlin
/**
 * Whether the search still has one of its agent's own listings to offer: PUBLISHED, and not turned down by the
 * agent (DISMISSED) or the customer (REJECTED). When it has none, co-brokerage may fill in.
 */
@Query(
    "SELECT CASE WHEN COUNT(m) > 0 THEN TRUE ELSE FALSE END FROM MatchEntity m " +
        "WHERE m.mandate.id = :mandateId AND m.coBrokerage = FALSE " +
        "AND m.status NOT IN :statuses AND m.listing.status = :listingStatus"
)
fun existsViableOwnMatch(
    mandateId: Long,
    statuses: Collection<MatchStatus>,
    listingStatus: ListingStatus,
): Boolean
```

`ListingRepository.kt` (import `com.wutsi.ndopify.listing.dto.ListingType`):
```kotlin
// The co-brokerage candidates of a search: another agent's published listings in its city and listing type
fun findByCityIdAndListingTypeAndStatusAndAgentIdNot(
    cityId: Long,
    listingType: ListingType,
    status: ListingStatus,
    agentId: Long,
): List<ListingEntity>
```

- [ ] **Step 7: Order own inventory first**

In `MatchService.search`, replace the sort:
```kotlin
val sort = Sort.by(
    Sort.Order.asc("coBrokerage"),
    Sort.Order.desc("score"),
    Sort.Order.asc("id"),
)
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchMatchEndpointTest`
Expected: PASS.

- [ ] **Step 9: Commit**
```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V10_5__match-co-brokerage.sql \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mapper/MatchMapper.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/dao/ListingRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/SearchMatchEndpointTest.kt \
    modules/ndopify-server/src/test/resources/db/test/match/SearchMatchEndpoint.sql
git commit -m "feat(match): persist and rank co-brokerage matches"
```

---

### Task 3: the eligibility gate and the fallback pass

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/match/CoBrokerageMatchEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/CoBrokerageMatchEndpointTest.kt`

**Interfaces:**
- Consumes: `MatchRepository.existsViableOwnMatch`, `ListingRepository.findByCityIdAndListingTypeAndStatusAndAgentIdNot`, `MatchEntity.coBrokerage` (Task 2).
- Produces: `MatchService.matchMandate` falls back; `matchAll` (no-arg) inherits it.

- [ ] **Step 1: Write the failing service tests**

In `MatchServiceTest`:
- Add `listingType = ListingType.RENTAL` to the `search` fixture, and import `ListingType` and `kotlin.test.assertTrue`.
- In `matchMandate creates the matches`, add `assertEquals(false, first.coBrokerage)`.

Append these tests (reuse `score()`, `listing1`, `search`):
```kotlin
private val otherListing = listing1.copy(id = 20L, price = 90_000, agent = AgentEntity(id = 30L))

@Test
fun `matchMandate falls back to another agent's listing`() {
    doReturn(emptyList<ListingEntity>()).whenever(listingDao)
        .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(listOf(otherListing)).whenever(listingDao)
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(CITY_ID, ListingType.RENTAL, ListingStatus.PUBLISHED, AGENT_ID)
    doReturn(score(0.8)).whenever(matcher).score(eq(search), eq(otherListing), any(), eq(today))

    assertEquals(1, service.matchMandate(MANDATE_ID))

    val captor = argumentCaptor<MatchEntity>()
    verify(matchDao).save(captor.capture())
    assertEquals(otherListing, captor.firstValue.listing)
    assertEquals(AGENT_ID, captor.firstValue.agentId)
    assertTrue(captor.firstValue.coBrokerage)
}

@Test
fun `matchMandate does not fall back when an own match is stored`() {
    doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())

    assertEquals(1, service.matchMandate(MANDATE_ID))

    verify(listingDao, never())
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(any(), any(), any(), any())
}

@Test
fun `matchMandate does not fall back while a viable own match exists`() {
    doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(score(0.4)).whenever(matcher).score(any(), any(), any(), any())
    doReturn(true).whenever(matchDao)
        .existsViableOwnMatch(eq(MANDATE_ID), any(), eq(ListingStatus.PUBLISHED))

    assertEquals(0, service.matchMandate(MANDATE_ID))

    verify(listingDao, never())
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(any(), any(), any(), any())
}

@Test
fun `matchMandate falls back when the only own match is dismissed`() {
    val dismissed = MatchEntity(id = 7L, mandate = search, listing = listing1, status = MatchStatus.DISMISSED)
    doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(dismissed).whenever(matchDao).findByMandateAndListing(search, listing1)
    doReturn(listOf(otherListing)).whenever(listingDao)
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(CITY_ID, ListingType.RENTAL, ListingStatus.PUBLISHED, AGENT_ID)
    doReturn(score(0.9)).whenever(matcher).score(eq(search), eq(listing1), any(), eq(today))
    doReturn(score(0.8)).whenever(matcher).score(eq(search), eq(otherListing), any(), eq(today))

    assertEquals(1, service.matchMandate(MANDATE_ID))

    val captor = argumentCaptor<MatchEntity>()
    verify(matchDao).save(captor.capture())
    assertEquals(otherListing, captor.firstValue.listing)
    assertTrue(captor.firstValue.coBrokerage)
}

@Test
fun `matchMandate never deletes a co-brokerage match`() {
    val existing = MatchEntity(
        id = 7L,
        mandate = search,
        listing = otherListing,
        status = MatchStatus.ACTIVE,
        coBrokerage = true,
    )
    doReturn(emptyList<ListingEntity>()).whenever(listingDao)
        .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(existing).whenever(matchDao).findByMandateAndListing(search, otherListing)
    doReturn(listOf(otherListing)).whenever(listingDao)
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(CITY_ID, ListingType.RENTAL, ListingStatus.PUBLISHED, AGENT_ID)
    doReturn(null).whenever(matcher).score(any(), any(), any(), anyOrNull())

    assertEquals(0, service.matchMandate(MANDATE_ID))

    verify(matchDao, never()).delete(any<MatchEntity>())
    verify(matchDao, never()).save(any<MatchEntity>())
}

@Test
fun `matchListing does not create co-brokerage matches`() {
    doReturn(Optional.of(listing1)).whenever(listingDao).findById(LISTING_ID)
    doReturn(listOf(search)).whenever(mandateDao)
        .findByAgentIdAndMandateTypeAndStatus(AGENT_ID, MandateType.SEARCH, MandateStatus.APPROVED)
    doReturn(score(0.9)).whenever(matcher).score(any(), any(), any(), any())

    assertEquals(1, service.matchListing(LISTING_ID))

    verify(listingDao, never())
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(any(), any(), any(), any())
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: FAIL — the fallback methods are not called, `coBrokerage` is false, the co-brokerage row is deleted.

- [ ] **Step 3: Implement the gate and the fallback**

In `MatchService`, add the constant:
```kotlin
// The own matches that show the agent's inventory still has nothing for the search
val NON_VIABLE_OWN_STATUSES = setOf(MatchStatus.DISMISSED, MatchStatus.REJECTED)
```

Replace `matchMandate` with (keeping its KDoc, adding the fallback sentence):
```kotlin
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
    LOGGER.info(">>> Mandate#$mandateId - CO_BROKERAGE candidate_count=${candidates.size}")
    return matchAll(candidates.map { listing -> Triple(mandate, listing, distances) }, today)
}

// A PUBLISHED own match that neither the agent nor the customer turned down
private fun hasViableOwnMatch(mandateId: Long): Boolean =
    dao.existsViableOwnMatch(
        mandateId = mandateId,
        statuses = NON_VIABLE_OWN_STATUSES,
        listingStatus = ListingStatus.PUBLISHED,
    )
```

In `store`, compute the flag from the pair and never delete a co-brokerage row:
```kotlin
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
            // ...the existing partial scores, distanceKm, missingAmenities, listingPrice, modifiedAt unchanged...
        )
    )
    return true
}
```

- [ ] **Step 4: Run the unit tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: PASS.

- [ ] **Step 5: Write the failing endpoint tests and fixture**

Create `CoBrokerageMatchEndpoint.sql` with one tenant, city `2370201`, neighbourhood `1001`, parties `100` (search customer), `101` (owner), `200` (agent 20, user 11, the caller), `300` (agent 30), customers `10`/`11`, agents `20`/`30`, and:

- Seven searches of agent 20: `1, 4, 5, 6, 7` with budget `100000` (own listing 102 at 240 000 is over budget, so no own match); `2, 3` with budget `250000` (listing 102 matches).
- Five listings:
  - `101`: agent 30, PUBLISHED, 90 000 XAF — the co-brokerage candidate.
  - `102`: agent 20, PUBLISHED, 240 000 XAF — the own listing.
  - `103`: agent 30, PUBLISHED, 90 000 XAF, its mandate's customer `10` — the search customer's own property, must be ruled out.
  - `104`: agent 30, PUBLISHED, 90 000 USD — another currency, ruled out.
  - `105`: agent 30, RENTED — never a candidate and hidden on read.
- Pre-seeded matches:
  ```sql
  INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, status, score, listing_price, co_brokerage) VALUES
      (1, 1, 3, 101, 20, 'ACTIVE', 0.8, 90000, TRUE),      -- sticky
      (2, 1, 4, 102, 20, 'DISMISSED', 0.9, 240000, FALSE), -- does not block the fallback
      (3, 1, 5, 102, 20, 'REJECTED', 0.9, 240000, FALSE),  -- does not block the fallback
      (4, 1, 6, 104, 20, 'ACTIVE', 0.8, 90000, TRUE),      -- no longer qualifies (USD), never deleted
      (5, 1, 7, 105, 20, 'ACTIVE', 0.8, 90000, TRUE);      -- one of search 7's, but its listing is rented
  ```

Create `CoBrokerageMatchEndpointTest` (mirror `RunMatchEndpointTest`: extends `AuthorizationAwareEndpointIntegrationTest`, `roles = arrayOf(RoleCode.ADMIN)`, `@Sql` with `clean.sql` and the fixture, `@Autowired JdbcTemplate`):
```kotlin
@Test
fun `falls back to another agent's listing`() {
    run(1)
    assertEquals(
        mapOf(101L to true),
        coBrokerageByListing(1L), // 103: the customer's own   104: another currency   105: not published
    )
}

@Test
fun `own inventory wins`() {
    run(2)
    assertEquals(mapOf(102L to false), coBrokerageByListing(2L)) // 101 also matches, but is not offered
}

@Test
fun `keeps the co-brokerage match when own inventory appears`() {
    run(3)
    assertEquals(mapOf(102L to false, 101L to true), coBrokerageByListing(3L))

    val matches = rest.getForEntity("/v1/matches?mandate-id=3", SearchMatchResponse::class.java).body!!.matches
    assertEquals(listOf(102L, 101L), matches.map { it.listingId }) // Own first
}

@Test
fun `a dismissed own match does not block the fallback`() {
    run(4)
    assertEquals(MatchStatus.DISMISSED, status(mandateId = 4L, listingId = 102L))
    assertEquals(mapOf(102L to false, 101L to true), coBrokerageByListing(4L))
}

@Test
fun `a rejected own match does not block the fallback`() {
    run(5)
    assertEquals(MatchStatus.REJECTED, status(mandateId = 5L, listingId = 102L))
    assertTrue(coBrokerageByListing(5L).getValue(101L)!!)
}

@Test
fun `a co-brokerage match no longer qualifying is kept`() {
    run(6)
    assertEquals(MatchStatus.ACTIVE, status(mandateId = 6L, listingId = 104L))
    assertTrue(coBrokerageByListing(6L).getValue(101L)!!)
}

@Test
fun `a co-brokerage match of a rented listing is not listed`() {
    val matches = rest.getForEntity("/v1/matches?mandate-id=7", SearchMatchResponse::class.java).body!!.matches
    assertTrue(matches.isEmpty())
}
```
Helpers: `run(id)` posts `/v1/matches/run?mandate-id=$id`; `coBrokerageByListing(mandateId): Map<Long, Boolean>` reads `SELECT listing_id, co_brokerage FROM T_MATCH WHERE mandate_id=?` with `JdbcTemplate` (not the repository, which is tenant-filtered); `status(mandateId, listingId)` reads the status the same way.

- [ ] **Step 6: Run the endpoint tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageMatchEndpointTest,SearchMatchEndpointTest,RunMatchEndpointTest,RematchJobTest`
Expected: PASS, including the existing tests: `RunMatchEndpointTest.run()` still returns only A, C and B (agent 30's and the customer's own listing are not offered while own matches exist), and `RematchJobTest` keeps its re-score/delete behaviour for own rows.

- [ ] **Step 7: Run the whole match package and the coverage gate**

Run: `cd modules/ndopify-server && mvn test -Dtest='com.wutsi.ndopify.match.**' && mvn verify`
Expected: PASS; JaCoCo line coverage ≥ 0.95 and class coverage ≥ 0.90.

- [ ] **Step 8: Commit**
```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt \
    modules/ndopify-server/src/test/resources/db/test/match/CoBrokerageMatchEndpoint.sql \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/CoBrokerageMatchEndpointTest.kt
git commit -m "feat(match): fall back to other agents' listings"
```

---

### Task 4: document the co-brokerage eligibility

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` — the **match** bullet of *Domains in `ndopify-server`*

**Interfaces:**
- Consumes: nothing.
- Produces: the written rule the next slices (closing, visits, notifications) build on.

- [ ] **Step 1: Update the match domain bullet**

In the `match` paragraph, replace "Only an APPROVED SEARCH mandate with an agent is matched, against that agent's PUBLISHED listings" with:

> Only an APPROVED SEARCH mandate with an agent is matched: against that agent's PUBLISHED listings first, then, only when it has no viable own match (`existsViableOwnMatch`: PUBLISHED, not `DISMISSED` nor `REJECTED`), against the other agents' PUBLISHED listings of the same city and listing type (co-brokerage, `coBrokerage`, `V10_5`). The same `Matcher`, `min-score` and distances apply. A co-brokerage row is never deleted (sticky when own inventory appears later); an own-inventory row keeps the re-score/delete behaviour. `GET /v1/matches` lists the agent's own inventory first, then co-brokerage, best score first. `matchListing` never creates co-brokerage rows: the daily `rematch` picks a newly published listing up.

- [ ] **Step 2: Commit**
```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(match): co-brokerage eligibility"
```
