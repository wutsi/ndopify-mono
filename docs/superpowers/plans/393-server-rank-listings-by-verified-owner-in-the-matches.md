# Rank a verified owner's listings first in the matches — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Store each match's ranking score — its relevance plus `ndopify.match.ownership-bonus` (0.10) when the
listing's owner was verified at publication, capped at 1.0 — and order both the agent's match list and the
auto-proposed top 20 by it, without changing what admits a match nor what any DTO shows.

**Architecture:** One column (`T_MATCH.ranking_score`, `V23_0`, backfilled from `score`), one entity field, and one
formula, `MatchService.rank(listing, score)`, applied wherever a `score` is written (`store` and `accepted`).
`MatchService.search`'s JPA sort and `MatchAutoProposalService`'s in-memory sort swap their `score` key for
`rankingScore`, with `score` then `id` as tie-breakers; `min-score`, `MatchMapper` and the DTOs stay untouched.

**Tech Stack:** Kotlin, Spring Boot 4 / Spring Data JPA, Flyway (MySQL), `kotlin.test` + `mockito-kotlin`,
`TestRestTemplate` against MySQL, `mvn verify`.

**Spec:** GitHub issue #393 — https://github.com/wutsi/ndopify-mono/issues/393 — the enriched story is the design: its
*Decisions and assumptions* 1-8 and *Refined acceptance criteria* 1-8.

## Global Constraints

- **Prerequisite, checked before anything else:** #392 must be on `main` — `ListingEntity.ownershipVerified` and
  `T_LISTING.ownership_verified` (`V22_0`). Check with
  `git grep -n "ownershipVerified" origin/main -- modules/ndopify-server/src/main` and
  `git ls-tree --name-only origin/main -- modules/ndopify-server/src/main/resources/db/migration/common`.
  Missing ⇒ **stop and report**: this story is implemented after #392 merges, never against a guess at its names.
- One module: `modules/ndopify-server`. No `ndopify-dto`, `ndopify-web`, `ndopify-partner-central`, e-mail, template
  nor prototype change. `MatchMapper` and `Match` are untouched: the rank never leaves the server.
- **The formula, exactly:** `if (listing.ownershipVerified) minOf(1.0, score + ownershipBonus) else score`, where
  `ownershipBonus` is `ndopify.match.ownership-bonus` = `0.10`. `0` must reproduce today's order.
- **Admission is unchanged:** `store`'s `!force && score.total < minScore` check reads the raw relevance and runs
  before the rank is computed. The bonus never turns a rejected pair into a match, `force` included.
- **The order, exactly:** `coBrokerage asc`, then `rankingScore desc`, then `score desc`, then `id asc` — co-brokerage
  stays primary (own inventory first, whatever the ranks), the rank orders within each group, the raw score breaks a
  rank tie (the cap makes ties possible), the id makes the order total. Both consumers use that same chain.
- **`T_MATCH.ranking_score`** is `DOUBLE NOT NULL DEFAULT 0.0` and `AFTER score`; the migration backfills
  `ranking_score = score` (no bonus). If another migration already took `V23_0` on `main`, renumber to the next free
  version — Flyway needs a unique, increasing version, nothing else.
- **Fixtures carry explicit ranks:** Flyway runs before the SQL fixtures, so a `T_MATCH` row inserted without
  `ranking_score` reads 0.0. Every fixture row an ordering test reads sets it (equal to its score, unless it is a rank
  case).
- Assertions `kotlin.test` (never `org.junit.jupiter.api.Assertions`), mocks `com.nhaarman.mockitokotlin2`, tests mirror
  the main package. Doubles compare with `assertEquals(expected, actual, 0.000001)`.
- ktlint runs in `mvn validate`; the gate is `cd modules/ndopify-server && mvn verify` (needs local MySQL). Do not
  change any other test's assertion: every existing match test must stay green without an edit beyond the ones named
  here.

## Review Focus

The failure modes this change can ship, most likely first; each gets its test in the task named.

1. **A pair admitted by the bonus.** The bonus must never satisfy `min-score`: a verified listing scoring 0.45 under a
   0.5 threshold must not be stored. Task 1 (`a verified owner's listing below min-score is not stored`).
2. **The rank leaking to a client.** `GET /v1/matches` must keep returning `score` and no rank field; a DTO or mapper
   change fails the test that asserts the payload's `score` values. Task 2.
3. **Co-brokerage overtaken by its rank.** A 0.99 co-brokerage match must still come after a 0.70 own one. Task 2's
   endpoint test and the existing `own inventory first, then co-brokerage`.
4. **A row that predates the story sinking.** A fixture row with no `ranking_score` reads 0.0 and every list silently
   changes order: the fixture must set it to the row's score. Task 2 (`active matches, best score first`).
5. **The cap rounding up.** 0.95 verified stores 1.0, never 1.05, and its `score` stays 0.95. Task 1
   (`caps a verified owner's rank at 1`).

---

## File Structure

- Create `modules/ndopify-server/src/main/resources/db/migration/common/V23_0__match-ranking-score.sql` — the column
  and the backfill.
- Modify `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt` — `rankingScore`.
- Modify `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt` — the setting,
  `rank()`, `store`, `accepted`, `search`'s sort.
- Modify `modules/ndopify-server/src/main/resources/application.yml` — `ndopify.match.ownership-bonus`.
- Modify `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalService.kt` —
  the top 20 by rank.
- Modify `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchProposalService.kt` — none:
  its `sendProposedEmail` keeps the `score` order (display unchanged).
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`,
  `.../service/MatchAutoProposalServiceTest.kt`,
  `.../endpoint/SearchMatchEndpointTest.kt`,
  `modules/ndopify-server/src/test/resources/db/test/match/SearchMatchEndpoint.sql`.
- Modify `modules/ndopify-server/CLAUDE.md` — the *match* section's five sentences on the order.

---

### Task 1: The stored rank, computed where the score is

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V23_0__match-ranking-score.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt` (after `score`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt` (constructor,
  `store`, `accepted`, new private `rank`)
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (the `ndopify.match:` block, after `min-score`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`

**Interfaces:**
- Consumes: #392's `ListingEntity.ownershipVerified: Boolean` and `T_LISTING.ownership_verified`.
- Produces: `MatchEntity.rankingScore: Double = 0.0`; the setting `ndopify.match.ownership-bonus` (Double);
  `private fun MatchService.rank(listing: ListingEntity, score: Double): Double`.

- [ ] **Step 1: Write the failing tests**

In `MatchServiceTest`, add the constructor argument to the existing instance (an eleventh positional parameter, after
`20`) and the listing the rank cases need:

```kotlin
    private val service = MatchService(
        matchDao,
        mandateDao,
        listingDao,
        matcher,
        locationService,
        tenantService,
        clock,
        mock(),
        0.5,
        3.0,
        20,
        0.1, // ndopify.match.ownership-bonus
    )

    // listing2 (id 12) as if its owner had been verified at publication
    private val verifiedListing = listing2.copy(ownershipVerified = true)
```

Then these six tests:

```kotlin
    @Test
    fun `store ranks a verified owner's listing with the bonus`() {
        doReturn(listOf(listing1, verifiedListing)).whenever(listingDao)
            .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(scored(0.8)).whenever(matcher).evaluate(eq(search), eq(listing1), any(), eq(today))
        doReturn(scored(0.72)).whenever(matcher).evaluate(eq(search), eq(verifiedListing), any(), eq(today))

        assertEquals(2, service.matchMandate(MANDATE_ID))

        val saved = argumentCaptor<MatchEntity>()
        verify(matchDao, times(2)).save(saved.capture())
        assertEquals(0.8, saved.firstValue.rankingScore, 0.000001) // Not verified: the score itself
        assertEquals(0.82, saved.secondValue.rankingScore, 0.000001) // Verified: the score + 0.10
    }

    @Test
    fun `caps a verified owner's rank at 1`() {
        doReturn(listOf(verifiedListing)).whenever(listingDao)
            .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(scored(0.95)).whenever(matcher).evaluate(eq(search), eq(verifiedListing), any(), eq(today))

        service.matchMandate(MANDATE_ID)

        val saved = argumentCaptor<MatchEntity>()
        verify(matchDao).save(saved.capture())
        assertEquals(1.0, saved.firstValue.rankingScore, 0.000001)
        assertEquals(0.95, saved.firstValue.score, 0.000001) // The relevance keeps its own value
    }

    @Test
    fun `a verified owner's listing below min-score is not stored`() {
        doReturn(listOf(verifiedListing)).whenever(listingDao)
            .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(scored(0.45)).whenever(matcher).evaluate(any(), any(), any(), any())

        assertEquals(0, service.matchMandate(MANDATE_ID))

        verify(matchDao, never()).save(any<MatchEntity>())
    }

    @Test
    fun `a re-score recomputes the rank of an existing match`() {
        val existing = MatchEntity(
            id = 9L,
            mandate = search,
            listing = verifiedListing,
            status = MatchStatus.ACTIVE,
            score = 0.6,
            rankingScore = 0.7,
        )
        doReturn(existing).whenever(matchDao).findByMandateAndListing(search, verifiedListing)
        doReturn(listOf(verifiedListing)).whenever(listingDao)
            .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(scored(0.8)).whenever(matcher).evaluate(eq(search), eq(verifiedListing), any(), eq(today))

        service.matchMandate(MANDATE_ID)

        val saved = argumentCaptor<MatchEntity>()
        verify(matchDao).save(saved.capture())
        assertEquals(9L, saved.firstValue.id)
        assertEquals(0.8, saved.firstValue.score, 0.000001)
        assertEquals(0.9, saved.firstValue.rankingScore, 0.000001) // 0.8 + 0.10, not the old 0.7
    }

    @Test
    fun `a zero bonus stores the score as the rank`() {
        val service = MatchService(
            matchDao,
            mandateDao,
            listingDao,
            matcher,
            locationService,
            tenantService,
            clock,
            mock(),
            0.5,
            3.0,
            20,
            0.0,
        )
        doReturn(listOf(verifiedListing)).whenever(listingDao)
            .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
        doReturn(scored(0.72)).whenever(matcher).evaluate(eq(search), eq(verifiedListing), any(), eq(today))

        service.matchMandate(MANDATE_ID)

        val saved = argumentCaptor<MatchEntity>()
        verify(matchDao).save(saved.capture())
        assertEquals(0.72, saved.firstValue.rankingScore, 0.000001)
    }

    @Test
    fun `acceptInterest ranks the accepted match too`() {
        val verified = listing1.copy(ownershipVerified = true)
        doReturn(Optional.of(verified)).whenever(listingDao).findById(LISTING_ID)
        doReturn(score(0.9)).whenever(matcher).scoreInterest(eq(search), eq(verified), any(), eq(today))
        doReturn(null).whenever(matchDao).findByMandateAndListing(search, verified)

        service.acceptInterest(MANDATE_ID, LISTING_ID)

        val saved = argumentCaptor<MatchEntity>()
        verify(matchDao).save(saved.capture())
        assertEquals(0.9, saved.firstValue.score, 0.000001)
        assertEquals(1.0, saved.firstValue.rankingScore, 0.000001) // 0.9 + 0.10, capped
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: compilation failure — `MatchService` has no eleventh parameter, `MatchEntity` has no `rankingScore`. That
failure is the red state; the class-level run needs no MySQL.

- [ ] **Step 3: Add the column and the backfill**

Create `V23_0__match-ranking-score.sql`:

```sql
ALTER TABLE T_MATCH
  ADD COLUMN ranking_score DOUBLE NOT NULL DEFAULT 0.0 AFTER score;

-- Rows matched before this story keep today's order until their next re-match: the bonus is never backfilled
UPDATE T_MATCH SET ranking_score = score;
```

- [ ] **Step 4: Add the entity field**

In `MatchEntity`, right after `val score: Double = 0.0,`:

```kotlin
    // The relevance with the verified-owner bonus, capped at 1.0: what the lists order by. Internal to the server,
    // the agent reads score (ranking_score, V23_0)
    val rankingScore: Double = 0.0,
```

- [ ] **Step 5: Compute the rank where the score is written**

In `MatchService`, append the setting to the constructor (after `nearbyMaxNeighborhoods`; the existing positional
call in `MatchServiceTest` passes it last):

```kotlin
    @param:Value("\${ndopify.match.ownership-bonus}") private val ownershipBonus: Double,
```

Add the helper next to `store`:

```kotlin
    // A listing whose owner was verified ranks `ownership-bonus` higher, never above 1.0: a bonus, not a strict sort.
    // The raw score keeps deciding min-score and the display.
    private fun rank(listing: ListingEntity, score: Double): Double =
        if (listing.ownershipVerified) minOf(1.0, score + ownershipBonus) else score
```

In `store`'s `match.copy(...)`, add `rankingScore = rank(listing, score.total),` immediately after
`score = score.total,`. In `accepted`'s `match.copy(...)`, add `rankingScore = rank(listing, score.total),` immediately
after its `score = score.total,` — an explicit interest writes a fresh score, and the rank must never lag it.

- [ ] **Step 6: Add the setting**

In `modules/ndopify-server/src/main/resources/application.yml`, in the `ndopify.match:` block, after `min-score`:

```yaml
        ownership-bonus: 0.10           # 0..1: a listing whose owner was verified ranks as if it scored this much higher; 0 = today's order
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: PASS, the six new tests and every existing one.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V23_0__match-ranking-score.sql \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt \
  modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt
git commit -m "feat(server): store the verified-owner ranking score on a match" -m "Refs #393"
```

---

### Task 2: The agent's list orders by the rank

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt` (`search`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/SearchMatchEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/resources/db/test/match/SearchMatchEndpoint.sql`

**Interfaces:**
- Consumes: Task 1's `MatchEntity.rankingScore`.
- Produces: nothing new — `MatchService.search(SearchMatchRequest): List<MatchEntity>` keeps its signature and its
  `coBrokerage, rankingScore, score, id` sort.

- [ ] **Step 1: Write the failing unit test for the sort**

In `MatchServiceTest`, with the imports `org.springframework.data.domain.PageImpl`, `org.springframework.data.domain.Pageable`,
`org.springframework.data.domain.Sort`, `org.springframework.data.jpa.domain.Specification` and
`com.wutsi.ndopify.match.dto.SearchMatchRequest`:

```kotlin
    @Test
    fun `the list sorts by co-brokerage, then rank, then relevance, then id`() {
        doReturn(PageImpl(emptyList<MatchEntity>()))
            .whenever(matchDao).findAll(any<Specification<MatchEntity>>(), any<Pageable>())

        service.search(SearchMatchRequest(mandateIds = listOf(MANDATE_ID)))

        val pageable = argumentCaptor<Pageable>()
        verify(matchDao).findAll(any<Specification<MatchEntity>>(), pageable.capture())
        assertEquals(
            listOf(
                Sort.Order.asc("coBrokerage"),
                Sort.Order.desc("rankingScore"),
                Sort.Order.desc("score"),
                Sort.Order.asc("id"),
            ),
            pageable.firstValue.sort.toList(),
        )
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: FAIL — the sort is `coBrokerage, score, id`, so the captured `Pageable.sort` differs.

- [ ] **Step 3: Replace the sort and its KDoc**

In `MatchService.search`, replace the `Sort.by(...)` block with:

```kotlin
        val sort = Sort.by(
            Sort.Order.asc("coBrokerage"),
            Sort.Order.desc("rankingScore"),
            Sort.Order.desc("score"),
            Sort.Order.asc("id"),
        )
```

Its KDoc's first sentence becomes: "The matches of the mandates [SearchMatchRequest.mandateIds] and / or of the
listings [SearchMatchRequest.listingIds] ..., best rank first (`rankingScore`, the score plus the verified-owner bonus
when the listing's owner was verified at publication, capped at 1.0; ties by score then id), all mandates together."
Keep the rest of the KDoc as it is. Do not touch `MatchMapper` nor `SearchMatchResponse`.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: PASS.

- [ ] **Step 5: Carry the ranks in the search fixture**

In `db/test/match/SearchMatchEndpoint.sql`, replace the two existing `T_MATCH` inserts with the ones below — they add
`ranking_score` right after `score` and give every existing row its score as rank (the migration's backfill) — then
append the rank-case block: a search of agent 20's for the new rows, on its own so the existing assertions stay true.
(The mandates 15-18, 21 and 22 and the listings 105-108, 111 and 112 are new; 19, 109 and 901 are already taken.)

```sql
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, ranking_score, listing_price,
    neighborhood_score, price_score, rooms_score, amenities_score, availability_score, furniture_score,
    created_at, modified_at
) VALUES
    (1, 1, 1, 101, 20, 'ACTIVE', 0.9, 0.9, 160000, 1.0, 1.0, 0.7, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (2, 1, 1, 102, 20, 'ACTIVE', 0.7, 0.7, 140000, 0.5, 1.0, 0.7, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (3, 1, 1, 103, 20, 'ACTIVE', 0.95, 0.95, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (4, 1, 1, 104, 20, 'DISMISSED', 0.8, 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (5, 1, 2, 109, 30, 'ACTIVE', 0.8, 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (6, 1, 3, 101, 20, 'ACTIVE', 0.8, 0.8, 160000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (7, 1, 4, 102, 20, 'ACTIVE', 0.85, 0.85, 140000, 1.0, 1.0, 0.7, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (9, 2, 9, 901, 90, 'ACTIVE', 0.8, 0.8, 150000, 1.0, 1.0, 1.0, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00'),
    (10, 1, 4, 101, 20, 'ACCEPTED', 0.75, 0.75, 160000, 1.0, 1.0, 0.7, 0.5, 1.0, 1.0, '2026-10-01 10:00:00', '2026-10-01 10:00:00');

-- 11: own inventory (below the co-brokerage score)   12: another agent's listing
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, ranking_score, listing_price, co_brokerage
) VALUES
    (11, 1, 5, 104, 20, 'ACTIVE', 0.4, 0.4, 150000, FALSE),
    (12, 1, 5, 109, 20, 'ACTIVE', 0.99, 0.99, 150000, TRUE);

-- Search 6 (agent 20's) and its listings' mandates: the verified-owner rank. 22: the co-brokerage listing's
INSERT INTO T_MANDATE(
    id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id,
    property_type, rooms, price, price_periodicity_type, currency_code
) VALUES
    (6, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (18, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (21, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (22, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

INSERT INTO T_LISTING(
    id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, neighborhood_id,
    property_type, rooms, price, currency_code, price_periodicity_type, title, ownership_verified
) VALUES
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Unverified 0.80', 0),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Verified 0.72', 1),
    (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Verified 0.95', 1),
    (108, 1, 18, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Unverified 1.00', 0),
    (111, 1, 21, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Verified 0.60', 1),
    (112, 1, 22, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 1001, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Co-brokerage 0.99', 0);

-- 21: 0.80, unverified (rank 0.80)      22: 0.72, verified (rank 0.82)   23: 0.95, verified (rank 1.0, capped)
-- 24: 1.00, unverified (rank 1.0)       25: 0.60, verified (rank 0.70)   26: 0.99, co-brokerage
INSERT INTO T_MATCH(
    id, tenant_id, mandate_id, listing_id, agent_id, status, score, ranking_score, listing_price, co_brokerage
) VALUES
    (21, 1, 6, 105, 20, 'ACTIVE', 0.80, 0.80, 150000, FALSE),
    (22, 1, 6, 106, 20, 'ACTIVE', 0.72, 0.82, 150000, FALSE),
    (23, 1, 6, 107, 20, 'ACTIVE', 0.95, 1.00, 150000, FALSE),
    (24, 1, 6, 108, 20, 'ACTIVE', 1.00, 1.00, 150000, FALSE),
    (25, 1, 6, 111, 20, 'ACTIVE', 0.60, 0.70, 150000, FALSE),
    (26, 1, 6, 112, 20, 'ACTIVE', 0.99, 0.99, 150000, TRUE);
```

- [ ] **Step 6: Write the failing endpoint test**

In `SearchMatchEndpointTest`:

```kotlin
    @Test
    fun `a verified owner's listing ranks above a better score`() {
        val matches = rest.getForEntity("/v1/matches?mandate-id=6", SearchMatchResponse::class.java).body!!.matches

        // 24 and 23 both rank 1.0 (the cap): the raw score orders them. 22 (rank 0.82) beats 21 (0.80) although it
        // scores less; 25 (0.70) loses to both; 26 (rank 0.99) is co-brokerage, so last whatever its rank
        assertEquals(listOf(24L, 23L, 22L, 21L, 25L, 26L), matches.map { it.id })
        assertEquals(listOf(1.0, 0.95, 0.72, 0.80, 0.60, 0.99), matches.map { it.score })
    }
```

- [ ] **Step 7: Run the endpoint tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchMatchEndpointTest` (local MySQL required)
Expected: PASS — the new test and every existing one (`active matches, best score first`, `own inventory first, then
co-brokerage`, `several mandates`, `limit and offset`, `by listing`, …) prove the rank backfilled as its score leaves
today's order untouched.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/SearchMatchEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/match/SearchMatchEndpoint.sql
git commit -m "feat(server): order the match list by the verified-owner rank" -m "Refs #393"
```

---

### Task 3: The auto-proposal takes the best ranks, and the docs

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalServiceTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the *match* section)

**Interfaces:**
- Consumes: Task 1's `MatchEntity.rankingScore`.
- Produces: nothing new — `MatchAutoProposalService.autoProposeDue(): Int` and `MAX_PER_PROPOSAL = 20` keep their
  contract.

- [ ] **Step 1: Write the failing tests**

In `MatchAutoProposalServiceTest`, give the `match` helper a rank defaulting to its score:

```kotlin
    private fun match(id: Long, mandateId: Long, score: Double, rankingScore: Double = score) = MatchEntity(
        id = id,
        mandate = MandateEntity(id = mandateId),
        listing = ListingEntity(id = 1000 + id),
        agentId = 20,
        status = MatchStatus.ACTIVE,
        score = score,
        rankingScore = rankingScore,
        createdAt = Date.from(Instant.parse("2026-11-13T10:00:00Z")),
    )
```

Then two tests:

```kotlin
    @Test
    fun `the top 20 are the best ranks, not the best scores`() {
        doReturn(
            (1L..25L).map { match(id = it, mandateId = 1, score = (26L - it).toDouble(), rankingScore = it.toDouble()) },
        ).whenever(dao).findStalled(any(), any(), any(), any(), any())
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        assertEquals(20, service.autoProposeDue())

        verify(proposalService).propose((25L downTo 6L).toList())
    }

    @Test
    fun `equal ranks fall back to the relevance, then the id`() {
        doReturn(
            listOf(
                match(id = 12, mandateId = 1, score = 0.7, rankingScore = 0.8),
                match(id = 11, mandateId = 1, score = 0.9, rankingScore = 0.8),
                match(id = 13, mandateId = 1, score = 0.7, rankingScore = 0.8),
            ),
        ).whenever(dao).findStalled(any(), any(), any(), any(), any())
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        service.autoProposeDue()

        verify(proposalService).propose(listOf(11L, 12L, 13L))
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAutoProposalServiceTest`
Expected: the first test FAILS — the sort still reads `score`, so it proposes `(1L..20L)` instead of `(25L downTo 6L)`.
The second passes already (by score, `11, 12, 13` is the same answer); it pins the tie-break for the new sort.

- [ ] **Step 3: Sort the proposal by the rank**

In `MatchAutoProposalService.propose`, replace the `sortedByDescending` line with:

```kotlin
        val ids = matches
            .sortedWith(
                compareByDescending<MatchEntity> { it.rankingScore }
                    .thenByDescending { it.score }
                    .thenBy { it.id ?: Long.MAX_VALUE },
            )
            .take(MAX_PER_PROPOSAL)
            .map { it.id!! }
```

- [ ] **Step 4: Run them to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAutoProposalServiceTest`
Expected: PASS, the two new tests and the three existing ones.

- [ ] **Step 5: Document the order in the module's instructions**

In `modules/ndopify-server/CLAUDE.md`, in the *match* section:

1. The `GET /v1/matches` sentence "lists the agent's own inventory first, then co-brokerage, best score first" becomes
   "lists the agent's own inventory first, then co-brokerage, best rank first".
2. "ACTIVE; best score first across them, only while the mandate is APPROVED and the listing PUBLISHED" becomes
   "ACTIVE; best rank first across them (`rankingScore`: the score plus `ndopify.match.ownership-bonus` (0.10) when
   the listing's `ownershipVerified` was frozen `true` at publication, capped at 1.0; ties by score then id — the agent
   still reads `score`), only while the mandate is APPROVED and the listing PUBLISHED".
3. "Under `min-score` it isn't stored." becomes "Under `min-score` it isn't stored — the bonus never counts towards
   it."
4. The `MatchJobs.rematch` sentence "re-scores against today (availability)" becomes "re-scores against today
   (availability) and the listing's stored `ownershipVerified` (the rank)".
5. The auto-proposal sentence "grouped by mandate, at most 20 per proposal, through `MatchProposalService.propose`"
   becomes "grouped by mandate, the 20 best-ranked per proposal (ties by score then id), through
   `MatchProposalService.propose`".

Leave the proposal's own "best score first" (the e-mail's order) as it is: display is unchanged.

- [ ] **Step 6: Run the module's gate**

Run: `cd modules/ndopify-server && mvn verify` (local MySQL required)
Expected: BUILD SUCCESS — ktlint, every unit and endpoint test, the JaCoCo gate.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalServiceTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): auto-propose the best-ranked matches" -m "Refs #393"
```
