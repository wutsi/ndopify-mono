# Land Is Offered for Sale or Purchase Only, Never for Rent Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The two Partner Central wizards offer *Terrain* only for a sale or a purchase, never for a rental, and the mandate edit page no longer offers it on a rental while a mandate already saved as a rental land stays readable and savable.

**Architecture:** Remove `LAND` from the rental maps of `OfferPropertyRules` and `FindPropertyRules` (the sale maps already derive from them and keep it under both uses), keep the offer wizard's *Terrain* label and the sale's *Commercial* wording, and make the mandate edit page's type check use the mandate's own listing type plus an exception for a stored rental land. The edit page's type grid becomes one full-width row per type. No server, DTO, migration or `ndopify-web` change.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, JUnit 5 + `kotlin.test`, mockito-kotlin, `MockMvc`, jsoup.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/275

**Approved prototype (the baseline to port, not to design again):** `modules/ndopify-partner-central/src/main/html/v2/`
- `offer-property.html`, `assets/js/offer-property.js`
- `find-property.html`, `assets/js/find-property.js`
- `mandate/edit.html`, `assets/js/partner-central.js`, `README.md`

Review states: `offer-property.html?sample=rental-land` (rental type step: no *Terrain*, *Suivant* → *Choisissez le type de bien.*), `offer-property.html?type=SALE` (*Terrain* under both uses, sale copy/documents/surface/rate intact), `find-property.html?type=RENTAL` vs `?type=SALE` (step 3), `mandate/edit.html?id=208` (renter's search) and `?id=209` (landlord's property) against `?id=201` (ordinary rental) and `?id=104` (sale); `mandate/edit.html`'s type choices read one full-width row per type.

**Commit note:** while #275's prototype was being built, the parallel #273 session committed and pushed the shared working tree as `f138e53a` on `main`, so #275's prototype edits of `v2/offer-property.html`, `v2/assets/js/offer-property.js` and `v2/README.md` already landed inside that #273 commit; the other prototype files landed with this plan. Do not revert, amend or re-apply any of them; port from the prototypes as they are on `main`.

## Before you start

Confirm the story's prototype is on `main` and the rules are still as described:

```bash
git grep -n "PropertyType.LAND" -- \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt
git grep -n "data-categories=\"\" data-sale-categories=\"RESIDENTIAL COMMERCIAL\"" -- modules/ndopify-partner-central/src/main/html/v2
git grep -n "data-rental-categories" -- modules/ndopify-partner-central/src/main/html/v2
```

Expected: the two rental maps still list `LAND`; both prototypes carry the empty rental categories for *Terrain*; the edit prototype carries `data-rental-categories`. If any check fails, stop and reconcile with the story first.

## Global Constraints

- **The rules are the only validation.** ndopify-server validates no `propertyType` / `listingType` (see #254): the wizard's rules are what must refuse a rental land. Do not add server validation.
- **A sale keeps land under both uses.** Both `OfferPropertyRules` and `FindPropertyRules` keep `LAND` under `RESIDENTIAL` and `COMMERCIAL` for `ListingType.SALE`, in the same order as today, with no other type, label or threshold moved.
- **A stored rental land stays readable and savable.** The edit page validates with `FindPropertyRules`; a rental mandate whose saved type is `LAND` must pass unchanged (its rules check), while a rental land that is not the saved answer is refused with *Choisissez un type de bien proposé.* No data rewrite and no server call beyond the normal update.
- **One type per row on the edit page.** The *Type de bien* choices use a plain `.choices__grid` — one full-width row per type at every width; the neighbourhoods, rooms and parkings grids keep `.choices__grid--2`. This is a hard requirement, not a preference.
- **The prototype is the design.** Port it exactly: labels, copy, data attributes, states. The rental *Commercial* description drops "terrain"; the sale's keeps it. No new screen.
- **Only `modules/ndopify-partner-central` is touched.** No `ndopify-web` change (its rental pages offer no property type), no `ndopify-server`, `ndopify-dto`, `ndopify-web-core` or `ndopify-esignature` change. Shared code: none.
- **Do not touch stored data.** No migration, no cleanup, no listing change.
- `mvn verify -Dheadless=true` green in `modules/ndopify-partner-central` (ktlint + tests + coverage); no MySQL needed.
- French UI copy, English code, comments, plan and commits; commits conventional and scoped, each with `Refs #275` in the body.

## Review Focus

- **A stored rental *Terrain* survives a save.** Submitting the edit form unchanged must send `propertyType=LAND` and show no error, even though a rental no longer offers it; a rental land that isn't the mandate's saved answer must be refused. Task 2 pins both, Task 3 pins the rendering.
- **The sale map is derived from the rental map.** Removing `LAND` from the rental map must not remove it from a sale's types, its label, or its documents/surface rules in either wizard. Tasks 1 and 2 pin the sale lists both ways.
- **The sale edit page checked the rental rules.** `MandateEditRules.typeError` passed `null` as the listing type; after the rental map loses land, a sale land mandate would be refused unless the check uses `mandate.listingType`. Task 2 pins a sale land edit.
- **Only the type grid becomes single-column.** The edit page's other choice grids (neighbourhoods, rooms, parkings) must keep two columns, and the wizards' own type grids are not touched. Task 3 pins the classes.
- **A hidden or unchecked type must not be silently dropped.** A stored rental *Terrain* must render checked and submit; a rental without one must render no *Terrain* option at all. Task 3 pins both.

---

### Task 1: The offer wizard offers land for sale only

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: `PropertyType`, `PropertyCategory`, `ListingType`, `MandateLabels.propertyType`.
- Produces: `OfferPropertyRules.CATEGORIES_BY_TYPE` (the rental map) no longer contains `PropertyType.LAND`; `OfferPropertyRules.propertyTypes(COMMERCIAL, ListingType.RENTAL)` returns `[OFFICE, RETAIL, INDUSTRIAL]`; `OfferPropertyRules.typeFits(COMMERCIAL, "LAND", ListingType.RENTAL)` is false; every sale result is unchanged. `OfferPropertyController.PROPERTY_TYPE_LABELS` still has a label for `LAND` (used by a sale).

- [ ] **Step 1: Write the failing rules tests**

In `OfferPropertyRulesTest`, update `categoriesByTypeInFormOrder` to expect the rental map without `LAND` and replace its `LAND` assertions:

```kotlin
assertEquals(
    listOf(
        PropertyType.APARTMENT, PropertyType.HOUSE, PropertyType.ROOM, PropertyType.STUDIO,
        PropertyType.DUPLEX, PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL,
        PropertyType.INDUSTRIAL,
    ),
    OfferPropertyRules.CATEGORIES_BY_TYPE.keys.toList(),
)
assertEquals(residential, OfferPropertyRules.CATEGORIES_BY_TYPE[PropertyType.BUILDING])
assertNull(OfferPropertyRules.CATEGORIES_BY_TYPE[PropertyType.LAND])
```

Update `a sale offers land to both uses and no room` so the rental commercial list drops `LAND`, and the last assertion becomes false:

```kotlin
assertEquals(
    listOf(PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL),
    OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL),
)
assertFalse(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.RENTAL))
```

Keep every sale expectation in that test unchanged (`LAND` still last in both sale lists).

- [ ] **Step 2: Run the rules tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest`
Expected: FAIL — the rental map still contains `LAND`.

- [ ] **Step 3: Remove land from the rental map**

In `OfferPropertyRules`, delete the `PropertyType.LAND to PropertyCategory.COMMERCIAL` entry from `CATEGORIES_BY_TYPE`, and change `SALE_CATEGORIES_BY_TYPE` to add it back for a sale:

```kotlin
// A sale drops the room type and offers land for both uses (a plot to build on is residential too); a rental
// never offers land.
private val SALE_CATEGORIES_BY_TYPE: Map<PropertyType, Set<PropertyCategory>> =
    (CATEGORIES_BY_TYPE - PropertyType.ROOM)
        .mapValues { (_, category) -> setOf(category) } +
        (PropertyType.LAND to setOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL))
```

Update the KDoc above the maps and the `CATEGORIES_BY_TYPE` comment to say a rental offers no land, a sale offers it under both uses. Keep `propertyTypes` / `typeFits` logic unchanged.

- [ ] **Step 4: Run the rules tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest`
Expected: PASS.

- [ ] **Step 5: Write the failing controller and template tests**

In `OfferPropertyControllerTest`, replace `a land rental drops the documents` (a rental land was accepted) with:

```kotlin
@Test
fun `a rental refuses land`() {
    session.setAttribute(
        PendingListing.SESSION_ATTRIBUTE,
        complete.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = null, area = null, rooms = null, furnitureType = null, parkings = null,
        ),
    )

    val result = mvc.perform(features("propertyType" to "LAND", "area" to "800")).andReturn()

    assertEquals("features", model(result)["step"])
    assertEquals("Choisissez le type de bien.", errors(result)["propertyType"])
    assertNull(pending()?.propertyType)
}
```

In `OfferPropertyTemplateTest`, change `features step for commercial shows only the area` to expect `listOf("OFFICE", "RETAIL", "INDUSTRIAL")`, and add a step-1 copy test:

```kotlin
@Test
fun `the rental commercial description names no land`() {
    val rental = render(get("/offer-property?step=property"))
    val commercialRental = rental.selectFirst("input[value=COMMERCIAL]")!!.parent()!!.text()
    assertFalse(commercialRental.contains("terrain")) { commercialRental }

    session.setAttribute(PendingListing.SESSION_ATTRIBUTE, complete.copy(listingType = ListingType.SALE))
    val sale = render(get("/offer-property?step=property"))
    val commercialSale = sale.selectFirst("input[value=COMMERCIAL]")!!.parent()!!.text()
    assertTrue(commercialSale.contains("terrain")) { commercialSale }
}
```

- [ ] **Step 6: Run the controller and template tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyControllerTest,OfferPropertyTemplateTest`
Expected: FAIL — a rental still lists `LAND` and the shared *Commercial* text still says "terrain".

- [ ] **Step 7: Keep the land label and split the *Commercial* copy**

In `OfferPropertyController`, build the labels from the rental map plus `LAND` (the sale's type), so *Terrain* keeps its label on a sale:

```kotlin
private val PROPERTY_TYPE_LABELS: Map<PropertyType, String> =
    (OfferPropertyRules.CATEGORIES_BY_TYPE.keys + PropertyType.LAND).associateWith { type ->
        if (type == PropertyType.HOUSE) "Villa, maison" else MandateLabels.propertyType(type)
    }
```

Replace the `COMMERCIAL` entry of `CATEGORY_DETAIL_LABELS` with two entries, the rental one without land:

```kotlin
"RENTAL_COMMERCIAL" to "Pour une activité\u00a0: bureau, boutique, entrepôt…",
"SALE_COMMERCIAL" to "Pour une activité\u00a0: bureau, boutique, entrepôt, terrain…",
```

In `templates/offer-property.html`, the *Commercial* card picks the project's key, as the *Résidentiel* card already does:

```html
<span class="choice__detail"
      th:text="${sale} ? ${categoryDetailLabels['SALE_COMMERCIAL']} : ${categoryDetailLabels['RENTAL_COMMERCIAL']}">Pour une activité&nbsp;: bureau, boutique, entrepôt…</span>
```

Nothing else in the template changes: the type list is already rendered from the model.

- [ ] **Step 8: Run the module's offer tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=OfferPropertyRulesTest,OfferPropertyControllerTest,OfferPropertyTemplateTest,PendingListingTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty \
  modules/ndopify-partner-central/src/main/resources/templates/offer-property.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty
git commit -m "feat(npc): offer land for sale only, never for rent" -m "Refs #275"
```

---

### Task 2: A rental land is refused in a search and on the mandate edit page, a stored one is kept

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt`

**Interfaces:**
- Consumes: `FindPropertyRules.propertyType(category, type, listingType)`, `Mandate.listingType`, `Mandate.propertyType`, `Mandate.propertyCategory`.
- Produces: `FindPropertyRules.propertyTypes(COMMERCIAL, ListingType.RENTAL)` returns `[RETAIL, OFFICE, INDUSTRIAL, BUILDING]`; `FindPropertyRules.typeFits(COMMERCIAL, "LAND", ListingType.RENTAL)` is false; every purchase result is unchanged. `MandateEditRules.errors` keeps its signature; a rental `LAND` is valid only when it equals the mandate's saved `propertyType` and the form keeps the mandate's `propertyCategory`.

- [ ] **Step 1: Write the failing find-property tests**

In `FindPropertyRulesTest`, in `a sale offers land to both uses and no room`, add the rental commercial list and flip the last rental assertion:

```kotlin
assertEquals(
    listOf(PropertyType.RETAIL, PropertyType.OFFICE, PropertyType.INDUSTRIAL, PropertyType.BUILDING),
    FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.RENTAL),
)
assertFalse(FindPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.RENTAL))
```

Keep both sale lists and the sale `typeFits` assertions unchanged.

In `FindPropertyControllerTest.propertyTypesFilteredByCategory` (a rental), drop `PropertyType.LAND` from the expected list. In `FindPropertyTemplateTest.propertyShowsOnlyCategoryTypes` (a rental), expect `listOf("RETAIL", "OFFICE", "INDUSTRIAL", "BUILDING", "ANY")`; leave `a sale property step offers land and no room nor furniture` unchanged.

In `PendingMandateTest`, a rental land must no longer complete the property step:

```kotlin
@Test
fun `a rental land never completes the property step`() {
    val land = COMPLETE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = "LAND", rooms = null, furnitureType = null)
    assertFalse(land.isComplete("property"))
}
```

Change `furnitureRequiredOnlyWhenTheTypeHasFurniture`'s no-furniture case to a type a rental offers (a whole building):

```kotlin
assertTrue(COMPLETE.copy(propertyType = "BUILDING", rooms = null, furnitureType = null).isComplete("property"))
```

Change `furnitureAndParkingsNotSentForLand` to a land purchase, so it stays a reachable answer:

```kotlin
val request = COMPLETE.copy(
    listingType = ListingType.SALE,
    propertyCategory = PropertyCategory.COMMERCIAL,
    propertyType = "LAND",
    area = 400,
    priceAmount = 10_000_000,
    rooms = null,
    furnitureType = "FULLY_FURNISHED",
).toCreateMandateRequest("XAF")
```

- [ ] **Step 2: Run the find-property tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest,FindPropertyControllerTest,FindPropertyTemplateTest,PendingMandateTest`
Expected: FAIL — the rental map still offers and accepts `LAND`.

- [ ] **Step 3: Remove land from the rental search map**

In `FindPropertyRules`, delete the `PropertyType.LAND to setOf(PropertyCategory.COMMERCIAL)` entry and its comment from `RENTAL_CATEGORIES_BY_TYPE`. `SALE_CATEGORIES_BY_TYPE` keeps adding `PropertyType.LAND to setOf(RESIDENTIAL, COMMERCIAL)`, which still works once the base map no longer has it. Update the KDoc to say land is bought, never rented.

- [ ] **Step 4: Run the find-property tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyRulesTest,FindPropertyControllerTest,FindPropertyTemplateTest,PendingMandateTest`
Expected: PASS.

- [ ] **Step 5: Write the failing mandate edit rules and controller tests**

In `MandateEditRulesTest`, replace the rental land case of `errors - rooms required for a home only` with a stored rental land, and add the two cases it implies:

```kotlin
val storedLand = MANDATE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.LAND)
val landForm = FORM.copy(
    propertyCategory = PropertyCategory.COMMERCIAL,
    propertyType = "LAND",
    rooms = null,
    furnitureType = null,
    parkings = null,
)
assertEquals(emptyMap(), MandateEditRules.errors(landForm, storedLand, FORMATTER))
assertEquals(
    "Choisissez un type de bien proposé.",
    MandateEditRules.errors(landForm, MANDATE, FORMATTER)["propertyType"],
)
```

Add a sale land edit test (a sale search with a one-off budget, no rental terms):

```kotlin
@Test
fun `errors - a sale land is accepted`() {
    val sale = MANDATE.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = PropertyType.LAND,
        rooms = null,
        furnitureType = null,
        parkings = null,
        price = Money(15_000_000, "XAF", PeriodicityType.NONE),
    )
    val form = FORM.copy(
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = "LAND",
        rooms = null,
        furnitureType = null,
        parkings = null,
        area = "300",
        priceAmount = "15 000 000",
    )

    assertNull(MandateEditRules.errors(form, sale, FORMATTER)["propertyType"])
}
```

Change `request - drops follow-ups that don't apply` to build on a stored rental land (`MANDATE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.LAND)`) so its form is reachable; keep its assertions.

In `MandateControllerTest`, add the save-unchanged and save-refused cases next to the other `save -` tests:

```kotlin
@Test
fun `save - a stored rental land is kept`() {
    doReturn(MANDATE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.LAND))
        .whenever(mandateClient).get(201)

    val result = mvc.perform(
        edit("propertyCategory" to "COMMERCIAL", "propertyType" to "LAND", "rooms" to "", "furnitureType" to "", "parkings" to ""),
    ).andReturn()

    assertEquals("redirect:/mandates/201", result.modelAndView?.viewName)
    val request = argumentCaptor<UpdateMandateRequest>()
    verify(mandateClient).update(eq(201L), request.capture())
    assertEquals(PropertyType.LAND, request.firstValue.propertyType)
}

@Test
fun `save - a rental land that isn't stored is refused`() {
    val result = mvc.perform(
        edit("propertyCategory" to "COMMERCIAL", "propertyType" to "LAND", "rooms" to "", "furnitureType" to "", "parkings" to ""),
    ).andReturn()

    assertEquals("mandate/edit", result.modelAndView?.viewName)
    assertEquals("Choisissez un type de bien proposé.", errors(result)["propertyType"])
    verify(mandateClient, never()).update(any(), any())
}
```

Use the file's existing imports for `argumentCaptor`, `eq` and `UpdateMandateRequest`.

- [ ] **Step 6: Run the mandate tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateEditRulesTest,MandateControllerTest`
Expected: FAIL — a stored rental land is still validated against the rental table without land (after Step 3) and a sale land is validated as a rental.

- [ ] **Step 7: Validate the edit type with the mandate's listing type and the stored answer**

In `MandateEditRules`, change `typeError` to take the mandate and use its listing type, plus the stored-land exception:

```kotlin
private fun typeError(form: MandateEditForm, mandate: Mandate): String? {
    val answer = form.propertyType?.takeIf { it.isNotEmpty() && it != ANY }
    val owner = mandate.mandateType == MandateType.LISTING
    // A rental land stored before #275 keeps its answer: only unchanged, in its saved category.
    val storedLand = !FindPropertyRules.isSale(mandate.listingType) &&
        mandate.propertyType == PropertyType.LAND &&
        form.propertyType == PropertyType.LAND.name &&
        form.propertyCategory == mandate.propertyCategory
    return when {
        answer == null && owner -> "Choisissez un type de bien proposé."
        answer != null && type(form) == null -> "Choisissez un type de bien proposé."
        storedLand -> null
        else -> FindPropertyRules.propertyType(form.propertyCategory, answer, mandate.listingType)
    }
}
```

Update the `errors` call to `typeError(form, mandate)`. Nothing else in the rules changes: `needsRooms`, `needsFurniture`, `asksParkings`, `needsArea`, the price and the request already read the mandate's listing type or the parsed type.

- [ ] **Step 8: Run the mandate tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateEditRulesTest,MandateControllerTest`
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRules.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateControllerTest.kt
git commit -m "feat(npc): refuse land on a rental search and mandate edit" -m "Refs #275"
```

---

### Task 3: The edit page shows one type per row and keeps a stored land visible

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt`

**Interfaces:**
- Consumes: the `sale` and `answers` model attributes of `MandateController.edit` (`sale` is a `Boolean`, `answers` a `MandateEditForm`).
- Produces: the *Type de bien* grid is `div.choices__grid` (no `choices__grid--2`); the `LAND` choice renders iff `sale` or `answers.propertyType == 'LAND'`; no other grid's classes change.

- [ ] **Step 1: Write the failing template tests**

In `MandateEditTemplateTest.form`, remove the line asserting a `LAND` input (an ordinary rental no longer renders one) and add the layout assertions:

```kotlin
val typeGrid = form.selectFirst("[data-group=propertyType] > .choices__grid")!!
assertFalse(typeGrid.hasClass("choices__grid--2"))
assertTrue(form.selectFirst("[data-group=rooms] > .choices__grid")!!.hasClass("choices__grid--2"))
assertTrue(form.selectFirst("[data-group=parkings] > .choices__grid")!!.hasClass("choices__grid--2"))
```

Add the three rendering cases:

```kotlin
@Test
fun `a rental no longer offers land`() {
    val page = render(MANDATE)
    assertNull(page.selectFirst("input[name=propertyType][value=LAND]"))
}

@Test
fun `a stored rental land stays checked and readable`() {
    val page = render(MANDATE.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.LAND))
    val land = assertNotNull(page.selectFirst("input[name=propertyType][value=LAND]"))
    assertTrue(land.hasAttr("checked"))
    assertEquals("COMMERCIAL", land.parent()!!.attr("data-categories"))
}

@Test
fun `a sale still offers land`() {
    val page = render(MANDATE.copy(listingType = ListingType.SALE))
    val land = assertNotNull(page.selectFirst("input[name=propertyType][value=LAND]"))
    assertFalse(land.hasAttr("checked"))
}
```

Use the file's existing imports for `PropertyCategory`, `PropertyType` and `ListingType` (add the missing ones).

- [ ] **Step 2: Run the template tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateEditTemplateTest`
Expected: FAIL — the type grid is still two columns and `LAND` still renders for every mandate.

- [ ] **Step 3: Make the type grid single-column and gate the land choice**

In `templates/mandate/edit.html`, in the *Type de bien* fieldset only, change `<div class="choices__grid choices__grid--2">` to `<div class="choices__grid">`, and add the render condition to the `LAND` label:

```html
<label class="choice" data-categories="COMMERCIAL"
       th:if="${sale or answers.propertyType == 'LAND'}"><input name="propertyType" type="radio" value="LAND"
       th:checked="${#strings.equals(answers.propertyType, 'LAND')}"> <th:block th:text="${propertyTypeLabels['LAND']}">Terrain</th:block></label>
```

Leave the neighbourhoods, rooms, parkings and every other grid with `choices__grid--2`. No script change is needed: `mandate.js` reads `data-categories` to hide a type outside the chosen category, and the stored land stays in its `COMMERCIAL` category.

- [ ] **Step 4: Run the template tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateEditTemplateTest`
Expected: PASS.

- [ ] **Step 5: Run the module's verification**

Run: `cd modules/ndopify-partner-central && mvn verify -Dheadless=true`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt
git commit -m "feat(npc): show property types one per row and keep a stored rental land" -m "Refs #275"
```

---

## Verification

After all three tasks, from a clean tree on the story's branch:

```bash
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
git log --oneline -3
```

Expected: `BUILD SUCCESS`, the three commits above, and `git status --porcelain` empty. No file outside `modules/ndopify-partner-central` is modified, and no server, DTO or web-core release is needed.

Manual smoke (optional, with Partner Central running): *Proposer un bien* as a rental shows no *Terrain* and answers *Choisissez le type de bien.* for a hand-posted `propertyType=LAND`; the same for *Trouver un bien*; a sale keeps *Terrain* under both uses; `mandate/edit.html` for a stored rental land (a mandate saved through the API) shows it checked, saves it unchanged, and its type choices read one per row.
