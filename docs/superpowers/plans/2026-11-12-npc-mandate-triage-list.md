# NPC Mandate Cards — Identity, Triage Order, Fold Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Espace agent's *Mes mandats* findable at a glance: title each card with the client's name, order it for triage, and show five cards with the rest folded.

**Architecture:** The BFF (`ndopify-partner-central`) loads the agent's active mandates with the API's maximum page size, batch-loads their customers, orders them by status rank then creation date, and splits them into a five-card preview and a folded remainder. Both lists render one shared Thymeleaf card fragment. No ndopify-server or ndopify-dto change.

**Tech Stack:** Kotlin, Spring MVC, Thymeleaf, Maven, Mockito-Kotlin, `MockRestServiceServer`, `kotlin.test`, HTML/CSS.

**Spec:** `docs/superpowers/specs/2026-11-12-npc-mandate-triage-list-design.md`

## Global Constraints

- French copy, English code and comments.
- No ndopify-server or ndopify-dto change: use the existing `GET /v1/mandates` with `limit=100`.
- Reuse the existing tier-2 CSS tokens; no new colours.
- Every secondary read degrades locally: a failed customer, stats or listing lookup only drops that line.
- ktlint runs on `validate`; keep the build green.
- Do not touch the prototype's unrelated files, `visit.css`, `visit.html`, or the in-flight listing/offer changes in
  the same working tree.

## Review Focus

1. **No client name:** the customer lookup fails or returns an empty name. Expect the old party/type title and no
   second line — pinned in Task 2 and Task 5.
2. **Equal creation dates:** two mandates of the same status created at the same instant. Expect a deterministic,
   newest-id-first order — pinned in Task 3 and Task 4.
3. **A sixth mandate:** five cards plus one folded, singular label `Voir l'autre mandat` — pinned in Task 5.
4. **A card in the folded list:** it must keep its listing line and activity lines, exactly like a visible card —
   pinned in Task 5 by rendering the same fragment.
5. **Customer lookup failing must not fail the page:** the home still renders the cards with fallback titles — pinned
   in Task 4.

---

### Task 1: Load the full active list

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt`

**Interfaces:**
- Produces: `NdMandateClient.ACTIVE_LIMIT: Int = 100`; `active(agentId: Long): List<MandateSummary>` keeps its
  signature and always requests `limit=100`.

- [ ] **Step 1: Update the failing request expectation**

In `NdMandateClientTest.active`, expect
`http://localhost:8080/v1/mandates?agent-id=20&status=ASSIGNED&status=PENDING_APPROVAL&status=APPROVED&limit=100`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=NdMandateClientTest#active test` in `modules/ndopify-partner-central`.
Expected: FAIL — the request has no `limit` parameter.

- [ ] **Step 3: Add the limit**

Add `const val ACTIVE_LIMIT = 100` to a `companion object` on `NdMandateClient`; append `&limit={limit}` to the
`active` URI and pass `ACTIVE_LIMIT`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=NdMandateClientTest#active test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClient.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateClientTest.kt
git commit -m "feat(npc): load up to 100 active mandates for the home page"
```

---

### Task 2: Put the client's name on the card

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt`

**Interfaces:**
- Produces: `MandateCardView.clientName: String?` (null when unknown).
- Produces: `MandateCards.of(..., customers: Map<Long, CustomerSummary>? = null)`; `clientName` is
  `"first last"` from `CustomerSummary.party`, trimmed and blank-collapsed to null.

- [ ] **Step 1: Write the failing tests**

In `MandateCardsTest`, extend the `card(...)` helper with `customers: Map<Long, CustomerSummary>? = null` and pass it
to `MandateCards.of`. Add:
- `client name leads`: customer 555 with `Party(firstName = "Sandrine", lastName = "Eboa")` → `clientName ==
  "Sandrine Eboa"`.
- `no customer - no client name`: null map and empty map → `clientName == null`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=MandateCardsTest test`.
Expected: FAIL — `clientName` and the `customers` parameter do not exist.

- [ ] **Step 3: Implement**

Add `clientName: String? = null` after `title` in `MandateCardView`. Add the `customers` parameter to
`MandateCards.of` and a private `clientName(mandate, customers)` helper joining the trimmed first and last names,
returning null when empty.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=MandateCardsTest test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt
git commit -m "feat(npc): title mandate cards with the client's name"
```

---

### Task 3: Add the triage order and the five-card page size

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt`

**Interfaces:**
- Produces: `MandateCards.PAGE_SIZE: Int = 5`.
- Produces: `MandateCards.order(mandates: List<Mandate>): List<Mandate>` — rank `ASSIGNED=0`,
  `PENDING_APPROVAL=1`, `APPROVED=2`, unknown last; then `createdAt` descending; then `id` descending.

- [ ] **Step 1: Write the failing test**

Add `order - actionable first, newest within a status`: four mandates with ids/dates
`(1, APPROVED, 1000)`, `(2, ASSIGNED, 500)`, `(3, PENDING_APPROVAL, 2000)`, `(4, ASSIGNED, 1500)`; assert
`MandateCards.order(...).map { it.id } == listOf(4L, 2L, 3L, 1L)`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -Dtest=MandateCardsTest test`.
Expected: FAIL — `order` does not exist.

- [ ] **Step 3: Implement**

Add `const val PAGE_SIZE = 5`, a private `STATUS_RANK` map, and `order` using
`compareBy { rank }.thenByDescending { createdAt }.thenByDescending { id }`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -Dtest=MandateCardsTest test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCards.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateCardsTest.kt
git commit -m "feat(npc): add the mandate triage order and page size"
```

---

### Task 4: Wire the home controller

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt`

**Interfaces:**
- Consumes: `MandateCards.order`, `MandateCards.PAGE_SIZE`, `MandateCards.of(..., customers)`.
- Produces model attributes: `mandates` (first five cards), `foldedMandates` (the rest), `foldedMandatesLabel`
  (`Voir l'autre mandat` / `Voir les N autres mandats`).

- [ ] **Step 1: Write the failing tests**

Add to `HomeControllerTest`:
- `mandates - five cards, the rest folded`: seven `ASSIGNED` mandates 201..207 → visible `207,206,205,204,203`,
  folded `202,201`.
- `mandate card titled with the client name`: stub `customerClient.search(listOf(555L))` with Sandrine Eboa →
  `mandates(result).single().clientName == "Sandrine Eboa"`.
Update `mandates shown` to expect the triage order `201, 202, 203`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=HomeControllerTest test`.
Expected: FAIL — `foldedMandates` is absent and the order is unchanged.

- [ ] **Step 3: Implement**

In wave 2, add `loadMandateCustomers(mandates)` beside the other reads. After the join, order with
`MandateCards.order`, split at `PAGE_SIZE`, and write `mandates`, `foldedMandates` and `foldedMandatesLabel` through
`MandateCards.of(..., customers = mandateCustomers)`. The customer read returns an empty map on `RestClientException`
and is skipped when there are no customer ids.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=HomeControllerTest test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/home/HomeController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeControllerTest.kt
git commit -m "feat(npc): order and fold the home page mandates"
```

---

### Task 5: Render the name-first card and the folded list

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-card.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/index.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt`

**Interfaces:**
- Consumes: `MandateCardView.clientName`, `title`, `owner`, `tag`, `facts`, `listing`, `stats`, `searchStats`.
- Produces: fragment `fragments/mandate-card :: mandateCard(m)` rendering the `<li class="mandate-card">`.

- [ ] **Step 1: Write the failing template tests**

Add to `HomeTemplateTest`:
- `mandate card titled with the client name`: stub customer 555 Sandrine Eboa → link text `Sandrine Eboa` and
  `.mandate-card__kind` text `Locataire · Maison`.
- `mandates - five cards, the rest folded`: seven mandates 201..207 → main list `207..203`, fold summary
  `Voir les 2 autres mandats`, folded cards `202,201`.
- `one folded mandate - singular label`: six mandates → `Voir l'autre mandat`.
Update the landlord's-listing test to select cards by `data-mandate-id` (the order now follows the triage rule).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=HomeTemplateTest test`.
Expected: FAIL — no `.mandate-card__kind`, no `[data-mandate-more]`.

- [ ] **Step 3: Implement**

Create the fragment with the link text `${m.clientName ?: m.title}` and a `.mandate-card__kind` line rendered only
when `m.clientName != null`. In `index.html`, loop `mandates` through the fragment, then render
`foldedMandates` inside `<details class="mandate-more" data-mandate-more>` with the `foldedMandatesLabel` summary.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=HomeTemplateTest test`.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-card.html \
        modules/ndopify-partner-central/src/main/resources/templates/index.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/home/HomeTemplateTest.kt
git commit -m "feat(npc): render the client name and the folded mandate list"
```

---

### Task 6: Style the two-line title and the fold

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css`

**Interfaces:**
- Produces: `.mandate-title__text` (column layout), `.mandate-card__kind` (`--step-hint`, weight 400),
  `.mandate-more` (summary link and spacing, same affordance as `.visit-more`).

- [ ] **Step 1: Add the styles**

Add `.mandate-title__text { display: flex; flex-direction: column; }` and
`.mandate-card__kind { font-size: var(--step-hint); font-weight: 400; }` beside `.mandate-title`.
Add `.mandate-more` spacing plus a `min-height: var(--target)` underlined summary, and
`.mandate-more > .assignments { margin-top: var(--space-4); }`.

- [ ] **Step 2: Verify the page renders**

Run the template tests again (`mvn -Dtest=HomeTemplateTest test`) and inspect the prototype at 390 px and 1280 px;
the name leads, the party/type is one step smaller, and the fold opens without layout shift.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/home.css
git commit -m "style(npc): two-line mandate titles and folded list"
```

---

### Task 7: Keep the prototype aligned

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/index.html`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/home.css`

**Interfaces:**
- The prototype mirrors the served markup: `data-mandate-card`, `data-mandate-kind-label`, `data-mandate-more`,
  `data-mandate-more-list`, `data-mandate-more-label`.

- [ ] **Step 1: Add fixtures and markup**

Give every `MANDATES` fixture a `createdAt` (days ago); add 205–207 so the fold is demonstrable; add their static
cards; add the folded `<details>` after the main list.

- [ ] **Step 2: Add the client name and the layout**

`clientNameOf(id)` returns `MANDATES[id].contact.name`; `initMandates` renders the name as the link, the party/type
as `.mandate-card__kind`, then sorts by status rank and `createdAt` and moves cards beyond five into the fold.

- [ ] **Step 3: Verify in a browser**

Open `index.html` at 390 px and 1280 px: first five cards in triage order, `Voir les 2 autres mandats`, the fold
opens, and accepting an offer re-files the new card. No console errors.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/index.html \
        modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js \
        modules/ndopify-partner-central/src/main/html/assets/css/partner-central.css \
        modules/ndopify-partner-central/src/main/html/assets/css/home.css
git commit -m "docs(npc): align the mandate prototype with the served list"
```

---

### Task 8: Verify the branch

- [ ] **Step 1: Run the full module suite**

Run: `mvn test` in `modules/ndopify-partner-central`.
Expected: `BUILD SUCCESS`, zero failures.

- [ ] **Step 2: Confirm the diff**

Run: `git diff --stat main...HEAD` and check that only the mandate/prototype/BFF files are touched; no
ndopify-server or ndopify-dto change.

- [ ] **Step 3: Report**

Record the branch name and the test count, and note the known 100-mandate cap.
