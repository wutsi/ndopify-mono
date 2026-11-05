# Listing Activity Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show, for every PUBLISHED listing, how many clients it interested and how many visits it has (upcoming and completed), on the Espace agent's mandate cards, the mandate page and the listing page.

**Architecture:** One batch read, `GET /v1/listings/stats?listing-id=…`, aggregates matches and visits in the database with `GROUP BY` — no counters on `ListingEntity`, nothing loaded per listing. It returns one row per requested listing, zeros included. `ndopify-partner-central` reads it in one call per page and renders one line, `"2 personnes intéressées · 3 visites"`, only for a PUBLISHED listing. The mandate page reuses the matches and visits it already loads for its *Propositions* and *Visites* sections.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (JPQL), MySQL + Flyway, Thymeleaf, Maven, `kotlin.test`, MockMvc, Jsoup.

**Spec:** the 2026-11-05 design discussion. The visual reference is the prototype's activity line in `modules/ndopify-partner-central/src/main/html` (`LISTING_ACTIVITY` / `statsSummary` in `assets/js/partner-central.js`), which also shows views — views are deliberately not part of this plan.

## Global Constraints

- **Views are out of scope.** The endpoint, the DTOs and the UI carry interest and visits only; leave room for `views` later.
- **PUBLISHED only in the UI.** A DRAFT, PENDING_VERIFICATION, UNDER_VERIFICATION, VERIFIED or PUBLISHING listing never shows the line. The endpoint itself may return a row for any of the agent's listings.
- **No denormalized counters on `ListingEntity`.** Compute with `GROUP BY`; `T_LISTING`'s `@Version` must not be touched by stats.
- **Endpoint contract:** `GET /v1/listings/stats?listing-id=…`. One row per distinct id, in request order, zeros included. Anonymous → 401; another agent's listing → 403 `HTTP_ACCESS_DENIED`; another tenant's/gone → 404 `LISTING_NOT_FOUND`; no `listing-id` → 400 `HTTP_MISSING_PARAMETER`.
- **Visits counted:** `upcoming` = `REQUESTED` still within `ndopify.visit.policy.expire-after`, `CONFIRMED` with `scheduledAt >= now - check-in-after`, and `CHECKED_IN`; `completed` = `COMPLETED`. `CANCELLED`, `EXPIRED` and `NO_SHOW` are never counted.
- **Interested** = matches in `ACCEPTED`, including another agent's client (the listing interested them too).
- **Copy (French):** `"2 personnes intéressées"`, `"Aucune personne intéressée pour l'instant"`, `"3 visites"`, `"Aucune visite"`. Separator: `" · "`. No views.
- Kotlin formatted by ktlint; assertions via `kotlin.test`; tests named in backticks.

## Review Focus

- **Published listing with no activity:** the endpoint returns a row of zeros and the UI shows `"Aucune personne intéressée pour l'instant · Aucune visite"` — not a blank line.
- **Stale visit:** a `REQUESTED` older than `expire-after`, or a `CONFIRMED` whose slot is more than `check-in-after` old, is not `upcoming` even before the visit jobs run.
- **Wrong listing:** another agent's listing never returns counts (403); another tenant's is a 404.
- **Duplicate ids:** `listing-id=101&listing-id=101` returns one row, not two.
- **Stats unavailable:** a timeout from ndopify-server leaves the page working, with the line simply absent.
- **Listing not PUBLISHED:** stats may exist, but the line stays hidden until it is PUBLISHED.

---

### Task 1: Filter visits by listing

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/SearchVisitRequest.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/SearchVisitEndpointTest.kt`

**Interfaces:**
- Produces: `SearchVisitRequest.listingIds: List<Long>` bound to `listing-id`, and the matching predicate in `VisitService.search`.

- [ ] **Step 1: Write the failing test** — add to `SearchVisitEndpointTest` (fixture already loaded):

```kotlin
@Test
fun `by listing`() {
    assertEquals(listOf(1L, 3L), search("/v1/visits?listing-id=101"))
    assertEquals(listOf(2L), search("/v1/visits?listing-id=102"))
    assertEquals(emptyList(), search("/v1/visits?listing-id=109")) // Another agent's visit
}
```

- [ ] **Step 2: Run it and watch it fail** — `cd modules/ndopify-server && mvn test -Dtest=SearchVisitEndpointTest` → FAIL (the filter is ignored, so all the agent's visits come back).

- [ ] **Step 3: Implement** — in `SearchVisitRequest`, after `mandateIds`:

```kotlin
@param:BindParam("listing-id") val listingIds: List<Long> = emptyList(),
```

and in `VisitService.search`, after the `matchIds` predicate:

```kotlin
if (request.listingIds.isNotEmpty()) {
    predicates.add(root.get<Long>("listingId").`in`(request.listingIds))
}
```

- [ ] **Step 4: Run the test to verify it passes** — `cd modules/ndopify-server && mvn test -Dtest=SearchVisitEndpointTest` → PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/visit/dto/SearchVisitRequest.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/endpoint/SearchVisitEndpointTest.kt
git commit -m "feat(visit): filter visits by listing"
```

---

### Task 2: `GET /v1/listings/stats` — interested clients

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ListingStats.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/SearchListingStatsRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/SearchListingStatsResponse.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingStatsService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingStatsEndpointTest.kt`

**Interfaces:**
- Produces: `ListingStats(listingId, interested, upcomingVisits, completedVisits)` (visits are 0 until Task 3), `SearchListingStatsRequest(listingIds)`, `SearchListingStatsResponse(stats)`, `ListingStatsService.search(listingIds: List<Long>): List<ListingStats>`.

- [ ] **Step 1: Write the failing test** — create `ListingStatsEndpointTest`, reusing the visit world (it already has T_USER/T_MEMBERSHIP for user 11 → agent 20, the listings, and the matches):

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql"])
class ListingStatsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `interested clients - one row per id, in request order, zeros included`() {
        val stats = stats("/v1/listings/stats?listing-id=101&listing-id=104&listing-id=102&listing-id=101")

        assertEquals(listOf(101L, 104L, 102L), stats.map { it.listingId })
        assertEquals(listOf(2, 0, 1), stats.map { it.interested }) // 101: matches 1 & 5, 104: PROPOSED only, 102: match 2
    }

    @Test
    fun `another agent's listing`() {
        val response = rest.getForEntity("/v1/listings/stats?listing-id=109", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `another tenant's listing`() {
        val response = rest.getForEntity("/v1/listings/stats?listing-id=901", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no listing id`() {
        val response = rest.getForEntity("/v1/listings/stats", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/listings/stats?listing-id=101", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun stats(url: String) =
        rest.getForEntity(url, SearchListingStatsResponse::class.java).body!!.stats
}
```

- [ ] **Step 2: Run it and watch it fail** — `cd modules/ndopify-dto && mvn install` then `cd modules/ndopify-server && mvn test -Dtest=ListingStatsEndpointTest` → FAIL (unknown types / 404 on `/stats`).

- [ ] **Step 3: Implement the DTOs**

`ListingStats.kt`:

```kotlin
data class ListingStats(
    val listingId: Long = -1,
    val interested: Int = 0,
    val upcomingVisits: Int = 0,
    val completedVisits: Int = 0,
)
```

`SearchListingStatsRequest.kt`:

```kotlin
data class SearchListingStatsRequest(
    @param:BindParam("listing-id") val listingIds: List<Long> = emptyList(),
)
```

`SearchListingStatsResponse.kt`:

```kotlin
data class SearchListingStatsResponse(
    val stats: List<ListingStats> = emptyList(),
)
```

- [ ] **Step 4: Implement the aggregate query** — in `MatchRepository.kt`, above the interface:

```kotlin
/** Number of matches of a listing in a given status. */
interface ListingMatchCount {
    val listingId: Long
    val total: Long
}
```

and in the interface:

```kotlin
@Query(
    "SELECT m.listing.id AS listingId, COUNT(m) AS total FROM MatchEntity m " +
        "WHERE m.listing.id IN :listingIds AND m.status = :status GROUP BY m.listing.id"
)
fun countByListingIdsAndStatus(
    listingIds: Collection<Long>,
    status: MatchStatus,
): List<ListingMatchCount>
```

- [ ] **Step 5: Implement `ListingStatsService`**

```kotlin
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
```

(Task 3 adds `visitDao`, `policy` and `clock` to this constructor and the visit counts to `search`.)

- [ ] **Step 6: Add the endpoint** — inject `ListingStatsService` into `ListingEndpoints` and add, **before** the `get` mapping:

```kotlin
@GetMapping("/stats")
fun stats(@ModelAttribute request: SearchListingStatsRequest): SearchListingStatsResponse {
    if (request.listingIds.isEmpty()) {
        throw BadRequestException(
            Error(
                code = ErrorCode.HTTP_MISSING_PARAMETER,
                parameter = Parameter(name = "listing-id", type = ParameterType.PARAMETER_TYPE_QUERY),
            )
        )
    }
    request.listingIds.distinct().forEach { id ->
        accessService.ensureIsAgent(service.findById(id))
    }
    return SearchListingStatsResponse(stats = statsService.search(request.listingIds))
}
```

- [ ] **Step 7: Run the test to verify it passes** — `cd modules/ndopify-server && mvn test -Dtest=ListingStatsEndpointTest` → PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/{ListingStats,SearchListingStatsRequest,SearchListingStatsResponse}.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingStatsService.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingStatsEndpointTest.kt
git commit -m "feat(listing): list the clients a listing interested"
```

---

### Task 3: `GET /v1/listings/stats` — visits

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingStatsService.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V10_1__visit-listing-index.sql`
- Create: `modules/ndopify-server/src/test/resources/db/test/visit/ListingStatsVisits.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingStatsEndpointTest.kt`

**Interfaces:**
- Consumes: Task 2's endpoint and DTOs.
- Produces: `VisitRepository.countUpcomingByListingIds(...)`, `countByListingIdsAndStatus(...)`, and the visit fields in `ListingStats`.

- [ ] **Step 1: Add the failing tests** — in `ListingStatsEndpointTest`, add `"/db/test/visit/ListingStatsVisits.sql"` to `@Sql` and:

```kotlin
@Test
fun `visits - upcoming, completed, stale and closed`() {
    val stats = stats("/v1/listings/stats?listing-id=101&listing-id=102&listing-id=103")

    assertEquals(3, stats[0].upcomingVisits) // fresh request, future confirmation, checked in
    assertEquals(1, stats[0].completedVisits)
    assertEquals(0, stats[1].upcomingVisits)
    assertEquals(0, stats[1].completedVisits)
    assertEquals(0, stats[2].upcomingVisits)
    assertEquals(1, stats[2].completedVisits)
}
```

- [ ] **Step 2: Run it and watch it fail** — `cd modules/ndopify-server && mvn test -Dtest=ListingStatsEndpointTest` → FAIL (visit counts are 0).

- [ ] **Step 3: Create the fixture** — `ListingStatsVisits.sql` (7101–7103 upcoming, 7104 completed, 7105–7109 stale/closed on listing 101; 7110 completed on 103; 7111 another tenant's, never counted for tenant 1):

```sql
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, created_at
) VALUES
    (7101, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', NOW(), NULL, NOW()),
    (7102, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW()),
    (7103, 1, 5, 2, 101, 20, 11, 'CHECKED_IN', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW()),
    (7104, 1, 5, 2, 101, 20, 11, 'COMPLETED', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7105, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'),
    (7106, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7107, 1, 1, 1, 101, 20, 10, 'CANCELLED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'),
    (7108, 1, 1, 1, 101, 20, 10, 'NO_SHOW', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7109, 1, 1, 1, 101, 20, 10, 'EXPIRED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'),
    (7110, 1, 3, 1, 103, 20, 10, 'COMPLETED', 'ON_SITE', '2000-01-01 09:00:00', '2000-01-01 09:00:00', '2000-01-01 09:00:00'),
    (7111, 2, 9, 9, 901, 90, 90, 'CONFIRMED', 'ON_SITE', NOW(), '2099-01-01 09:00:00', NOW());
```

- [ ] **Step 4: Add the index** — `V10_1__visit-listing-index.sql`:

```sql
CREATE INDEX I_VISIT_listing_status ON T_VISIT(listing_id, status);
```

- [ ] **Step 5: Implement the two aggregate queries** — in `VisitRepository.kt`:

```kotlin
/** Number of visits of a listing that count, by kind. */
interface ListingVisitCount {
    val listingId: Long
    val total: Long
}

@Query(
    "SELECT v.listingId AS listingId, COUNT(v) AS total FROM VisitEntity v " +
        "WHERE v.listingId IN :listingIds AND (" +
        "(v.status = :requested AND v.createdAt > :expireBefore) OR " +
        "(v.status = :confirmed AND v.scheduledAt >= :checkInBefore) OR " +
        "v.status = :checkedIn) " +
        "GROUP BY v.listingId"
)
fun countUpcomingByListingIds(
    listingIds: Collection<Long>,
    requested: VisitStatus,
    confirmed: VisitStatus,
    checkedIn: VisitStatus,
    expireBefore: Date,
    checkInBefore: Date,
): List<ListingVisitCount>

@Query(
    "SELECT v.listingId AS listingId, COUNT(v) AS total FROM VisitEntity v " +
        "WHERE v.listingId IN :listingIds AND v.status = :status GROUP BY v.listingId"
)
fun countByListingIdsAndStatus(
    listingIds: Collection<Long>,
    status: VisitStatus,
): List<ListingVisitCount>
```

- [ ] **Step 6: Fill the visit counts in `ListingStatsService.search`**

```kotlin
val now = clock.millis()
val upcoming = visitDao.countUpcomingByListingIds(
    ids,
    VisitStatus.REQUESTED,
    VisitStatus.CONFIRMED,
    VisitStatus.CHECKED_IN,
    Date(now - policy.expireAfter.toMillis()),
    Date(now - policy.checkInAfter.toMillis()),
).associate { it.listingId to it.total }
val completed = visitDao.countByListingIdsAndStatus(ids, VisitStatus.COMPLETED)
    .associate { it.listingId to it.total }

return ids.map { id ->
    ListingStats(
        listingId = id,
        interested = interested[id]?.toInt() ?: 0,
        upcomingVisits = upcoming[id]?.toInt() ?: 0,
        completedVisits = completed[id]?.toInt() ?: 0,
    )
}
```

- [ ] **Step 7: Run the tests to verify they pass** — `cd modules/ndopify-server && mvn test -Dtest='ListingStatsEndpointTest,SearchVisitEndpointTest,ResetDBTest'` → PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingStatsService.kt \
  modules/ndopify-server/src/main/resources/db/migration/common/V10_1__visit-listing-index.sql \
  modules/ndopify-server/src/test/resources/db/test/visit/ListingStatsVisits.sql \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingStatsEndpointTest.kt
git commit -m "feat(listing): count a listing's visits"
```

---

### Task 4: NPC client and view model

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitStats.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingStatsView.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingStatsViewTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitStatsTest.kt`

**Interfaces:**
- Produces:
  - `NdListingClient.stats(listingIds: List<Long>): Map<Long, ListingStats>` (no call without an id).
  - `NdVisitClient.searchByListings(agentId: Long, statuses: List<VisitStatus>, listingIds: List<Long>, limit: Int = 100)`.
  - `VisitStats.totalLabel: String`.
  - `ListingStatsView(interest: String, visits: VisitStats?)` with `label` and `of(stats: ListingStats?)`.

- [ ] **Step 1: Write the failing client tests**

In `NdListingClientTest`:

```kotlin
@Test
fun stats() {
    server.expect(requestTo("http://localhost:8080/v1/listings/stats?listing-id=501&listing-id=502"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                json(SearchListingStatsResponse(listOf(ListingStats(501, 2, 1, 2)))),
                MediaType.APPLICATION_JSON,
            ),
        )

    val stats = client.stats(listOf(501, 502))

    assertEquals(2, stats[501]?.interested)
    assertNull(stats[502])
    server.verify()
}
```

In `NdVisitClientTest`:

```kotlin
@Test
fun `search by listings`() {
    server.expect(
        requestTo(
            "http://localhost:8080/v1/visits?agent-id=20&listing-id=501&listing-id=502" +
                "&status=REQUESTED&status=COMPLETED&limit=100",
        ),
    ).andRespond(withSuccess("""{"visits":[]}""", MediaType.APPLICATION_JSON))

    assertEquals(emptyList(), client.searchByListings(20, listOf(VisitStatus.REQUESTED, VisitStatus.COMPLETED), listOf(501, 502)))
    server.verify()
}
```

- [ ] **Step 2: Write the failing view tests**

`ListingStatsViewTest`:

```kotlin
@Test
fun label() {
    assertEquals("2 personnes intéressées · 3 visites", ListingStatsView.of(ListingStats(501, 2, 1, 2))?.label)
    assertEquals("1 personne intéressée · 1 visite", ListingStatsView.of(ListingStats(501, 1, 0, 1))?.label)
    assertEquals(
        "Aucune personne intéressée pour l'instant · Aucune visite",
        ListingStatsView.of(ListingStats(501, 0, 0, 0))?.label,
    )
    assertEquals("2 personnes intéressées", ListingStatsView(MatchRules.interest(2), null).label)
    assertNull(ListingStatsView.of(null))
}
```

`VisitStatsTest`:

```kotlin
@Test
fun `total label`() {
    assertEquals("Aucune visite", VisitStats(0, 0).totalLabel)
    assertEquals("1 visite", VisitStats(1, 0).totalLabel)
    assertEquals("3 visites", VisitStats(2, 1).totalLabel)
}
```

- [ ] **Step 3: Run them and watch them fail** — `cd modules/ndopify-partner-central && mvn test -Dtest='NdListingClientTest,NdVisitClientTest,ListingStatsViewTest,VisitStatsTest'` → FAIL (types/methods missing).

- [ ] **Step 4: Implement the clients**

`NdListingClient.stats`:

```kotlin
/** How each of [listingIds] is doing: the clients it interested and its visits. One call; none without an id. */
fun stats(listingIds: List<Long>): Map<Long, ListingStats> {
    if (listingIds.isEmpty()) return emptyMap()
    return rest.get()
        .uri { uri ->
            listingIds.forEach { uri.queryParam("listing-id", it) }
            uri.path("/v1/listings/stats").build()
        }
        .retrieve()
        .body<SearchListingStatsResponse>()
        ?.stats
        ?.associateBy { it.listingId }
        ?: emptyMap()
}
```

`NdVisitClient.searchByListings`:

```kotlin
/** The visits of [agentId] in [statuses] of the listings [listingIds], one `listing-id` parameter each. */
fun searchByListings(
    agentId: Long,
    statuses: List<VisitStatus>,
    listingIds: List<Long>,
    limit: Int = 100,
): List<VisitSummary> = find(agentId, statuses, limit, null, emptyList(), listingIds)
```

Extend the private `find(...)` with a `listingIds: List<Long>` parameter (after `matchIds`) and, in the URI builder, add `listingIds.forEach { uri.queryParam("listing-id", it) }`; update the two existing callers to pass `emptyList()`.

- [ ] **Step 5: Implement the view model** — `ListingStatsView.kt`:

```kotlin
/** A published listing's activity: who it interested and its visits. */
data class ListingStatsView(val interest: String, val visits: VisitStats?) {
    /** "2 personnes intéressées · 3 visites"; the visits are left out when they can't be read. */
    val label: String get() = listOfNotNull(interest, visits?.totalLabel).joinToString(" · ")

    companion object {
        /** Null when the stats can't be had: the line is then left out. */
        fun of(stats: ListingStats?): ListingStatsView? = stats?.let {
            ListingStatsView(
                interest = MatchRules.interest(it.interested),
                visits = VisitStats(it.upcomingVisits, it.completedVisits),
            )
        }
    }
}
```

`VisitStats.totalLabel`:

```kotlin
/** "Aucune visite", "1 visite", "3 visites": the total alone, for a listing's activity line. */
val totalLabel: String
    get() = if (total == 0) "Aucune visite" else "$total " + plural(total, "visite", "visites")
```

- [ ] **Step 6: Run the tests to verify they pass** — `cd modules/ndopify-partner-central && mvn test -Dtest='NdListingClientTest,NdVisitClientTest,ListingStatsViewTest,VisitStatsTest'` → PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClient.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitStats.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingStatsView.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdVisitClientTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingStatsViewTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitStatsTest.kt
git commit -m "feat(npc): read a listing's activity in one call"
```

---

### Task 5: Espace agent — the mandate cards

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt`

**Interfaces:**
- Consumes: `NdListingClient.stats`, `ListingStatsView`.
- Produces: `MandateCardView.stats: String?`, `MandateCards.of(..., stats: Map<Long, ListingStats>?)`, `HomeController.listingStats(listings)`.

- [ ] **Step 1: Write the failing tests** — in `HomeControllerTest`, replace the interest tests with:

```kotlin
@Test
fun `published listings - one stats search`() {
    active(landlord to null, landlord.copy(id = 206) to null, landlord.copy(id = 207) to null)
    doReturn(
        listOf(
            ListingSummary(id = 501, mandateId = 205, status = ListingStatus.PUBLISHED),
            ListingSummary(id = 502, mandateId = 206, status = ListingStatus.PUBLISHED),
            ListingSummary(id = 503, mandateId = 207, status = ListingStatus.DRAFT),
        ),
    ).whenever(listingClient).searchByMandates(any(), any())
    doReturn(
        mapOf(501L to ListingStats(501, 2, 1, 2), 502L to ListingStats(502, 0, 0, 0)),
    ).whenever(listingClient).stats(listOf(501L, 502L))

    val result = mvc.perform(get("/")).andReturn()

    verify(listingClient).stats(listOf(501L, 502L))
    val mandates = mandates(result)
    assertEquals("2 personnes intéressées · 3 visites", mandates[0].stats)
    assertEquals("Aucune personne intéressée pour l'instant · Aucune visite", mandates[1].stats)
    assertNull(mandates[2].stats)
}

@Test
fun `no published listing - no stats search`() {
    active(landlord to null)
    doReturn(listOf(ListingSummary(id = 501, mandateId = 205, status = ListingStatus.DRAFT)))
        .whenever(listingClient).searchByMandates(any(), any())

    val result = mvc.perform(get("/")).andReturn()

    verify(listingClient, never()).stats(any())
    assertNull(mandates(result).single().stats)
}

@Test
fun `stats unavailable - cards kept without the line`() {
    active(landlord to null)
    doReturn(listOf(ListingSummary(id = 501, mandateId = 205, status = ListingStatus.PUBLISHED)))
        .whenever(listingClient).searchByMandates(any(), any())
    doThrow(ResourceAccessException("timeout")).whenever(listingClient).stats(any())

    val result = mvc.perform(get("/")).andReturn()

    assertNull(mandates(result).single().stats)
    assertEquals("/listings/501", mandates(result).single().listing?.url)
}
```

(The existing `active(...)`, `landlord` and `mandates(result)` helpers are in `HomeControllerTest`; the old `interest` tests are replaced, not added next to them.)

In `MandateCardsTest`, update the `approved` case and the `listed(...)` helper (its `interest: Map<Long, Int>?` becomes `stats: Map<Long, ListingStats>?`), and replace the `listing published - interest` test:

```kotlin
val card = MandateCards.of(
    mandate, CONTRACT_SUMMARY,
    mapOf(201L to ListingSummary(id = 501, mandateId = 201, status = ListingStatus.PUBLISHED)),
    NAMES, NOW, FORMATTER,
    stats = mapOf(501L to ListingStats(501, 2, 0, 3)),
)

assertEquals("2 personnes intéressées · 3 visites", card.stats)

// listing published - activity
assertEquals("2 personnes intéressées · 3 visites", listed(published, mapOf(501L to ListingStats(501, 2, 1, 2))).stats)
assertEquals(
    "Aucune personne intéressée pour l'instant · Aucune visite",
    listed(published, mapOf(501L to ListingStats(501, 0, 0, 0))).stats,
)
assertNull(listed(published, mapOf(502L to ListingStats(502, 2, 0, 0))).stats) // No row for 501
assertNull(listed(published, null).stats) // Unavailable
assertNull(listed(published.copy(status = ListingStatus.DRAFT), mapOf(501L to ListingStats(501, 2, 0, 0))).stats)
assertNull(listed(null).stats)
```

- [ ] **Step 2: Run them and watch them fail** — `cd modules/ndopify-partner-central && mvn test -Dtest='HomeControllerTest,MandateCardsTest'` → FAIL.

- [ ] **Step 3: Implement `HomeController`** — replace the `interest(...)` helper with:

```kotlin
/**
 * How each PUBLISHED listing is doing (its interested clients and visits), by listing id, in one call; null when it
 * can't be had: the cards then show no activity line.
 */
private fun listingStats(listings: Collection<ListingSummary>): Map<Long, ListingStats>? {
    val ids = listings.filter { it.status == ListingStatus.PUBLISHED }.map { it.id }
    if (ids.isEmpty()) return emptyMap()
    return try {
        listingClient.stats(ids)
    } catch (ex: RestClientException) {
        LOGGER.warn("No stats for the listings ${ids.joinToString()}: ${ex.message}")
        null
    }
}
```

Replace `val interest = listings?.let { interest(it.values) }` with `val stats = listings?.let { listingStats(it.values) }`, and pass `stats` to `MandateCards.of` (last parameter).

- [ ] **Step 4: Implement the card view** — `MandateCardView.interest` becomes `val stats: String? = null`. In `MandateCards.of`, replace the `interest` parameter with `stats: Map<Long, ListingStats>? = null`, and compute:

```kotlin
val listingStats = listing?.takeIf { it.status == ListingStatus.PUBLISHED && MandateRules.listed(mandate) }
    ?.let { stats?.get(it.id) }
    ?.let { ListingStatsView.of(it) }
```

then `stats = listingStats?.label` in the returned view.

- [ ] **Step 5: Update the template and CSS** — in `templates/index.html`, replace the `mandate-card__interest` block with:

```html
<p class="listing-stats" th:if="${m.stats}" data-listing-stats>
    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 20h18"/><path d="M6 20v-6M12 20V8M18 20V4"/></svg>
    <span><span class="visually-hidden">Activité de l'annonce : </span><span th:text="${m.stats}">2 personnes intéressées · 3 visites</span></span>
</p>
```

In `resources/public/assets/css/home.css`, rename `.mandate-card__interest` to `.listing-stats` and use `align-items: flex-start; gap: var(--space-1) var(--space-2);` (mirror the prototype).

- [ ] **Step 6: Update the template test** — in `HomeTemplateTest`, replace the `[data-listing-interest]` assertions, mock `listingClient.stats(...)`, render a published listing with stats and assert:

```kotlin
assertEquals(
    "2 personnes intéressées · 3 visites",
    page.selectFirst("[data-listing-stats]")?.text(),
)
```

and a card without stats has no `[data-listing-stats]`.

- [ ] **Step 7: Run the tests to verify they pass** — `cd modules/ndopify-partner-central && mvn test -Dtest='HomeControllerTest,HomeTemplateTest,MandateCardsTest'` → PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt \
  modules/ndopify-partner-central/src/main/resources/templates/index.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt
git commit -m "feat(npc): show a listing's activity on the mandate cards"
```

---

### Task 6: Mandate page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: `ListingStatsView`, `NdVisitClient.searchByListings`, the existing `ListingClientsView.stats` (`VisitStats?`).
- Produces: `ListingClientsView.interested: Int`; `ListingViews.forMandate(..., stats: ListingStatsView? = null)`; `MandateListingView.stats: String?`.

- [ ] **Step 1: Write the failing tests** — in `MandateControllerTest`, update `approved landlord - published listing - clients interested` to return ACCEPTED matches and mock the visits, then assert the combined label:

```kotlin
doReturn(
    listOf(
        Match(id = 1, listingId = 501, status = MatchStatus.ACCEPTED),
        Match(id = 2, listingId = 501, status = MatchStatus.ACCEPTED),
    ),
).whenever(matchClient).searchByListings(any(), any(), any())
doReturn(listOf(VisitFixtures.confirmed(), VisitFixtures.completed()))
    .whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

val view = model(mvc.perform(get("/mandates/201")).andReturn())["mandateListing"] as MandateListingView

assertEquals("2 personnes intéressées · 2 visites", view.stats)
```

Add the visits-unavailable case:

```kotlin
@Test
fun `approved landlord - published listing - visits unavailable, interest still shown`() {
    // The matches load, the visit search throws: the line keeps the interested clients only.
    assertEquals("2 personnes intéressées", view.stats)
}
```

In `ListingViewsTest`, assert `view.interested` (the count of ACCEPTED matches) and keep the existing `view.stats?.label` assertions. `publishedListing()` in the template test must also mock `visitClient.searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)`.

- [ ] **Step 2: Run them and watch them fail** — `cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,ListingViewsTest'` → FAIL.

- [ ] **Step 3: Add `interested` to `ListingClientsView` and compute it in `ListingViews.clients`**

```kotlin
/** The clients who accepted it (its ACCEPTED matches), including another agent's. */
val interested: Int,
```

and in `clients(...)`: `interested = matches.count { it.status == MatchStatus.ACCEPTED }`.

- [ ] **Step 4: Use the new visit filter** — in `MandateController.listingClients`, replace `visitClient.searchByMatches(agentId, VisitRules.COUNTED, matches.map { it.id }, limit = matches.size)` with:

```kotlin
visitClient.searchByListings(agentId, VisitRules.COUNTED, listOf(listing.id), limit = 100)
```

- [ ] **Step 5: Build the listing line** — in `MandateController.show`, after `listingClients`:

```kotlin
val clients = listing?.let { listingClients(it) }
val listingStats = clients?.let {
    ListingStatsView(MatchRules.interest(it.interested), it.stats ?: visitStats)
}
model.addAttribute("listingVisits", clients?.visits)
model.addAttribute("listingProposals", clients?.proposals)
model.addAttribute("visitStats", if (listingStats?.visits != null) null else visitStats)
```

Pass `listingStats` to `ListingViews.forMandate(...)` instead of the old `interested(...)` value, and delete the now-unused `interested(listing)` helper.

In `ListingViews.forMandate`, replace `interested: Int?` with `stats: ListingStatsView?`, and set `stats = if (listing?.status == ListingStatus.PUBLISHED) stats?.label else null`; rename `MandateListingView.interest` to `stats`.

- [ ] **Step 6: Update the template** — in `templates/mandate.html`, replace the `ml.interest` block with the same `listing-stats` markup as Task 5, using `ml.stats`; keep the WIP `visitStats` line as is (it now disappears when the listing line is shown).

- [ ] **Step 7: Add the template tests** — in `MandateTemplateTest`:

```kotlin
assertEquals("Aucune personne intéressée pour l'instant · Aucune visite", box.selectFirst("[data-listing-stats]")?.text())
assertNull(page.selectFirst("[data-mandate-visit-stats]")) // no duplicate visits line
```

for the existing `approved - landlord with listing published - the line only` test (no matches mocked). In a test using `publishedListing()` plus one confirmed and one completed visit, assert `"2 personnes intéressées · 2 visites"`. For a search mandate (or a draft listing), `[data-listing-stats]` is absent and `[data-mandate-visit-stats]` is present.

- [ ] **Step 8: Run the tests to verify they pass** — `cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,MandateTemplateTest,ListingViewsTest'` → PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
  modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): show a listing's activity on its mandate page"
```

---

### Task 7: Listing page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: `NdListingClient.stats`, `ListingStatsView`.
- Produces: `ListingView.stats: String?`, `ListingViews.of(..., stats: ListingStats? = null)`.

- [ ] **Step 1: Write the failing tests** — in `ListingControllerTest`:

```kotlin
@Test
fun `published listing - activity line`() {
    doReturn(LISTING.copy(status = ListingStatus.PUBLISHED, publishedAt = Date()))
        .whenever(listingClient).get(501)
    doReturn(mapOf(501L to ListingStats(501, 2, 1, 2))).whenever(listingClient).stats(listOf(501L))

    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertEquals("2 personnes intéressées · 3 visites", view.stats)
}

@Test
fun `draft listing - no activity line`() {
    val view = model(mvc.perform(get("/listings/501")).andReturn())["listing"] as ListingView

    assertNull(view.stats)
    verify(listingClient, never()).stats(any())
}
```

In `ListingTemplateTest`, assert `[data-listing-stats]` text for a PUBLISHED listing and its absence for a draft.

- [ ] **Step 2: Run them and watch them fail** — `cd modules/ndopify-partner-central && mvn test -Dtest='ListingControllerTest,ListingTemplateTest'` → FAIL.

- [ ] **Step 3: Read the stats** — in `ListingController.show`, after the images:

```kotlin
val stats = if (listing.status == ListingStatus.PUBLISHED) {
    lookup("stats of listing ${listing.id}") { listingClient.stats(listOf(listing.id)) }?.get(listing.id)
} else {
    null
}
```

Pass it to `ListingViews.of(listing, images, locations(listing), formatter(), tenantContext.getTenant().publicPortalUrl, stats)`.

- [ ] **Step 4: Build the view label** — add `val stats: String? = null` to `ListingView`; add `stats: ListingStats? = null` as the last parameter of `ListingViews.of`, and set `stats = if (published) ListingStatsView.of(stats)?.label else null`.

- [ ] **Step 5: Update the template** — in `templates/listing.html`, under the `data-listing-public-url` line:

```html
<p class="listing-stats" th:if="${l.stats}" data-listing-stats>
    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 20h18"/><path d="M6 20v-6M12 20V8M18 20V4"/></svg>
    <span><span class="visually-hidden">Activité de l'annonce : </span><span th:text="${l.stats}">2 personnes intéressées · 3 visites</span></span>
</p>
```

- [ ] **Step 6: Run the tests to verify they pass** — `cd modules/ndopify-partner-central && mvn test -Dtest='ListingControllerTest,ListingTemplateTest,ListingViewsTest'` → PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingController.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
  modules/ndopify-partner-central/src/main/resources/templates/listing.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): show a listing's activity on its page"
```

---

### Task 8: Docs and full verification

**Files:**
- Modify: `modules/ndopify-dto/README.md` (the `listing` row)
- Modify: `CLAUDE.md` or `README.md` only if the domain summary needs it

- [ ] **Step 1: Update the DTO module doc** — in `modules/ndopify-dto/README.md`, list `ListingStats`, `SearchListingStatsRequest` and `SearchListingStatsResponse` with the other `listing` DTOs, and say the endpoint aggregates matches/visits. No code.

- [ ] **Step 2: Format** — `mvn antrun:run@ktlint-format` at the root; then `mvn validate` → no ktlint violation.

- [ ] **Step 3: Run the server suite** — `cd modules/ndopify-server && mvn verify` → BUILD SUCCESS, including `ListingStatsEndpointTest`, `SearchVisitEndpointTest`, `ResetDBTest` and the coverage gate.

- [ ] **Step 4: Run the Partner Central suite** — `cd modules/ndopify-partner-central && mvn verify` → BUILD SUCCESS, including the client, controller and template tests.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/README.md
git commit -m "docs(dto): document the listing stats endpoint"
```

---

## Self-Review

- **Spec coverage:** endpoint + DTOs (Tasks 2–3), `listingIds` filter (Task 1), NPC client/view model (Task 4), home (Task 5), mandate page (Task 6), listing page (Task 7), docs/verification (Task 8). Views are explicitly out of scope.
- **Review Focus covered:** zeros (Task 2 test and Task 5 test), stale visits (Task 3 fixture/test), wrong listing (Task 2 tests), duplicate ids (Task 2 test), unavailable stats (Tasks 5/7 tests), unpublished listing hidden (Tasks 5/7 tests and Task 6 template test).
- **Type consistency:** `ListingStats` fields are used with the same names in the service, the client and `ListingStatsView`; `ListingStatsView` is the only UI wrapper; `VisitStats.totalLabel` is the only new label.
