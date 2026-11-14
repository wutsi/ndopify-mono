# Co-Brokerage Line in *Biens correspondants* — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a search mandate's *Biens correspondants*, show the listing agent's name under the title of a co-brokerage card, in the BFF, with no change to own-inventory cards.

**Architecture:** `MandateController` resolves the distinct listing agents of the co-brokerage matches and passes their names to `MatchViews`, which sets a presentational `MatchView.origin` (the name, or the generic fallback). The Thymeleaf card renders that line with a person icon. No API, controller action, ordering or DTO change.

**Tech Stack:** Kotlin, Spring Boot + Thymeleaf, Jsoup template tests, JUnit 5 / `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-11-13-npc-match-co-brokerage-badge-design.md`

## Global Constraints

- French UI copy, consistent with the existing wording.
- Reuse `--color-info-fg`, `--step-hint` and `--space-1`; no new colour token.
- The marker is text plus an `aria-hidden` person icon; colour is never the only signal.
- Own-inventory cards render no line and keep their current height.
- The line is independent of the status tag: it must stay when a visit block hides the tag.
- The listing agent is read only for co-brokerage listings; never the listing's mandate or customer.
- The working tree has unrelated WIP. Stage only the paths each task names (`git add <paths>`), never `git add -A`.
- Run module tests from `modules/ndopify-partner-central` with `mvn test -Dtest=<Class>`.
- The static prototype (`src/main/html`) is **not** updated in this plan; it follows in a separate pass.

## Review Focus

1. **Co-brokerage match with a requested or confirmed visit** — the status tag is hidden; the name line must remain.
2. **Own-inventory match** — no line, and no extra spacing or height.
3. **Name lookup fails or 404s** — the card falls back to « Bien d'un autre agent », and the page still renders.
4. **No co-brokerage match** — no agent lookup is made at all.
5. **Screen readers** — the icon is `aria-hidden`, the text is present, and the line is not colour-only.

---

### Task 1: Carry `coBrokerage` into `MatchView` — DONE

**Files:** `MatchView.kt`, `MatchViews.kt`, `MatchViewsTest.kt`.

- [x] Add `coBrokerage: Boolean = false` and pass `match.coBrokerage`.
- [x] Test `co-brokerage card is flagged for the template`.
- [x] Commit `fcfb674c`.

---

### Task 2: Add the presentational origin line to `MatchView`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`

**Interfaces:**
- Consumes: `Match.coBrokerage`, `ListingSummary.agentId`.
- Produces:
  - `MatchView.origin: String?` — the name under the title, null for own inventory.
  - `MatchViews.section(..., agentNames: Map<Long, String> = emptyMap())`.

- [x] **Step 1: Write the failing tests**

Extend the `section` helper with `agentNames: Map<Long, String> = emptyMap()` and pass it as the last argument to
`MatchViews.section`. Add:

```kotlin
@Test
fun `co-brokerage card - the listing agent's name`() {
    val section = section(
        listOf(MATCH_A.copy(coBrokerage = true)),
        agentNames = mapOf(20L to "Clarisse Mbarga"),
    )

    assertEquals("Clarisse Mbarga", section.matches.single().origin)
}

@Test
fun `co-brokerage card - generic label without the agent name`() {
    val section = section(listOf(MATCH_A.copy(coBrokerage = true)))

    assertEquals("Bien d'un autre agent", section.matches.single().origin)
}

@Test
fun `own-inventory card - no origin line`() {
    val section = section(listOf(MATCH_A))

    assertNull(section.matches.single().origin)
}
```

- [x] **Step 2: Run them and verify they fail**

Run: `mvn test -Dtest=MatchViewsTest`
Expected: compilation fails on `origin` / `agentNames`.

- [x] **Step 3: Implement the field and the resolution**

In `MatchView`, after `coBrokerage`:

```kotlin
/** The publisher line under the title of a co-brokerage card; null on the agent's own listings. */
val origin: String? = null,
```

In `MatchViews`:

```kotlin
private const val OTHER_AGENT = "Bien d'un autre agent"

fun section(
    mandate: Mandate,
    matches: List<Match>?,
    listings: Map<Long, ListingSummary>?,
    visits: Map<Long, VisitCardView>,
    locations: Map<Long, Location>,
    today: LocalDate,
    formatter: TenantFormatter,
    agentNames: Map<Long, String> = emptyMap(),
): MatchSectionView
```

Pass `agentNames` to `view(...)`, and set:

```kotlin
origin = if (match.coBrokerage) agentNames[listing.agentId] ?: OTHER_AGENT else null,
```

- [x] **Step 4: Run them and verify they pass**

Run: `mvn test -Dtest=MatchViewsTest`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt
git commit -m "feat(npc): resolve the co-brokerage card origin line"
```

---

### Task 3: Read the agent names, render the line, fix the intro

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`
- Test wiring: `MandateControllerTest.kt`, `MandateEditTemplateTest.kt`, `MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchView.origin` (Task 2), `NdAgentClient.get(id): Agent?`, `ProfileRules.fullName(agent): String`.
- Produces: the rendered `[data-match-origin]` line and `[data-matches-intro]` paragraph.

- [x] **Step 1: Write the failing tests**

In `MandateTemplateTest`, add `private val agentClient = mock<NdAgentClient>()` and pass it right after
`listingClient` in the `MandateController(...)` construction. Do the same minimal wiring in `MandateControllerTest`
and `MandateEditTemplateTest` (no assertions there).

Add next to the existing co-brokerage test:

```kotlin
@Test
fun `co-brokerage match - the listing agent's name`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, coBrokerage = true))
    doReturn(listOf(MatchFixtures.LISTING_A.copy(agentId = 30))).whenever(listingClient).searchByIds(any())
    doReturn(
        ProfileFixtures.AGENT.copy(
            id = 30,
            party = ProfileFixtures.AGENT.party.copy(id = 130, firstName = "Clarisse", lastName = "Mbarga"),
        ),
    ).whenever(agentClient).get(30)

    val page = render(APPROVED)
    val card = page.selectFirst("[data-match-id=9001]")!!

    assertEquals("Clarisse Mbarga", card.selectFirst("[data-match-origin]")?.text())
    assertTrue(card.selectFirst("[data-match-origin] svg")!!.hasAttr("aria-hidden"))
}

@Test
fun `co-brokerage match - generic label when the agent can't be read`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, coBrokerage = true))
    doReturn(listOf(MatchFixtures.LISTING_A.copy(agentId = 30))).whenever(listingClient).searchByIds(any())

    val page = render(APPROVED)

    assertEquals(
        "Bien d'un autre agent",
        page.selectFirst("[data-match-id=9001] [data-match-origin]")?.text(),
    )
}

@Test
fun `own-inventory match - no origin line`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))

    val page = render(APPROVED)

    assertNull(page.selectFirst("[data-match-id=9001] [data-match-origin]"))
}

@Test
fun `co-brokerage match with a visit keeps its origin line`() {
    doReturn(VisitFixtures.NOW).whenever(clock).millis()
    withMatches(
        MatchFixtures.MATCH_A.copy(
            mandateId = 201,
            status = MatchStatus.ACCEPTED,
            coBrokerage = true,
            proposedAt = proposedAt,
        ),
    )
    doReturn(listOf(MatchFixtures.LISTING_A.copy(agentId = 30))).whenever(listingClient).searchByIds(any())
    doReturn(
        ProfileFixtures.AGENT.copy(
            id = 30,
            party = ProfileFixtures.AGENT.party.copy(id = 130, firstName = "Clarisse", lastName = "Mbarga"),
        ),
    ).whenever(agentClient).get(30)
    doReturn(listOf(VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001))))
        .whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

    val page = render(APPROVED)
    val card = page.selectFirst("[data-match-id=9001]")!!

    assertNull(card.selectFirst("[data-match-tag]"))
    assertEquals("Clarisse Mbarga", card.selectFirst("[data-match-origin]")?.text())
}

@Test
fun `co-brokerage line stays when visits are unavailable`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201, coBrokerage = true))
    doReturn(listOf(MatchFixtures.LISTING_A.copy(agentId = 30))).whenever(listingClient).searchByIds(any())
    doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

    val page = render(APPROVED)

    assertEquals(
        "Bien d'un autre agent",
        page.selectFirst("[data-match-id=9001] [data-match-origin]")?.text(),
    )
}

@Test
fun `matches intro covers other agents' listings`() {
    withMatches(MatchFixtures.MATCH_A.copy(mandateId = 201))

    val page = render(APPROVED)

    assertEquals(
        "Les biens qui correspondent à la recherche du client, les vôtres comme ceux publiés par d'autres " +
            "agents. Envoyez-les-lui ou écartez-les.",
        page.selectFirst("[data-matches-intro]")?.text(),
    )
}
```

- [x] **Step 2: Run them and verify they fail**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: failures on the missing `[data-match-origin]` / `[data-matches-intro]`, and the unknown `agentClient`
constructor argument.

- [x] **Step 3: Read the listing agents in `MandateController`**

Add the `NdAgentClient` import and constructor parameter after `listingClient`:

```kotlin
private val agentClient: NdAgentClient,
```

In `render`, before the `MatchViews.section` call:

```kotlin
val agentNames = if (matches != null && listings != null) {
    listingAgentNames(agent, matches, listings)
} else {
    emptyMap()
}
```

Pass `agentNames` as the last argument. Add the helper:

```kotlin
/**
 * The co-brokerage matches' listing agents, by id: another agent's identity is allowed, its customer never is.
 * Secondary: a failed or missing agent leaves the card its generic "Bien d'un autre agent" line.
 */
private fun listingAgentNames(
    agent: Agent,
    matches: List<Match>,
    listings: Map<Long, ListingSummary>,
): Map<Long, String> {
    val ids = matches
        .filter { it.coBrokerage && it.status in MatchRules.ORDER }
        .mapNotNull { listings[it.listingId]?.agentId }
        .filter { it > 0 && it != agent.id }
        .distinct()
    return ids.mapNotNull { id ->
        lookup("agent $id of a co-brokerage listing") { agentClient.get(id) }
            ?.let { agent -> id to ProfileRules.fullName(agent) }
            ?.takeIf { (_, name) -> name.isNotBlank() }
    }.toMap()
}
```

- [x] **Step 4: Render the line**

In `templates/mandate.html`, after the closing `</div>` of `assignment__head` and before
`<dl class="match__facts" data-match-facts>`:

```html
<p class="match__origin" th:if="${match.origin}" data-match-origin>
    <svg class="icon" viewBox="0 0 20 20" aria-hidden="true" focusable="false"><circle cx="10" cy="6.5" r="3.25" fill="none" stroke="currentColor" stroke-width="1.5"/><path d="M4 17c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
    <span th:text="${match.origin}">Clarisse Mbarga</span>
</p>
```

- [x] **Step 5: Correct the section intro**

Replace the current intro paragraph with:

```html
<p data-matches-intro>Les biens qui correspondent à la recherche du client, les vôtres comme ceux publiés par
    d'autres agents. Envoyez-les-lui ou écartez-les.</p>
```

- [x] **Step 6: Add the CSS rule**

In `resources/public/assets/css/home.css`, immediately before the `.match__facts` rule:

```css
/* A co-brokerage card's publisher, between the head and the facts: a name, not a status. */
.match__origin {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    color: var(--color-info-fg);
    font-size: var(--step-hint);
    font-weight: 600;
}

.match__origin .icon {
    width: 1rem;
    height: 1rem;
    flex: none;
}
```

The existing `.match article > * + *` rule supplies the small top margin; do not add one.

- [x] **Step 7: Run the tests**

Run: `mvn test -Dtest=MandateTemplateTest`
Expected: PASS.

- [x] **Step 8: Run the module suite**

Run: `mvn test`
Expected: PASS, including the updated constructor wiring.

- [x] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt
git commit -m "feat(npc): show the listing agent on co-brokerage matches"
```

---

## Final verification

- [x] `mvn test` in `modules/ndopify-partner-central` — all pass.
- [x] `mvn validate` — ktlint passes.
- [x] `git status` contains no unintended files from this work.
