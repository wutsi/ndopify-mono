# Partner Central — Merged Visit/Proposal Cards (BFF) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the approved prototype's merged visit/proposal cards to the served mandate page: one card per match or client, the visit's state, date, countdown and action inside it; the separate *Visites* sections, response summaries and converted-match filtering removed.

**Architecture:** Keep the Espace agent's *Visites* section unchanged. For the mandate page, build the same `VisitCardsView` the prototype uses, attach each `VisitCardView` to its `MatchView` or `ProposalRow`, group the cards by the agent's next action in the BFF view models, and render everything from Thymeleaf. The controller also emits a small `PendingVisitView` for the approved panel and the section-level outcomes line; no JavaScript is added.

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, jsoup + MockMvc + `MockRestServiceServer`, mockito-kotlin, `TenantFormatter`.

**Spec:** `modules/ndopify-partner-central/src/main/html/mandate.html`, its `assets/js/partner-central.js`, `assets/css/visit.css` and `assets/css/home.css` (the approved prototype), plus the critique at `.impeccable/critique/2026-11-08T01-54-29Z__artner-central-src-main-html-mandate-html-e06e9ef5.md`. The served template already has the stats move and the response-summary removal; this plan starts there.

## Global Constraints

- Never modify anything under `src/main/html/` while implementing this plan: that is the approved prototype. Copy from it.
- Do not modify `ndopify-server` or `ndopify-dto`; `ndopify-dto.version` stays `0.0.80`.
- UI copy is French; code and comments are English.
- Dates and visit wording go through `TenantFormatter`; never hard-code a date format.
- Keep the Espace agent's *Visites* section (`HomeController`, `VisitCards`, `visit.css`) working exactly as today.
- The mandate page has **no separate Visites section** for either a landlord's published listing or a signed search.
- Anti-bypass: another agent's client is never named, linked, or given a visit block; no visit code, note or customer address is ever put in a card.
- A visit read is secondary: if it fails, the matches/proposals still render, the card shows no visit block, an alert explains the missing visits, and no pending-visit callout is shown.
- A listing, mandate, match or customer read that is already secondary stays secondary; do not turn a degraded read into a failed page.
- Completed visits are out of scope for this pass: their match keeps the client's answer and no visit block, exactly as today. The activity line still counts them. Do not add a *Visités* group.
- The visible card order is: requested visits (soonest expiry), upcoming visits (soonest first), interested, new, waiting, expired.
- Retenir/SHORTLISTED and the prototype's bulk send remain unimplemented in the served BFF, as today; do not add a *Retenus* group or a bulk-send button.
- A card with a visit has no header status tag; the visit block carries the state. A card without a visit keeps its proposal tag.
- Envoyer is primary only when no requested visit exists on the page; otherwise it is secondary. The visit CTAs are the only primary card actions.
- Tests use `kotlin.test` assertions and mockito-kotlin mocks. Coverage gates in the root POM: line `0.98`, class `0.95`.
- Work on a branch: `git checkout -b npc-merged-cards`. Commit only the files each task names.

## Review Focus

1. **Visits unavailable:** `visitClient.search` or `searchByListings` fails. Cards still render without visit blocks, the section shows the visit-unavailable alert, and `pendingVisit` is absent. A user should never see a card with a broken empty visit block.
2. **Another agent's client with a visit:** the listing page must not attach that visit, name the client, link the mandate, or include it in the pending callout. The existing privacy rule wins over the merged layout.
3. **Stale or absent visit:** a requested visit past 48 h, or a confirmed one past its `+4 h` window, is not shown. Its match keeps the normal proposal state; no pending callout points at a card that isn't there.
4. **Unavailable matches/listings:** when the matches or their listings cannot be read, the section is unavailable and there are no cards or pending anchors. When only visits fail, the cards remain.
5. **Pending anchor integrity:** the pending callout's `#match-{id}` / `#proposal-{id}` URL always points at a rendered card; if the card is hidden or the section unavailable, the callout is absent.
6. **Secondary reads:** a missing listing or customer leaves a card with its fallback text ("Annonce n° …", "Client"), never a failed page.
7. **Completed visits:** they are counted by the activity line but do not gain a visit block in this pass; the match keeps "Intéressé".

---

### Task 1: Enrich the visit card view

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCards.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardsTest.kt`

**Interfaces:**
- Consumes: `VisitRules`, `TenantFormatter`, `VisitSummary`, `VisitStatus`.
- Produces:
```kotlin
data class VisitCardView(
    val id: Long,
    val matchId: Long?,
    val customerName: String,
    val type: String,
    val video: Boolean,
    val status: VisitStatus,
    val kindLabel: String,
    val whenText: String,
    val tag: VisitRules.Tag?,
    val whenLabel: String,
    val at: Long?,
    val listing: String,
    val expiresAt: Long?,
    val expiresIn: String?,
    val urgent: Boolean,
)
```
`VisitCards.of(...)` keeps its current signature; `visitCards` default behavior (open visits only) is unchanged for the Espace agent.

- [ ] **Step 1: Write the failing tests**

Add to `VisitCardsTest`:
```kotlin
@Test
fun `requested card carries its match, state and block wording`() {
    val card = of(listOf(summary(requested()))).requested.single()

    assertEquals(9001L, card.matchId)
    assertEquals(VisitStatus.REQUESTED, card.status)
    assertFalse(card.video)
    assertEquals("Visite demandée", card.kindLabel)
    assertEquals("pour le sam. 7 novembre 2026 à 10:30", card.whenText)
    assertEquals(requested().requestedAt!!.time, card.at)
}

@Test
fun `video request names the video state`() {
    val card = of(listOf(summary(requested().copy(type = VisitType.VIDEO)))).requested.single()

    assertTrue(card.video)
    assertEquals("Visite vidéo demandée", card.kindLabel)
}

@Test
fun `confirmed card says when the visit is`() {
    val card = of(listOf(summary(confirmed()))).upcoming.single()

    assertEquals(VisitStatus.CONFIRMED, card.status)
    assertEquals("Visite confirmée", card.kindLabel)
    assertEquals("le sam. 7 novembre 2026 à 10:30", card.whenText)
}

@Test
fun `checked in card says since when`() {
    val card = of(listOf(summary(checkedIn()))).upcoming.single()

    assertEquals("Visite en cours", card.kindLabel)
    assertEquals("depuis 10:05", card.whenText)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=VisitCardsTest test
```
Expected: FAIL — `matchId`, `status`, `kindLabel`, `whenText` and `video` do not exist.

- [ ] **Step 3: Implement the fields and labels**

In `VisitCards`, while building each `VisitCardView`, set:
- `matchId = visit.matchId`
- `video = visit.type == VisitType.VIDEO`
- `status = visit.status`
- `at = VisitRules.timeOf(visit)` (add a small `timeOf` helper or use `visit.scheduledAt ?: visit.requestedAt`)
- `kindLabel` from a private `visitKind(status, video)` helper:
  - REQUESTED: `Visite demandée` / `Visite vidéo demandée`
  - CONFIRMED: `Visite confirmée` / `Visite vidéo confirmée`
  - CHECKED_IN: `Visite en cours` / `Visite vidéo en cours`
  - COMPLETED: `Visité` / `Visité en vidéo`
- `whenText`:
  - REQUESTED: `"pour le $whenLabel"`
  - CONFIRMED: `"le $whenLabel"`
  - CHECKED_IN: `visit.checkedInAt?.let { "depuis ${formatter.time(it)}" } ?: "en cours"`
  - COMPLETED: `"le $whenLabel"`

Keep the existing `type`, `tag`, `whenLabel`, `listing`, `expiresAt`, `expiresIn` and `urgent` values for the Espace agent.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=VisitCardsTest test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCards.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/VisitCardsTest.kt
git commit -m "feat(npc): add visit card state and block wording"
```

---

### Task 2: Build the pending-visit callout model

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/PendingVisit.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/PendingVisitTest.kt`

**Interfaces:**
- Consumes: `VisitCardView` from Task 1, `VisitStatus.REQUESTED`.
- Produces:
```kotlin
data class PendingVisitView(val label: String, val url: String)

data class PendingVisitCandidate(
    val visit: VisitCardView,
    /** "Sandrine Eboa" for a listing client, "Appartement · 3 chambres · annonce n° 3012" for a match. */
    val name: String,
    /** "match-9001" or "proposal-9002"; the URL becomes "#$anchor". */
    val anchor: String,
)

object PendingVisit {
    fun of(candidates: List<PendingVisitCandidate>): PendingVisitView?
}
```
`of` filters `visit.status == VisitStatus.REQUESTED`, orders by `visit.expiresAt` (nulls last), and returns:
- one request: `"1 visite à confirmer · ${expiresIn} — ${name}"`
- several: `"${n} visites à confirmer · la plus urgente dans ${expiresIn}"` (no name)
- none: `null`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `no requested visit - no callout`() {
    assertNull(PendingVisit.of(emptyList()))
}

@Test
fun `one requested visit names the client`() {
    val visit = card(7001, expiresIn = "42 h")
    val view = PendingVisit.of(listOf(PendingVisitCandidate(visit, "Sandrine Eboa", "proposal-9002")))!!

    assertEquals("1 visite à confirmer · 42 h — Sandrine Eboa", view.label)
    assertEquals("#proposal-9002", view.url)
}

@Test
fun `several requested visits use the soonest expiry and no name`() {
    val later = card(7002, expiresIn = "44 h", expiresAt = 2_000L)
    val sooner = card(7001, expiresIn = "8 h", expiresAt = 1_000L)

    val view = PendingVisit.of(
        listOf(
            PendingVisitCandidate(later, "Later", "match-9002"),
            PendingVisitCandidate(sooner, "Sooner", "match-9001"),
        ),
    )!!

    assertEquals("2 visites à confirmer · la plus urgente dans 8 h", view.label)
    assertEquals("#match-9001", view.url)
}
```

`card(...)` is a local helper building a `VisitCardView` with `status = VisitStatus.REQUESTED` and the given `expiresIn`/`expiresAt`; all other fields can be the existing fixture defaults.

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=PendingVisitTest test
```
Expected: FAIL — `PendingVisit` does not exist.

- [ ] **Step 3: Implement `PendingVisit`**

Keep the plural wording consistent with the prototype. Do not include the name in the multiple-visit label; the group headings below already name the cards.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=PendingVisitTest test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/visit/PendingVisit.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/visit/PendingVisitTest.kt
git commit -m "feat(npc): add pending visit callout model"
```

---

### Task 3: Merge visits into the match section

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchGroup.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt`

**Interfaces:**
- Consumes: `VisitCardView`, `VisitStatus`, `Match`, `ListingSummary`.
- Produces:
```kotlin
enum class MatchGroup(val label: String) {
    REQUESTED("À confirmer"),
    UPCOMING("Visites à venir"),
    INTERESTED("Intéressés"),
    NEW("Nouveaux"),
    WAITING("Envoyés, en attente"),
    EXPIRED("Sans réponse"),
}

data class MatchGroupView(val label: String, val matches: List<MatchView>)

data class MatchView(
    // existing fields unchanged
    val visit: VisitCardView? = null,
    /** "Envoyé le 28 oct. 2026"; the only meta line a visit card shows. */
    val sent: String? = null,
    val group: MatchGroup,
)

data class MatchSectionView(
    val matches: List<MatchView> = emptyList(),
    val dismissed: Int = 0,
    val unavailable: Boolean = false,
    val visitsUnavailable: Boolean = false,
    val outcomes: String? = null,
) {
    val groups: List<MatchGroupView> get() = matches.groupBy { it.group }.map { MatchGroupView(it.key.label, it.value) }
    val hasPendingVisit: Boolean get() = matches.any { it.visit?.status == VisitStatus.REQUESTED }
}

object MatchViews {
    fun section(
        mandate: Mandate,
        matches: List<Match>?,
        listings: Map<Long, ListingSummary>?,
        visits: Map<Long, VisitCardView>,
        locations: Map<Long, Location>,
        today: LocalDate,
        formatter: TenantFormatter,
    ): MatchSectionView
}
```
`MatchRules` loses `sort` and `proposals`; it gains:
```kotlin
fun outcomes(matches: List<Match>): String?
```
which returns `"1 refusé · 1 sans réponse"`-style counts for `REJECTED` and `EXPIRED`, or null when neither exists.

- [ ] **Step 1: Write the failing tests**

In `MatchViewsTest`, add `import com.wutsi.ndopify.npc.ui.visit.VisitFixtures`; update **every** direct `MatchViews.section(...)` call (not only the helper) to pass `emptyMap()` or a visit map after `LISTINGS`:
```kotlin
private fun section(
    matches: List<Match> = listOf(MATCH_B, MATCH_A, MATCH_C),
    visits: Map<Long, VisitCardView> = emptyMap(),
) = MatchViews.section(MANDATE, matches, LISTINGS, visits, LOCATIONS, TODAY, FORMATTER)
```

Add:
```kotlin
@Test
fun `a requested visit is attached to its match and leads the groups`() {
    val visit = visitCard(matchId = 9001, status = VisitStatus.REQUESTED, expiresAt = 1_000L)
    val section = section(
        matches = listOf(
            MATCH_A.copy(status = MatchStatus.ACCEPTED, proposedAt = VisitFixtures.date("2026-11-01T10:00:00Z")),
            MATCH_B.copy(status = MatchStatus.ACTIVE),
        ),
        visits = mapOf(9001L to visit),
    )

    val first = section.matches.first()
    assertEquals(9001L, first.id)
    assertEquals(MatchGroup.REQUESTED, first.group)
    assertEquals(visit, first.visit)
    assertEquals("Envoyé le 1 nov. 2026", first.sent)
    assertEquals(listOf("À confirmer", "Nouveaux"), section.groups.map { it.label })
    assertTrue(section.hasPendingVisit)
}

@Test
fun `outcomes counts refused and unanswered`() {
    val section = section(
        matches = listOf(
            MATCH_A.copy(status = MatchStatus.REJECTED),
            MATCH_B.copy(status = MatchStatus.EXPIRED),
            MATCH_C.copy(status = MatchStatus.ACTIVE),
        ),
    )

    assertEquals("1 refusé · 1 sans réponse", section.outcomes)
}
```

`visitCard(...)` is a local helper returning a `VisitCardView` with the requested fields (Task 1).

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=MatchViewsTest test
```
Expected: FAIL — `MatchViews.section` has no `visits` parameter, and `MatchGroup`, `groups`, `outcomes`, `hasPendingVisit` do not exist.

- [ ] **Step 3: Implement the match view model and sorting**

- Build the `MatchView` for each match whose listing is present.
- Attach `visits[match.id]`.
- Set `sent = match.proposedAt?.let { "Envoyé le ${formatter.date(it)}" }`.
- Set `group`:
  - visit REQUESTED -> `REQUESTED`
  - visit CONFIRMED/CHECKED_IN -> `UPCOMING`
  - no visit: ACCEPTED -> `INTERESTED`, ACTIVE -> `NEW`, PROPOSED -> `WAITING`, EXPIRED -> `EXPIRED`
- Sort by `group.ordinal`, then:
  - requested visits by `visit.expiresAt` (nulls last)
  - upcoming visits by `visit.at` (nulls last)
  - other groups by `match.score` descending
- `outcomes = MatchRules.outcomes(matches)`.
- `visitsUnavailable` is passed by the controller later; keep the default false here.
- Remove `proposals` from `MatchSectionView` and `MatchViews`.
- Remove `MatchRules.sort` and `MatchRules.proposals`; add `MatchRules.outcomes`.
- Update `MatchRulesTest`: remove the `sort` and `proposals` tests; add `outcomes` tests for null, refused only, expired only, both.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=MatchViewsTest,MatchRulesTest test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchGroup.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt
git commit -m "feat(npc): merge visits into match cards"
```

---

### Task 4: Merge visits into the listing proposals

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ProposalGroup.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`

**Interfaces:**
- Consumes: `VisitCardView`, `VisitStatus`, `Match`, `MandateSummary`, `CustomerSummary`.
- Produces:
```kotlin
enum class ProposalGroup(val label: String) {
    REQUESTED("À confirmer"),
    UPCOMING("Visites à venir"),
    INTERESTED("Intéressés"),
    WAITING("En attente"),
}

data class ProposalGroupView(val label: String, val rows: List<ProposalRow>)

data class ProposalRow(
    val matchId: Long,
    val client: String,
    val url: String?,
    val tag: MandateRules.Tag,
    /** "Envoyé le 28 oct. 2026" / "Intéressé le 29 oct. 2026"; for a visit card, the sent date only. */
    val sent: String,
    val visit: VisitCardView? = null,
    val group: ProposalGroup = ProposalGroup.WAITING,
)

data class ProposalsView(
    val rows: List<ProposalRow>,
    val outcomes: String? = null,
    val visitsUnavailable: Boolean = false,
) {
    val groups: List<ProposalGroupView> get() = rows.groupBy { it.group }.map { ProposalGroupView(it.key.label, it.value) }
}

data class ListingClientsView(
    val proposals: ProposalsView,
    val stats: VisitStats?,
    val interested: Int,
)
```
`ListingViews.clients` keeps its current parameters and returns this new shape.

- [ ] **Step 1: Write the failing tests**

In `ListingViewsTest`, update the `clients()` helper and assertions. Add:
```kotlin
@Test
fun `an open visit stays in its proposal card`() {
    val view = clients(listOf(visit())).proposals

    val row = view.rows.first()
    assertEquals(9002L, row.matchId)
    assertEquals("Sandrine Eboa", row.client)
    assertEquals(ProposalGroup.REQUESTED, row.group)
    assertEquals("Envoyé le 28 oct. 2026", row.sent)
    assertEquals(VisitStatus.REQUESTED, row.visit?.status)
    assertEquals(listOf("À confirmer", "Intéressés", "En attente"), view.groups.map { it.label })
}

@Test
fun `the refused client is counted, not listed`() {
    val view = clients().proposals

    assertEquals("1 refusé", view.outcomes)
    assertTrue(view.rows.none { it.matchId == 9004L })
}

@Test
fun `visits unavailable - every card stays, with a warning`() {
    val view = clients(null).proposals

    assertTrue(view.visitsUnavailable)
    assertEquals(3, view.rows.size)
    assertTrue(view.rows.all { it.visit == null })
}
```
Remove assertions on `view.summary`, `view.visits`, and the old "open visit leaves its proposal" expectations. Add a test that an `OTHER_AGENT` visit is not attached and does not enter the pending rows.

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=ListingViewsTest test
```
Expected: FAIL — `ProposalsView` has no `groups`, `outcomes` or `visitsUnavailable`; `ProposalRow` has no `matchId`, `visit` or `group`; `ListingClientsView` still exposes `visits`.

- [ ] **Step 3: Implement the listing view model**

- Build `owned` visits as today.
- Build `cards = VisitCards.of(owned, null, customersById, emptyMap(), now, tenant, formatter)`.
- Map `(cards.requested + cards.upcoming)` by `matchId`.
- `visible = matches.filter { status == ACCEPTED || status == PROPOSED }`; do **not** filter out visiting matches.
- For each match:
  - `visit = byMatch[match.id]`
  - `group`: visit requested -> `REQUESTED`; visit confirmed/checked-in -> `UPCOMING`; no visit + ACCEPTED -> `INTERESTED`; otherwise `WAITING`
  - `sent`: if visit != null, `match.proposedAt?.let { "Envoyé le ${formatter.date(it)}" }.orEmpty()`; else `MatchRules.answer(match, formatter).orEmpty()`
  - `matchId = match.id`
  - `tag = MatchRules.tag(match)`
  - `client`/`url` as today; another agent's client remains `OTHER_AGENT` with no URL and no visit.
- Sort rows by `group.ordinal`, then requested expiry / upcoming `visit.at`, then `answeredAt ?: proposedAt` descending.
- `outcomes = MatchRules.outcomes(matches)`.
- `visitsUnavailable = cards.unavailable`.
- Remove `proposalsSummary` and `ProposalsView.summary`.
- Remove `ListingClientsView.visits`.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=ListingViewsTest test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ProposalGroup.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt
git commit -m "feat(npc): merge visits into listing proposal cards"
```

---

### Task 5: Wire the mandate controller

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`

**Interfaces:**
- Consumes: `MatchViews.section(..., visits, ...)`, `MatchSectionView`, `ProposalsView`, `PendingVisit.of`, `VisitCardView`.
- Produces model attributes:
  - `matchSection: MatchSectionView?` (search only)
  - `listingProposals: ProposalsView?` (published listing only)
  - `pendingVisit: PendingVisitView?`
  - `searchStats` and `visitStats` unchanged
  - `listingVisits` and `visits` removed
- The controller no longer filters matches through `convertedMatchIds`.

- [ ] **Step 1: Write the failing tests**

In `MandateControllerTest`, replace the old "open visit replaces its match" test with:
```kotlin
@Test
fun `approved search - open visit stays in its match card`() {
    doReturn(VisitFixtures.NOW).whenever(clock).millis()
    withVisit(VisitFixtures.summary(VisitFixtures.requested().copy(mandateId = 201, matchId = 9001)))

    val result = mvc.perform(get("/mandates/201")).andReturn()
    val section = section(result)

    assertEquals(listOf(9001L, 9003L), section.matches.map { it.id })
    assertEquals(VisitStatus.REQUESTED, section.matches[0].visit?.status)
    assertEquals(MatchGroup.REQUESTED, section.matches[0].group)
    assertTrue(section.hasPendingVisit)
    assertEquals("#match-9001", (model(result)["pendingVisit"] as PendingVisitView).url)
    verify(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)
}
```

Add:
```kotlin
@Test
fun `approved search - visits unavailable keeps every match`() {
    withMatches()
    doThrow(ResourceAccessException("timeout")).whenever(visitClient).search(AGENT.id, VisitRules.COUNTED, 20, 201L)

    val result = mvc.perform(get("/mandates/201")).andReturn()

    assertTrue(section(result).visitsUnavailable)
    assertEquals(listOf(9001L, 9003L), section(result).matches.map { it.id })
    assertNull(model(result)["pendingVisit"])
}

@Test
fun `approved listing - open visit stays in its proposal card`() {
    doReturn(VisitFixtures.NOW).whenever(clock).millis()
    publishedListing()
    doReturn(
        listOf(
            VisitFixtures.summary(
                VisitFixtures.requested().copy(matchId = 9002, mandateId = 204, listingId = 501, customerId = 14),
            ),
        ),
    ).whenever(visitClient).searchByListings(AGENT.id, VisitRules.COUNTED, listOf(501L), 100)

    val result = mvc.perform(get("/mandates/201")).andReturn()
    val proposals = model(result)["listingProposals"] as ProposalsView

    assertEquals("Sandrine Eboa", proposals.rows.first().client)
    assertEquals(ProposalGroup.REQUESTED, proposals.rows.first().group)
    assertEquals(VisitStatus.REQUESTED, proposals.rows.first().visit?.status)
    assertEquals("#proposal-9002", (model(result)["pendingVisit"] as PendingVisitView).url)
}
```
Update the old `listingVisits` assertions to read `listingProposals.rows` instead. Remove `section(result).proposals`; use `section(result).outcomes`.

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=MandateControllerTest test
```
Expected: FAIL — the controller still hides converted matches and emits `listingVisits`/`visits`, and the new signatures are not wired.

- [ ] **Step 3: Implement the controller wiring**

- For a search:
  - Build `cards = VisitCards.of(visits, visitListings, emptyMap(), locations, now, tenant, formatter)`.
  - `val byMatch = (cards.requested + cards.upcoming).mapNotNull { v -> v.matchId?.let { it to v } }.toMap()`.
  - `val section = MatchViews.section(mandate, matches, listings, byMatch, locations, today(), formatter).copy(visitsUnavailable = cards.unavailable)`.
  - `pendingVisit = PendingVisit.of(section.matches.mapNotNull { m -> m.visit?.let { PendingVisitCandidate(it, "${m.title} · annonce n° ${m.listingId}", "match-${m.id}") } })`.
  - Add `matchSection` and `pendingVisit` to the model.
  - Remove `model.addAttribute("visits", cards)` and `convertedMatchIds`.
- For a listing:
  - `val clients = listingClients(agent, listing)`.
  - Add `listingProposals = clients?.proposals`.
  - `pendingVisit = clients?.proposals?.let { p -> PendingVisit.of(p.rows.mapNotNull { r -> r.visit?.let { PendingVisitCandidate(it, r.client, "proposal-${r.matchId}") } }) }`.
  - Remove `listingVisits`.
- Add `pendingVisit` once at the end; one branch is always null for a given mandate type.
- Keep `searchStats`, `visitStats`, `mandateListing`, and the close/cancel form behavior unchanged.
- Delete `convertedMatchIds` and its KDoc.

- [ ] **Step 4: Run the tests to verify they pass**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=MandateControllerTest test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt
git commit -m "feat(npc): emit merged visit cards from the mandate controller"
```

---

### Task 6: Render the merged cards in Thymeleaf and CSS

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/visit-detail.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `MatchSectionView.groups`, `match.visit`, `match.sent`, `ProposalsView.groups`, `row.visit`, `row.sent`, `pendingVisit`, `outcomes`, `visitsUnavailable`.
- Produces: the served card DOM used by `mandate.html`.

**Fragment contract:**
```html
<div th:fragment="detail(v, label)" class="visit-detail"
     th:attr="data-state=${v.status.name().toLowerCase()},data-urgent=${v.urgent} ? '' : null">
  <p class="visit-detail__when">
    <!-- house or camera icon, from v.video -->
    <span><strong th:text="${v.kindLabel}">Visite demandée</strong> ·
      <span th:text="${v.whenText}">pour le …</span></span>
  </p>
  <p class="visit-detail__timer" th:if="${v.expiresIn != null}">
    <svg …clock…></svg>
    <span>Il reste <strong th:text="${v.expiresIn}">42 h</strong> pour confirmer</span>
  </p>
  <a class="btn btn--primary btn--compact visit-detail__cta"
     th:if="${v.status.name() == 'REQUESTED'}"
     th:href="@{/visits/{id}(id=${v.id})}"
     th:attr="aria-label=|Confirmer la visite — ${label} — ${v.whenLabel}|">Confirmer la visite</a>
  <a class="btn btn--outline btn--compact visit-detail__cta"
     th:unless="${v.status.name() == 'REQUESTED'}"
     th:href="@{/visits/{id}(id=${v.id})}"
     th:attr="aria-label=|Voir la visite — ${label} — ${v.whenLabel}|">Voir la visite</a>
</div>
```

- [ ] **Step 1: Write the failing template tests**

In `MandateTemplateTest`, add/update tests that assert:
```kotlin
// Search, with a requested visit
val card = page.selectFirst("[data-match-id=9001]")!!
assertNull(card.selectFirst("[data-match-tag]")) // tag hidden when a visit exists
assertEquals("Visite demandée", card.selectFirst("[data-visit-kind]")?.text())
assertEquals("pour le sam. 7 novembre 2026 à 10:30", card.selectFirst("[data-visit-when]")?.text())
assertEquals("Il reste 42 h pour confirmer", card.selectFirst("[data-visit-timer]")?.text())
assertEquals("Confirmer la visite", card.selectFirst("[data-visit-link]")?.text())
assertEquals("Envoyé le 28 oct. 2026", card.selectFirst("[data-match-answer]")?.text())
assertTrue(card.selectFirst("[data-match-send]")!!.hasClass("btn--secondary"))

// Approved panel
val pending = page.selectFirst("[data-mandate-pending-visit]")!!
assertEquals("1 visite à confirmer · 42 h — Appartement · 3 chambres · annonce n° 3012", pending.text())
assertEquals("#match-9001", pending.attr("href"))

// Groups and outcomes
assertEquals("À confirmer (1)", page.selectFirst("[data-matches-section] .assignments__group-title")?.text())
assertTrue(page.select("[data-matches-section] .assignments__group-title").all { it.text().contains("(") })
assertEquals("1 refusé · 1 sans réponse", page.selectFirst("[data-match-outcomes]")?.text())
assertNull(page.selectFirst("section[data-mandate-visits]"))
assertTrue(page.selectFirst("[data-contact-whatsapp]")!!.hasClass("btn--secondary"))
```

For the listing page:
```kotlin
val card = page.selectFirst("[data-proposal][id^=proposal-]")!!
assertNull(card.selectFirst("[data-proposal-tag]"))
assertEquals("Visite demandée", card.selectFirst("[data-visit-kind]")?.text())
assertEquals("Confirmer la visite", card.selectFirst("[data-visit-link]")?.text())
assertEquals("1 refusé", page.selectFirst("[data-proposal-outcomes]")?.text())
assertNull(page.selectFirst("section[data-listing-visits]"))
```

Also assert:
- `[data-match-in-visit]` and `[data-proposals-in-visit]` are absent.
- The empty state still appears when there are no matches/rows.
- The search-stats line stays inside `[data-panel=APPROVED]`.
- The visits-unavailable alert appears when `ms.visitsUnavailable`/`p.visitsUnavailable` is true.
- The pending callout is absent when there is no requested visit.

- [ ] **Step 2: Run the tests to verify they fail**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=MandateTemplateTest test
```
Expected: FAIL — the template still renders the old sections and no visit block.

- [ ] **Step 3: Update the template**

- Add the fragment above.
- Contact card: change the WhatsApp button to `btn btn--secondary btn--compact` so the pending visit's Confirmer remains the page-level primary.
- Approved panel: add the pending callout above the search-stats line:
```html
<a class="mandate-pending" th:if="${pendingVisit}" th:href="${pendingVisit.url}" data-mandate-pending-visit>
  <svg …clock…></svg>
  <span th:text="${pendingVisit.label}">1 visite à confirmer · 8 h — Sandrine Eboa</span>
  <svg …chevron…></svg>
</a>
```
- Match section:
  - Wrap the groups in `<div data-match-list th:if="${!#lists.isEmpty(ms.matches)}">`; iterate `ms.groups`; print `<p class="assignments__group-title" th:text="|${group.label} (${#lists.size(group.matches)})|">`; then one `<ul class="assignments">` per group.
  - Card id `|match-${match.id}|`; header tag only `th:if="${match.visit == null}"`.
  - Meta line: `th:if="${match.visit != null ? !#strings.isEmpty(match.sent) : match.answer != null}"`, text `th:text="${match.visit != null ? match.sent : match.answer}"`.
  - Visit block: `<div th:replace="~{fragments/visit-detail :: detail(${match.visit}, ${match.title + ' · annonce n° ' + match.listingId})}"></div>` when `match.visit != null`.
  - Send buttons: replace the static class with `th:class="${ms.hasPendingVisit} ? 'btn btn--secondary' : 'btn btn--primary'"`.
  - Visits-unavailable alert under the section head.
  - Outcomes line `th:if="${ms.outcomes}"`.
  - Empty state only when `#lists.isEmpty(ms.matches) && ms.outcomes == null`.
  - Remove `data-match-in-visit` and the old `data-match-proposals` block (already removed).
- Listing section:
  - Rename heading to `Propositions et visites`.
  - Intro: `Les visites à confirmer d'abord.`
  - Wrap groups in `<div data-proposals-list th:if="${!#lists.isEmpty(p.rows)}">`; iterate `p.groups`; print the same group title; one `<ul class="assignments">` per group.
  - Card `<li class="assignment proposal-card" ...>` with id `|proposal-${row.matchId}|` and `data-proposal`; `<article th:attr="aria-labelledby=|proposal-${row.matchId}-title|">`; title id; link/protected as today; tag only `th:if="${row.visit == null}"`.
  - Visit block fragment with `row.visit` and `row.client`.
  - Meta line `th:if="${!#strings.isEmpty(row.sent)}"` and `th:text="${row.sent}"`.
  - Visits-unavailable alert.
  - Outcomes line `th:if="${p.outcomes}"`.
  - Empty state only when `p.rows.isEmpty && p.outcomes == null`.
  - Remove `data-proposals-in-visit` and the old summary (already removed).
- Remove both separate Visites sections (`data-listing-visits` and `data-mandate-visits`) entirely.
- Update the template's top comment to describe the merged cards, groups, pending callout and outcomes.

- [ ] **Step 4: Add the served CSS**

In `src/main/resources/public/assets/css/visit.css`, add the `.visit-detail`, `.visit-detail__when`, `.visit-detail__icon`, `.visit-detail__timer`, `[data-urgent]` and `.visit-detail__cta` rules copied from the prototype's `assets/css/visit.css`; the block is a flex column with a 1px neutral border and `--color-surface-alt` background. Remove `.match__visit-cta` if it is now unused.

In `src/main/resources/public/assets/css/home.css`, add:
- `.mandate-pending` (warning triad, flex row, arrow pushed right)
- `.assignments__group-title` and `+ .assignments` spacing
- `.match__outcomes`
- `.proposal-card` title link 44px hit area
- `.match .assignment__details summary { min-height: var(--target); }`
- the existing `.mandate-card__proposals` rules are already gone.

- [ ] **Step 5: Run the template tests**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central -Dtest=MandateTemplateTest test
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/visit-detail.html \
        modules/ndopify-partner-central/src/main/resources/templates/mandate.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/visit.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): render merged visit cards on the mandate page"
```

---

### Task 7: Remove dead model fields and verify the whole module

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt`
- Test: any test that still references removed fields.

**Interfaces:**
- No new public API. This task proves `MatchRules.sort`, `MatchRules.proposals`, `MatchSectionView.proposals`, `ProposalsView.summary`, `listingVisits`, `visits` (the mandate search model attribute), `convertedMatchIds` and `data-match-proposals`/`data-proposals-summary` are gone and nothing references them.

- [ ] **Step 1: Find the leftovers**

Run:
```bash
grep -R "MatchRules.sort\|MatchRules.proposals\|MatchSectionView.proposals\|ProposalsView.summary\|listingVisits\|convertedMatchIds\|data-match-proposals\|data-proposals-summary\|data-listing-visits\|data-mandate-visits" \
  modules/ndopify-partner-central/src/main modules/ndopify-partner-central/src/test
```
Expected: no matches except possibly in the prototype under `src/main/html` (ignore those) or historical docs.

- [ ] **Step 2: Remove any real leftovers**

Delete the dead fields, helpers and tests rather than keeping aliases. Update KDocs that still say a client with a visit leaves the Propositions, or that a match is replaced by a visit.

- [ ] **Step 3: Run the full module tests**

Run:
```bash
mvn -q -pl modules/ndopify-partner-central test
```
Expected: PASS, including the coverage gate. If the line/class gate fails, add the missing branch tests (empty groups, unavailable visits, null expiry, another-agent visit) rather than excluding code.

- [ ] **Step 4: Run the served template layout detector**

Run:
```bash
/Users/herve/.config/opencode/skills/impeccable/scripts/impeccable detect --json --scope layout \
  modules/ndopify-partner-central/src/main/resources/templates/mandate.html
```
Expected: `[]` and exit 0.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main modules/ndopify-partner-central/src/test
git commit -m "refactor(npc): remove the superseded proposal summaries and visit lists"
```

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-11-07-partner-central-merged-visit-cards.md`.

This plan has seven tightly coupled tasks: the view models feed the controller, and the template consumes both. A shipped mistake in the view-model shape would ripple through every later task, so I recommend **subagent-driven development**: a fresh implementer and reviewer per task, with the whole-branch review at the end.

The plan intentionally leaves completed visits out of the merged cards for this pass. If you want them shown as *Visités* with their own group, that is a small follow-up plan after the open-visit flow ships.
