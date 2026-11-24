# Land Is Residential and Sold Only Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land is a residential property type, offered only on a sale — never on a rental — in the listing section, both Partner Central wizards and the mandate edit page; the prototypes and the stored data follow.

**Architecture:** Move `LAND` from `COMMERCIAL` to `RESIDENTIAL` in `ListingRules.TYPES`, make the listing section's type list and validation project-aware (`types(category, sale)` / `typeFits(..., sale)`), keep its follow-up questions off a plot of land now that it is residential, and offer it under `RESIDENTIAL` only in both wizards' sale maps. The mandate edit page keeps a stored rental land readable (#275) in its saved category, now `RESIDENTIAL`. One Flyway migration realigns stored `LAND` + `COMMERCIAL` rows. No matcher, DTO, `ndopify-web` or `ndopify-esignature` change.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, Thymeleaf 3, JUnit 5 + `kotlin.test`, mockito-kotlin, `MockMvc`, jsoup, Flyway/MySQL, Playwright-free static prototypes.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/290

**Decisions (2026-11-23, with the requester):**
- The rule applies to the running app **and** the prototypes (`src/main/html`).
- A rental listing's *Le bien* hides a legacy *Terrain* entirely (no option, no checked answer); the mandate edit page keeps the #275 legacy readability.
- Existing `LAND` + `COMMERCIAL` rows are realigned by a migration.

## Global Constraints

- **Residential only.** No rule maps `LAND` to `COMMERCIAL`; a stored commercial land is not a valid answer after the migration.
- **Sale only.** Both wizards and the listing section never offer `LAND` on a rental; a posted rental land is refused by the rules (ndopify-server validates neither, as #254 notes).
- **Listing section follow-ups.** A plot of land asks no furniture, rooms, living rooms, bathrooms, kitchens, parkings, levels nor floor level; the surface stays asked.
- **Mandate edit legacy.** A rental mandate whose saved type is `LAND` still renders, checks and saves unchanged (#275); it is not offered anew once another type is picked.
- **No matcher change.** `Matcher` keeps its type matrix and category veto; the story does not change matching.
- **No invented data.** The migration only rewrites rows already stored as `LAND` + `COMMERCIAL`; it seeds nothing.
- **Prototype is the design.** Port the v2 prototype; v1's rental-only wizards no longer offer *Terrain*.
- **Verification.** `mvn test` and `mvn validate` in `modules/ndopify-partner-central`; `mvn -Dtest=GetListingEndpointTest test` in `modules/ndopify-server` with local MySQL.

## Task 1: Listing section — residential and project-aware

**Files:**
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt`
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt`
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionController.kt`
- `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingFormRulesTest.kt`
- `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionTemplateTest.kt`

- [x] Move `LAND` from the `COMMERCIAL` list to the `RESIDENTIAL` list of `ListingRules.TYPES`.
- [x] Add `ListingRules.types(category, sale)` (filters `LAND` out of a rental) and make `typeFits(category, type, sale)` use it; `errors(...)` passes the listing's project.
- [x] Add `ListingViews.types(sale)` and have `ListingSectionController` pass `ListingViews.types(sale)`; a rental renders no *Terrain* at all, a sale renders it under `RESIDENTIAL`.
- [x] Keep land's follow-ups off now that it is residential: `applies(...)` excludes `LAND` from furniture, rooms, kitchens and parkings (bathrooms already did).
- [x] Update the rules and template tests; add a sale case `ListingSectionTemplateTest.property - a sale offers residential land`, and pin the rental case as 10 types with no `LAND`.

## Task 2: Wizards — sale land under Résidentiel only

**Files:**
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- the rules, controller and template tests of both wizards.

- [x] `FindPropertyRules.SALE_CATEGORIES_BY_TYPE`: `LAND` → `setOf(RESIDENTIAL)`; `needsRooms` excludes `LAND` explicitly (a rental land no longer inherits the residential question).
- [x] `OfferPropertyRules.SALE_CATEGORIES_BY_TYPE`: `LAND` → `setOf(RESIDENTIAL)`.
- [x] Drop "terrain…" from the sale's *Commercial* description (`CATEGORY_DETAIL_LABELS`); the sale's *Résidentiel* description keeps "terrain à bâtir…".
- [x] Update the rule and template tests: a sale no longer lists `LAND` under `COMMERCIAL`; `typeFits(COMMERCIAL, LAND, SALE)` is false; the listed fixture categories become `RESIDENTIAL`.

## Task 3: Mandate edit page

**Files:**
- `modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html`
- `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditTemplateTest.kt`
- the mandate edit rules/controller/label tests.

- [x] `LAND` is `data-categories="RESIDENTIAL"` and keeps `data-rental-categories=""`; a sale offers it under *Résidentiel* only.
- [x] The #275 legacy path is unchanged: a stored rental land renders checked in its saved category and saves unchanged; an unsaved rental land is refused.
- [x] Update the fixtures to `RESIDENTIAL` for every land mandate; the landlord criteria test now reads *Résidentiel*.

## Task 4: Prototypes

**Files:** `modules/ndopify-partner-central/src/main/html/` (v1 and v2).

- [x] v1: `find-property.html` and `offer-property.html` no longer offer *Terrain* (rental-only flows); `listing/bien.html`, `mandate/edit.html` and the JS maps use `RESIDENTIAL`.
- [x] v2: `find-property.html`, `offer-property.html`, `mandate/edit.html` and `listing/bien.html` use `RESIDENTIAL`; `offer-property.js` (`SALE_CATEGORIES`) and `find-property.js` (`saleCategories`) offer land under `RESIDENTIAL` only.
- [x] v2 `partner-central.js`: the listing section hides `LAND` on a rental (including a legacy checked answer) and shows it on a sale; mandate edit keeps the #275 `savedLand` exception.
- [x] v2 samples 208, 209 and 309 read `RESIDENTIAL`; `v2/README.md` documents the rule.

## Task 5: Stored data

**Files:**
- `modules/ndopify-server/src/main/resources/db/migration/common/V17_1__land-is-residential.sql`

- [x] Rewrite `T_MANDATE.property_category` and `T_LISTING.property_category` to `RESIDENTIAL` where `property_type = 'LAND'` and `property_category = 'COMMERCIAL'`; no other row moves.

## Task 6: Verification

- [x] `mvn test -Dheadless=true` in `modules/ndopify-partner-central` → 1989 tests, 0 failures, `BUILD SUCCESS`.
- [x] `mvn validate` in `modules/ndopify-partner-central` → ktlint clean.
- [x] `mvn -o test -Dtest=GetListingEndpointTest` in `modules/ndopify-server` → 10/10, Flyway applies `17.1 - land-is-residential` and ends at `v17.1`.
- [x] `git diff --check` → clean.

## Review Focus

- **Rental listing hides land entirely.** The controller/view filter, the template's `types` loop and `ListingRules.typeFits(..., sale = false)` must agree: no option rendered and a posted `LAND` refused — not merely unchecked.
- **Sale listing still offers land.** A sale must render `LAND` under `RESIDENTIAL` and save it; `applies(...)` must not ask the land follow-ups.
- **Legacy rental mandate.** `MandateEditTemplateTest` and `MandateEditRulesTest` pin that a stored rental land is readable and savable, while `FindPropertyRules` refuses a rental land that isn't the saved answer.
- **Wizard fixtures are residential.** Every land fixture that was `COMMERCIAL` is now `RESIDENTIAL`; the commercial sale list no longer contains `LAND`.
- **Migration scope.** Only `LAND` + `COMMERCIAL` rows move; unknown categories are untouched.
