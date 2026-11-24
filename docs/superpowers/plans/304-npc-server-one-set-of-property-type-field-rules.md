# One set of property-type field rules (npc, server) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** One property-type rule set — the same fields asked per type on *Proposer un bien*, *Trouver un bien* and the mandate edit page — with documents on every sale, thousands-grouped price and surface, and a read-only price/m² (land) or price/unit (building).

**Architecture:** The Kotlin rule objects stay the source of truth (`OfferPropertyRules`, `FindPropertyRules`, `MandateEditRules`); the page scripts read one mirror table added to `src/main/assets/js/util.js` (`NdUtil.propertyRules`), so the four copies that disagree today become one per side. The approved prototype `modules/ndopify-partner-central/src/main/html/v2/` is the baseline for every page: its markup order, wording and states are ported, not redesigned. ndopify-server widens two gates only: the documents it keeps on create/update, and the matcher's document veto.

**Tech Stack:** Kotlin (Spring Boot 25 JDK), Thymeleaf, plain ES5 page scripts bundled by esbuild, JUnit 5 + `kotlin.test` + `mockito-kotlin`, `jsoup` for template tests, MySQL for the server's endpoint tests.

**Spec:** GitHub issue [#304](https://github.com/wutsi/ndopify-mono/issues/304) — its body carries the settled decisions, the *Target rules* table and AC 1–15, and its *Prototype* section names the approved prototype (approved 2026-11-24).

## Global Constraints

- UI text, e-mails and contracts in French; code, comments, logs and docs in English.
- Assertions with `kotlin.test` (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`), never `org.junit.jupiter.api.Assertions.*`; mocks with `mockito-kotlin` (`mock`, `doReturn(...).whenever(...)`, `argumentCaptor`, `verify`).
- Amounts shown to users go through `TenantFormatter` (`compactMoney` for a displayed amount, `formatNumber` for a count); a page script uses `window.NdUtil.compactNumber` and the tenant's `data-decimal-separator` / `data-currency` the page already carries. Never a hard-coded `FCFA` in Kotlin.
- Every page works without JavaScript: a script only enhances. A rule-driven question is rendered by the template from the pending answers (hidden when the current type doesn't ask it, `required` when it does), and the script shows/hides them as the type changes. The server drops an answer that doesn't apply (today's pattern).
- The price/unit and price/m² lines are display only: never posted, never stored, rounded to the nearest 100 `FCFA`-of-the-tenant, hidden without a usable amount and divisor.
- No `ndopify-dto` change, no `version` bump beyond the module snapshots, no Flyway migration.
- `src/main/html/` is the prototype: already committed as the approved baseline, never served, never edited again by this plan.
- Test commands: `cd modules/ndopify-partner-central && mvn test`; `cd modules/ndopify-server && mvn test -Dtest=ClassName` (needs the local MySQL of `AGENTS.md`); `mvn validate` from the module for ktlint.
- The client rule table and the Kotlin rules must agree; when they diverge the Kotlin rule is right and the table is fixed.

## Review Focus

- A building's review without its counts: the *Étages* / *Unités* rows leave the two templates only once `propertyLabel` carries "N étages · N unités" (then the surface) — otherwise a building's review silently loses them.
- A hidden question on the edit page wiping a stored answer: the surface of a residential non-land mandate, the furniture of a rental office, an amenities set, a document list. A hidden field must travel unchanged, never as `null`.
- A legacy stored rental *Immeuble*: the type stays checked and enabled, the page asks what a rental building asked (surface, furniture), and saving it keeps its answers — nothing turns it into a sale building nor drops it.
- The widened document veto creating false rule-outs: a search requiring no document, a rental pair, or an `UNKNOWN`-only requirement must never be ruled out; the ordering of `MatchRuleOut` (listing type, then documents) must not change.
- Furniture and parking parity across the pages: a *Chambre* asks its parkings, a rental office or shop asks neither (it does today on the buyer's page), a residential sale asks furniture — and the review/criteria rows follow the same rules.

---

## File Structure

**ndopify-partner-central — rules (the source of truth)**
- `src/main/kotlin/.../ui/offerproperty/OfferPropertyRules.kt` — the seller's rules.
- `src/main/kotlin/.../ui/findproperty/FindPropertyRules.kt` — the buyer's rules, also the edit page's.
- `src/main/kotlin/.../ui/mandate/MandateEditRules.kt`, `MandateEditForm.kt` — the edit page's validation and request.
- `src/main/kotlin/.../ui/offerproperty/PendingListing.kt`, `src/main/kotlin/.../ui/findproperty/PendingMandate.kt` — the wizards' answers.

**ndopify-partner-central — pages**
- `src/main/kotlin/.../ui/offerproperty/OfferPropertyController.kt` + `templates/offer-property.html`.
- `src/main/kotlin/.../ui/findproperty/FindPropertyController.kt` + `templates/find-property.html`.
- `src/main/kotlin/.../ui/mandate/MandateController.kt` (edit endpoints), `MandateLabels.kt` + `templates/mandate/edit.html`, `templates/mandate.html`.
- `src/main/assets/js/util.js` (the shared table), `offer-property.js`, `find-property.js`, `mandate.js`.

**ndopify-server**
- `src/main/kotlin/.../mandate/server/service/MandateService.kt` — what a mandate stores.
- `src/main/kotlin/.../match/server/service/Matcher.kt` — the document veto.
- `src/main/kotlin/.../mandate/server/mail/MandateMailLabels.kt` — the offer e-mail and Telegram line.

---

### Task 1: The seller's rule set (`OfferPropertyRules`)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`

**Interfaces:**
- Produces: `needsFurniture(category, type, listingType)`, `needsParking(category, type, listingType)`, `ownershipDocumentTypes(listingType, type)`, `pricePerUnit(listingType, type, priceAmount: Long?, units: Int?): Long?` — the signatures Tasks 3, 4, 5, 7 call.

- [ ] **Step 1: Write the failing tests**

In `OfferPropertyRulesTest`, replace `a sale asks no furniture, no rooms nor parking for land`, `the documents are a land sale's only` and the BUILDING expectation of `categoriesByTypeInFormOrder`, and add:

```kotlin
@Test
fun `a residential type asks furniture on a sale too, and a room asks its parking`() {
    assertTrue(OfferPropertyRules.needsFurniture(residential, PropertyType.APARTMENT, ListingType.SALE))
    assertTrue(OfferPropertyRules.needsFurniture(residential, PropertyType.STUDIO, ListingType.RENTAL))
    assertFalse(OfferPropertyRules.needsFurniture(residential, PropertyType.BUILDING, ListingType.RENTAL))
    assertFalse(OfferPropertyRules.needsFurniture(residential, PropertyType.LAND, ListingType.SALE))
    assertFalse(OfferPropertyRules.needsFurniture(commercial, PropertyType.OFFICE, ListingType.RENTAL))
    assertTrue(OfferPropertyRules.needsParking(residential, PropertyType.ROOM, ListingType.RENTAL))
}

@Test
fun `a residential non-land type asks no surface`() {
    assertFalse(OfferPropertyRules.needsArea(residential, PropertyType.VILLA, ListingType.SALE))
    assertTrue(OfferPropertyRules.needsArea(residential, PropertyType.LAND, ListingType.SALE))
}

@Test
fun `every sale declares its documents`() {
    val three = listOf(
        TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN, TitleDocumentType.CERTIFICATE_OF_OWNERSHIP,
    )
    assertEquals(three, OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.LAND))
    assertEquals(three, OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.VILLA))
    assertEquals(three, OfferPropertyRules.ownershipDocumentTypes(ListingType.SALE, PropertyType.BUILDING))
    assertEquals(emptyList(), OfferPropertyRules.ownershipDocumentTypes(ListingType.RENTAL, PropertyType.APARTMENT))
}

@Test
fun `a building's implied price per unit`() {
    assertEquals(20_833_300L, OfferPropertyRules.pricePerUnit(ListingType.SALE, PropertyType.BUILDING, 250_000_000L, 12))
    assertNull(OfferPropertyRules.pricePerUnit(ListingType.RENTAL, PropertyType.BUILDING, 250_000_000L, 12))
    assertNull(OfferPropertyRules.pricePerUnit(ListingType.SALE, PropertyType.LAND, 250_000_000L, 12))
    assertNull(OfferPropertyRules.pricePerUnit(ListingType.SALE, PropertyType.BUILDING, 250_000_000L, null))
    assertNull(OfferPropertyRules.pricePerUnit(ListingType.SALE, PropertyType.BUILDING, null, 12))
}

@Test
fun `a rental never offers a building`() {
    assertEquals(emptyList(), OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL))
    assertEquals(emptyList(), OfferPropertyRules.propertyTypes(PropertyCategory.RESIDENTIAL, ListingType.RENTAL)
        .filter { it == PropertyType.BUILDING })
    assertEquals(listOf(PropertyType.BUILDING), OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE)
        .filter { it == PropertyType.BUILDING || it == PropertyType.LAND }.dropLast(1))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest`
Expected: FAIL — `pricePerUnit` unresolved; the furniture, documents and building assertions fail.

- [ ] **Step 3: Change the rules**

In `OfferPropertyRules`:
- `CATEGORIES_BY_TYPE` loses its `PropertyType.BUILDING` entry (a building is sold only; `SALE_CATEGORIES_BY_TYPE` already adds `BUILDING to setOf(PropertyCategory.COMMERCIAL)`).
- `needsFurniture`: `residential(category) && type != PropertyType.BUILDING && type != PropertyType.LAND` (no `isSale` gate).
- `needsParking`: `!buildingSale(listingType, type) && residential(category) && type != PropertyType.LAND` (drop `type != PropertyType.ROOM`).
- `ownershipDocumentTypes`: `if (isSale(listingType)) TITLE_DOCUMENT_TYPES else emptyList()` (ignore the type).
- Add `fun pricePerUnit(listingType: ListingType?, type: PropertyType?, priceAmount: Long?, units: Int?): Long?` mirroring `pricePerSqm`: a `BUILDING` sale only, `priceAmount > 0`, `units != null && units >= 1 && units <= MAX_UNITS`, `Math.round((priceAmount.toDouble() / units) / 100) * 100`, `takeIf { it > 0 }`.
- Update the KDoc that still says a sale asks no furniture, and that a plot's documents are the only ones.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt
git commit -m "feat(npc): one seller rule set per property type"
```

---

### Task 2: The buyer's rule set (`FindPropertyRules`)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`

**Interfaces:**
- Produces: `needsFurniture(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean`, `asksParkings(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean`, `titleDocumentTypes(listingType, type)` for every purchase — Tasks 3, 6, 7, 8 and `MandateLabels` call them with the category now.

- [ ] **Step 1: Write the failing tests**

Replace the "a sale asks no furniture…" and "the documents are asked for a land sale only" tests and the BUILDING expectations of the category tests, and add:

```kotlin
@Test
fun `a residential purchase asks furniture, a room asks its parkings, a commercial type asks none`() {
    assertTrue(FindPropertyRules.needsFurniture(residential, PropertyType.HOUSE, ListingType.SALE))
    assertTrue(FindPropertyRules.needsFurniture(residential, null, ListingType.SALE)) // "Indifférent"
    assertFalse(FindPropertyRules.needsFurniture(residential, PropertyType.BUILDING, ListingType.SALE))
    assertFalse(FindPropertyRules.needsFurniture(commercial, PropertyType.OFFICE, ListingType.RENTAL))
    assertTrue(FindPropertyRules.asksParkings(residential, PropertyType.ROOM, ListingType.RENTAL))
    assertFalse(FindPropertyRules.asksParkings(commercial, PropertyType.OFFICE, ListingType.RENTAL))
    assertFalse(FindPropertyRules.asksParkings(residential, PropertyType.LAND, ListingType.SALE))
    assertFalse(FindPropertyRules.asksParkings(commercial, PropertyType.BUILDING, ListingType.SALE))
}

@Test
fun `every purchase may require its documents`() {
    val three = listOf(
        TitleDocumentType.LAND_TITLE, TitleDocumentType.SURVEY_PLAN, TitleDocumentType.CERTIFICATE_OF_OWNERSHIP,
    )
    assertEquals(three, FindPropertyRules.titleDocumentTypes(ListingType.SALE, PropertyType.LAND))
    assertEquals(three, FindPropertyRules.titleDocumentTypes(ListingType.SALE, PropertyType.APARTMENT))
    assertEquals(emptyList(), FindPropertyRules.titleDocumentTypes(ListingType.RENTAL, PropertyType.APARTMENT))
}

@Test
fun `a rental never offers a building`() {
    assertFalse(FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL).contains(PropertyType.BUILDING))
    assertTrue(FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE).contains(PropertyType.BUILDING))
}

@Test
fun `a residential non-land purchase asks no surface`() {
    assertFalse(FindPropertyRules.needsArea(residential, PropertyType.VILLA, ListingType.SALE))
    assertTrue(FindPropertyRules.needsArea(commercial, PropertyType.OFFICE, ListingType.SALE))
    assertTrue(FindPropertyRules.needsArea(residential, PropertyType.LAND, ListingType.SALE))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest`
Expected: FAIL — compile error on the `category` parameter of `needsFurniture` / `asksParkings`.

- [ ] **Step 3: Change the rules**

- `RENTAL_CATEGORIES_BY_TYPE` loses `PropertyType.BUILDING`; `SALE_CATEGORIES_BY_TYPE` adds `(PropertyType.BUILDING to setOf(PropertyCategory.COMMERCIAL))` explicitly.
- `needsFurniture(category, type, listingType)`: `category == PropertyCategory.RESIDENTIAL && type != PropertyType.LAND && type != PropertyType.BUILDING` (a null type, i.e. *Indifférent*, is asked — the buyer keeps the answer).
- `asksParkings(category, type, listingType)`: `category == PropertyCategory.RESIDENTIAL && type != PropertyType.LAND && !buildingSale(listingType, type)`.
- `titleDocumentTypes`: `if (isSale(listingType)) TITLE_DOCUMENT_TYPES else emptyList()`.
- Update the KDoc (furniture and documents are not land-only/new).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt
git commit -m "feat(npc): one buyer rule set per property type"
```

---

### Task 3: The wizards' answers follow the rules

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListing.kt`, `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandate.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListingTest.kt`, `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt`

**Interfaces:**
- Consumes: Task 1's and Task 2's rules (the new `category` parameters).
- Produces: the same `toCreateMandateRequest(currency)` / `toCreateMandateRequest(...)` returning `ownershipDocuments` for every sale and `titleDocuments` for every purchase; `isComplete(step, today)` unchanged in name.

- [ ] **Step 1: Write the failing tests**

In `PendingListingTest`:

```kotlin
@Test
fun `a sale sends its documents whatever its type, a rental none`() {
    val documents = listOf(TitleDocumentType.LAND_TITLE, TitleDocumentType.LAND_TITLE, TitleDocumentType.UNKNOWN)
    val answers = PendingListing(
        listingType = ListingType.SALE, propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = "HOUSE",
        cityId = DOUALA, neighborhoodId = BONAPRISO, priceAmount = 45_000_000,
        ownershipDocuments = documents,
    )
    assertEquals(listOf(TitleDocumentType.LAND_TITLE), answers.toCreateMandateRequest("XAF").ownershipDocuments)
    assertEquals(
        emptyList(),
        answers.copy(listingType = ListingType.RENTAL, propertyType = "APARTMENT").toCreateMandateRequest("XAF").ownershipDocuments,
    )
}
```

In `PendingMandateTest`, the same shape for `titleDocuments` (a `HOUSE` purchase keeps `LAND_TITLE`; a rental sends none), and a `?`-less assertion that a `HOUSE` purchase with no document sends `emptyList()`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='PendingListingTest,PendingMandateTest'`
Expected: FAIL — the documents are still dropped for a non-land sale.

- [ ] **Step 3: Change the answers**

- `PendingListing.toCreateMandateRequest`: `ownershipDocuments = ownershipDocuments.filter { isSale() && it != TitleDocumentType.UNKNOWN }.distinct()`; import `TitleDocumentType`.
- `PendingMandate.toCreateMandateRequest`: `titleDocuments = titleDocuments.filter { sale && it != TitleDocumentType.UNKNOWN }.distinct()`.
- `PendingMandate`'s `needsFurniture` / `asksParkings` calls pass `propertyCategory`; `PendingListing`'s calls are unchanged in name (its rules already take the category).

- [ ] **Step 4: Run the module's tests to verify nothing else broke**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='PendingListingTest,PendingMandateTest'`
Expected: PASS. (`mvn test` in full is run at the end of Task 8.)

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListing.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandate.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListingTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt
git commit -m "feat(npc): a sale's documents travel for every type"
```

---

### Task 4: The seller's page (controller + template)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`, `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`, `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: Task 1's `needsFurniture`, `needsParking`, `needsArea`, `ownershipDocumentTypes`, `pricePerUnit`.
- Produces: the model attributes `needsFurniture`, `needsRooms`, `needsParking`, `needsArea` (Booleans of the posted type), `asksOwnershipDocuments` (= `sale`), `ownershipDocumentsLabel` (every sale), `pricePerUnitLabel` (a building sale) — Task 5's script reads the same DOM hooks.

- [ ] **Step 1: Write the failing tests**

In `OfferPropertyTemplateTest` (a sale, features step, no type posted yet):

```kotlin
@Test
fun `a sale shows no follow-up question before its type is chosen`() {
    get("/offer-property?step=features") // with a SALE PendingListing seeded in the session whose type is null
    val page = render()
    assertTrue(page.select("[data-group=rooms]").attr("hidden").isNotEmpty())
    assertTrue(page.select("[data-group=furnitureType]").attr("hidden").isNotEmpty())
    assertTrue(page.select("[data-group=area]").attr("hidden").isNotEmpty())
    assertNotNull(page.selectFirst("[data-group=ownershipDocuments]:not([hidden])"))
}

@Test
fun `a sold villa asks its furniture, its rooms and shows its documents`() {
    // ... post type=HOUSE, furnitureType=UNFURNISHED, rooms=3, parkings=2 and render again
    val page = render()
    assertTrue(page.select("[data-group=furnitureType]").attr("hidden").isEmpty())
    assertTrue(page.select("[data-group=area]").attr("hidden").isNotEmpty()) // a residential non-land has no surface
    assertNotNull(page.selectFirst("input[name=ownershipDocuments][value=LAND_TITLE]"))
}

@Test
fun `a building sale's review reads its counts once`() {
    // ... a BUILDING sale, levels=3, units=12, then GET /offer-property?step=review
    val page = render()
    assertEquals("Immeuble entier · 3 étages · 12 unités · 850 m²", page.selectFirst("[data-review-value=property]")!!.text())
    assertNull(page.selectFirst("[data-review-value=levels]"))
    assertTrue(page.select("[data-review-value=terms]").text().contains("≈ 20,83M FCFA/unité pour 12 unités"))
}

@Test
fun `a sold villa's review lists its documents, or none`() {
    // ... a HOUSE sale with LAND_TITLE + SURVEY_PLAN, then without any
    assertEquals("Titre foncier · Plan de bornage", page.selectFirst("[data-review-value=ownershipDocuments]")!!.text())
    assertEquals("Aucun document déclaré", page.selectFirst("[data-review-value=ownershipDocuments]")!!.text())
}
```

In `OfferPropertyControllerTest`: posting `ownershipDocuments=LAND_TITLE&ownershipDocuments=SURVEY_PLAN` on a `HOUSE` sale reaches `CreateMandateRequest.ownershipDocuments` with both (captor), and a `RENTAL` posts none.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyTemplateTest,OfferPropertyControllerTest'`
Expected: FAIL — the questions render visible before the type, the documents row is missing for a house, the counts rows are still there.

- [ ] **Step 3: Change the controller**

In `features()`'s model block (around lines 444–478):
- Delete `showsRooms`, `showsParking` (keep `requiresRooms`, `requiresParking` but with `type != null` and no `!sale` branch): `requiresRooms = residential && type != null && OfferPropertyRules.needsRooms(...)`, idem parking.
- `asksOwnershipDocuments` = `sale` (no type condition).
- Replace `asksArea = !residential(category) || sale` with `asksArea = OfferPropertyRules.needsArea(category, type, answers.listingType)` (false before the type is chosen, false for residential non-land, true for a sold plot under either usage and for a commercial type).
- Add `needsFurniture` (Boolean) so the template can hide the group before a type: `type != null && OfferPropertyRules.needsFurniture(...)`.
- In the review block: `propertyLabel(answers)` folds the counts (Step 3b) and the documents label drops its `type == LAND` condition; add `pricePerUnitLabel` = `OfferPropertyRules.pricePerUnit(...)?.let { "≈ " + formatter().compactMoney(it) + "/unité pour " + units + (if (units > 1) " unités" else " unité") }`.
- `propertyLabel`: after the parking part, `answers.levels?.takeIf { answers.buildingSale() }?.let { "$it étages" }`, `answers.units?...` then the area (prototype order).
- `termsLines`: after the price, `pricePerUnit(...)?.let { "≈ " + formatter().compactMoney(it) + "/unité pour " + answers.units!! + " unités" }` for a building sale, like the existing `/m²` line.
- Delete the now-dead `!sale`/`needsArea` branches from the features POST handler if the rules make them unreachable (`area` is kept only when `needsArea`).

- [ ] **Step 4: Change the template**

In `templates/offer-property.html`, features step:
- `data-group="rooms"`: keep `th:if="${residential}"`, replace `th:hidden="${!showsRooms}"` with `th:hidden="${!requiresRooms}"`.
- `data-group="furnitureType"`: `th:hidden="${!needsFurniture}"` (the group is rendered for a sale too now).
- `data-group="hasParking"` / `"parkings"`: `th:hidden="${!requiresParking}"`.
- `data-group="area"`: `th:hidden="${!asksArea}"`.
- `data-group="ownershipDocuments"`: `th:hidden="${!asksOwnershipDocuments}"`, legend and hint as the prototype ("Pour toute vente : …").
Review step: remove the `levelsLabel` / `unitsLabel` rows; add the `pricePerUnitLabel` line right after the terms' price line (a `<p>` inside the same `data-review-value="terms"` block or a new line above `availability`, whichever the prototype's order shows); the documents row renders for every sale with `Aucun document déclaré` when empty (already through `ownershipDocumentsLabel`).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyTemplateTest,OfferPropertyControllerTest,OfferPropertyRulesTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/offer-property.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt
git commit -m "feat(npc): the seller's page follows the one rule set"
```

---

### Task 5: The buyer's page (controller + template)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt`, `modules/ndopify-partner-central/src/main/resources/templates/find-property.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`, `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: Task 2's `needsFurniture(category, …)`, `asksParkings(category, …)`, `titleDocumentTypes`, `needsArea`.
- Produces: model attributes `needsFurniture`, `needsParkings`, `showsArea`, `asksTitleDocuments` (= `sale`), `titleDocumentsLabel` (every purchase), `pricePerUnitLabel` — Task 6's script reads the same hooks.

- [ ] **Step 1: Write the failing tests**

In `FindPropertyTemplateTest`:

```kotlin
@Test
fun `a purchase asks its documents whatever its type`() {
    // ... a SALE search, type HOUSE, the property step
    assertNotNull(page.selectFirst("[data-group=titleDocuments]:not([hidden])"))
    assertNotNull(page.selectFirst("input[name=titleDocuments][value=LAND_TITLE]"))
}

@Test
fun `a residential purchase asks furniture and no surface, a commercial type asks its surface`() {
    // ... type HOUSE: furnitureType visible, data-group=area hidden
    // ... type OFFICE: furnitureType hidden, data-group=area visible
}

@Test
fun `a rental office asks no parkings`() {
    // ... a RENTAL search, type OFFICE
    assertTrue(page.select("[data-group=parkings]").attr("hidden").isNotEmpty())
}

@Test
fun `a building's review reads its counts once, with the budget's price per unit`() {
    // ... a BUILDING purchase, units=12, budget 250 000 000
    assertEquals("Immeuble · 3 étages · 12 unités · 850 m² minimum", page.selectFirst("[data-review-value=property]")!!.text())
    assertNull(page.selectFirst("[data-review-value=levels]"))
    assertTrue(page.select("[data-review-value=price]").text().contains("≈ 20,83M FCFA/unité pour 12 unités"))
}
```

In `FindPropertyControllerTest`: a `HOUSE` purchase posts `titleDocuments=LAND_TITLE` and it reaches `CreateMandateRequest.titleDocuments`; a rental posts none.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyTemplateTest,FindPropertyControllerTest'`
Expected: FAIL.

- [ ] **Step 3: Change the controller**

- `STEP_PROPERTY` model: `asksTitleDocuments = FindPropertyRules.isSale(answers.listingType)`; add `needsFurniture = FindPropertyRules.needsFurniture(answers.propertyCategory, type, answers.listingType)`, `needsParkings = FindPropertyRules.asksParkings(answers.propertyCategory, type, answers.listingType)`, `showsArea = FindPropertyRules.needsArea(...) || (type == null && (commercial || sale))` (the prototype's `showsArea`: an *Indifférent* search keeps the field when it may match a plot).
- `STEP_BUDGET`: no change beyond the script (the price/unit line is computed client-side and repeated in the review).
- `STEP_REVIEW`: `titleDocumentsLabel` for every purchase; `priceLabel` gains the rate line — either keep `priceLabel` on one line and add `pricePerUnitLabel`/`pricePerSqmLabel` rendered after it in the template (prototype order), or append to `priceLabel`; use the labels the prototype shows (`≈ … FCFA/unité pour N unités`, `≈ … FCFA/m² pour N m²`).
- `propertyLabel`: after the parking part, the counts (`answers.buildingSale()`): `levels?.toString() ?: "Indifférent"` + " étages", idem units, then the surface.

- [ ] **Step 4: Change the template**

In `templates/find-property.html`:
- rooms: unchanged (`th:if` residential).
- furniture: replace `th:if="${!sale}"` with `th:if="${#strings.equals(answers.propertyCategory, 'RESIDENTIAL')}"` (the file's existing pattern, as for rooms) and `th:hidden="${!needsFurniture}"`; the "Indifférent" radio stays.
- parkings: `th:hidden="${!needsParkings}"` (drop `buildingSale`).
- area: `th:hidden="${!showsArea}"`, label as today.
- titleDocuments: `th:hidden="${!asksTitleDocuments}"`, comment "every purchase".
- review: drop the `levelsLabel` / `unitsLabel` rows; add the rate line after the budget (`priceLabel`), keeping `data-review-value="price"` for the budget itself and a `data-review-value="priceRate"` (or the `review__multiline` block the prototype uses) for the rate.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyTemplateTest,FindPropertyControllerTest,FindPropertyRulesTest,PendingMandateTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt \
        modules/ndopify-partner-central/src/main/resources/templates/find-property.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt
git commit -m "feat(npc): the buyer's page follows the one rule set"
```

---

### Task 6: The shared script table and the three page scripts

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/js/util.js`, `offer-property.js`, `find-property.js`, `mandate.js`
- Test: none — no JS test harness exists in this module; the checks are the two `node` commands below plus the module's Kotlin tests (the templates' DOM hooks the scripts use).

**Interfaces:**
- Produces: `window.NdUtil.propertyRules` = `{ TYPES, DOCUMENTS, categories(type, listingType), offers(type, listingType), asks(type, listingType, field), asksDocuments(listingType) }`, the prototype's table (`modules/ndopify-partner-central/src/main/html/v2/assets/js/util.js`) with its per-type flags; the three scripts stop keeping their own `WITHOUT_*` / category lists.

- [ ] **Step 1: Add the table and check its shape**

Copy the prototype's `PROPERTY_RULES` / `PROPERTY_DOCUMENTS` / `propertyRule` / `typeCategories` / `typeOffered` / `typeAsks` / `asksDocuments` into `src/main/assets/js/util.js`, exported under `propertyRules`, with the prototype's KDoc (the Kotlin rules are the source of truth; this table mirrors them). Keep `compactNumber` and the phone helpers.

Check: `node -e 'global.window={}; require("./modules/ndopify-partner-central/src/main/assets/js/util.js"); const r=window.NdUtil.propertyRules; console.log(JSON.stringify([r.offers("BUILDING","RENTAL"), r.offers("BUILDING","SALE"), r.asks("ROOM","RENTAL","parkings"), r.asks("HOUSE","SALE","furniture"), r.asks("OFFICE","RENTAL","parkings"), r.asksDocuments("SALE"), r.asksDocuments("RENTAL")]))'`
Expected: `[[],["COMMERCIAL"],true,true,false,true,false]`.

- [ ] **Step 2: Migrate `offer-property.js`**

Replace its `WITHOUT_ROOMS` / `WITHOUT_FURNITURE` / `WITHOUT_PARKING` lists and its `needsFurniture` / `needsRooms` / `needsParking` / `needsArea` bodies with `RULES.asks(type, sale ? "SALE" : "RENTAL", "<field>")` (`var RULES = window.NdUtil.propertyRules;`), delete the `showsRooms` / `showsParking` / `requiresRooms` / `requiresParking` preview and their `places` clause (no question before a type, documents excepted — the template already renders them hidden and `required`), send `ownershipDocuments` for every sale, add the `[data-price-per-unit]` line under the units (same `compactNumber` + `data-decimal-separator` rendering as the existing `/m²` line, wording `Soit ≈ … FCFA/unité pour N unités`), and group the area as typed (same `bindNumber` as the price).

- [ ] **Step 3: Migrate `find-property.js`**

Same table; `show("furnitureType", RULES.asks(type, project, "furniture") || (isAny && residential), …)`, parkings from `RULES.asks(..., "parkings")` (a *Chambre* shows them, a commercial type doesn't), the area hidden unless `RULES.asks(..., "area")` or an *Indifférent* search that may match a plot, `titleDocuments` shown for every purchase, and the price/unit line on the budget step (`units` not *Indifférent*).

- [ ] **Step 4: Migrate `mandate.js`'s edit page**

`syncProperty()` reads `NdUtil.propertyRules` (drop `data-categories` / `data-rental-categories` reliance, keep the stored rental `LAND` / `BUILDING` exception), hides the furniture group only where the table says a type asks none, and binds `#area` with the price's grouping helper; add the rate `<p>` update (price ÷ surface for a plot, price ÷ units for a building, `NdUtil.compactNumber`) and keep every hidden question's value untouched (the template posts it; the script never clears it).

Check: `node --check` on the three scripts, and `grep -n "WITHOUT_" src/main/assets/js/*.js` prints nothing.

- [ ] **Step 5: Run the module's tests and commit**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='OfferPropertyTemplateTest,FindPropertyTemplateTest,MandateEditTemplateTest'`
Expected: PASS (the DOM hooks the scripts use are the ones these templates render).

```bash
git add modules/ndopify-partner-central/src/main/assets/js/util.js \
        modules/ndopify-partner-central/src/main/assets/js/offer-property.js \
        modules/ndopify-partner-central/src/main/assets/js/find-property.js \
        modules/ndopify-partner-central/src/main/assets/js/mandate.js
git commit -m "feat(npc): one rule table for the page scripts"
```

---

### Task 7: The mandate edit page's rules and request

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRules.kt`, `MandateEditForm.kt`, `MandateController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt`, `MandateControllerTest.kt`

**Interfaces:**
- Consumes: Task 1/2 rules, `MandateLabels.formatNumber`.
- Produces: `MandateEditForm.documents: List<String> = emptyList()` (the checkboxes' `name="documents"`), `MandateEditRules.request(...)` writing `ownershipDocuments` for a LISTING and `titleDocuments` for a SEARCH (hidden questions travel unchanged), `MandateEditForm.of` pre-filling `area` grouped.

- [ ] **Step 1: Write the failing tests**

In `MandateEditRulesTest`:

```kotlin
@Test
fun `a sale's edit page saves the documents on the mandate's own list`() {
    val listing = MANDATE.copy(mandateType = MandateType.LISTING, listingType = ListingType.SALE, propertyType = PropertyType.HOUSE)
    val request = MandateEditRules.request(FORM.copy(propertyType = "HOUSE", area = "120 000", documents = listOf("LAND_TITLE", "UNKNOWN")), listing)
    assertEquals(listOf(TitleDocumentType.LAND_TITLE), request.ownershipDocuments)
    assertEquals(listing.titleDocuments, request.titleDocuments)

    val search = MANDATE.copy(mandateType = MandateType.SEARCH, listingType = ListingType.SALE, propertyType = PropertyType.HOUSE)
    assertEquals(
        listOf(TitleDocumentType.SURVEY_PLAN),
        MandateEditRules.request(FORM.copy(propertyType = "HOUSE", documents = listOf("SURVEY_PLAN")), search).titleDocuments,
    )
}

@Test
fun `a question the page doesn't ask keeps its stored answer`() {
    val rental = MANDATE.copy(listingType = ListingType.RENTAL, propertyType = PropertyType.APARTMENT, area = 150)
    assertEquals(150, MandateEditRules.request(FORM.copy(propertyType = "APARTMENT", area = ""), rental).area)
    val office = MANDATE.copy(listingType = ListingType.RENTAL, propertyType = PropertyType.OFFICE, furnitureType = FurnitureType.UNFURNISHED)
    assertEquals(FurnitureType.UNFURNISHED, MandateEditRules.request(FORM.copy(propertyType = "OFFICE", furnitureType = null), office).furnitureType)
}

@Test
fun `a stored rental building keeps its type and its rental answers`() {
    val building = MANDATE.copy(listingType = ListingType.RENTAL, propertyType = PropertyType.BUILDING, area = 300, furnitureType = FurnitureType.UNFURNISHED)
    val request = MandateEditRules.request(FORM.copy(propertyType = "BUILDING", area = "300", furnitureType = "UNFURNISHED"), building)
    assertEquals(PropertyType.BUILDING, request.propertyType)
    assertEquals(FurnitureType.UNFURNISHED, request.furnitureType)
    assertNull(request.levels)
}
```

In `MandateControllerTest`, `POST /mandates/104/edit` with `documents=LAND_TITLE&documents=SURVEY_PLAN` captures an `UpdateMandateRequest` whose `titleDocuments` holds both.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateEditRulesTest`
Expected: FAIL — `documents` doesn't exist on the form, the surface is cleared.

- [ ] **Step 3: Change the form and the rules**

- `MandateEditForm`: add `val documents: List<String> = emptyList()`; `of(mandate)` sets `documents = (if (mandate.mandateType == LISTING) mandate.ownershipDocuments else mandate.titleDocuments).map { it.name }` and `area = mandate.area?.let { MandateLabels.formatNumber(it) }`.
- `MandateEditRules.request`: parse `area` as today (spaces already ignored); `area = if (areaShown(…) ) parsed else mandate.area`; `furnitureType = if (FindPropertyRules.needsFurniture(category, type, listingType)) form… else mandate.furnitureType`; `parkings`, `rooms`, `levels`, `units` follow the same "shown ? posted : stored" rule; `documents` → `ownershipDocuments` for a LISTING, `titleDocuments` for a SEARCH, deduplicated with `TitleDocumentType.UNKNOWN` dropped, parsed through `TitleDocumentType.entries.firstOrNull { it.name == it }`, and the mandate's own list for the other key; a rental keeps both stored lists (they are empty).
- `MandateEditRules.summaries`: the property summary drops the surface for a residential non-land type (it isn't asked), the budget summary unchanged.
- `MandateController.edit(...)`: add the model attributes the template needs — `documentLabels` (`MandateLabels.TITLE_DOCUMENT_LABELS`), `documentNames` (the checked names), `currency`, `decimalSeparator`, `sale` (already) — and keep `summaries`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateEditRulesTest,MandateControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRules.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditForm.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateController.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt
git commit -m "feat(npc): the mandate edit page follows the one rule set"
```

---

### Task 8: The mandate edit page's template

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt`

**Interfaces:**
- Consumes: Task 7's model attributes (`documentLabels`, `documentNames`, `currency`, `decimalSeparator`, the grouped `answers.area`), Task 6's `mandate.js`.

- [ ] **Step 1: Write the failing tests**

In `MandateEditTemplateTest`:

```kotlin
@Test
fun `a sale's edit page shows its documents and its rate`() {
    // ... GET /mandates/104/edit (a SEARCH · SALE) and GET /mandates/310/edit (a LISTING · SALE, seeded mandate)
    assertNotNull(page.selectFirst("[data-group=documents]:not([hidden])"))
    assertNotNull(page.selectFirst("input[name=documents][value=LAND_TITLE][checked]"))
    assertTrue(page.select("[data-rate]").text().contains("FCFA/m²"))
}

@Test
fun `a rental's edit page has no documents and a grouped surface`() {
    // ... GET /mandates/201/edit
    assertNull(page.selectFirst("[data-group=documents]")) // or hidden
    assertEquals("150", page.selectFirst("input[name=area]")!!.`val`())
}

@Test
fun `a stored rental building keeps its type`() {
    // ... GET /mandates/210/edit
    assertTrue(page.selectFirst("input[name=propertyType][value=BUILDING]")!!.hasAttr("checked"))
    assertFalse(page.selectFirst("input[name=propertyType][value=BUILDING]")!!.hasAttr("disabled"))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateEditTemplateTest`
Expected: FAIL — no documents fieldset, the area is `150` only by luck of the seed.

- [ ] **Step 3: Add the fields**

Port the prototype's `v2/mandate/edit.html` into `templates/mandate/edit.html`:
- Inside the property group, after the surface: `<fieldset class="choices" data-group="documents" th:hidden="${!sale}">` with the legend `Document de propriété` (`data-label-owner`) / `Documents exigés` (`data-label-tenant`) and three checkboxes `name="documents"` (`LAND_TITLE`, `SURVEY_PLAN`, `CERTIFICATE_OF_OWNERSHIP`) checked from `documentNames`, plus the hint.
- Under the price: `<p class="field__counter" data-rate hidden></p>`, and the price/area inputs carry the tenant's `data-currency` / `data-decimal-separator` if they don't already.
- The property type radios keep `data-tenant-only` on *Indifférent*; **drop** the `data-categories` / `data-rental-categories` attributes (the script reads the table).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateEditTemplateTest,MandateEditRulesTest,MandateControllerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt
git commit -m "feat(npc): documents and rate on the mandate edit page"
```

---

### Task 9: The mandate page's criteria

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt`

**Interfaces:**
- Produces: `MandateLabels.criteria` rows — furniture on a residential sale, documents for every sale (`Documents exigés` for a search; `Document de propriété` only when non-empty, for a listing).

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test
fun `a sale of any type shows its documents`() {
    val villa = SALE_LISTING.copy(propertyType = PropertyType.HOUSE, ownershipDocuments = listOf(TitleDocumentType.LAND_TITLE))
    assertEquals("Titre foncier", MandateLabels.criteria(villa, emptyMap(), FORMATTER).toMap()["Document de propriété"])
    val none = villa.copy(ownershipDocuments = emptyList())
    assertNull(MandateLabels.criteria(none, emptyMap(), FORMATTER).toMap()["Document de propriété"])
    val search = SALE_SEARCH.copy(propertyType = PropertyType.APARTMENT, titleDocuments = emptyList())
    assertEquals("Aucun document exigé", MandateLabels.criteria(search, emptyMap(), FORMATTER).toMap()["Documents exigés"])
}

@Test
fun `a residential sale shows its furniture, a rental office none`() {
    assertEquals("Meublé", MandateLabels.criteria(VILLA_SALE.copy(furnitureType = FurnitureType.FULLY_FURNISHED), emptyMap(), FORMATTER).toMap()["Meublé"])
    assertNull(MandateLabels.criteria(OFFICE_RENTAL.copy(furnitureType = FurnitureType.FULLY_FURNISHED), emptyMap(), FORMATTER).toMap()["Meublé"])
}
```

(Use the test class's existing mandate fixtures; add local copies with the listing type and type the case needs.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateLabelsTest`
Expected: FAIL.

- [ ] **Step 3: Change the rows**

In `MandateLabels.criteria`: drop `type == PropertyType.LAND` from the two documents rows and `!sale` from the furniture row; pass `mandate.propertyCategory` to `FindPropertyRules.needsFurniture` / `asksParkings`. Keep `ownershipDocuments(...)` non-empty guard for a listing and `titleDocuments(...)` always for a search.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateLabelsTest,MandateTemplateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt
git commit -m "feat(npc): a sale's documents on the mandate page"
```

---

### Task 10: ndopify-server stores the documents of every sale

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateServiceTest.kt`, `.../mandate/server/endpoint/CreateMandateEndpointTest.kt`, `.../mandate/server/endpoint/UpdateMandateEndpointTest.kt`

**Interfaces:**
- Produces: `MandateEntity.ownershipDocuments` / `titleDocuments` kept for a `SALE` of any type — Tasks 11 and 12 read them.

- [ ] **Step 1: Write the failing tests**

In `MandateServiceTest` (create and update): a `SALE · HOUSE` with `ownershipDocuments = [LAND_TITLE, LAND_TITLE, UNKNOWN]` and `titleDocuments = [SURVEY_PLAN]` stores `[LAND_TITLE]` and `[SURVEY_PLAN]`; a `RENTAL` stores both empty.

In `CreateMandateEndpointTest` / `UpdateMandateEndpointTest`: invert `drop the documents of a non-land sale` / `drop the ownership documents of a non-land sale` into "keep the documents of a house sale"; keep `drop the ownership documents of a rental`.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateMandateEndpointTest` (MySQL running)
Expected: FAIL — the non-land sale's documents are dropped.

- [ ] **Step 3: Change the filters**

In `MandateService.create` and `MandateService.update`: `filter { sale && it != TitleDocumentType.UNKNOWN }` for both lists (drop `criteria.propertyType == PropertyType.LAND` / `request.propertyType == PropertyType.LAND`); update their KDoc.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='CreateMandateEndpointTest,UpdateMandateEndpointTest,MandateServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server
git commit -m "feat(server): a sale's documents are stored whatever its type"
```

---

### Task 11: The matcher's document veto covers every sale

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Consumes: Task 10's stored documents.
- Produces: `MatchRuleOut.TITLE_DOCUMENTS` for a `SALE` search requiring documents vs a `SALE` listing declaring none of them, whatever the two types.

- [ ] **Step 1: Write the failing tests**

Replace `a non-land sale never fires the documents rule` with:

```kotlin
@Test
fun `a non-land sale fires the documents rule too`() {
    val search = landSearch.copy(propertyType = PropertyType.HOUSE, rooms = "3")
    val listing = landListing.copy(propertyType = PropertyType.HOUSE, rooms = 3, mandate = landListing.mandate.copy(ownershipDocuments = emptyList()))

    assertNull(score(search, listing))
    assertEquals(MatchRuleOut.TITLE_DOCUMENTS, rule(search, listing))
}

@Test
fun `a rental still never fires the documents rule, whatever the search requires`() {
    assertNull(rule(search.copy(titleDocuments = listOf(TitleDocumentType.LAND_TITLE)), listingA))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: FAIL — the house sale pair scores.

- [ ] **Step 3: Widen the rule**

In `documentsMismatch`: keep `required.isNotEmpty()`, `search.listingType == SALE`, `listing.listingType == SALE`, and the "declares none of the required" test; delete the two `propertyType == PropertyType.LAND` conditions. Update its KDoc.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt
git commit -m "feat(server): the document veto covers every sale type"
```

---

### Task 12: The offer e-mail and the Telegram line

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferMailetTest.kt`, `.../mandate/server/mail/MandateOfferTemplateTest.kt`, `.../mandate/server/bot/MandateOfferBotletTest.kt`

**Interfaces:**
- Consumes: Task 10's stored documents.
- Produces: `MandateMailLabels.ownershipDocuments(mandate)` non-null for a `SALE` of any type with documents; `titleDocuments` unchanged (a search's requirement).

- [ ] **Step 1: Write the failing tests**

In `MandateOfferMailetTest`: `say no ownership document for a villa sale` becomes `offer the ownership documents of a sale of any type` (a villa sale with `[LAND_TITLE]` renders "Titre foncier"); keep a "no line without documents" case. In `MandateOfferBotletTest`: the same inversion for its villa case (`document de propriété : Titre foncier`).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateOfferMailetTest,MandateOfferBotletTest'`
Expected: FAIL.

- [ ] **Step 3: Drop the land condition**

`MandateMailLabels.ownershipDocuments`: `if (mandate.listingType == ListingType.SALE)` (drop `&& mandate.propertyType == PropertyType.LAND`); update the KDoc.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateOfferMailetTest,MandateOfferTemplateTest,MandateOfferBotletTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server
git commit -m "feat(server): a sale's documents in the offer e-mail and Telegram"
```

---

## Final verification

- [ ] **Run both modules' suites**

```bash
cd modules/ndopify-partner-central && mvn verify
cd modules/ndopify-server && mvn verify
```

Expected: both green (ktlint + tests + coverage gates; the server needs the local MySQL of `AGENTS.md`).

- [ ] **Check the rule copies agree**

`grep -n "WITHOUT_\|data-categories\|data-rental-categories" modules/ndopify-partner-central/src/main/assets/js/*.js modules/ndopify-partner-central/src/main/resources/templates/*.html` prints nothing but the listing pages' own attributes (`listing/bien.html`, `partner-central.js`'s listing section), which this plan leaves untouched.

- [ ] **Confirm the prototype is untouched**

`git status --porcelain modules/ndopify-partner-central/src/main/html` prints nothing (the approved prototype was committed with the plan).
