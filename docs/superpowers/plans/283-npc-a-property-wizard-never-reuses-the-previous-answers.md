# A Property Wizard Never Reuses the Answers of a Previous Run Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /find-property` and `GET /offer-property` without a `step` always start a new run — dropping the previous
run's answers and its confirmation — while every in-run URL (a step, *Précédent*, a review edit, a step shown again
with its errors) keeps them, and `?type=SALE|RENTAL` preselects the new run's project.

**Architecture:** The two wizard controllers already treat a request without `step` as the entry point (`show`), but
it reuses the session's `PendingMandate` / `PendingListing`; `show` gains one branch that removes both session
attributes before building the run's answers, and `save`/`submit` keep their current handling (answers leave the
session on submit, the recap stays for the confirmation). The only in-run links that still point at the bare URL are
the step-1 *Précédent* button of each template and the controllers' "step not allowed" redirect: both carry their
`step` afterwards, so the bare URL means "start over" everywhere else. No prototype, no CSS, no JS, no server, DTO or
`ndopify-web-core` change; the visible pages do not change.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, JUnit 5 + `kotlin.test`, `MockMvc`, mockito-kotlin, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/283

**UI: no** — no prototype to port; `src/main/html/` is not touched. `Shared code: none`.

## Global Constraints

- **The bare URL is the only start.** `GET /find-property` / `GET /offer-property` with no `step` drops
  `npc.pending-mandate` / `npc.pending-listing` and `npc.submitted-mandate` / `npc.submitted-listing`, then shows step
  1 with `listingType` from `?type=` only. A URL with `?step=…` is in-run and never drops anything.
- **The confirmation is not a start.** `?step=confirmation` still renders the recap while a `SubmittedMandate` is in
  the session (the recap survives a reload); without one it renders step 1, never an error.
- **The answers leave the session on submit.** `POST …/review` keeps its current behaviour: it removes the pending
  answers and stores only the `SubmittedMandate` the confirmation renders. Do not store or re-store a `Pending…` after
  a submit.
- **One wizard, one session attribute.** Starting *Trouver un bien* never removes `npc.pending-listing` /
  `npc.submitted-listing`, nor the reverse; `join`, `login` and `kyc` are out of scope and untouched.
- **No JavaScript, no visible change.** Every page keeps working without JS; no template markup, CSS or script
  changes beyond the two `data-prev` `href`s; no French copy changes.
- **`modules/ndopify-partner-central` only.** No `ndopify-server`, `ndopify-dto`, `ndopify-web-core` or `ndopify-web`
  change, no `pom.xml` or `application*.yml` change, no new dependency.
- **French UI, English code.** KDoc, comments, plan, test names and commits in English; every label stays French.
- **`mvn verify` green in `ndopify-partner-central`** (ktlint, tests, JaCoCo).
- Commits are conventional and scoped `fix(npc): …`, each with `Refs #283` in the body.

## Review Focus

- **A step posted with errors.** `POST /find-property/{step}` with a validation error renders the step again with the
  run's answers and must leave `npc.pending-mandate` as it was — never clear it (the step URL is not in the browser
  there, the render is).
- **A bookmark mid-run.** `GET /find-property?step=area` on a complete previous run resumes that run (answers kept,
  `area` shown), it does not start over.
- **A stale confirmation URL.** `GET /find-property?step=confirmation` after a start-over renders step 1 with an empty
  form, not a blank page, a 500, nor the previous recap (`confirmationWithoutSubmissionShowsWizard` already pins this;
  `confirmationWithoutSubmissionShowsStep1` pins it for the seller).
- **Cross-wizard isolation.** After starting *Trouver un bien*, a *Proposer un bien* run in the same session still has
  its answers and its confirmation.
- **A `?type=` that isn't a project.** `?type=LAND`, `?type=` or no `?type=` leaves step 1's project unticked and does
  not fail (`an unknown type parameter is ignored` exists in both suites); `?type=` on a `?step=` URL never overwrites
  the run's saved project.

---

### Task 1: *Trouver un bien* — the bare URL starts a new run

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`
  (class KDoc, `show`'s `?type=` block at lines 92-96, `redirect` at lines 429-430)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html:162` (the area step's
  `data-prev`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
  (`the type parameter never overwrites a saved project`; the redirect expectations in `areaBeforeNeedRedirects` and
  `submitIncompleteRedirects`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`
  (`the need step asks to rent or buy`, `needPrefilled`, `the forms carry the listing type for the script`, `step 1
  shows no project pill`, the area step's `data-prev` assertion)
- Modify: `modules/ndopify-partner-central/CLAUDE.md:142` (the *Trouver un bien* bullet)

**Interfaces:**
- Consumes: `PendingMandate(listingType: ListingType? = null)`, `PendingMandate.SESSION_ATTRIBUTE`
  (`"npc.pending-mandate"`), `SubmittedMandate.SESSION_ATTRIBUTE` (`"npc.submitted-mandate"`),
  `FindPropertyRules.parseListingType(value: String?): ListingType?` (null for no value, `UNKNOWN` or anything that
  isn't a `ListingType` name).
- Produces: no new type or signature — `GET /find-property` (no `step`) is the start of a run; every redirect the
  controller issues carries `?step=`.

- [ ] **Step 1: Write the failing controller tests**

In `FindPropertyControllerTest`, replace `the type parameter never overwrites a saved project` with these four tests
(the fixtures `session`, `complete`, `model(result)` and `pending()` already exist):

```kotlin
@Test
fun `starting a new run drops the answers of the previous one`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

    val result = mvc.perform(get("/find-property").session(session)).andReturn()

    assertEquals("need", model(result)["step"])
    assertEquals(PendingMandate(), model(result)["answers"])
    assertNull(pending())
}

@Test
fun `starting a new run drops the previous confirmation`() {
    session.setAttribute(
        SubmittedMandate.SESSION_ATTRIBUTE,
        SubmittedMandate("ray@gmail.com", "+237670000001", ContactPeriodType.EVENING),
    )

    val result = mvc.perform(get("/find-property").session(session)).andReturn()

    assertEquals("need", model(result)["step"])
    assertNull(session.getAttribute(SubmittedMandate.SESSION_ATTRIBUTE))
}

@Test
fun `the type parameter of a new run wins over the previous project`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, PendingMandate(listingType = ListingType.RENTAL))

    val result = mvc.perform(get("/find-property").param("type", "SALE").session(session)).andReturn()

    assertEquals(ListingType.SALE, (model(result)["answers"] as PendingMandate).listingType)
    assertNull(pending())
}

@Test
fun `going back to the need step keeps the answers of the current run`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

    val result = mvc.perform(get("/find-property?step=need").session(session)).andReturn()

    assertEquals("need", model(result)["step"])
    assertEquals(complete, model(result)["answers"])
    assertEquals(complete, pending())
}
```

and these two, for the Review Focus lines (import `PendingListing` from `ui.offerproperty` for the last one):

```kotlin
@Test
fun `a step posted with its errors leaves the run untouched`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

    val result = mvc.perform(
        post("/find-property/budget").session(session).param("priceAmount", "1"),
    ).andReturn()

    assertEquals("budget", model(result)["step"])
    assertTrue(errors(result).containsKey("priceAmount"))
    assertEquals(complete, pending())
}

@Test
fun `starting a run keeps the other wizard's answers`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing())

    mvc.perform(get("/find-property").session(session)).andReturn()

    assertEquals(PendingListing(), session.getAttribute(PendingListing.SESSION_ATTRIBUTE))
}
```

Then change the two redirect expectations the new `redirect` produces: `assertEquals("redirect:/find-property", …)`
becomes `assertEquals("redirect:/find-property?step=need", …)` in `areaBeforeNeedRedirects` and in
`submitIncompleteRedirects`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyControllerTest`
Expected: FAIL — the bare URL returns `complete` as `answers` and leaves `pending()` set; the redirects read
`redirect:/find-property`.

- [ ] **Step 3: Implement the entry-point branch and the step-carrying redirect**

In `FindPropertyController.show`, replace the `?type=` block (the `saved`/`copy` expression) with:

```kotlin
// The public site's links preselect the project (?type=SALE|RENTAL), for display only: the session keeps
// what the visitor posts.
val pending = if (requestedStep == null) {
    // The entry point: a new run, never the answers of a previous one (an abandoned run, or a submitted
    // request whose confirmation is still in the session).
    session.removeAttribute(PendingMandate.SESSION_ATTRIBUTE)
    session.removeAttribute(SubmittedMandate.SESSION_ATTRIBUTE)
    PendingMandate(listingType = FindPropertyRules.parseListingType(type))
} else {
    pending(session)
}
```

Leave the confirmation branch above it untouched. Then make `redirect` unconditional and document why:

```kotlin
/** Every step has its own URL: the bare `/find-property` is the entry point, which starts a new run. */
private fun redirect(step: String): String = "redirect:/find-property?step=$step"
```

Add the class KDoc paragraph:

```
 * No `step` is the entry point ([show]), and always starts a new run: the answers of a previous one (abandoned, or
 * already submitted) are dropped, never shown. Every in-run URL carries its step.
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyControllerTest`
Expected: PASS, including the six new tests and both redirect assertions.

- [ ] **Step 5: Write the failing template tests**

In `FindPropertyTemplateTest`, every test that renders a saved run through the bare URL must name its step instead —
`render(get("/find-property"))` becomes `render(get("/find-property?step=need"))` in `the need step asks to rent or
buy`, `needPrefilled`, the sale half of `the forms carry the listing type for the script` and in `step 1 shows no
project pill` (which must keep a saved project in the session for its assertion to mean anything) — and this test
joins them:

```kotlin
@Test
fun `starting again shows an empty need step`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, complete)

    val page = render(get("/find-property"))

    assertEquals("Étape 1 sur 7", page.selectFirst("[data-progress-label]")?.text())
    assertTrue(page.select("input[name=listingType][checked]").isEmpty())
    assertTrue(page.select("input[name=propertyCategory][checked]").isEmpty())
}
```

In the area step's test, change `assertEquals("/find-property", page.selectFirst("[data-prev]")?.attr("href"))` to
`assertEquals("/find-property?step=need", …)`.

- [ ] **Step 6: Run the template tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyTemplateTest`
Expected: FAIL — `data-prev` still reads `/find-property`, and the new bare-URL test sees the saved answers.

- [ ] **Step 7: Carry the step in the template's *Précédent***

In `templates/find-property.html`, the step-1 *Précédent* link becomes:

```html
<a class="btn btn--outline" data-prev
   th:href="${returning} ? @{/find-property(step='review')} : @{/find-property(step='need')}">Précédent</a>
```

No other markup, CSS or script change.

- [ ] **Step 8: Run the template tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyTemplateTest`
Expected: PASS.

- [ ] **Step 9: Update the module's documentation**

In `modules/ndopify-partner-central/CLAUDE.md`, the *Trouver un bien* bullet: replace *"`?type=SALE|RENTAL`
preselects it when the session has no answer yet, and never overwrites one"* with *"`?type=SALE|RENTAL` preselects
it. `GET /find-property` without a `step` is the entry point and starts a new run: it drops the session's
`PendingMandate` and `SubmittedMandate`, so a previous run's answers are never shown (every in-run URL, *Précédent*
included, carries its `step`)."* Keep the bullet's other sentences unchanged.

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/find-property.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt \
        modules/ndopify-partner-central/CLAUDE.md
git commit -m "fix(npc): start a new find-property run on the bare URL" -m "Refs #283"
```

### Task 2: *Proposer un bien* — the same rule

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
  (class KDoc, `show`'s `?type=` block at lines 101-104, `redirect` at lines 528-529)
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html:160` (the address step's
  `data-prev`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
  (`the type parameter never overwrites a saved project`; the redirect expectation in `addressBeforePropertyRedirects`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`
  (`a sale session ticks Vendre and words the project`, `other steps link to the previous one`)
- Modify: `modules/ndopify-partner-central/CLAUDE.md:162` (the *Proposer un bien* bullet)

**Interfaces:**
- Consumes: `PendingListing(listingType: ListingType? = null)`, `PendingListing.SESSION_ATTRIBUTE`
  (`"npc.pending-listing"`), `OfferPropertyController.SUBMITTED_ATTRIBUTE` (`"npc.submitted-listing"`),
  `FindPropertyRules.parseListingType`, and the controller's private `pending(session)` / `firstIncomplete(pending)`
  (day-dependent, via the `Clock`).
- Produces: `GET /offer-property` (no `step`) is the start of a run; every redirect carries `?step=`.

- [ ] **Step 1: Write the failing controller tests**

In `OfferPropertyControllerTest`, replace `the type parameter never overwrites a saved project` with the four mirror
tests (*Proposer un bien*'s step 1 is `property`):

```kotlin
@Test
fun `starting a new run drops the answers of the previous one`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

    val result = mvc.perform(get("/offer-property").session(session)).andReturn()

    assertEquals("property", model(result)["step"])
    assertEquals(PendingListing(), model(result)["answers"])
    assertNull(pending())
}

@Test
fun `starting a new run drops the previous confirmation`() {
    session.setAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE, submitted())

    val result = mvc.perform(get("/offer-property").session(session)).andReturn()

    assertEquals("property", model(result)["step"])
    assertNull(session.getAttribute(OfferPropertyController.SUBMITTED_ATTRIBUTE))
}

@Test
fun `the type parameter of a new run wins over the previous project`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, PendingListing(listingType = ListingType.RENTAL))

    val result = mvc.perform(get("/offer-property").param("type", "SALE").session(session)).andReturn()

    assertEquals(ListingType.SALE, (model(result)["answers"] as PendingListing).listingType)
    assertNull(pending())
}

@Test
fun `going back to the property step keeps the answers of the current run`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

    val result = mvc.perform(get("/offer-property?step=property").session(session)).andReturn()

    assertEquals("property", model(result)["step"])
    assertEquals(complete, model(result)["answers"])
    assertEquals(complete, pending())
}
```

Use the class's existing `complete` fixture and its `submitted()` helper. Add these two, for the Review Focus lines
(import `PendingMandate` from `ui.findproperty` for the last one):

```kotlin
@Test
fun `a step posted with its errors leaves the run untouched`() {
    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete)

    val result = mvc.perform(post("/offer-property/terms").session(session)).andReturn()

    assertEquals("terms", model(result)["step"])
    assertTrue(errors(result).isNotEmpty())
    assertEquals(complete, pending())
}

@Test
fun `starting a run keeps the other wizard's answers`() {
    session.setAttribute(PendingMandate.SESSION_ATTRIBUTE, PendingMandate())

    mvc.perform(get("/offer-property").session(session)).andReturn()

    assertEquals(PendingMandate(), session.getAttribute(PendingMandate.SESSION_ATTRIBUTE))
}
```

Then change `addressBeforePropertyRedirects`'s expectation to
`assertEquals("redirect:/offer-property?step=property", …)`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyControllerTest`
Expected: FAIL — the bare URL returns `complete`, `pending()` stays set, the redirect lacks `?step=`.

- [ ] **Step 3: Implement the entry-point branch and the redirect**

In `OfferPropertyController.show`, replace the `?type=` block with:

```kotlin
// The public site's links preselect the project (?type=SALE|RENTAL), for display only: the session keeps
// what the visitor posts.
val pending = if (requestedStep == null) {
    // The entry point: a new run, never the answers of a previous one (an abandoned run, or a submitted
    // listing whose confirmation is still in the session).
    session.removeAttribute(PendingListing.SESSION_ATTRIBUTE)
    session.removeAttribute(SUBMITTED_ATTRIBUTE)
    PendingListing(listingType = FindPropertyRules.parseListingType(type))
} else {
    pending(session)
}
```

Keep the confirmation branch above untouched. Then:

```kotlin
/** Every step has its own URL: the bare `/offer-property` is the entry point, which starts a new run. */
private fun redirect(step: String): String = "redirect:/offer-property?step=$step"
```

and the class KDoc paragraph:

```
 * No `step` is the entry point ([show]), and always starts a new run: the answers of a previous one (abandoned, or
 * already submitted) are dropped, never shown. Every in-run URL carries its step.
```

- [ ] **Step 4: Run the controller tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyControllerTest`
Expected: PASS.

- [ ] **Step 5: Write the failing template tests**

In `OfferPropertyTemplateTest`, `render(get("/offer-property"))` becomes
`render(get("/offer-property?step=property"))` in `a sale session ticks Vendre and words the project`, add:

```kotlin
@Test
fun `starting again shows an empty property step`() {
    val page = render(get("/offer-property"))

    assertEquals("Étape 1 sur 7", page.selectFirst("[data-progress-label]")?.text())
    assertTrue(page.select("input[name=listingType][checked]").isEmpty())
    assertTrue(page.select("input[name=propertyCategory][checked]").isEmpty())
}
```

and change the address step's `data-prev` expectation to
`assertEquals("/offer-property?step=property", render(get("/offer-property?step=address")).selectFirst("a[data-prev]")?.attr("href"))`.

- [ ] **Step 6: Run the template tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyTemplateTest`
Expected: FAIL — `data-prev` reads `/offer-property`.

- [ ] **Step 7: Carry the step in the template's *Précédent***

```html
<a class="btn btn--outline" data-prev
   th:href="${returning} ? @{/offer-property(step='review')} : @{/offer-property(step='property')}">Précédent</a>
```

No other markup, CSS or script change; `exitUrl` and the *Quitter* link stay as they are.

- [ ] **Step 8: Run the template tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyTemplateTest`
Expected: PASS.

- [ ] **Step 9: Update the module's documentation**

In `modules/ndopify-partner-central/CLAUDE.md`, the *Proposer un bien* bullet: replace *"preselected from
`?type=SALE|RENTAL` while no answer is saved"* with *"preselected from `?type=SALE|RENTAL`"*, and after the
confirmation sentence add: *"`GET /offer-property` without a `step` is the entry point and starts a new run, dropping
the session's `PendingListing` and `SubmittedMandate` like *Trouver un bien*."*

- [ ] **Step 10: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/offer-property.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt \
        modules/ndopify-partner-central/CLAUDE.md
git commit -m "fix(npc): start a new offer-property run on the bare URL" -m "Refs #283"
```

### Task 3: Whole-wizard verification

**Files:** none expected to change; this task either finds a gap (fix it with a test first, in the owning task's
files) or closes the story.

**Interfaces:**
- Consumes: both wizards as Tasks 1-2 leave them.
- Produces: the evidence the story's ACs are met.

- [ ] **Step 1: Prove no in-run link uses the bare URL**

Run: `cd modules/ndopify-partner-central && grep -rn "@{/find-property}\|@{/offer-property}" src/main/resources/templates/`
Expected: only the header logo and the confirmation's *Retour à l'accueil* fallbacks
(`th:href="${#strings.isEmpty(tenant.publicPortalUrl)} ? @{/find-property} : …`), which mean "leave the wizard" on a
tenant without a public portal. No `data-prev`, no form action, no JS navigation.

- [ ] **Step 2: Prove the other wizards and the prototype are untouched**

Run: `git diff --stat origin/main -- modules/ndopify-partner-central/src/main/html modules/ndopify-web modules/ndopify-server modules/ndopify-web-core`
Expected: empty. Also `grep -n "SESSION_ATTRIBUTE" modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/{join,login,kyc}/*.kt`
shows their own attributes, none removed by this change.

- [ ] **Step 3: Run the module's gate**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS — ktlint clean, every test green, JaCoCo thresholds met. (`ndopify-server` is not built; this
module needs neither MySQL nor a running server.)

- [ ] **Step 4: Walk the acceptance criteria once, on the built pages**

With `mvn spring-boot:run` and a tenant whose `partnerCentralUrl` is `http://localhost:8081`, in one browser session:
answer step 1 of *Trouver un bien*, open `/find-property` again (empty step 1), open
`/find-property?type=SALE` (purchase ticked, nothing else kept), go to step 2 and press *Précédent* (step 1 keeps the
answers), then complete the wizard and reload `?step=confirmation` (recap kept), then open `/find-property` (recap
gone). Repeat on `/offer-property`. With JavaScript disabled, every step still posts.

- [ ] **Step 5: Report, do not commit**

Comment the walk's result on #283. No source change means no commit; a gap found in Steps 1-2 goes back to Task 1 or
2 as a failing test first, with its own `fix(npc): …` commit.
