# Land Offered Under Both Usages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sold plot of land is offered under *Résidentiel* and under *Commercial* alike — one choice, stored under the usage the user picked — in the listing section, both Partner Central wizards and the mandate edit page, and Partner Central's rule is no longer contradicted by `ndopify-server`'s matcher; a rental still offers none.

**Architecture:** `LAND` becomes a conditional dual-usage type exactly as `BUILDING` already is: `ListingRules.typeCategories` gains `COMMERCIAL` for it when `sale`, both wizards' `SALE_CATEGORIES_BY_TYPE` give it both usages, and every rendered radio keeps carrying its usages in `data-categories` (one radio, never one per usage). `ListingRules` gains one ordering helper that puts a sale's *Terrain* last in *Le bien*'s type list; `Matcher.categoryMismatch` lets the type decide for a `LAND` pair as it already does for `BUILDING`. No migration, no DTO, no `ndopify-web` or `ndopify-esignature` change.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, JUnit 5 + `kotlin.test`, mockito-kotlin, `MockMvc`, jsoup, plain unit tests for `Matcher`.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/293

**Prototype (approved 2026-11-24, committed with this plan):** `modules/ndopify-partner-central/src/main/html/` — `v2/find-property.html`, `v2/assets/js/find-property.js`, `v2/offer-property.html`, `v2/assets/js/offer-property.js`, `v2/listing/bien.html`, `v2/mandate/edit.html`, `listing/bien.html`, `mandate/edit.html`, `v2/assets/js/partner-central.js`, `assets/js/partner-central.js`, `v2/README.md`. It is the baseline: the tasks below port it into the production templates and rules, and nothing here redesigns it.

**Decisions (2026-11-24, with the requester):**
- A sale offers *Terrain* under both usages; a rental offers none (#275's sale-only rule stands, #290's residential-only rule is reversed).
- The stored `propertyCategory` is whichever usage the user picked; no row is realigned and no migration is added.
- No wording change: no new label for a residential vs commercial plot, and the wizards' *Résidentiel* / *Commercial* descriptions keep their current text.
- `#287` keeps its own subject (the land section's parking and ownership documents); the category rule lives here.

## Global Constraints

- **Sale only.** No rule maps `LAND` to `COMMERCIAL`, or to a sale usage, on a rental: both wizards, the listing section and the mandate edit page never offer it there, and a posted rental land is refused by the existing messages.
- **One radio.** A type is one radio whose usages are its `data-categories` (space-separated). Never emit *Terrain* once per usage: two radios would share one `name` and value.
- **Both usages, one answer.** `RESIDENTIAL` and `COMMERCIAL` are both valid for a sale `LAND`; the posted usage is stored as sent, never coerced.
- **Follow-ups unchanged.** A plot of land asks no furniture, rooms, living rooms, bathrooms, kitchens, parkings, levels nor floor level, under either usage; the surface stays asked.
- **No other type moves.** Only `LAND`'s membership changes, plus its position last in *Le bien*'s type list; every other radio keeps its order and usages.
- **Wording frozen.** No French copy is edited by this plan (labels, hints, category descriptions, error messages).
- **No migration, no contract change.** `V17_1__land-is-residential.sql` stays; no `ndopify-dto`, `ndopify-web` or `ndopify-esignature` edit.
- **Matcher scope.** The category veto is skipped only when the searched or the listed type is `BUILDING` or `LAND`; the type still scores (land against non-land stays 0.0), and a search with no type still lets the category decide.
- **Verification.** `mvn -Dheadless=true test` and `mvn validate` in `modules/ndopify-partner-central`; `mvn -o -Dtest=MatcherTest test` in `modules/ndopify-server` (unit test, no MySQL); `git diff --check`.

## Review Focus

- **A rental land is still refused.** Both usages of a rental must offer no *Terrain*: the listing section's posted `propertyType=LAND` (either category) and both wizards' sale-only maps — a rental land that slips through rewrites what owners can publish.
- **A commercial plot stays commercial.** A sale saved under *Commercial* must be stored and re-rendered as `COMMERCIAL`, and a residential row from #290 must stay residential: no realignment anywhere, in rules or in the prototype's sample data.
- **The #275 rental-legacy path.** A stored rental land stays checked and savable on the mandate edit page, in its saved usage, and disappears once another type is picked.
- **One row for two usages.** *Le bien* and the mandate edit page each show a single *Terrain* radio whose `data-categories` names both usages; a second radio (or `data-categories="RESIDENTIAL"` alone) breaks the page's filter and the save.
- **The matcher's veto.** A land search and a land listing stored under different usages must meet, while a land against a non-land still scores 0.0 and a search with no type still lets the category decide — a blanket "skip the veto for land" would propose plots to offices.

---

### Task 1: Listing section — a sale land under both usages, last in the list

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingFormRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionTemplateTest.kt`

**Interfaces:**
- Consumes: `ListingRules.TYPES`, `ListingRules.typeCategories(type, sale)`, `ListingRules.typeFits(category, type, sale)`, `ListingViews.types(sale)` as they exist today.
- Produces: `ListingRules.allTypes(sale: Boolean): List<PropertyType>` (every type once, in *Le bien*'s order, a sale's *Terrain* last); `ListingRules.types(category: PropertyCategory?, sale: Boolean)` and `ListingViews.types(sale: Boolean)` built on it; `ListingRules.typeCategories(PropertyType.LAND, sale = true) == [RESIDENTIAL, COMMERCIAL]`. Task 2 does not use them; no later task changes their signature.

- [ ] **Step 1: Write the failing tests**

In `ListingRulesTest`, replace `a commercial sale lists the building before the offices` with the orders this story pins, and add the land cases:

```kotlin
@Test
fun `a commercial sale lists the building first and the plot of land last`() {
    assertEquals(
        listOf(PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL, PropertyType.LAND),
        ListingRules.types(PropertyCategory.COMMERCIAL, sale = true),
    )
    assertEquals(
        listOf(PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL),
        ListingRules.types(PropertyCategory.COMMERCIAL, sale = false),
    )
    assertEquals(
        ListingRules.TYPES.getValue(PropertyCategory.RESIDENTIAL),
        ListingRules.types(PropertyCategory.RESIDENTIAL, sale = true),
    )
    assertEquals(
        ListingRules.TYPES.getValue(PropertyCategory.RESIDENTIAL) - PropertyType.LAND,
        ListingRules.types(PropertyCategory.RESIDENTIAL, sale = false),
    )
}

@Test
fun `a sale land fits both categories, a rental only none`() {
    assertTrue(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.LAND, sale = true))
    assertTrue(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.LAND, sale = true))
    assertFalse(ListingRules.typeFits(PropertyCategory.RESIDENTIAL, PropertyType.LAND, sale = false))
    assertFalse(ListingRules.typeFits(PropertyCategory.COMMERCIAL, PropertyType.LAND, sale = false))
    assertEquals(
        listOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL),
        ListingRules.typeCategories(PropertyType.LAND, sale = true),
    )
    assertEquals(listOf(PropertyCategory.RESIDENTIAL), ListingRules.typeCategories(PropertyType.LAND, sale = false))
}

@Test
fun `every type is listed once and a rental lists no land`() {
    assertEquals(
        listOf(
            PropertyType.APARTMENT,
            PropertyType.HOUSE,
            PropertyType.VILLA,
            PropertyType.DUPLEX,
            PropertyType.STUDIO,
            PropertyType.ROOM,
            PropertyType.BUILDING,
            PropertyType.OFFICE,
            PropertyType.RETAIL,
            PropertyType.INDUSTRIAL,
            PropertyType.LAND,
        ),
        ListingRules.allTypes(sale = true),
    )
    assertEquals(ListingRules.allTypes(sale = true) - PropertyType.LAND, ListingRules.allTypes(sale = false))
}
```

In `ListingFormRulesTest`, add the commercial save next to `property - land is refused on a rental`:

```kotlin
@Test
fun `property - a sale land saves under either usage`() {
    listOf("RESIDENTIAL", "COMMERCIAL").forEach { category ->
        val form = ListingSectionForm(version = 4, propertyCategory = category, propertyType = "LAND", area = "800")

        assertEquals(emptyMap(), errors(ListingSection.PROPERTY, form, SALE), category)
        assertEquals(PropertyCategory.valueOf(category), ListingRules.request(ListingSection.PROPERTY, form, SALE).propertyCategory)
    }
}
```

In `ListingSectionTemplateTest`, rename `property - a sale offers residential land` and pin both usages and the order:

```kotlin
@Test
fun `property - a sale offers land under both usages, last in the list`() {
    val form = form(render("property", SALE))

    assertEquals(11, form.select("input[name=propertyType]").size)
    assertEquals(
        "RESIDENTIAL COMMERCIAL",
        form.selectFirst("input[name=propertyType][value=LAND]")?.parent()?.attr("data-categories"),
    )
    assertEquals(
        "LAND",
        form.select("input[name=propertyType]").last()?.attr("value"),
    )
    assertEquals(
        listOf("BUILDING", "OFFICE", "RETAIL", "INDUSTRIAL", "LAND"),
        form.select("input[name=propertyType]")
            .filter { (it.parent()?.attr("data-categories") ?: "").contains("COMMERCIAL") }
            .map { it.attr("value") },
    )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true -Dtest='ListingRulesTest,ListingFormRulesTest,ListingSectionTemplateTest' test`
Expected: FAIL — `allTypes` unresolved, the commercial sale list lacks `LAND`, the sale land's `data-categories` reads `RESIDENTIAL`.

- [ ] **Step 3: Implement `ListingRules.allTypes`, the dual usage, and `ListingViews.types`**

In `ListingRules`, name the conditional commercial usages once and use them in `typeCategories`:

```kotlin
/** The types a sale offers under COMMERCIAL without listing them there in [TYPES]: a whole building, a plot of land. */
private val SALE_COMMERCIAL_TYPES = setOf(PropertyType.BUILDING, PropertyType.LAND)

/**
 * The categories a type is offered under: its [TYPES] entries, plus COMMERCIAL for a sale [SALE_COMMERCIAL_TYPES]
 * type — a sale building and a sale plot of land are offered under both (a rental plot of land under none, see
 * [types]).
 */
fun typeCategories(type: PropertyType, sale: Boolean): List<PropertyCategory> {
    val categories = TYPES.filterValues { type in it }.keys.toMutableList()
    if (sale && type in SALE_COMMERCIAL_TYPES && PropertyCategory.COMMERCIAL !in categories) {
        categories += PropertyCategory.COMMERCIAL
    }
    return categories
}
```

Add the ordering helper and build `types` on it (a sale moves `LAND` last; a rental lists none):

```kotlin
/**
 * Every type once, in *Le bien*'s order (the prototype's `listing/bien.html`): a sale adds its conditional types
 * where [TYPES] already puts them (a building before the offices) and lists a plot of land last; a rental lists no
 * plot of land.
 */
fun allTypes(sale: Boolean): List<PropertyType> =
    TYPES.values.flatten().let { if (sale) it - PropertyType.LAND + PropertyType.LAND else it - PropertyType.LAND }

/** The types of [category] a project may choose, in [allTypes]' order: a plot of land is sold, never let (#275). */
fun types(category: PropertyCategory?, sale: Boolean): List<PropertyType> =
    if (category == null) emptyList() else allTypes(sale).filter { category in typeCategories(it, sale) }
```

In `ListingViews`, replace the hand-rolled filter with the helper so the page and the rules can't drift:

```kotlin
/**
 * *Le bien*'s types, in the prototype's order, each once: its categories (`data-categories`, space-separated)
 * follow [ListingRules.typeCategories]; a rental is offered no plot of land.
 */
fun types(sale: Boolean): List<TypeChoice> =
    ListingRules.allTypes(sale).map { type ->
        TypeChoice(
            value = type.name,
            categories = ListingRules.typeCategories(type, sale).joinToString(" "),
            label = TYPE_LABELS.getValue(type),
        )
    }
```

The template (`templates/listing/section.html`) needs no edit: it already renders `data-categories=${t.categories}` on one radio per type.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true -Dtest='ListingRulesTest,ListingFormRulesTest,ListingSectionTemplateTest,ListingViewsTest' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingFormRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionTemplateTest.kt
git commit -m "feat(npc): offer a sale plot of land under both usages in Le bien" -m "Refs #293"
```

---

### Task 2: Wizards — a sold land under Résidentiel and Commercial

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: `FindPropertyRules.propertyTypes(category, listingType)`, `FindPropertyRules.typeFits(category, type, listingType)`, `FindPropertyRules.propertyType(category, value, listingType)`, `OfferPropertyRules`' same three — all unchanged in signature.
- Produces: for `ListingType.SALE`, `propertyTypes(RESIDENTIAL, …)` and `propertyTypes(COMMERCIAL, …)` both contain `PropertyType.LAND` (last), and `typeFits(COMMERCIAL, "LAND", SALE)` is true; for a rental, `propertyTypes` contains it in neither and `typeFits(…, "LAND", …)` is false. Controllers need no edit: they already pass `propertyTypes(category, listingType)` to the template.

- [ ] **Step 1: Write the failing tests**

In `FindPropertyRulesTest`, extend `a sale offers residential land and no room` (rename it `a sale offers land under both usages and no room`): the commercial sale list gains `LAND` last, and `typeFits(COMMERCIAL, "LAND", SALE)` becomes true, the rental assertions staying false.

```kotlin
assertEquals(
    listOf(PropertyType.RETAIL, PropertyType.OFFICE, PropertyType.INDUSTRIAL, PropertyType.BUILDING, PropertyType.LAND),
    FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE),
)
assertTrue(FindPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
assertFalse(FindPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.RENTAL))
assertFalse(FindPropertyRules.typeFits(PropertyCategory.RESIDENTIAL, "LAND", ListingType.RENTAL))
```

and add a commercial purchase case:

```kotlin
@Test
fun `a commercial purchase accepts a plot of land`() {
    assertNull(FindPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
    assertEquals(
        listOf(PropertyType.RETAIL, PropertyType.OFFICE, PropertyType.INDUSTRIAL, PropertyType.BUILDING, PropertyType.LAND),
        FindPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE),
    )
    assertFalse(FindPropertyRules.needsRooms(PropertyCategory.COMMERCIAL, PropertyType.LAND, ListingType.SALE))
    assertFalse(FindPropertyRules.asksParkings(PropertyType.LAND, ListingType.SALE))
    assertTrue(FindPropertyRules.needsArea(PropertyCategory.COMMERCIAL, PropertyType.LAND, ListingType.SALE))
    assertFalse(FindPropertyRules.needsFurniture(PropertyType.LAND, ListingType.SALE))
}
```

In `OfferPropertyRulesTest`, extend the sale list assertions the same way (`propertyTypes(COMMERCIAL, SALE)` ends with `LAND`; `typeFits(COMMERCIAL, "LAND", SALE)` true; the rental ones stay false), and pin that a rental still refuses it:

```kotlin
assertEquals(
    listOf(PropertyType.BUILDING, PropertyType.OFFICE, PropertyType.RETAIL, PropertyType.INDUSTRIAL, PropertyType.LAND),
    OfferPropertyRules.propertyTypes(PropertyCategory.COMMERCIAL, ListingType.SALE),
)
assertTrue(OfferPropertyRules.typeFits(PropertyCategory.COMMERCIAL, "LAND", ListingType.SALE))
assertEquals("Choisissez le type de bien.", OfferPropertyRules.propertyType(PropertyCategory.COMMERCIAL, "LAND", ListingType.RENTAL))
```

In `FindPropertyTemplateTest`, add the page case:

```kotlin
@Test
fun `a commercial purchase property step offers land under both usages`() {
    session.setAttribute(
        PendingMandate.SESSION_ATTRIBUTE,
        complete.copy(
            listingType = ListingType.SALE,
            propertyCategory = PropertyCategory.COMMERCIAL,
            propertyType = null,
            rooms = null,
        ),
    )

    val page = render(get("/find-property?step=property"))

    assertEquals(
        listOf("RETAIL", "OFFICE", "INDUSTRIAL", "BUILDING", "LAND", "ANY"),
        page.select("input[name=propertyType]").map { it.attr("value") },
    )
}
```

In `OfferPropertyTemplateTest`, add the same case for the sale's *Caractéristiques* step (`OfferPropertyRules.propertyTypes(COMMERCIAL, SALE)` order, `LAND` checked when the pending answer is `LAND` under `COMMERCIAL`); reuse its existing sale fixture and its `render`/`form` helpers.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true -Dtest='FindPropertyRulesTest,OfferPropertyRulesTest,FindPropertyTemplateTest,OfferPropertyTemplateTest' test`
Expected: FAIL — the commercial sale lists lack `LAND`;

- [ ] **Step 3: Give `LAND` both usages in both sale maps**

In `FindPropertyRules`, its `SALE_CATEGORIES_BY_TYPE` entry becomes

```kotlin
(PropertyType.LAND to setOf(PropertyCategory.RESIDENTIAL, PropertyCategory.COMMERCIAL))
```

and the comment above the map reads: a purchase offers a plot of land under both usages (#293), a rental never offers land (#275). In `OfferPropertyRules`, the same entry replaces `(PropertyType.LAND to setOf(PropertyCategory.RESIDENTIAL))`, and its map comment says the same (keeping #274's building sentence).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true -Dtest='FindPropertyRulesTest,OfferPropertyRulesTest,FindPropertyTemplateTest,OfferPropertyTemplateTest,FindPropertyControllerTest,OfferPropertyControllerTest,PendingMandateTest' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt
git commit -m "feat(npc): offer a sale plot of land under both usages in the wizards" -m "Refs #293"
```

---

### Task 3: Mandate edit page — the plot's usages, the rental legacy kept

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt`

**Interfaces:**
- Consumes: the page's `data-categories` / `data-rental-categories` attributes and `mandate.js`'s `syncProperty` (already splits both on spaces; no script edit), plus `MandateEditRules.request(form, mandate)`.
- Produces: the *Terrain* radio carries `data-categories="RESIDENTIAL COMMERCIAL"` and `data-rental-categories=""`; a sale land saved with `propertyCategory=COMMERCIAL` is stored commercial, a rental land keeps its saved answer (Task 2's rules already accept the pair).

- [ ] **Step 1: Write the failing tests**

In `MandateEditTemplateTest`, update the two land cases:

```kotlin
@Test
fun `a stored rental land stays checked and readable`() {
    val page = render(MANDATE.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.LAND))
    val form = page.selectFirst("form[data-edit-form]")!!
    assertEquals("false", form.attr("data-sale"))
    val land = assertNotNull(page.selectFirst("input[name=propertyType][value=LAND]"))
    assertTrue(land.hasAttr("checked"))
    // #293: both usages on the one radio; a rental keeps data-rental-categories empty, so mandate.js offers it only
    // while it is the saved answer.
    assertEquals("RESIDENTIAL COMMERCIAL", land.parent()!!.attr("data-categories"))
    assertEquals("", land.parent()!!.attr("data-rental-categories"))
}

@Test
fun `a sale offers land under both usages`() {
    val page = render(
        MANDATE.copy(listingType = ListingType.SALE, propertyCategory = PropertyCategory.RESIDENTIAL),
    )
    val form = page.selectFirst("form[data-edit-form]")!!
    assertEquals("true", form.attr("data-sale"))
    val land = assertNotNull(page.selectFirst("input[name=propertyType][value=LAND]"))
    assertFalse(land.hasAttr("checked"))
    assertEquals("RESIDENTIAL COMMERCIAL", land.parent()!!.attr("data-categories"))
    // One radio, never one per usage
    assertEquals(1, page.select("input[name=propertyType][value=LAND]").size)
}
```

In `MandateEditRulesTest`, add the commercial save next to the stored-land cases:

```kotlin
@Test
fun `errors - a sale land saves under its commercial usage`() {
    // #293: the usage the agent picks is the one stored, both being valid for a sold plot of land
    val sale = MANDATE.copy(
        listingType = ListingType.SALE,
        propertyCategory = PropertyCategory.RESIDENTIAL,
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

    assertEquals(emptyMap(), MandateEditRules.errors(form, sale, FORMATTER))
    val request = MandateEditRules.request(form, sale)
    assertEquals(PropertyCategory.COMMERCIAL, request.propertyCategory)
    assertEquals(PropertyType.LAND, request.propertyType)
}
```

Keep the existing `FORM`, `MANDATE` and `FORMATTER` fixtures (see `errors - a sale land is accepted`, which this test mirrors).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true -Dtest='MandateEditTemplateTest,MandateEditRulesTest' test`
Expected: FAIL — `data-categories` reads `RESIDENTIAL` on the *Terrain* radio; the commercial save is refused.

- [ ] **Step 3: Set both usages on the *Terrain* radio**

In `templates/mandate/edit.html`, the *Terrain* label becomes

```html
<label class="choice" data-categories="RESIDENTIAL COMMERCIAL" data-rental-categories=""><input name="propertyType" type="radio" value="LAND"
       th:checked="${#strings.equals(answers.propertyType, 'LAND')}"> <th:block th:text="${propertyTypeLabels['LAND']}">Terrain</th:block></label>
```

and its `th:if="${sale or answers.propertyType == 'LAND'}"` stays. Rewrite the `#275` comment above it: a plot of land is sold, never let; a rental offers no *Terrain* except the saved answer a stored mandate already gave (`data-rental-categories=""`, `mandate.js` shows it while it is that answer); a sale offers it under *Résidentiel* and *Commercial* alike (#293), and the picked usage is saved.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true -Dtest='MandateEditTemplateTest,MandateEditRulesTest,MandateControllerTest,MandateViewsTest' test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRulesTest.kt
git commit -m "feat(npc): offer a sale plot of land under both usages on the mandate edit page" -m "Refs #293"
```

---

### Task 4: Matcher — the type decides for a plot of land too

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Consumes: `Matcher.score(search: MandateEntity, listing: ListingEntity, distances, today)` and `MatcherTest`'s `landSearch`, `landListing`, `search`, `commercialSearch` fixtures.
- Produces: `categoryMismatch` unchanged in signature; a `SEARCH`/`LISTING` pair whose searched or listed type is `BUILDING` or `LAND` no longer hits the category veto.

- [ ] **Step 1: Write the failing tests**

In `MatcherTest`, next to `building matches a building of the other category`:

```kotlin
@Test
fun `land matches a land of the other category`() {
    // #293: a sold plot of land is stored under the usage the owner picked, either one
    val result = score(
        landSearch,
        landListing.copy(
            propertyCategory = PropertyCategory.COMMERCIAL,
            mandate = landListing.mandate.copy(ownershipDocuments = listOf(TitleDocumentType.SURVEY_PLAN)),
        ),
    )

    assertNotNull(result)
    assertEquals(1.0, result.type, DELTA)
}

@Test
fun `land still scores zero against another type`() {
    // The veto is skipped, not the type rule: a plot of land only matches a plot of land
    val commercialLand = landListing.copy(
        propertyCategory = PropertyCategory.COMMERCIAL,
        mandate = landListing.mandate.copy(ownershipDocuments = listOf(TitleDocumentType.SURVEY_PLAN)),
    )

    assertNull(score(landSearch.copy(propertyType = PropertyType.OFFICE, titleDocuments = emptyList()), commercialLand))
}
```

`landSearch` is `RESIDENTIAL`; the second case pins that a commercial office purchase does not match a plot of land (the type score is 0.0 whatever the veto does). Read the fixtures first and reuse them rather than building new mandates.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn -o -Dtest=MatcherTest test`
Expected: FAIL — the first case is ruled out (`MatchRuleOut.CATEGORY`).

- [ ] **Step 3: Let the type decide for `LAND` in `categoryMismatch`**

```kotlin
// The type is the specific fact; the category is derived, and the two wizards map BUILDING, and a sold plot of land,
// under both usages (#293). With no preferred type the category still decides, as before.
private fun categoryMismatch(search: MandateEntity, listing: ListingEntity): Boolean =
    listing.propertyCategory != search.propertyCategory &&
        (
            search.propertyType == null ||
                search.propertyType == PropertyType.UNKNOWN ||
                search.propertyType in CATEGORY_DECIDED_BY_TYPE ||
                listing.propertyType in CATEGORY_DECIDED_BY_TYPE
            )
```

with `private val CATEGORY_DECIDED_BY_TYPE = setOf(PropertyType.BUILDING, PropertyType.LAND)` in the companion next to `HOUSING`. Update the class KDoc's rule 3 to "other `listingType`, `cityId`, or `propertyCategory` (a `BUILDING` or `LAND` pair is decided by its type instead)".

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn -o -Dtest=MatcherTest test`
Expected: PASS, the whole class (including `no property type searched - the category decides` and `ruled out - land`).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt
git commit -m "fix(server): let the type decide for a plot of land in the matcher" -m "Refs #293"
```

---

### Task 5: Module guidance and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the *Trouver un bien* and *Proposer un bien* bullets)
- Modify: `modules/ndopify-server/CLAUDE.md` (the matching rules list)
- Test: the whole reactor's affected modules (no new test; the gates below)

**Interfaces:**
- Consumes: nothing to build on; this task only keeps the module guidance true after Tasks 1–4.
- Produces: no code.

- [ ] **Step 1: Update the two wizards' bullets**

In `modules/ndopify-partner-central/CLAUDE.md`, in the *Trouver un bien* bullet, `offers *Terrain* under the residential use only with a required surface` becomes `offers *Terrain* under both usages (residential and commercial) with a required surface`; in the *Proposer un bien* bullet, `*Terrain* offered under the residential use only and no *Chambre*` becomes `*Terrain* offered under both usages and no *Chambre*`. Do not touch anything else in those bullets.

- [ ] **Step 2: Update the matcher line**

In `modules/ndopify-server/CLAUDE.md`, the match rules' "… category — skipped only for a specified `BUILDING` pair — …" becomes "… category — skipped only for a specified `BUILDING` or `LAND` pair — …". No other line.

- [ ] **Step 3: Run the Partner Central gates**

Run: `cd modules/ndopify-partner-central && mvn -Dheadless=true test && mvn validate`
Expected: `BUILD SUCCESS`, no ktlint violation.

- [ ] **Step 4: Run the server's matcher gate**

Run: `cd modules/ndopify-server && mvn -o -Dtest=MatcherTest test`
Expected: `BUILD SUCCESS` (this test needs no MySQL).

- [ ] **Step 5: Check the tree**

Run: `git diff --check && git status --porcelain`
Expected: no whitespace error; only the files of Tasks 1–5 (no production file outside them, no migration, no `ndopify-dto` / `ndopify-web` / `ndopify-esignature` change).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md modules/ndopify-server/CLAUDE.md
git commit -m "docs(npc,server): a plot of land is offered under both usages" -m "Refs #293"
```
