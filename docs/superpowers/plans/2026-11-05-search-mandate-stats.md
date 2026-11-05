# Search Mandate Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /v1/mandates/stats`, use it to draw the one-line activity summary `12 biens · 1 intéressé · 3 visites`, and remove the mandate card's next step so the home page stops loading matches and contracts.

**Architecture:** `ndopify-server` aggregates the mandate's matches and visits in SQL and returns one `MandateStats` row per requested id, mirroring `GET /v1/listings/stats`. `ndopify-partner-central` reads that batch once for its approved searches; its home page no longer calls `GET /v1/matches` or `GET /v1/contracts`, and its visit read stays open-only. The mandate page keeps its match and visit reads because it renders their rows, and builds the same line from them.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Thymeleaf, Jsoup template tests, Mockito, Maven, `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-11-05-search-mandate-stats-design.md`

## Global Constraints

- **Only an `APPROVED` `SEARCH` mandate** shows the line. No line before signature, for a `LISTING` mandate, or when the stats can't be read.
- **`12 biens`** counts `MatchStatus.ACTIVE`, `PROPOSED`, `ACCEPTED` and `EXPIRED`. Never count `REJECTED`, `DISMISSED`, `SHORTLISTED` or `UNKNOWN`.
- **`1 intéressé`** counts `MatchStatus.ACCEPTED`.
- **`3 visites`** is upcoming + completed, with the same windows as `ListingStatsService`: fresh `REQUESTED`, future `CONFIRMED` and `CHECKED_IN` are upcoming; `COMPLETED` is completed.
- **Exact copy:** `12 biens · 1 intéressé · 3 visites`; separator `" · "`. Empty: `Aucun bien correspondant pour l'instant`. Singular: `1 bien`, `1 intéressé`, `1 visite`.
- **One line:** `white-space: nowrap`; `font-size: var(--step-hint)` (14px); 13px below 360px. The home link keeps `min-height: var(--target)` (44px).
- **Home card:** the line replaces `m.matches` and `m.proposals`; there is no next step any more. **Mandate page:** the line goes in the *Biens correspondants* header only when `properties > 0`; the detailed proposal line, visit breakdown and match cards stay.
- **Home visits read:** `visitClient.search(agent.id, VisitRules.OPEN)` — open visits only, for the *Visites* section. The stats endpoint supplies the visit counts.
- **Endpoint:** `GET /v1/mandates/stats?mandate-id=…`, one row per distinct id in request order, zeros included.
- Run `mvn test` in the changed module; before the final commit run `mvn verify` in `ndopify-partner-central`.
- Commit after each task.

## Review Focus

- **Stats unavailable:** the home card still renders, without the activity line.
- **No matches:** the home card shows `Aucun bien correspondant pour l'instant`; the mandate page hides the line and keeps its empty state.
- **Refused / dismissed / shortlisted:** never counted in `12 biens`.
- **Another agent's mandate:** `GET /v1/mandates/stats` answers 403, not another agent's numbers.
- **No leftover loads:** the home page makes no `GET /v1/matches` and no `GET /v1/contracts` call.

---

### Task 1: Server — `GET /v1/mandates/stats`

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/MandateStats.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/SearchMandateStatsRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/SearchMandateStatsResponse.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateStatsService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateStatsEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/mandate/MandateStatsEndpoint.sql`

**Interfaces:**
- Produces: `MandateStats(mandateId: Long = -1, properties: Int = 0, interested: Int = 0, upcomingVisits: Int = 0, completedVisits: Int = 0)`, `SearchMandateStatsRequest(mandateIds)`, `SearchMandateStatsResponse(stats)`, `MandateStatsService.search(mandateIds: List<Long>): List<MandateStats>`, `MandateService.findAllByIds(ids: Collection<Long>): List<MandateEntity>`.

- [ ] **Step 1: Write the failing endpoint test**

Create the fixture self-contained, with the same tenant/user/agent/customer/location/auth rows
as `db/test/match/SearchMatchEndpoint.sql`, its listings (so the match rows have a listing), then:

- mandates: `1` and `3` are agent 20's `APPROVED` `SEARCH`; `2` belongs to agent 30; `9` belongs to tenant 2.
- matches: on mandate 1, one each of `ACTIVE`, `PROPOSED`, `ACCEPTED`, `EXPIRED`, `REJECTED`, `DISMISSED`, `SHORTLISTED`; on mandate 3, one `REJECTED` (so it has no counted match, but its completed visit has a match); on mandate 2, one `ACCEPTED`; on mandate 9, one `ACTIVE`.
- visits: on mandate 1, a fresh `REQUESTED` (`created_at = NOW()`), a future `CONFIRMED`, a `CHECKED_IN`, a `COMPLETED`, a stale `REQUESTED` (`created_at = '2000-01-01 09:00:00'`), a stale `CONFIRMED`, and a `CANCELLED`; on mandate 3, one `COMPLETED`; on mandate 2, a future `CONFIRMED`; on mandate 9, a future `CONFIRMED`.

```kotlin
package com.wutsi.ndopify.mandate.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.mandate.dto.SearchMandateStatsResponse
import org.springframework.http.HttpStatus
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/MandateStatsEndpoint.sql"])
class MandateStatsEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @Test
    fun `one row per id, in request order, zeros included`() {
        val stats = stats("/v1/mandates/stats?mandate-id=1&mandate-id=3&mandate-id=1")

        assertEquals(listOf(1L, 3L), stats.map { it.mandateId })
        assertEquals(listOf(4, 0), stats.map { it.properties })
        assertEquals(listOf(1, 0), stats.map { it.interested })
        assertEquals(listOf(3, 0), stats.map { it.upcomingVisits })
        assertEquals(listOf(1, 1), stats.map { it.completedVisits })
    }

    @Test
    fun `another agent's mandate`() {
        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=2", ErrorResponse::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(ErrorCode.MANDATE_ACCESS_DENIED, response.body?.error?.code)
    }

    @Test
    fun `another tenant's mandate`() {
        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=9", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `gone mandate`() {
        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=999", ErrorResponse::class.java)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.MANDATE_NOT_FOUND, response.body?.error?.code)
    }

    @Test
    fun `no mandate id`() {
        val response = rest.getForEntity("/v1/mandates/stats", ErrorResponse::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.HTTP_MISSING_PARAMETER, response.body?.error?.code)
    }

    @Test
    fun anonymous() {
        anonymousUser = true

        val response = rest.getForEntity("/v1/mandates/stats?mandate-id=1", ErrorResponse::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    private fun stats(url: String) =
        rest.getForEntity(url, SearchMandateStatsResponse::class.java).body!!.stats
}
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd modules/ndopify-dto && mvn install
cd modules/ndopify-server && mvn test -Dtest=MandateStatsEndpointTest
```

Expected: compilation failure — the DTOs and the endpoint do not exist yet.

- [ ] **Step 3: Add the DTOs**

`MandateStats.kt`:

```kotlin
package com.wutsi.ndopify.mandate.dto

data class MandateStats(
    val mandateId: Long = -1,
    val properties: Int = 0,
    val interested: Int = 0,
    val upcomingVisits: Int = 0,
    val completedVisits: Int = 0,
)
```

`SearchMandateStatsRequest.kt`:

```kotlin
package com.wutsi.ndopify.mandate.dto

import org.springframework.web.bind.annotation.BindParam

data class SearchMandateStatsRequest(
    @param:BindParam("mandate-id") val mandateIds: List<Long> = emptyList(),
)
```

`SearchMandateStatsResponse.kt`:

```kotlin
package com.wutsi.ndopify.mandate.dto

data class SearchMandateStatsResponse(
    val stats: List<MandateStats> = emptyList(),
)
```

- [ ] **Step 4: Add the aggregate queries**

In `MatchRepository.kt`, next to `ListingMatchCount`:

```kotlin
/** Number of matches of a mandate in the given statuses. */
interface MandateMatchCount {
    val mandateId: Long
    val total: Long
}

@Query(
    "SELECT m.mandate.id AS mandateId, COUNT(m) AS total FROM MatchEntity m " +
        "WHERE m.mandate.id IN :mandateIds AND m.status IN :statuses GROUP BY m.mandate.id"
)
fun countByMandateIdsAndStatusIn(
    mandateIds: Collection<Long>,
    statuses: Collection<MatchStatus>,
): List<MandateMatchCount>
```

In `VisitRepository.kt`, next to `ListingVisitCount`:

```kotlin
/** Number of visits of a mandate that count, by kind. */
interface MandateVisitCount {
    val mandateId: Long
    val total: Long
}

@Query(
    "SELECT v.mandateId AS mandateId, COUNT(v) AS total FROM VisitEntity v " +
        "WHERE v.mandateId IN :mandateIds AND (" +
        "(v.status = :requested AND v.createdAt > :expireBefore) OR " +
        "(v.status = :confirmed AND v.scheduledAt >= :checkInBefore) OR " +
        "v.status = :checkedIn) " +
        "GROUP BY v.mandateId"
)
fun countUpcomingByMandateIds(
    mandateIds: Collection<Long>,
    requested: VisitStatus,
    confirmed: VisitStatus,
    checkedIn: VisitStatus,
    expireBefore: Date,
    checkInBefore: Date,
): List<MandateVisitCount>

@Query(
    "SELECT v.mandateId AS mandateId, COUNT(v) AS total FROM VisitEntity v " +
        "WHERE v.mandateId IN :mandateIds AND v.status = :status GROUP BY v.mandateId"
)
fun countByMandateIdsAndStatus(
    mandateIds: Collection<Long>,
    status: VisitStatus,
): List<MandateVisitCount>
```

- [ ] **Step 5: Add `MandateService.findAllByIds`**

```kotlin
fun findAllByIds(ids: Collection<Long>): List<MandateEntity> = dao.findAllById(ids).toList()
```

- [ ] **Step 6: Add `MandateStatsService`**

Mirror `ListingStatsService`, with `MATCHED_STATUSES = listOf(ACTIVE, PROPOSED, ACCEPTED, EXPIRED)`:

```kotlin
@Service
class MandateStatsService(
    private val matchDao: MatchRepository,
    private val visitDao: VisitRepository,
    private val policy: VisitPolicyProperties,
    private val clock: Clock,
) {
    fun search(mandateIds: List<Long>): List<MandateStats> {
        val ids = mandateIds.distinct()
        val pool = matchDao.countByMandateIdsAndStatusIn(ids, MATCHED_STATUSES)
            .associate { it.mandateId to it.total }
        val interested = matchDao.countByMandateIdsAndStatusIn(ids, listOf(MatchStatus.ACCEPTED))
            .associate { it.mandateId to it.total }

        val now = clock.millis()
        val upcoming = visitDao.countUpcomingByMandateIds(
            ids,
            VisitStatus.REQUESTED,
            VisitStatus.CONFIRMED,
            VisitStatus.CHECKED_IN,
            Date(now - policy.expireAfter.toMillis()),
            Date(now - policy.checkInAfter.toMillis()),
        ).associate { it.mandateId to it.total }
        val completed = visitDao.countByMandateIdsAndStatus(ids, VisitStatus.COMPLETED)
            .associate { it.mandateId to it.total }

        return ids.map { id ->
            MandateStats(
                mandateId = id,
                properties = pool[id]?.toInt() ?: 0,
                interested = interested[id]?.toInt() ?: 0,
                upcomingVisits = upcoming[id]?.toInt() ?: 0,
                completedVisits = completed[id]?.toInt() ?: 0,
            )
        }
    }

    private companion object {
        val MATCHED_STATUSES =
            listOf(MatchStatus.ACTIVE, MatchStatus.PROPOSED, MatchStatus.ACCEPTED, MatchStatus.EXPIRED)
    }
}
```

- [ ] **Step 7: Add the endpoint**

In `MandateEndpoints`, inject `MandateStatsService` and add, before `GET /{id}`:

```kotlin
@GetMapping("/stats")
fun stats(@ModelAttribute request: SearchMandateStatsRequest): SearchMandateStatsResponse {
    if (request.mandateIds.isEmpty()) {
        throw BadRequestException(
            error = Error(
                code = ErrorCode.HTTP_MISSING_PARAMETER,
                parameter = Parameter(name = "mandate-id", type = ParameterType.PARAMETER_TYPE_QUERY),
            )
        )
    }
    val ids = request.mandateIds.distinct()
    val mandates = service.findAllByIds(ids)
    if (mandates.size != ids.size) {
        throw NotFoundException(error = Error(code = ErrorCode.MANDATE_NOT_FOUND))
    }
    mandates.forEach { access.ensureCanRead(it) }
    return SearchMandateStatsResponse(stats = statsService.search(ids))
}
```

`/stats` must be declared before `/{id}`, so the literal path wins.

- [ ] **Step 8: Run the tests to verify they pass**

```bash
cd modules/ndopify-dto && mvn install
cd modules/ndopify-server && mvn test -Dtest=MandateStatsEndpointTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/MandateStats.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/SearchMandateStatsRequest.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/SearchMandateStatsResponse.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateStatsEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/mandate/MandateStatsEndpoint.sql
git commit -m "feat(server): add the mandate stats endpoint"
```

---

### Task 2: partner-central — stats client and view

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsView.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsViewTest.kt`

**Interfaces:**
- Produces: `NdMandateClient.stats(mandateIds: List<Long>): Map<Long, MandateStats>`; `SearchStatsView.of(mandate: Mandate, stats: MandateStats?): SearchStatsView?` (null when the stats can't be read); `SearchStatsView.of(mandate: Mandate, matches: List<Match>, visits: VisitStats?): SearchStatsView` (for the mandate page).

- [ ] **Step 1: Write the failing client test**

In `NdMandateClientTest`, add:

```kotlin
@Test
fun stats() {
    server.expect(requestTo("http://localhost:8080/v1/mandates/stats?mandate-id=201&mandate-id=203"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                json(
                    SearchMandateStatsResponse(
                        stats = listOf(
                            MandateStats(201, properties = 3, interested = 1, upcomingVisits = 2, completedVisits = 1),
                            MandateStats(203, properties = 0, interested = 0, upcomingVisits = 0, completedVisits = 0),
                        ),
                    ),
                ),
                MediaType.APPLICATION_JSON,
            )
        )

    val stats = client.stats(listOf(201, 203))

    assertEquals(3, stats[201]?.properties)
    assertEquals(1, stats[201]?.interested)
    assertEquals(0, stats[203]?.properties)
    assertEquals(setOf(201L, 203L), stats.keys)
    server.verify()
}

@Test
fun `stats - no id`() {
    assertTrue(client.stats(emptyList()).isEmpty())
    server.verify()
}
```

- [ ] **Step 2: Write the failing view test**

```kotlin
package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.npc.ui.visit.VisitStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchStatsViewTest {
    private val mandate = Mandate(
        id = 201,
        mandateType = MandateType.SEARCH,
        status = MandateStatus.APPROVED,
    )

    @Test
    fun `label - pool, interest and visits`() {
        val stats = SearchStatsView.of(mandate, MandateStats(201, 12, 1, 1, 2))!!

        assertEquals(12, stats.properties)
        assertEquals(1, stats.interested)
        assertEquals("12 biens · 1 intéressé · 3 visites", stats.label)
        assertEquals("/mandates/201#matches-title", stats.url)
    }

    @Test
    fun `label drops zero segments`() {
        assertEquals("12 biens", SearchStatsView.of(mandate, MandateStats(201, 12, 0, 0, 0))!!.label)
        assertEquals(
            "12 biens · 3 visites",
            SearchStatsView.of(mandate, MandateStats(201, 12, 0, 1, 2))!!.label,
        )
        assertEquals(
            "12 biens · 1 intéressé",
            SearchStatsView.of(mandate, MandateStats(201, 12, 1, 0, 0))!!.label,
        )
    }

    @Test
    fun `label - empty`() {
        assertEquals(
            "Aucun bien correspondant pour l'instant",
            SearchStatsView.of(mandate, MandateStats(201))!!.label,
        )
    }

    @Test
    fun `stats unavailable`() {
        assertNull(SearchStatsView.of(mandate, null))
    }

    @Test
    fun `label - singular`() {
        assertEquals(
            "1 bien · 1 intéressé · 1 visite",
            SearchStatsView.of(mandate, MandateStats(201, 1, 1, 0, 1))!!.label,
        )
    }

    @Test
    fun `mandate page - visits unavailable`() {
        val stats = SearchStatsView.of(mandate, emptyList(), null)

        assertEquals("Aucun bien correspondant pour l'instant", stats.label)
        assertNull(stats.visits)
    }
}
```

- [ ] **Step 3: Run the tests and watch them fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='NdMandateClientTest,SearchStatsViewTest'
```

Expected: compilation failures — `stats`, `MandateStats`, `SearchStatsView` and the DTOs are missing.

- [ ] **Step 4: Implement the client**

In `NdMandateClient`, import `MandateStats` and `SearchMandateStatsResponse`, then:

```kotlin
/** How each of [mandateIds] is doing: its matching pool, interested clients and visits. One call; none without an id. */
fun stats(mandateIds: List<Long>): Map<Long, MandateStats> {
    if (mandateIds.isEmpty()) return emptyMap()
    return rest.get()
        .uri { uri ->
            mandateIds.forEach { uri.queryParam("mandate-id", it) }
            uri.path("/v1/mandates/stats").build()
        }
        .retrieve()
        .body<SearchMandateStatsResponse>()
        ?.stats
        ?.associateBy { it.mandateId }
        ?: emptyMap()
}
```

- [ ] **Step 5: Implement `SearchStatsView`**

```kotlin
package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStats
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.ui.match.MatchRules
import com.wutsi.ndopify.npc.ui.visit.VisitStats

/**
 * A signed search's one-line activity: the current matching pool, the client's interest and the visits on the
 * mandate. See `docs/superpowers/specs/2026-11-05-search-mandate-stats-design.md`.
 */
data class SearchStatsView(
    val mandateId: Long,
    /** Matches still in play: ACTIVE, PROPOSED, ACCEPTED and EXPIRED. */
    val properties: Int,
    /** Matches the client accepted. */
    val interested: Int,
    /** Upcoming + completed visits; null when they can't be read. */
    val visits: Int?,
) {
    /** "12 biens · 1 intéressé · 3 visites", with zero segments left out. */
    val label: String
        get() {
            val parts = listOfNotNull(
                properties.takeIf { it > 0 }?.let { "$it " + plural(it, "bien", "biens") },
                interested.takeIf { it > 0 }?.let { "$it " + plural(it, "intéressé", "intéressés") },
                visits?.takeIf { it > 0 }?.let { "$it " + plural(it, "visite", "visites") },
            )
            return parts.ifEmpty { listOf(EMPTY) }.joinToString(" · ")
        }

    val url: String get() = "/mandates/$mandateId#matches-title"

    companion object {
        const val EMPTY = "Aucun bien correspondant pour l'instant"

        /** The home card: null when the stats can't be read, so the line is left out. */
        fun of(mandate: Mandate, stats: MandateStats?): SearchStatsView? = stats?.let {
            SearchStatsView(
                mandateId = mandate.id,
                properties = it.properties,
                interested = it.interested,
                visits = it.upcomingVisits + it.completedVisits,
            )
        }

        /** The mandate page, from the matches and visits it already loaded. */
        fun of(mandate: Mandate, matches: List<Match>, visits: VisitStats?): SearchStatsView =
            SearchStatsView(
                mandateId = mandate.id,
                properties = matches.count { it.status in MatchRules.ORDER },
                interested = matches.count { it.status == MatchStatus.ACCEPTED },
                visits = visits?.total,
            )

        private fun plural(count: Int, one: String, many: String): String = if (count > 1) many else one
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='NdMandateClientTest,SearchStatsViewTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsView.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsViewTest.kt
git commit -m "feat(npc): read the mandate stats"
```

---

### Task 3: Home card — remove the next step, use the stats endpoint

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateRulesTest.kt`

**Interfaces:**
- Consumes: `MandateStats`, `NdMandateClient.stats`, `SearchStatsView.of(mandate, stats)`.
- Produces: `MandateCardView(id, title, owner, tag, facts, listing, stats, searchStats)`; `MandateCards.of(mandate, listings, locations, formatter, mandateStats, stats)`.

- [ ] **Step 1: Write the failing card tests**

In `MandateCardsTest`, drop `CONTRACT`, `CONTRACT_SUMMARY`, `CONTRACT_2027` and the match helpers. The new helper:

```kotlin
private fun card(
    mandate: Mandate = MANDATE,
    listings: Map<Long, ListingSummary>? = emptyMap(),
    mandateStats: Map<Long, MandateStats>? = null,
    stats: Map<Long, ListingStats>? = null,
) = MandateCards.of(mandate, listings, NAMES, FORMATTER, mandateStats, stats)
```

Keep the facts/title/listing tests, deleting their `nextStep` assertions. Replace the match tests with:

```kotlin
@Test
fun `search stats`() {
    val approved = MANDATE.copy(status = MandateStatus.APPROVED)
    val card = card(
        mandate = approved,
        mandateStats = mapOf(201L to MandateStats(201, properties = 3, interested = 1, upcomingVisits = 1, completedVisits = 2)),
    )

    assertEquals("3 biens · 1 intéressé · 3 visites", card.searchStats?.label)
    assertEquals("/mandates/201#matches-title", card.searchStats?.url)
}

@Test
fun `search stats - unavailable`() {
    assertNull(card(MANDATE.copy(status = MandateStatus.APPROVED), mandateStats = null).searchStats)
}

@Test
fun `search stats - no match`() {
    val card = card(
        mandate = MANDATE.copy(status = MandateStatus.APPROVED),
        mandateStats = mapOf(201L to MandateStats(201)),
    )

    assertEquals("Aucun bien correspondant pour l'instant", card.searchStats?.label)
}

@Test
fun `not an approved search - no stats`() {
    assertNull(card(MANDATE, mandateStats = mapOf(201L to MandateStats(201, 3, 1, 0, 0))).searchStats)
}
```

For the landlord tests, pass `stats = mapOf(...)` as before and keep the `card.stats` assertions. There is no `nextStep` to assert.

- [ ] **Step 2: Write the failing home controller tests**

In `HomeControllerTest`:

- Remove `contractClient` and `matchClient` from the constructor and the imports.
- `active(vararg mandates: Mandate)` only stubs `mandateClient.active`; no contract search.
- Delete `contract without dates`, `contracts unavailable` and `no contract` tests; replace them with:

```kotlin
@Test
fun `mandate stats - one call for the approved searches`() {
    active(approved, landlord, assigned.copy(id = 204, status = MandateStatus.APPROVED))
    doReturn(mapOf(203L to MandateStats(203, 2, 1, 0, 2), 204L to MandateStats(204, 0, 0, 0, 0)))
        .whenever(mandateClient).stats(any())

    val result = mvc.perform(get("/")).andReturn()

    verify(mandateClient).stats(listOf(203L, 204L))
    assertEquals("2 biens · 1 intéressé · 2 visites", mandates(result)[0].searchStats?.label)
    assertEquals("Aucun bien correspondant pour l'instant", mandates(result)[2].searchStats?.label)
}

@Test
fun `mandate stats unavailable - cards kept without the line`() {
    active(approved)
    doThrow(ResourceAccessException("timeout")).whenever(mandateClient).stats(any())

    val result = mvc.perform(get("/")).andReturn()

    assertNull(mandates(result).single().searchStats)
    assertEquals(false, model(result)["mandatesUnavailable"])
}

@Test
fun `no approved search - no mandate stats call`() {
    active(assigned)

    mvc.perform(get("/")).andReturn()

    verify(mandateClient, never()).stats(any())
}
```

In `mandates shown`, change every `active(x to contract, …)` call to `active(x, …)`, remove the
contract-search verification and the next-step assertions, and assert the cards' ids/facts. In
the `signed searches` test, keep the match-free stats assertions.

- [ ] **Step 3: Write the failing template tests**

In `HomeTemplateTest`:

- Keep `visitClient.search(AGENT.id, VisitRules.OPEN)` everywhere; do not add a completed-visit read.
- Replace `signed search - match lines` with:

```kotlin
@Test
fun `signed search - activity line`() {
    val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
    doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
    doReturn(mapOf(203L to MandateStats(203, 3, 1, 1, 2))).whenever(mandateClient).stats(any())

    val card = render(get("/")).selectFirst("[data-mandate-card]")!!

    val stats = card.selectFirst("[data-search-stats]")!!
    val link = stats.selectFirst("[data-search-stats-link]")!!
    assertEquals("/mandates/203#matches-title", link.attr("href"))
    assertEquals("3 biens · 1 intéressé · 3 visites", link.text())
    assertNull(card.selectFirst("[data-matches]"))
    assertNull(card.selectFirst("[data-proposals]"))
    assertNull(card.selectFirst(".mandate-card__next"))
}
```

Replace `no match lines` with an assertion that `[data-search-stats]` is null when `mandateClient.stats` returns null. Keep the existing `no match` test but stub `mandateClient.stats` to an empty map and assert the empty label.

- [ ] **Step 4: Remove the dead rules and tests**

- In `MatchRules.kt`, delete `summary` and `nextStep`.
- In `MandateRules.kt`, delete `nextStep` and `listingStep`.
- In `MatchRulesTest`, delete `summary` and `nextStep`; keep `proposals`.
- In `MandateRulesTest`, delete the `nextStep` tests (lines starting with `MandateRules.nextStep`).

- [ ] **Step 5: Run the tests and watch them fail**

```bash
cd modules/ndopify-partner-central && \
  mvn test -Dtest='MandateCardsTest,HomeControllerTest,HomeTemplateTest,MatchRulesTest,MandateRulesTest'
```

Expected: compilation failures on `MandateCards.of`, `MandateCardView.searchStats`, `nextStep`, and the removed dependencies.

- [ ] **Step 6: Implement the view and the card**

`MandateCardView`:

```kotlin
data class MandateCardView(
    val id: Long,
    val title: String,
    val owner: Boolean,
    val tag: MandateRules.Tag,
    val facts: List<Pair<String, String>>,
    val listing: ListingLineView?,
    /** A published listing only: "2 personnes intéressées · 3 visites"; null before, or when its stats can't be had. */
    val stats: String? = null,
    /** A signed search only: "12 biens · 1 intéressé · 3 visites"; null before or when its stats can't be had. */
    val searchStats: SearchStatsView? = null,
)
```

`MandateCards.of`:

```kotlin
fun of(
    mandate: Mandate,
    listings: Map<Long, ListingSummary>?,
    locations: Map<Long, Location>,
    formatter: TenantFormatter,
    mandateStats: Map<Long, MandateStats>? = null,
    stats: Map<Long, ListingStats>? = null,
): MandateCardView
```

Inside, keep the facts and listing logic; replace the match block with:

```kotlin
val searchStats = if (MatchRules.matched(mandate)) {
    SearchStatsView.of(mandate, mandateStats?.get(mandate.id))
} else {
    null
}
```

and remove `contract`, `now`, `matches`, `summary`, `matchesUrl` and `proposals`.

- [ ] **Step 7: Implement the home controller**

- Remove `NdContractClient`, `NdMatchClient`, `SearchContractRequest`, `ContractSummary`, `Match` and `matches()`/`contracts()`.
- `mandates(agent)` returns `List<Mandate>?`:

```kotlin
private fun mandates(agent: Agent): List<Mandate>? =
    try {
        mandateClient.active(agent.id).map { summary -> MandateSummaries.toMandate(summary) }
    } catch (ex: RestClientException) {
        LOGGER.warn("No mandates for agent ${agent.id}: ${ex.message}")
        null
    }
```

- Add:

```kotlin
/** The approved searches' activity, by mandate id, in one call; null when it can't be had. */
private fun mandateStats(mandates: List<Mandate>): Map<Long, MandateStats>? {
    val ids = mandates.filter { MatchRules.matched(it) }.map { it.id }
    if (ids.isEmpty()) return emptyMap()
    return try {
        mandateClient.stats(ids)
    } catch (ex: RestClientException) {
        LOGGER.warn("No stats for the mandates ${ids.joinToString()}: ${ex.message}")
        null
    }
}
```

- In `show`, load `val mandateStats = mandates?.let { mandateStats(it) }`, then map the mandates
  directly (`mandates?.map { mandate -> MandateCards.of(mandate, listings, names, formatter, mandateStats, stats) }`)
  instead of mapping pairs of mandate and contract.
- Keep `loadVisits` as `visitClient.search(agent.id, VisitRules.OPEN)`; do not expand it to completed visits. The `VisitStats` grouping is gone; `visits` is only for `VisitCards`.
- In the `names(...)` call, replace `mandates.orEmpty().map { (mandate, _) -> mandate }` with
  `mandates.orEmpty()`; the other terms are unchanged.

- [ ] **Step 8: Implement the template and CSS**

In `templates/index.html`, remove every `m.nextStep` block and the `m.matches`/`m.proposals` blocks, and add:

```html
<!-- A signed search: its current pool, the client's interest and its visits. One line, even on a phone; the
     mandate page has the detail. -->
<p class="search-stats mandate-card__search-stats" th:if="${m.searchStats}" data-search-stats>
    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 20h18"/><path d="M6 20v-6M12 20V8M18 20V4"/></svg>
    <span class="visually-hidden">Activité de la recherche : </span>
    <a th:href="@{${m.searchStats.url}}" data-search-stats-link><span th:text="${m.searchStats.label}">12 biens · 1 intéressé · 3 visites</span></a>
</p>
```

Update the template's top comment: replace the `m.matches`/`m.proposals`/next-step sentence with the activity line.

In `public/assets/css/home.css`, add after `.listing-stats`:

```css
/* A signed search: its pool, the client's interest and its visits — one line, even on a phone. */
.search-stats {
    display: flex;
    align-items: center;
    gap: var(--space-1) var(--space-2);
    font-size: var(--step-hint);
    white-space: nowrap;
}

.search-stats .icon {
    flex: none;
}

.search-stats > a {
    display: inline-flex;
    align-items: center;
    min-height: var(--target);
    color: inherit;
}

.search-stats > a > span,
.search-stats > span:not(.visually-hidden) {
    min-width: 0;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
}

.mandate-card__search-stats {
    position: relative;
    z-index: 1;
}

@media (max-width: 360px) {
    .search-stats {
        font-size: 0.8125rem;
    }
}
```

Remove the now-unused `.mandate-card__matches` rules. Keep `.mandate-card__proposals` (the mandate page) and `.mandate-card__next` (the visit cards).

- [ ] **Step 9: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && \
  mvn test -Dtest='SearchStatsViewTest,MandateCardsTest,HomeControllerTest,HomeTemplateTest,MatchRulesTest,MandateRulesTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui \
  modules/ndopify-partner-central/src/main/resources/templates/index.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui
git commit -m "feat(npc): drop the card next step and load the mandate stats"
```

---

### Task 4: Mandate page — activity line from the loaded matches

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `SearchStatsView.of(mandate, matches, visitStats)`.
- Produces: the mandate model attribute `searchStats: SearchStatsView?`.

- [ ] **Step 1: Write the failing tests**

In `MandateControllerTest`, in `approved search - matches`, add:

```kotlin
assertEquals("2 biens", (model(result)["searchStats"] as SearchStatsView).label)
```

Add:

```kotlin
@Test
fun `approved search - search stats with visits`() {
    doReturn(APPROVED).whenever(mandateClient).get(201)
    doReturn(listOf(matchA.copy(status = MatchStatus.ACCEPTED), matchB))
        .whenever(matchClient).search(any(), any(), any())
    doReturn(listOf(listingA, listingB)).whenever(listingClient).searchByIds(any())
    doReturn(
        listOf(
            VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)),
            VisitFixtures.summary(VisitFixtures.completed().copy(id = 7002, mandateId = 201, matchId = 9003)),
        ),
    ).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

    val result = mvc.perform(get("/mandates/201")).andReturn()

    assertEquals("2 biens · 1 intéressé · 2 visites", (model(result)["searchStats"] as SearchStatsView).label)
}
```

In `approved search - matches unavailable`, add `assertNull(model(result)["searchStats"])`; same for the non-search tests.

In `MandateTemplateTest`, assert the line in the `matches` and `visits` tests:

```kotlin
assertEquals("2 biens", section.selectFirst("[data-search-stats] .visually-hidden")?.nextElementSibling()?.text())
```

and add the completed-visits test as before, expecting `2 biens · 1 intéressé · 2 visites`. In `matches - empty`, `matches - unavailable` and `matches - only refused ones`, assert `assertNull(...selectFirst("[data-search-stats]"))`.

- [ ] **Step 2: Run the tests and watch them fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,MandateTemplateTest'
```

Expected: failures because `searchStats` is missing from the model and the template.

- [ ] **Step 3: Implement the controller**

In `MandateController.render`, after `model.addAttribute("mandate", view)`:

```kotlin
model.addAttribute(
    "searchStats",
    if (matched) matches?.let { SearchStatsView.of(mandate, it, visitStats) } else null,
)
```

Keep `visitStats` and its panel line unchanged.

- [ ] **Step 4: Implement the template**

In `mandate.html`, inside `<div class="matches__head">`, after the `<h2>`:

```html
<p class="search-stats" th:if="${searchStats != null and searchStats.properties > 0}" data-search-stats>
    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 20h18"/><path d="M6 20v-6M12 20V8M18 20V4"/></svg>
    <span><span class="visually-hidden">Activité de la recherche : </span><span th:text="${searchStats.label}">12 biens · 1 intéressé · 3 visites</span></span>
</p>
```

Update the template's top comment: add "`searchStats` is a signed search's one-line activity
(SearchStatsView), null for any other mandate or when the matches can't be read."

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,MandateTemplateTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
  modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show the search activity on the mandate page"
```

---

### Task 5: Documentation, prototype and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/README.md`
- Modify: `modules/ndopify-partner-central/CLAUDE.md`
- Prototype (updated in this design pass): `modules/ndopify-partner-central/src/main/html/index.html`,
  `modules/ndopify-partner-central/src/main/html/mandate.html`,
  `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`,
  `modules/ndopify-partner-central/src/main/html/assets/js/visit.js`,
  `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

- [ ] **Step 1: Update the README**

In the *Mes mandats* bullet, replace "each with its next step" with
"a signed search showing its one-line activity (`12 biens · 1 intéressé · 3 visites`)".
In the mandate page bullet, add that the *Biens correspondants* header carries the same line.

- [ ] **Step 2: Update `CLAUDE.md`**

Replace the *Mes mandats* paragraph's contract / match / next-step description with: the
mandates in one search, the approved searches' stats in one `GET /v1/mandates/stats`, the
locations in one lookup, the published listings' stats in one `GET /v1/listings/stats`, and the
open visits for the *Visites* section. Say the card has no next step. Replace the
*Mes mandats cards of signed searches get the matches' lines* paragraph with the activity line
and note that the mandate page derives it from its loaded matches/visits.

- [ ] **Step 3: Check the prototype**

Open `modules/ndopify-partner-central/src/main/html/index.html` and
`.../mandate.html?id=204`. Confirm:

- the four mandate cards no longer show *Prochaine étape*;
- 204 shows `6 biens · 3 intéressés · 3 visites` and links to 204's *Biens correspondants*;
- 203 keeps its listing activity and no search line;
- 101–103 accepted offers get the line after approval;
- visit cards still show their next step (those are `VisitRules`, not the mandate card's);
- `mandate.html?id=204` shows the line above *Biens correspondants*;
- `?mandates=empty`, `?visits=empty` and `?listing=published` still work.

- [ ] **Step 4: Grep for stale card fields/selectors**

```bash
grep -rn "m.nextStep\|data-next-step\|m.matches\|m.proposals\|matchesUrl\|data-matches-link\|data-proposals-text" \
  modules/ndopify-partner-central/src/main/kotlin \
  modules/ndopify-partner-central/src/main/resources \
  modules/ndopify-partner-central/src/main/html
```

Expected: no output. `VisitRules.nextStep` and the visit cards' `data-visit-next` are different
and stay.

- [ ] **Step 5: Run the full partner-central verification**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Run the server stats test once more**

```bash
cd modules/ndopify-dto && mvn install
cd modules/ndopify-server && mvn test -Dtest=MandateStatsEndpointTest
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/README.md \
  modules/ndopify-partner-central/CLAUDE.md \
  modules/ndopify-partner-central/src/main/html
git commit -m "docs(npc): document the mandate stats and the card without a next step"
```
