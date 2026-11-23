# The Property Wizards' Project Pill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show whether *Proposer un bien* is a sale or a rental, and *Trouver un bien* a purchase or a rental, on every
step after the first and on the confirmation — as a read-only pill rendered by the server, without JavaScript.

**Architecture:** Two pure rules, `OfferPropertyRules.projectLabel` ("Vente" / "Location") and
`FindPropertyRules.projectLabel` ("Achat" / "Location"), turn the saved `listingType` into the word. Each controller
adds it to the model on every step and on the confirmation branch; each template puts it in a `div.progress__head`
beside the step count (from step 2 on) and in a `div.confirmation__head` under the confirmation title. One shared CSS
block in `partner-central.css` styles both pages.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, the esbuild `partner-central` bundle, JUnit 5 + `kotlin.test`,
MockMvc, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/278

**Approved prototypes (the baseline to port, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/offer-property.html` — the seller's pill (already on `main`)
- `modules/ndopify-partner-central/src/main/html/v2/find-property.html` — the buyer's pill
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/offer-property.js`,
  `modules/ndopify-partner-central/src/main/html/v2/assets/js/find-property.js`
- `modules/ndopify-partner-central/src/main/html/v2/assets/css/partner-central.css` — the shared `.progress__head`,
  `.project-tag` and `.confirmation__head`
- `modules/ndopify-partner-central/src/main/html/v2/README.md`

The #273 story and plan no longer carry the indicator: this grooming moved it here, and #273 keeps only its land-sale
defect. Do not add the pill to #273's implementation.

## Global Constraints

- **The prototype is the design.** Port its markup, classes and values verbatim; change no wording, order or state it
  already settled. `src/main/html/` is never edited by the implementation.
- **French UI, English code.** Every visible word is French; classes, KDoc, comments, logs, plan and commits are
  English.
- **Server-rendered, no JavaScript.** The template renders the word from the saved `listingType`; the page script only
  enhances the same state. A no-JS visitor sees the pill on every step after the first and on the confirmation.
- **Read-only text.** The pill is a `<p>`, never a link, a button, a select or a toggle; no `role`, no `tabindex`, no
  focus target. Step 1 keeps the only control.
- **One shared style.** `.progress__head`, `.project-tag` and `.confirmation__head` live once in
  `src/main/assets/css/partner-central.css`; no copy in `offer-property.css` or `find-property.css`.
- **No server, DTO or web-core change.** `modules/ndopify-partner-central` only; no new dependency, no `pom.xml`
  change, no `application*.yml` change.
- `mvn verify -Dheadless=true` green in `ndopify-partner-central` (ktlint, tests, JaCoCo).
- Commits are conventional and scoped `feat(npc): …` / `test(npc): …`, each with `Refs #278` in the body.

## Review Focus

- **Step 1 never shows the pill**, even when `?type=SALE|RENTAL` preselected the project into the session answers.
- **The pill reads the saved project, not a default**: a sale step 2 reads *Vente*, a rental *Location*; the
  confirmation reads the **submitted** project, even when the pending answers say otherwise.
- **The buyer says *Achat*** (the review's noun), not *Acheter* (the first step's verb); the seller says *Vente*.
- **The pill is text**: a `<p>` with no link, button, `role` or `tabindex`, and its word is preceded by the visually
  hidden *Projet :* for screen readers.
- **One style, no drift**: both pages share the three classes in `partner-central.css`; the seller page's rendering is
  unchanged.

---

### Task 1: The seller's project pill on *Proposer un bien*

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/partner-central.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Produces on `OfferPropertyRules`:

```kotlin
/** "Vente" / "Location", the owner's project for every step after the first; rental when unanswered. */
fun projectLabel(listingType: ListingType?): String =
    if (listingType == ListingType.SALE) "Vente" else "Location"
```

- Produces in the `OfferPropertyController.step` model, on every step: `projectLabel =
  OfferPropertyRules.projectLabel(answers.listingType)`. The confirmation branch in `show` also adds
  `projectLabel = OfferPropertyRules.projectLabel(submitted.listingType)`.
- Produces in `offer-property.html`: the progress label wrapped in `div.progress__head` with
  `<p class="project-tag" th:if="${step != 'property'}"><span class="visually-hidden">Projet&nbsp;: </span><span
  data-project-label th:text="${projectLabel}">Vente</span></p>`; the confirmation's `h2` wrapped in
  `div.confirmation__head` with the same `<p class="project-tag">` under it (no `th:if`).
- Produces in `assets/css/partner-central.css`: `.progress__head` (flex, wraps, baseline, space-between, `--space-1`
  / `--space-3` gap), `.project-tag` (inline-flex, `--space-2` padding, `--color-border` hairline, `--radius-control`,
  `--color-surface-alt`, `--color-fg`, `--step-hint`, 600, line-height 1.6, nowrap) and `.confirmation__head` (column,
  centred, `--space-2` gap), copied from the approved prototype.

- [ ] **Step 1: Write the failing rules test**

In `OfferPropertyRulesTest`:

```kotlin
@Test
fun projectLabel() {
    assertEquals("Vente", OfferPropertyRules.projectLabel(ListingType.SALE))
    assertEquals("Location", OfferPropertyRules.projectLabel(ListingType.RENTAL))
    assertEquals("Location", OfferPropertyRules.projectLabel(null))
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest#projectLabel`
Expected: compilation failure, `projectLabel` unresolved.

- [ ] **Step 3: Add `OfferPropertyRules.projectLabel`**

As in *Interfaces*.

- [ ] **Step 4: Write the failing controller and template tests**

In `OfferPropertyControllerTest` (it already has the `complete` rental and `sale` answers and a `model(result)` helper):

```kotlin
@Test
fun `the project label follows the saved project`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale)
    val saleStep = mvc.perform(get("/offer-property?step=features").session(session)).andReturn()
    assertEquals("Vente", model(saleStep)["projectLabel"])

    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)
    val rentalStep = mvc.perform(get("/offer-property?step=features").session(session)).andReturn()
    assertEquals("Location", model(rentalStep)["projectLabel"])
}

@Test
fun `the confirmation carries the submitted project label`() {
    session.setAttribute(
        OfferPropertyController.SUBMITTED_ATTRIBUTE,
        SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
    )
    val result = mvc.perform(get("/offer-property?step=confirmation").session(session)).andReturn()
    assertEquals("Vente", model(result)["projectLabel"])
}
```

In `OfferPropertyTemplateTest` (it already has `complete`, `sale` and the `render` helper):

```kotlin
@Test
fun `every step after the first shows the project pill`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, sale)

    val page = render(get("/offer-property?step=features"))

    val pill = assertNotNull(page.selectFirst(".progress__head .project-tag"))
    assertEquals("Vente", pill.selectFirst("[data-project-label]")?.text())
    assertEquals("p", pill.tagName())
    assertEquals("", pill.attr("role"))
    assertEquals("", pill.attr("tabindex"))
    assertNull(pill.selectFirst("a, button"))
}

@Test
fun `a rental step reads Location`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

    val page = render(get("/offer-property?step=features"))

    assertEquals("Location", page.selectFirst(".progress__head [data-project-label]")?.text())
}

@Test
fun `step 1 shows no project pill`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.SALE))

    assertNull(render(get("/offer-property?step=property")).selectFirst(".project-tag"))
}

@Test
fun `the confirmation shows the submitted project under the title`() {
    session.setAttribute(
        OfferPropertyController.SUBMITTED_ATTRIBUTE,
        SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
    )

    val page = render(get("/offer-property?step=confirmation"))

    assertEquals("Vente", page.selectFirst(".confirmation__head [data-project-label]")?.text())
    assertEquals("Bien enregistré", page.selectFirst("#confirmation-title")?.text())
}
```

- [ ] **Step 5: Run them and verify they fail**

Run: `mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: the new assertions fail (no `projectLabel`, no `.project-tag`).

- [ ] **Step 6: Implement the controller, the template and the CSS**

Add the attribute in `step()` right after `step` / `stepNumber`, and in the confirmation branch of `show`. Port the
approved prototype's markup and the shared CSS block as in *Interfaces*. The progress block is inside the existing
`th:block th:if="${step != 'confirmation'}"`; the confirmation's own header is in its `section`.

- [ ] **Step 7: Run the tests and the module**

Run: `mvn test -Dtest='OfferPropertyRulesTest+OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty modules/ndopify-partner-central/src/main/resources/templates/offer-property.html modules/ndopify-partner-central/src/main/assets/css/partner-central.css modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty
git commit -m "feat(npc): show the seller wizard's project on every step" -m "Refs #278"
```

---

### Task 2: The buyer's project pill on *Trouver un bien*

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`

**Interfaces:**
- Produces on `FindPropertyRules`:

```kotlin
/** "Achat" / "Location", the buyer's project for every step after the first; rental when unanswered. */
fun projectLabel(listingType: ListingType?): String =
    if (listingType == ListingType.SALE) "Achat" else "Location"
```

- Produces in the `FindPropertyController.step` model, on every step: `projectLabel =
  FindPropertyRules.projectLabel(answers.listingType)`. The confirmation branch in `show` also adds
  `projectLabel = FindPropertyRules.projectLabel(submitted.listingType)`.
- Produces in `find-property.html`: the progress label wrapped in `div.progress__head` with
  `<p class="project-tag" th:if="${step != 'need'}"><span class="visually-hidden">Projet&nbsp;: </span><span
  data-project-label th:text="${projectLabel}">Achat</span></p>`; the confirmation's `h2` wrapped in
  `div.confirmation__head` with the same `<p class="project-tag">` under it (no `th:if`). No new CSS: Task 1's shared
  block already styles it.

- [ ] **Step 1: Write the failing rules test**

In `FindPropertyRulesTest`:

```kotlin
@Test
fun projectLabel() {
    assertEquals("Achat", FindPropertyRules.projectLabel(ListingType.SALE))
    assertEquals("Location", FindPropertyRules.projectLabel(ListingType.RENTAL))
    assertEquals("Location", FindPropertyRules.projectLabel(null))
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest#projectLabel`
Expected: compilation failure, `projectLabel` unresolved.

- [ ] **Step 3: Add `FindPropertyRules.projectLabel`**

As in *Interfaces*.

- [ ] **Step 4: Write the failing controller and template tests**

In `FindPropertyControllerTest` (it already has `complete` and the `model(result)` helper):

```kotlin
@Test
fun `the project label follows the saved project`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(listingType = ListingType.SALE))
    val saleStep = mvc.perform(get("/find-property?step=area").session(session)).andReturn()
    assertEquals("Achat", model(saleStep)["projectLabel"])

    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)
    val rentalStep = mvc.perform(get("/find-property?step=area").session(session)).andReturn()
    assertEquals("Location", model(rentalStep)["projectLabel"])
}

@Test
fun `the confirmation carries the submitted project label`() {
    session.setAttribute(
        SubmittedMandate.SESSION_ATTRIBUTE,
        SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
    )
    val result = mvc.perform(get("/find-property?step=confirmation").session(session)).andReturn()
    assertEquals("Achat", model(result)["projectLabel"])
}
```

In `FindPropertyTemplateTest` (it already has `complete` and the `render` helper):

```kotlin
@Test
fun `every step after the first shows the project pill`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete.copy(listingType = ListingType.SALE))

    val page = render(get("/find-property?step=area"))

    val pill = assertNotNull(page.selectFirst(".progress__head .project-tag"))
    assertEquals("Achat", pill.selectFirst("[data-project-label]")?.text())
    assertEquals("p", pill.tagName())
    assertEquals("", pill.attr("role"))
    assertEquals("", pill.attr("tabindex"))
    assertNull(pill.selectFirst("a, button"))
}

@Test
fun `a rental step reads Location`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

    val page = render(get("/find-property?step=area"))

    assertEquals("Location", page.selectFirst(".progress__head [data-project-label]")?.text())
}

@Test
fun `step 1 shows no project pill`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, PendingMandate(listingType = ListingType.SALE))

    assertNull(render(get("/find-property")).selectFirst(".project-tag"))
}

@Test
fun `the confirmation shows the submitted project under the title`() {
    session.setAttribute(
        SubmittedMandate.SESSION_ATTRIBUTE,
        SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING, ListingType.SALE),
    )

    val page = render(get("/find-property?step=confirmation"))

    assertEquals("Achat", page.selectFirst(".confirmation__head [data-project-label]")?.text())
    assertEquals("Demande envoyée", page.selectFirst("#confirmation-title")?.text())
}
```

- [ ] **Step 5: Run them and verify they fail**

Run: `mvn test -Dtest='FindPropertyControllerTest+FindPropertyTemplateTest'`
Expected: the new assertions fail (no `projectLabel`, no `.project-tag`).

- [ ] **Step 6: Implement the controller and the template**

Add the attribute in `step()` right after `step` / `stepNumber`, and in the confirmation branch of `show`. Port the
approved prototype's markup as in *Interfaces*. The progress block is inside the existing
`th:block th:if="${step != 'confirmation'}"`; the confirmation's own header is in its `section`.

- [ ] **Step 7: Run the tests and the module**

Run: `mvn test -Dtest='FindPropertyRulesTest+FindPropertyControllerTest+FindPropertyTemplateTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty modules/ndopify-partner-central/src/main/resources/templates/find-property.html modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty
git commit -m "feat(npc): show the buyer wizard's project on every step" -m "Refs #278"
```

---

### Task 3: Final verification

**Files:** none.

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-partner-central && mvn verify -Dheadless=true`
Expected: BUILD SUCCESS — ktlint, the tests and the JaCoCo threshold.

- [ ] **Step 2: Check the pill stays display-only**

Run:

```bash
grep -rn "projectLabel" modules/ndopify-partner-central/src/main
```

Expected: `projectLabel` only in `OfferPropertyRules` / `FindPropertyRules`, their controllers' `step()` and
confirmation branch, and the two templates. Never in a form, a `Pending*`, a DTO, a request, a mandate or a criterion.

- [ ] **Step 3: Check both prototypes against the production pages**

Open the approved prototypes (`python3 -m http.server 8899` in `src/main/html/v2`) and the two pages:

- `offer-property.html?sample=rental` vs `GET /offer-property`: *Location* next to *Étape n sur 7* from step 2 on,
  absent on step 1, *Location* under *Bien enregistré*.
- `find-property.html?type=SALE` walked to step 2 and the confirmation vs `GET /find-property?type=SALE`: *Achat*
  next to *Étape n sur 7*, absent on step 1, *Achat* under *Demande envoyée*; a rental reads *Location*.
- At 380 px and 1024 px, the pill stays on the step-count line; the header wraps under it only when both can't fit.

No commit: this is the reviewer's check.

## Self-Review

- **Spec coverage:** AC 1 (Task 1), AC 2 (Task 2), AC 3 (Tasks 1–2), AC 4 (the template tests' tag/role/tabindex
  assertions and Step 3's widths), AC 5 (server-rendered model attributes, no script change), AC 6 (Task 3).
- **Type consistency:** `projectLabel` is the one model attribute both templates read; `data-project-label` is the one
  test hook in both templates; `.progress__head`, `.project-tag`, `.confirmation__head` are the three shared classes.
- **One definition:** the CSS is added once (Task 1) and consumed by both pages; Task 2 adds no stylesheet.
- **Read-only:** no task adds a `role`, `tabindex`, link, button or form field to the pill.
- **Out of scope:** #273's land sale, the agent's screens, the public site, the wizards' steps and validation.
