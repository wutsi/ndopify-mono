# Search Mandate Stats Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the one-line activity summary `12 biens · 1 intéressé · 3 visites` for every approved `SEARCH` mandate on the Espace agent's card and on its mandate page.

**Architecture:** A new `SearchStatsView` turns the already-loaded matches and visits into the line. `MandateCards` and `HomeController` feed the home card; `MandateController` feeds the mandate page. The home page's single visit read expands from open visits to open + completed so the line can count visits; the existing *Visites* section still shows open visits only. No server or DTO change.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf, Jsoup template tests, Mockito, Maven, `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-11-05-search-mandate-stats-design.md`

## Global Constraints

- **Only an `APPROVED` `SEARCH` mandate** shows the line. No line before signature, for a `LISTING` mandate, or when the matches can't be read.
- **`12 biens`** counts `MatchStatus.ACTIVE`, `PROPOSED`, `ACCEPTED` and `EXPIRED` (`MatchRules.ORDER`). Never count `REJECTED`, `DISMISSED`, `SHORTLISTED` or `UNKNOWN`.
- **`1 intéressé`** counts `MatchStatus.ACCEPTED`.
- **`3 visites`** is `VisitStats.total` (upcoming + completed). Omit the segment when `VisitStats` is null or its total is 0.
- **Exact copy:** `12 biens · 1 intéressé · 3 visites`; separator is `" · "` (space, middle dot, space). Empty: `Aucun bien correspondant pour l'instant`. Singular: `1 bien`, `1 intéressé`, `1 visite`.
- **One line:** `white-space: nowrap`; `font-size: var(--step-hint)` (14px); 13px below 360px. The home link keeps `min-height: var(--target)` (44px).
- **Home card:** the line replaces `m.matches` and `m.proposals`. Keep `m.nextStep`.
- **Mandate page:** add the line to the *Biens correspondants* header only when `properties > 0`. Keep the detailed proposal line, the visit breakdown in the approved panel and the per-match cards.
- **Home visits read:** `visitClient.search(agent.id, VisitRules.COUNTED, limit = 100)`. The *Visites* section still gets open visits only.
- **No server or DTO changes.** In partner-central, run `mvn test`; before the final commit run `mvn verify`.
- Commit after each task.

## Review Focus

- **Visits unreadable:** the home and mandate lines still show the pool and interest, without the visits segment; the pages still render.
- **Matches unreadable:** the line is absent, not an empty label, and the rest of the card/page still renders.
- **Zero matches:** the home card shows `Aucun bien correspondant pour l'instant`; the mandate page keeps its existing empty state and hides the line.
- **Refused / dismissed / shortlisted:** never counted in `12 biens`; the line's pool equals the cards in *Biens correspondants*, not every match row.
- **Completed visits:** counted in `3 visites`, but still absent from the *Visites* section (which lists open visits only).

---

### Task 1: `SearchStatsView`

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsView.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsViewTest.kt`

**Interfaces:**
- Produces: `SearchStatsView(mandateId: Long, properties: Int, interested: Int, visits: Int?)` with `label: String`, `url: String`, companion `of(mandate: Mandate, matches: List<Match>, visits: VisitStats?): SearchStatsView`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.dto.MandateType
import com.wutsi.ndopify.match.dto.Match
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.npc.ui.visit.VisitStats
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SearchStatsViewTest {
    private val mandate = Mandate(
        id = 201,
        mandateType = MandateType.SEARCH,
        status = MandateStatus.APPROVED,
    )

    private fun match(id: Long, status: MatchStatus) = Match(
        id = id,
        mandateId = 201,
        listingId = 3000 + id,
        status = status,
    )

    @Test
    fun `label - pool, interest and visits`() {
        val matches = (1L..10L).map { match(it, MatchStatus.ACTIVE) } +
            match(11, MatchStatus.ACCEPTED) +
            match(12, MatchStatus.PROPOSED)

        val stats = SearchStatsView.of(mandate, matches, VisitStats(1, 2))

        assertEquals(12, stats.properties)
        assertEquals(1, stats.interested)
        assertEquals("12 biens · 1 intéressé · 3 visites", stats.label)
        assertEquals("/mandates/201#matches-title", stats.url)
    }

    @Test
    fun `pool excludes refused, dismissed and shortlisted`() {
        val stats = SearchStatsView.of(
            mandate,
            listOf(
                match(1, MatchStatus.ACTIVE),
                match(2, MatchStatus.PROPOSED),
                match(3, MatchStatus.ACCEPTED),
                match(4, MatchStatus.EXPIRED),
                match(5, MatchStatus.REJECTED),
                match(6, MatchStatus.DISMISSED),
                match(7, MatchStatus.SHORTLISTED),
            ),
            VisitStats(0, 0),
        )

        assertEquals(4, stats.properties)
        assertEquals(1, stats.interested)
        assertEquals("4 biens · 1 intéressé", stats.label)
    }

    @Test
    fun `label drops zero segments`() {
        val twelve = (1L..12L).map { match(it, MatchStatus.ACTIVE) }

        assertEquals("12 biens", SearchStatsView.of(mandate, twelve, VisitStats(0, 0)).label)
        assertEquals("12 biens · 3 visites", SearchStatsView.of(mandate, twelve, VisitStats(1, 2)).label)
        assertEquals(
            "12 biens · 1 intéressé",
            SearchStatsView.of(
                mandate,
                (1L..11L).map { match(it, MatchStatus.ACTIVE) } + match(12, MatchStatus.ACCEPTED),
                VisitStats(0, 0),
            ).label,
        )
    }

    @Test
    fun `label - empty`() {
        assertEquals(
            "Aucun bien correspondant pour l'instant",
            SearchStatsView.of(mandate, emptyList(), VisitStats(0, 0)).label,
        )
    }

    @Test
    fun `label - visits unavailable`() {
        assertEquals(
            "1 bien · 1 intéressé",
            SearchStatsView.of(mandate, listOf(match(1, MatchStatus.ACCEPTED)), null).label,
        )
    }

    @Test
    fun `label - singular`() {
        assertEquals(
            "1 bien · 1 intéressé · 1 visite",
            SearchStatsView.of(mandate, listOf(match(1, MatchStatus.ACCEPTED)), VisitStats(0, 1)).label,
        )
    }
}
```

- [ ] **Step 2: Run the test and watch it fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=SearchStatsViewTest
```

Expected: compilation failure — `SearchStatsView` doesn't exist.

- [ ] **Step 3: Implement `SearchStatsView`**

```kotlin
package com.wutsi.ndopify.npc.ui.mandate

import com.wutsi.ndopify.mandate.dto.Mandate
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

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=SearchStatsViewTest
```

Expected: `BUILD SUCCESS`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsView.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/SearchStatsViewTest.kt
git commit -m "feat(npc): add the search stats view"
```

---

### Task 2: Home card

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- Consumes: `SearchStatsView` from Task 1.
- Produces: `MandateCardView.searchStats: SearchStatsView?`; `MandateCards.of(..., visitStats: Map<Long, VisitStats>? = null)`; the home model's `mandates` cards carry `searchStats`.

- [ ] **Step 1: Write the failing tests**

In `MandateCardsTest`, import `com.wutsi.ndopify.npc.ui.visit.VisitStats`, change the `card` helper and the five match tests:

```kotlin
private fun card(matches: Map<Long, List<Match>>?, visits: Map<Long, VisitStats>? = null) =
    MandateCards.of(search, CONTRACT_SUMMARY, emptyMap(), NAMES, NOW, FORMATTER, matches, visitStats = visits)

@Test
fun `new matches`() {
    val card = card(mapOf(201L to (1L..3L).map { match(it, MatchStatus.ACTIVE) }))

    assertEquals("3 biens", card.searchStats?.label)
    assertEquals("/mandates/201#matches-title", card.searchStats?.url)
    assertNull(card.searchStats?.visits)
    assertEquals("Envoyez au client les biens qui lui correspondent.", card.nextStep)
}
```

In `matches sent`, replace the old `matches`/`proposals` assertions with:

```kotlin
assertEquals("3 biens · 1 intéressé", card.searchStats?.label)
```

In `matches all sent`, replace them with `assertEquals("1 bien", card.searchStats?.label)`.

In `no match`, replace them with:

```kotlin
assertEquals("Aucun bien correspondant pour l'instant", card.searchStats?.label)
```

In `matches unavailable`, replace them with `assertNull(card.searchStats)`.

Add:

```kotlin
@Test
fun `search stats - visits`() {
    val card = card(
        mapOf(201L to listOf(match(1, MatchStatus.ACCEPTED))),
        mapOf(201L to VisitStats(1, 2)),
    )

    assertEquals("1 bien · 1 intéressé · 3 visites", card.searchStats?.label)
}
```

In `HomeControllerTest`, update `matches of the signed searches`:

```kotlin
assertEquals("1 bien", mandates[0].searchStats?.label)
assertEquals("/mandates/203#matches-title", mandates[0].searchStats?.url)
assertEquals("1 bien · 1 intéressé", mandates[1].searchStats?.label)
assertEquals("L'annonce n° 2987 intéresse le client : proposez-lui une visite.", mandates[1].nextStep)
assertNull(mandates[2].searchStats)
```

Update `matches unavailable` to `assertNull(card.searchStats)`.

Replace **every** `search(AGENT.id, VisitRules.OPEN)` stub and verification in the file with
`search(AGENT.id, VisitRules.COUNTED, 100)` (`visits`, `no customers`, `upcoming` in the template
test, and the setup stubs).

Add:

```kotlin
@Test
fun `visits unavailable - the search stats keep the pool and interest`() {
    active(approved to null)
    doReturn(listOf(Match(id = 1, mandateId = 203, listingId = 3012, status = MatchStatus.ACCEPTED)))
        .whenever(matchClient).search(any(), any(), any())
    doThrow(ResourceAccessException("timeout"))
        .whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 100)

    val result = mvc.perform(get("/")).andReturn()

    assertEquals("1 bien · 1 intéressé", mandates(result).single().searchStats?.label)
}
```

Add:

```kotlin
@Test
fun `completed visits count in the search stats`() {
    active(approved to null)
    doReturn(listOf(Match(id = 1, mandateId = 203, listingId = 3012, status = MatchStatus.ACCEPTED)))
        .whenever(matchClient).search(any(), any(), any())
    doReturn(listOf(VisitFixtures.summary(VisitFixtures.completed().copy(mandateId = 203))))
        .whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 100)

    val result = mvc.perform(get("/")).andReturn()

    assertEquals("1 bien · 1 intéressé · 1 visite", mandates(result).single().searchStats?.label)
}
```

In `HomeTemplateTest`, replace **every** `search(AGENT.id, VisitRules.OPEN)` stub with
`search(AGENT.id, VisitRules.COUNTED, 100)` (the setup and the `visits` / `upcoming` tests).

Replace `signed search - match lines` with:

```kotlin
@Test
fun `signed search - activity line`() {
    val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
    doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
    doReturn(
        listOf(
            Match(id = 1, mandateId = 203, status = MatchStatus.ACTIVE),
            Match(id = 2, mandateId = 203, status = MatchStatus.ACTIVE),
            Match(id = 3, mandateId = 203, status = MatchStatus.PROPOSED),
        ),
    ).whenever(matchClient).search(any(), any(), any())

    val card = render(get("/")).selectFirst("[data-mandate-card]")!!

    val stats = card.selectFirst("[data-search-stats]")!!
    val link = stats.selectFirst("[data-search-stats-link]")!!
    assertEquals("/mandates/203#matches-title", link.attr("href"))
    assertEquals("3 biens", link.text())
    assertNull(card.selectFirst("[data-matches]"))
    assertNull(card.selectFirst("[data-proposals]"))
    assertEquals(
        "Envoyez au client les biens qui lui correspondent.",
        card.selectFirst(".mandate-card__next span")?.text(),
    )
}
```

Replace `no match lines` with `assertNull(render(get("/")).selectFirst("[data-search-stats]"))`.

Add:

```kotlin
@Test
fun `signed search - no match`() {
    val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
    doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
    doReturn(emptyList<Any>()).whenever(matchClient).search(any(), any(), any())

    val stats = render(get("/")).selectFirst("[data-search-stats]")!!

    assertEquals("Aucun bien correspondant pour l'instant", stats.selectFirst("[data-search-stats-link]")?.text())
}

@Test
fun `signed search - stats with interest and visits`() {
    val search = MandateCardsTest.MANDATE.copy(id = 203, status = MandateStatus.APPROVED)
    doReturn(listOf(MandateSummariesTest.summary(search))).whenever(mandateClient).active(AGENT.id)
    doReturn(listOf(Match(id = 1, mandateId = 203, listingId = 3012, status = MatchStatus.ACCEPTED)))
        .whenever(matchClient).search(any(), any(), any())
    doReturn(listOf(VisitFixtures.summary(VisitFixtures.completed().copy(mandateId = 203))))
        .whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 100)

    val stats = render(get("/")).selectFirst("[data-search-stats]")!!

    assertEquals("1 bien · 1 intéressé · 1 visite", stats.selectFirst("[data-search-stats-link]")?.text())
}
```

- [ ] **Step 2: Run the tests and watch them fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateCardsTest,HomeControllerTest,HomeTemplateTest'
```

Expected: compilation failures on `searchStats`, `visitStats` and the old `matches`/`proposals` fields.

- [ ] **Step 3: Implement the data changes**

In `MandateCardView`, delete `matches`, `matchesUrl` and `proposals`; add:

```kotlin
/** A signed search only: "12 biens · 1 intéressé · 3 visites"; null before signature or when matches can't be read. */
val searchStats: SearchStatsView? = null,
```

In `MandateCards.of`, add `visitStats: Map<Long, VisitStats>? = null` after `stats`, then replace the
match block with:

```kotlin
val matched = matches?.takeIf { MatchRules.matched(mandate) }?.get(mandate.id)
...
searchStats = matched?.let { SearchStatsView.of(mandate, it, visitStats?.get(mandate.id)) },
nextStep = MatchRules.nextStep(matched.orEmpty())
    ?: listings?.takeIf { MandateRules.listed(mandate) }?.let { MandateRules.listingStep(listing) }
    ?: MandateRules.nextStep(mandate, contract, now, formatter),
```

Remove the `summary`, `matches`, `matchesUrl` and `proposals` assignments.

In `HomeController`:

- Import `com.wutsi.ndopify.npc.ui.visit.VisitStats`.
- Change `loadVisits` to `visitClient.search(agent.id, VisitRules.COUNTED, limit = 100)`.
- Filter the cards' visits:

```kotlin
val visits = loadVisits(agent)
val openVisits = visits?.filter { VisitRules.isOpen(VisitRules.asVisit(it), now) }
val visitListings = openVisits?.let { loadVisitListings(it) }
val visitCustomers = openVisits?.let { loadVisitCustomers(it) } ?: emptyMap()
val visitStats = visits?.groupBy { it.mandateId }
    ?.mapValues { (_, list) -> VisitStats.of(list, now) ?: VisitStats(0, 0) }
```

- Pass `openVisits` to `VisitCards.of`.
- Pass `visitStats` to `MandateCards.of` (named argument `visitStats = visitStats`).

- [ ] **Step 4: Implement the template and CSS**

In `templates/index.html`, replace the two `<p class="mandate-card__matches" …>` and
`<p class="mandate-card__proposals" …>` blocks with:

```html
<!-- A signed search: its current pool, the client's interest and its visits. One line, even on a phone; the
     mandate page has the detail. -->
<p class="search-stats mandate-card__search-stats" th:if="${m.searchStats}" data-search-stats>
    <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M3 20h18"/><path d="M6 20v-6M12 20V8M18 20V4"/></svg>
    <span class="visually-hidden">Activité de la recherche : </span>
    <a th:href="@{${m.searchStats.url}}" data-search-stats-link><span th:text="${m.searchStats.label}">12 biens · 1 intéressé · 3 visites</span></a>
</p>
```

Update the template's top comment: replace the `m.matches`/`m.proposals` sentence with
"a signed search has its activity line (`m.searchStats`, one line on a phone), none when the matches
couldn't be loaded."

In `public/assets/css/home.css`, add after the `.listing-stats` rules:

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

Remove the now-unused `.mandate-card__matches` rule (around `home.css:754`). Keep
`.mandate-card__proposals`, still used on the mandate page.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='SearchStatsViewTest,MandateCardsTest,HomeControllerTest,HomeTemplateTest'
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
  modules/ndopify-partner-central/src/main/resources/templates/index.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt
git commit -m "feat(npc): show the search activity on the home card"
```

---

### Task 3: Mandate page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `SearchStatsView` from Task 1.
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

In `approved search - matches unavailable`, add `assertNull(model(result)["searchStats"])`.

In `not a signed search - no matches`, add `assertNull(model(landlord)["searchStats"])` and
`assertNull(model(assigned)["searchStats"])`.

In `MandateTemplateTest`, in `matches`, add:

```kotlin
val stats = section.selectFirst("[data-search-stats]")!!
assertEquals("2 biens", stats.selectFirst(".visually-hidden")?.nextElementSibling()?.text())
```

In `visits`, add:

```kotlin
assertEquals(
    "2 biens · 1 intéressé · 1 visite",
    page.selectFirst("[data-search-stats] .visually-hidden")?.nextElementSibling()?.text(),
)
```

Add:

```kotlin
@Test
fun `search stats - completed visits count`() {
    withVisits(
        VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)),
        VisitFixtures.summary(VisitFixtures.completed().copy(id = 7002, mandateId = 201, matchId = 9003)),
    )

    val stats = render(APPROVED).selectFirst("[data-search-stats]")!!

    assertEquals(
        "2 biens · 1 intéressé · 2 visites",
        stats.selectFirst(".visually-hidden")?.nextElementSibling()?.text(),
    )
}
```

In `visits unavailable`, add:

```kotlin
assertEquals(
    "1 bien · 1 intéressé",
    page.selectFirst("[data-search-stats] .visually-hidden")?.nextElementSibling()?.text(),
)
```

In `matches - empty`, add `assertNull(section.selectFirst("[data-search-stats]"))`.

In `matches - unavailable`, add `assertNull(page.selectFirst("[data-search-stats]"))`.

In `matches - only refused ones`, add `assertNull(section.selectFirst("[data-search-stats]"))`.

- [ ] **Step 2: Run the tests and watch them fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateControllerTest,MandateTemplateTest'
```

Expected: failures because `searchStats` is missing from the model and the template.

- [ ] **Step 3: Implement the controller**

In `MandateController.render`, after `model.addAttribute("mandate", view)`, add:

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

### Task 4: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/README.md`

- [ ] **Step 1: Update the README**

In the *Mes mandats* bullet, add: "A signed search also shows a one-line activity summary
(`12 biens · 1 intéressé · 3 visites`)." In the *Matched properties* bullet, add that the
mandate page's header carries the same line.

- [ ] **Step 2: Check no stale field or selector remains**

```bash
grep -rn "m.matches\|m.proposals\|matchesUrl\|data-matches-link\|data-proposals-text" \
  modules/ndopify-partner-central/src/main/kotlin \
  modules/ndopify-partner-central/src/main/resources
```

Expected: no output (the prototype under `src/main/html` is untouched, and the
`data-proposals-summary-text` on the mandate page is a different selector and stays).

- [ ] **Step 3: Run the full module suite**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: `BUILD SUCCESS`, including ktlint.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/README.md
git commit -m "docs(npc): describe the search activity line"
```
