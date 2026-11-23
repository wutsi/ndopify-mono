# The Seller Wizard's Land Sale Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix *Proposer un bien* so a sale of a *Terrain* skips bedrooms/parking, offers its ownership documents on the
first render, shows the price per m² on the features step and on the review (formatted like a price), and every step
after the first says *Vente* or *Location* — all without depending on JavaScript.

**Architecture:** The controller derives five display flags for the features step from the saved answers
(`showsRooms`, `requiresRooms`, `showsParking`, `requiresParking`, `asksOwnershipDocuments`) and the template renders
each question from them; a `requires…` flag is false while the type is unknown, so a sale's first render is optional,
and a land sale's questions are not rendered. One pure rule, `OfferPropertyRules.pricePerSqm`, gives the rate; the
review appends its line after the total price, formatted through `TenantFormatter.money` and
`MandateLabels.formatNumber`. `OfferPropertyRules.projectLabel` feeds a server-rendered *Vente* / *Location* pill on
every step after the first and on the confirmation. The page script mirrors the flags (hiding, disabling, and
`required`) so it enhances the server render instead of replacing it.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, the esbuild `partner-central` bundle, JUnit 5 + `kotlin.test`,
MockMvc, jsoup, mockito-kotlin.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/273

**Approved prototype (the baseline to port, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/offer-property.html`
- `modules/ndopify-partner-central/src/main/html/v2/assets/js/offer-property.js`
- `modules/ndopify-partner-central/src/main/html/v2/assets/css/offer-property.css`
- `modules/ndopify-partner-central/src/main/html/v2/README.md` (`?sample=` states)

Review it at `http://localhost:8899/offer-property.html?sample=sale|land|land-no-area|house|land-review|land-review-none|rental`
(`python3 -m http.server 8899` in that folder).

## Global Constraints

- **The prototype is the design.** Tasks 1–4 port its labels, states and behaviour into the production template,
  stylesheet and script; they change no wording, order or state it already settled. `src/main/html/` is never edited
  here.
- **French UI, English code.** Every label, legend, hint and error message is French; classes, KDoc, comments, logs,
  plan and commits are English.
- **Amounts are formatted, never raw.** The rate is money: `TenantFormatter.money(rate)` (grouped, tenant currency) and
  `MandateLabels.formatNumber(area)` for the surface. No `toString()`, no hard-coded *FCFA*. The rate is display only:
  never posted, never in `PendingListing`, never stored, sent with the mandate or a criterion.
- **No JavaScript is required.** The server renders the questions, the documents and the project label from the saved
  answers; the script only hides/disables/marks required to enhance the same states.
- **A rental is unchanged.** Its questions, documents and review are today's; the script keeps its current behaviour.
  The land-sale-only rule (a *Terrain* is never rented) is **out of scope**: do not drop `LAND` from the rental maps,
  the mandate edit page or the buyer's search here.
- **No server, DTO or web-core change.** `modules/ndopify-partner-central` only; no new dependency, no `pom.xml`
  change, no `application*.yml` change.
- `mvn verify` green in `ndopify-partner-central` (ktlint, tests, JaCoCo).
- Commits are conventional and scoped `feat(npc): …` / `test(npc): …`, each with `Refs #273` in the body.

## Review Focus

- **No-JS land sale.** Without the script, a *Terrain* sale must not render rooms/parking, must show the three optional
  documents, and must post and save with `rooms = null`, `parkings = null` and an empty document list.
- **No-JS sale, type not chosen.** The first render shows the questions but does not require them; a *Terrain* then
  posts without them, while a house still gets the server's *Indiquez le nombre de chambres.* message.
- **The rate's formatting.** The review line is *≈ 56 300 FCFA/m² pour 800 m²*, grouped and in the tenant's currency,
  never *56300* nor a hard-coded FCFA; a house or a rental has no line.
- **The project label.** Every step after the first, the review and the confirmation show *Vente* / *Location*; step 1
  shows only its choice; a land sale reads *Vente*, and the label is server-rendered (no script).
- **The rate never leaks.** No `pricePerSqm`, rate field or hidden input reaches the form, `PendingListing` or the
  `CreateMandateRequest`; changing the price or the surface changes only the next render's line.

---

### Task 1: The project label on every later step

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/offer-property.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Produces on `OfferPropertyRules`:

```kotlin
/** "Vente" / "Location", the wizard's project for every step after the first; rental when unanswered. */
fun projectLabel(listingType: ListingType?): String =
    if (listingType == ListingType.SALE) "Vente" else "Location"
```

- Produces in the `OfferPropertyController.step` model, for every step:
  `projectLabel = OfferPropertyRules.projectLabel(answers.listingType)`. The confirmation branch
  (`GET ?step=confirmation`) also adds `projectLabel = OfferPropertyRules.projectLabel(submitted.listingType)`.
- Produces in `offer-property.html`: the progress block wraps its label in `div.progress__head` and carries
  `<p class="project-tag" th:if="${step != 'property'}" th:text="${projectLabel}">Vente</p>`; the confirmation's title
  is wrapped in `div.confirmation__head` with the same `<p class="project-tag" th:text="${projectLabel}">Vente</p>`
  under the `h2`.
- Produces in `assets/css/offer-property.css`: `.progress__head` (flex, wraps, space-between), `.project-tag`
  (read-only pill: inline-flex, `--radius-control`, `--color-border`, `--color-surface-alt`, `--step-hint`, 600,
  nowrap) and `.confirmation__head` (column, centred, `--space-2`), copied from the prototype.

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

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest#projectLabel`
Expected: compilation failure, `projectLabel` unresolved.

- [ ] **Step 3: Add `OfferPropertyRules.projectLabel`**

As in *Interfaces*.

- [ ] **Step 4: Write the failing controller and template tests**

`OfferPropertyControllerTest` — the features step as a sale carries `projectLabel = "Vente"`, as a rental
`"Location"`; the confirmation of a sale carries `"Vente"` (after `POST /offer-property/review` with the complete sale
session, as the existing confirmation test does).

`OfferPropertyTemplateTest` — render `GET /offer-property?step=features` with `sale` in the session:
`page.selectFirst(".progress__head .project-tag")` reads `Vente`; with the default rental session it reads `Location`;
the step-1 page has no `.progress__head .project-tag`; the sale review page (the existing `sale` session) reads
`Vente` in the progress row; the confirmation page (after the sale review post, as the existing confirmation test) has
`.confirmation__head .project-tag` reading `Vente`; a land sale features step reads `Vente`.

- [ ] **Step 5: Run them and see them fail**

Run: `mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: the new assertions fail (no model attribute / no `.project-tag`).

- [ ] **Step 6: Implement the controller, template and CSS**

Add the attribute in `step()` (right after `step`/`stepNumber`) and in the confirmation branch; port the prototype's
markup and CSS as in *Interfaces*. The progress block is inside the existing `th:block th:if="${step != 'confirmation'}"`;
the confirmation's own header is in its `section`.

- [ ] **Step 7: Run the tests and the module**

Run: `mvn test -Dtest='OfferPropertyRulesTest+OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty modules/ndopify-partner-central/src/main/resources/templates/offer-property.html modules/ndopify-partner-central/src/main/assets/css/offer-property.css modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty
git commit -m "feat(npc): show the project on every step of the offer wizard" -m "Refs #273"
```

---

### Task 2: The features questions and documents

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: `OfferPropertyRules.needsRooms`, `needsParking`, `ownershipDocumentTypes` and `FindPropertyRules.parsePropertyType`.
- Produces in the features branch of `OfferPropertyController.step` (replace the current `asksOwnershipDocuments`
  computation):

```kotlin
val type = FindPropertyRules.parsePropertyType(answers.propertyType)
val sale = answers.isSale()
val residential = answers.propertyCategory == PropertyCategory.RESIDENTIAL
val showsRooms = residential && (!sale || type == null || OfferPropertyRules.needsRooms(answers.propertyCategory, type, answers.listingType))
val requiresRooms = residential && (!sale || (type != null && OfferPropertyRules.needsRooms(answers.propertyCategory, type, answers.listingType)))
val showsParking = residential && (!sale || type == null || OfferPropertyRules.needsParking(answers.propertyCategory, type, answers.listingType))
val requiresParking = residential && (!sale || (type != null && OfferPropertyRules.needsParking(answers.propertyCategory, type, answers.listingType)))
model.addAttribute("showsRooms", showsRooms)
model.addAttribute("requiresRooms", requiresRooms)
model.addAttribute("showsParking", showsParking)
model.addAttribute("requiresParking", requiresParking)
model.addAttribute("asksOwnershipDocuments", sale && (type == null || type == PropertyType.LAND))
```

  `landSale` stays `answers.isSale() && type == PropertyType.LAND`; `asksArea` stays as it is; `residential` stays the
  template's `th:with` for the furniture fieldset.

- Produces in `offer-property.html`:
  - the *Nombre de chambres* fieldset `th:if="${showsRooms}"`; every `input[name=rooms]` gets
    `th:attr="required=${requiresRooms} ? 'required' : null"` instead of the literal `required`;
  - the *Parking ou garage* fieldset `th:if="${showsParking}"`; every `input[name=hasParking]` gets the same
    `required` expression from `requiresParking`;
  - the *Combien de places ?* fieldset `th:if="${showsParking}"`; every `input[name=parkings]` gets the same
    `required` expression from `requiresParking`;
  - the documents fieldset keeps `th:hidden="${!asksOwnershipDocuments}"` and its optional checkboxes.
  The rooms/parking fieldsets no longer use the `residential` `th:with`; the furniture one keeps `th:if="${residential and !sale}"`.

- [ ] **Step 1: Write the failing controller tests**

`OfferPropertyControllerTest` — in the features model:
- a sale with `propertyType = null` (the existing `sale.copy(propertyType = null)`) has `showsRooms`, `showsParking` and
  `asksOwnershipDocuments` true and `requiresRooms`, `requiresParking` false;
- the `sale` (land, 800 m²) has `showsRooms`/`showsParking` false and `asksOwnershipDocuments` true;
- `sale.copy(propertyType = "HOUSE", area = null)` has `showsRooms`/`showsParking`/`requiresRooms`/`requiresParking`
  true and `asksOwnershipDocuments` false;
- the default rental (apartment) has `showsRooms`/`showsParking`/`requiresRooms`/`requiresParking` true and
  `asksOwnershipDocuments` false.

- [ ] **Step 2: Run them and see them fail**

Run: `mvn test -Dtest=OfferPropertyControllerTest`
Expected: the new model keys are null.

- [ ] **Step 3: Write the failing template tests**

In `OfferPropertyTemplateTest`:
- a sale with no type (`sale.copy(propertyType = null)`): `input[name=rooms]` and `input[name=hasParking]` exist and
  none has `required`; `[data-group=ownershipDocuments]` is not hidden;
- the land `sale`: no `input[name=rooms]`, no `input[name=hasParking]`, no `input[name=parkings]`; the documents are
  visible; `[data-price-per-sqm]` exists;
- a house sale (`sale.copy(propertyType = "HOUSE", area = null)`): rooms, hasParking and parkings exist with
  `required`; the documents are hidden;
- the default rental (apartment): rooms/hasParking/parkings exist with `required`; the documents are hidden;
- keep the existing `features step without JS shows every residential question` passing (the default rental).

- [ ] **Step 4: Run them and see them fail**

Run: `mvn test -Dtest=OfferPropertyTemplateTest`
Expected: the sale/land assertions fail (rooms still rendered / required, documents hidden).

- [ ] **Step 5: Implement the controller flags and the template**

As in *Interfaces*. The `required` on each input is conditional; do not leave a literal `required` on rooms,
hasParking or parkings. The server-side validation is unchanged: `features()` already drops the answers that don't
apply and only validates what applies.

- [ ] **Step 6: Add the controller's no-JS land post test**

`OfferPropertyControllerTest` — `features("propertyType" to "LAND", "area" to "800")` on a sale session (without
rooms, hasParking or any document) is saved: the result's view is
`redirect:/offer-property?step=amenities`, and the session's `PendingListing` has `rooms = null`, `parkings = null`
and `ownershipDocuments = emptyList()`. Use the existing `features(…)` helper and `session` assertions.

- [ ] **Step 7: Run the tests**

Run: `mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt modules/ndopify-partner-central/src/main/resources/templates/offer-property.html modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty
git commit -m "feat(npc): render the land sale questions and documents from the saved type" -m "Refs #273"
```

---

### Task 3: The rate, its formatting and the review line

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Produces on `OfferPropertyRules`:

```kotlin
/**
 * A sold plot's implied rate: asking price ÷ surface, rounded to the nearest 100, or null when the answers are not a
 * SALE · LAND or the price/surface is missing or unusable. A rate rounding to 0 is null too, as the page hides it.
 */
fun pricePerSqm(listingType: ListingType?, type: PropertyType?, priceAmount: Long?, area: Int?): Long? {
    if (!isSale(listingType) || type != PropertyType.LAND) return null
    if (priceAmount == null || priceAmount <= 0) return null
    if (area == null || area < MIN_AREA) return null
    val rate = Math.round((priceAmount.toDouble() / area) / 100) * 100
    return rate.takeIf { it > 0 }
}
```

- Produces in `OfferPropertyController.termsLines`, in the sale branch, after the price and before the availability:

```kotlin
val type = FindPropertyRules.parsePropertyType(answers.propertyType)
val rate = OfferPropertyRules.pricePerSqm(answers.listingType, type, answers.priceAmount, answers.area)
...
answers.priceAmount?.let { formatter.money(it) },
rate?.let {
    "≈ " + formatter.money(it) + "/m² pour " + MandateLabels.formatNumber(answers.area!!) + " m²"
},
```

`formatter.money(it)` is the tenant's grouped amount with its currency (*56 300 FCFA*), never a raw number.

- [ ] **Step 1: Write the failing rules tests**

In `OfferPropertyRulesTest`:

```kotlin
@Test
fun pricePerSqm() {
    assertEquals(56_300L, OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, 45_000_000L, 800))
    assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, 45_000_000L, 4))
    assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, null, 800))
    assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.HOUSE, 45_000_000L, 800))
    assertNull(OfferPropertyRules.pricePerSqm(ListingType.RENTAL, PropertyType.LAND, 45_000_000L, 800))
    assertNull(OfferPropertyRules.pricePerSqm(ListingType.SALE, PropertyType.LAND, 1_000_000L, 1_000_000))
}
```

- [ ] **Step 2: Run them and see them fail**

Run: `mvn test -Dtest=OfferPropertyRulesTest#pricePerSqm`
Expected: compilation failure, `pricePerSqm` unresolved.

- [ ] **Step 3: Implement `pricePerSqm`**

As in *Interfaces*.

- [ ] **Step 4: Write the failing review tests**

`OfferPropertyControllerTest` — the sale review's `termsLines` has the total first
(`assertEquals("45 000 000 FCFA", lines[0])`), then the rate
(`assertEquals("≈ 56 300 FCFA/m² pour 800 m²", lines[1])`), then the availability. A house sale's and the default
rental's `termsLines` have no line containing `FCFA/m²`. Use the existing review test's fixtures and date.

`OfferPropertyTemplateTest` — the sale review page's `[data-review-value=terms]` text contains
`≈ 56 300 FCFA/m² pour 800 m²` and not `56300`; a house sale and the rental review do not contain `FCFA/m²`.

- [ ] **Step 5: Run them and see them fail**

Run: `mvn test -Dtest='OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: the new assertions fail (no rate line).

- [ ] **Step 6: Implement the review line**

As in *Interfaces*; keep the sale's no-`/ mois` price, the rental branch untouched, and the availability line last.
Do not add the rate to `PendingListing`, the request or the template as a field.

- [ ] **Step 7: Run the tests**

Run: `mvn test -Dtest='OfferPropertyRulesTest+OfferPropertyControllerTest+OfferPropertyTemplateTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty
git commit -m "feat(npc): show the land sale rate on the review" -m "Refs #273"
```

---

### Task 4: The page script mirrors the server flags

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/js/offer-property.js`
- Test: the manual page check below; the server-rendered hooks are pinned by Task 2/3's `OfferPropertyTemplateTest`.

**Interfaces:**
- Consumes: the same rules as the server (`needsRooms`, `needsParking`, `needsOwnershipDocuments`), `sale` from the
  form's `data-listing-type`, and the checked `propertyType`.
- Produces in `initFeatures`:

```js
function showsRooms(type) {
    return sale ? (!type || needsRooms(type)) : needsRooms(type);
}
function requiresRooms(type) {
    return sale ? (!!type && needsRooms(type)) : showsRooms(type);
}
function showsParking(type) {
    return sale ? (!type || needsParking(type)) : needsParking(type);
}
function requiresParking(type) {
    return sale ? (!!type && needsParking(type)) : showsParking(type);
}
```

  and a `show(key, shown, required)` that sets `group.hidden = !shown`, disables the inputs when hidden, and sets
  `input.required = shown && (required !== false)` (the documents pass `false`; a question the server renders for a sale
  before its type passes `false`). The calls become:

```js
show("rooms", showsRooms(type), requiresRooms(type));
show("hasParking", showsParking(type), requiresParking(type));
show("parkings", needsParking(type) && checkedValue("hasParking", form) === "YES", true);
show("ownershipDocuments", sale && (!type || isLand(type)), false);
show("furnitureType", needsFurniture(type), true);
show("area", needsArea(type), true);
```

  The first render's sale keeps the questions visible (the server rendered them) but not `required`; a rental keeps the
  script's current behaviour (`showsRooms`/`requiresRooms` are `needsRooms(type)`); a land sale hides rooms/parking and
  keeps the documents; `updatePricePerSqm` and `rateText` are unchanged.

- [ ] **Step 1: Write the manual check (no JS harness)**

The module has no browser test harness; the script is checked by hand, as the other page scripts are. The template
tests pin the server-rendered `required` attributes and hooks. The check list:
  - a sale, type not chosen: the rooms/parking fields are visible, no `required` in the DOM, the documents visible; a
    Terrain then posts without them;
  - a land sale: no rooms/parking, documents, surface and *Soit ≈ 56 300 FCFA/m² pour 800 m²*;
  - a house sale: documents hidden, rooms/parking required;
  - a rental: today's questions, no documents, no rate.

- [ ] **Step 2: Implement the script flags**

As in *Interfaces*. Do not change `rateText` (it already groups with `formatNumber`), the sample states or the review
rendering.

- [ ] **Step 3: Run the module tests and lint**

Run: `mvn test -Dtest='OfferPropertyTemplateTest+OfferPropertyControllerTest+OfferPropertyRulesTest'`
Expected: PASS; the template tests still pin the server's `required` attributes and the empty `[data-price-per-sqm]`
hook.

- [ ] **Step 4: Build the bundle and check the page by hand**

Run: `mvn generate-resources -DskipTests` (the `frontend-maven-plugin` build) and open the page on the local server;
walk the four states above. Expected: the states of the approved prototype, with the script not overriding the
server's `required`/hidden state.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/assets/js/offer-property.js
git commit -m "feat(npc): let the offer script follow the server's land sale flags" -m "Refs #273"
```

---

### Task 5: Final verification

**Files:** none.

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-partner-central && mvn verify -Dheadless=true`
Expected: BUILD SUCCESS — ktlint, the tests, and the JaCoCo threshold.

- [ ] **Step 2: Check the rate never leaves the page**

Run:

```bash
grep -rn "pricePerSqm" modules/ndopify-partner-central/src/main
grep -rn "pricePerSqm\|price-per-sqm" modules/ndopify-partner-central/src/main/resources/templates modules/ndopify-partner-central/src/main/kotlin
```

Expected: `pricePerSqm` only in `OfferPropertyRules` and its call in `OfferPropertyController.termsLines`; the template
only has the `[data-price-per-sqm]` hook; no DTO, request, entity or hidden input.

- [ ] **Step 3: Re-read the approved prototype's states**

Open `?sample=sale`, `?sample=land`, `?sample=land-no-area`, `?sample=house`, `?sample=land-review`,
`?sample=land-review-none` and `?sample=rental`; confirm the production page matches them (the label, the questions,
the documents, the rate line, the review, the confirmation). No commit: this is the reviewer's check.

## Self-Review

- **Spec coverage:** AC 1 (Task 2), AC 2 (Task 2), AC 3 (Task 2), AC 4 (Tasks 1–4), AC 5 (Task 4's manual check plus
  the unchanged script), AC 6 (Tasks 2–4), AC 7 (Task 1), AC 8 (Tasks 3–4).
- **Type consistency:** `projectLabel`, `showsRooms`, `requiresRooms`, `showsParking`, `requiresParking`,
  `asksOwnershipDocuments`, `pricePerSqm` are the names used by the template, the script's equivalents and the tests.
- **Formatting:** `formatter.money(rate)` and `MandateLabels.formatNumber(area)` are the one formatting path; the
  template/controller tests assert the grouped *56 300 FCFA* and the exact line.
- **No JS dependency:** the first render, the documents and the project label are server-rendered; the script only
  hides/disables/marks required.
- **Out of scope:** the land-sale-only rule (rental), the listing page, the mandate edit page and the buyer's search are
  not touched.
