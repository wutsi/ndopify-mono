# Capture and Match Buildings by Levels and Units Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A building sold as a whole is captured by two counts — its étages (`levels`) and its unités (`units`) — on the mandate, on the listing, in both wizards, on every agent- and customer-facing screen, and in the matcher, which scores their closeness instead of the rooms and amenities a building doesn't have.

**Architecture:** `levels` already exists on `Listing` and `UpdateListingRequest`; the plan adds `units` there and both on the mandate, backed by two Flyway migrations, with the server keeping them for a building sale only. The agent's listing drops its *Eau, électricité, accès* section (server: `ListingSectionService` omits it, `ListingService.update` refuses it). `Matcher` gets a building weight set gated on a SALE pair of two `BUILDING`s, with `MatchScore.units`/`levels` closeness partials stored on `Match` through two new columns. Both wizards skip the amenities step for a building sale and ask the two counts; Partner Central and the public site port the approved prototype.

**Tech Stack:** Kotlin 2.3, Spring Boot 4, JUnit 5 + `kotlin.test`, mockito-kotlin, `TestRestTemplate` on MySQL 8, Flyway, Thymeleaf 3, jsoup, `MockMvc`, Mustache (contract templates), plain ES5 JavaScript in the prototypes.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/274

**Design record (epic roadmap):** [`docs/superpowers/plans/2026-11-22-v2-property-sales.md`](https://github.com/wutsi/ndopify-mono/blob/main/docs/superpowers/plans/2026-11-22-v2-property-sales.md)

**Approved prototype (the baseline to port in Tasks 6–10, not to design again):**
- `modules/ndopify-partner-central/src/main/html/v2/find-property.html` + `assets/js/find-property.js`
- `modules/ndopify-partner-central/src/main/html/v2/offer-property.html` + `assets/js/offer-property.js`
- `modules/ndopify-partner-central/src/main/html/v2/mandate.html`, `mandate/edit.html`, `listing.html`, `listing/bien.html`, `assets/js/partner-central.js`, `assets/css/find-property.css`, `assets/css/listing.css`
- `modules/ndopify-web/src/main/html/v2/annonce.html`, `proposition.html`, `assets/js/annonce.js`, `assets/js/proposition.js`

Review it at `http://localhost:8091/modules/ndopify-partner-central/src/main/html/v2/find-property.html?sample=building` (serve the worktree root), `offer-property.html?sample=building`, `mandate.html?id=305`, `mandate.html?id=306`, `mandate/edit.html?id=305`, `listing.html?id=701`, `listing/bien.html?id=701`, and `http://localhost:8091/modules/ndopify-web/src/main/html/v2/annonce.html?building=1`, `proposition.html?building=1`. The prototype is committed with this plan and is never edited here.

## Before you start

Confirm the landing state: `units` exists nowhere, `levels` only on the listing, and the DTO release flow is the one in `modules/ndopify-dto/CLAUDE.md`.

```bash
git grep -n "units" -- modules/ndopify-dto/src/main modules/ndopify-server/src/main | head
git grep -n "levels" -- modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto
git grep -n "AMENITIES" -- modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingSectionService.kt
```

Expected: no `units` field anywhere (only unrelated words), `levels` on `Listing` and `UpdateListingRequest`, `ListingSectionService.OPTIONAL_SECTIONS` = remarks only.

## Global Constraints

- **Sale only.** A SALE pair of two `BUILDING`s, or a SALE `BUILDING` mandate/listing. A rented building keeps today's questions, mapping, model, screens and weights — its matcher score must stay bit-for-bit what it is: the building weight set is gated on `listingType == SALE`, and the two counts are stored only for a building sale.
- **Two counts.** `levels` (existing field name, UI word *étages*) and `units`; `Int?`; whole numbers, bounded `1..30` étages and `1..500` unités by the wizards and the edit pages only (the server validates neither).
- **Buyer's *Indifférent*.** A UI-only answer, posted as an empty input and stored as `null`; the matcher treats `null` on either side as a neutral `1.0`.
- **Building weight set**, only for a SALE `BUILDING`↔`BUILDING` pair: neighbourhood `0.30`, price `0.25`, units `0.20`, levels `0.15`, availability `0.10`; the total is the weighted sum × `typeScore`, and rooms, amenities and furniture neither contribute nor appear as gaps. Every other pair keeps today's six weights and partials (`0.30/0.25/0.15/0.15/0.10/0.05`).
- **Closeness, never a rule-out**: `1 - |listing - search| / search`, clamped `0..1`, `1.0` when the search didn't answer or the listing states no value. No new `MatchRuleOut`, no new error code. The `min-score` threshold still applies to the total, as for every pair.
- **A building has no bathrooms, no parkings and no utilities**: neither wizard asks them, the listing's *Le bien* asks neither bathroom nor parking, its *Eau, électricité, accès* section is absent from the checklist and refused, and no agent- or customer-facing screen of a building words them.
- **Nothing else moves.** A building's price floor (`1 000 000`), its surface, its documents, its contract terms, dispatch, e-signature and the tenants' console are untouched; no data is migrated.
- **Exact copy** (French, from the approved prototype): *Nombre d'étages* `(1 à 30)`, *Nombre d'unités* `(1 à 500)`, *Étages*, *Unités*, *Indifférent*, errors `Indiquez le nombre d'étages, ou cochez Indifférent.`, `Indiquez un nombre entier, de 1 à 30.`, `Indiquez le nombre d'unités, ou cochez Indifférent.`, `Indiquez un nombre entier, de 1 à 500.`, card `Immeuble · 12 unités · 3 étages`, fit lines `12 unités, comme demandé`, `20 unités au lieu de 10`, `Nombre d'unités non précisé`, and the same for étages.
- **Shared code.** `ndopify-dto` changes; it is released before any module using the fields is deployed (Task 11).
- Code, comments, plan and commits in English; UI text in French. `mvn verify -Dheadless=true` green in each touched module (`ndopify-server` needs local MySQL). Commits are conventional and scoped, each with `Refs #274` in the body.

## Review Focus

- **A rented building is untouched.** A RENTAL `BUILDING`↔`BUILDING` pair keeps today's six weights and its rooms/amenities lines; a rental building's mandate stores neither count and its screens are today's. Task 4 and Task 6 pin the pair, Task 2 the mandate.
- **Missing data is neutral, not zero.** A search without étages, a listing without unités (an API-created building, or one published before this story), or both, scores `1.0` on that partial and leaves the pair's other partials and total unchanged. Task 4 pins all four cases.
- **A bigger or smaller building still matches.** 12 units for a 10-unit search scores `0.8` and stays a match (no rule-out); only the total's `min-score` can drop it, as for any weak pair. Task 4 pins the score and the absence of a new rule.
- **A building's checklist is sendable without its amenities.** Its `Listing.sections` has six rows, `verify` passes with them done, and a save of the amenities section is refused — a building must never be stuck unfillable or publishable with utility facts that its public page hides. Tasks 3 and 8 pin both sides.
- **An older building has no counts.** A building listing or mandate created before this story words *Nombre d'unités non précisé* / shows no *Étages* row instead of a zero, and matches everything on that partial. Tasks 4, 9 and 10 pin the blank case.

---

### Task 1: The DTO fields

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/Mandate.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/CreateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/UpdateMandateRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/Listing.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/UpdateListingRequest.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ListingSummary.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/Match.kt`

**Interfaces:**
- Produces: `Mandate.levels: Int?`, `Mandate.units: Int?`, the same on `CreateMandateRequest` and `UpdateMandateRequest`; `Listing.units: Int?`, `UpdateListingRequest.units: Int?`, `ListingSummary.units: Int?` and `ListingSummary.levels: Int?`; `Match.unitsScore: Double`, `Match.levelsScore: Double`.
- Consumes: nothing.

- [ ] **Step 1: Add the mandate fields**

In `Mandate`, `CreateMandateRequest` and `UpdateMandateRequest`, after `val area: Int? = null`:

```kotlin
    /** The building's storeys (worded "étages"); a building sale only. */
    val levels: Int? = null,

    /** The building's dwellings/units; a building sale only. */
    val units: Int? = null,
```

- [ ] **Step 2: Add the listing fields**

In `Listing`, after `val levels: Int? = null`: `val units: Int? = null` (same KDoc as above). In `UpdateListingRequest`, after `val levels: Int? = null`: `val units: Int? = null`. In `ListingSummary`, after `val area: Int? = null`:

```kotlin
    val levels: Int? = null,
    val units: Int? = null,
```

- [ ] **Step 3: Add the match partials**

In `Match`, after `val roomsScore: Double = 0.0`:

```kotlin
    val unitsScore: Double = 1.0,
    val levelsScore: Double = 1.0,
```

(`1.0` is the neutral partial the DTO documents: the same default `typeScore` uses.)

- [ ] **Step 4: Build the DTO locally and set the version the other modules read**

```bash
mvn -q -Dheadless=true -pl modules/ndopify-dto install
grep -n "<ndopify-dto.version>" pom.xml
```

Expected: BUILD SUCCESS. Set `<ndopify-dto.version>` in the root `pom.xml` to the DTO's own current `-SNAPSHOT` (its `pom.xml` version) so the other modules resolve the new fields locally; Task 11 pins the released version.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto pom.xml
git commit -m "feat(dto): levels and units on the mandate and the listing, building partials on the match" -m "Refs #274"
```

---

### Task 2: The mandate stores the two counts

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V18_0__mandate-levels-units.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateService.kt:175-300` (`create`) and `:310-390` (`update`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UpdateMandateEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateMapperTest.kt`

**Interfaces:**
- Consumes: `CreateMandateRequest.levels/units`, `UpdateMandateRequest.levels/units`, `Mandate.levels/units` (Task 1).
- Produces: `MandateEntity.levels: Int?`, `MandateEntity.units: Int?`, persisted and mapped back on `Mandate`; a private `MandateService.criteria`-read rule `levels = criteria.levels.takeIf { buildingSale }`, `units = criteria.units.takeIf { buildingSale }` with `buildingSale = sale && propertyType == PropertyType.BUILDING`.

- [ ] **Step 1: Write the migration**

`V18_0__mandate-levels-units.sql`:

```sql
ALTER TABLE T_MANDATE
  ADD COLUMN levels INT NULL AFTER area,
  ADD COLUMN units INT NULL AFTER levels;
```

- [ ] **Step 2: Write the failing endpoint tests**

In `CreateMandateEndpointTest`, a sale `BUILDING` search posted with `levels = 3, units = 12` returns them on `GET /v1/mandates/{id}`; a `RENTAL` `BUILDING` and a SALE `LAND` posted with the same values return `null` for both. In `UpdateMandateEndpointTest`, the same for `PUT /v1/mandates/{id}` (an ASSIGNED, unsigned mandate), including a SALE `LAND` update that must clear counts stored by an earlier `BUILDING` type. In `MandateMapperTest`:

```kotlin
@Test
fun `toMandate maps the building counts`() {
    val mandate = mapper.toMandate(MandateEntity(levels = 3, units = 12))
    assertEquals(3, mandate.levels)
    assertEquals(12, mandate.units)
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=CreateMandateEndpointTest+UpdateMandateEndpointTest+MandateMapperTest
```

Expected: FAIL — unknown property `levels` on the entity, `null` counts.

- [ ] **Step 4: Add the entity fields and the migration, then the normalization**

`MandateEntity`, after `val area: Int? = null`:

```kotlin
    @Column(name = "levels")
    val levels: Int? = null,
    @Column(name = "units")
    val units: Int? = null,
```

In `MandateService.create`, beside `val sale = criteria.listingType == ListingType.SALE`, add
`val buildingSale = sale && criteria.propertyType == PropertyType.BUILDING`, then pass
`levels = criteria.levels?.takeIf { buildingSale }` and `units = criteria.units?.takeIf { buildingSale }` in the
`MandateEntity(...)`. In `update`, the same from `request.propertyType` and `request.levels`/`request.units`, so that a
mandate whose type leaves `BUILDING` loses them (a sale LAND or a rental building keeps today's model: neither count).
Add the two fields to `MandateMapper.toMandate`.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=CreateMandateEndpointTest+UpdateMandateEndpointTest+MandateMapperTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): store a building's levels and units on its mandate" -m "Refs #274"
```

---

### Task 3: The listing stores the counts, and a building has no amenities section

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V18_1__listing-units.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ListingEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt:130-190` (`create`), `:205-250` (`PROPERTY` branch), `:240-265` (`AMENITIES` branch), `:290-300` (`verify`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingSectionService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt:85-92`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/GetListingEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/UpdateListingEndpointTest.kt`

**Interfaces:**
- Consumes: `MandateEntity.levels/units` (Task 2), `Listing.units`, `UpdateListingRequest.units`, `ListingSummary.units/levels` (Task 1).
- Produces: `ListingEntity.units: Int?`; `ListingSectionService.sections(listing: ListingEntity): List<ListingSection>` and `summaries(listing: ListingEntity)`; `ListingSectionService.soldBuilding(listing: ListingEntity): Boolean` (companion, reused by `ListingService`).

- [ ] **Step 1: Write the migration**

`V18_1__listing-units.sql`:

```sql
ALTER TABLE T_LISTING
  ADD COLUMN units INT NULL AFTER levels;
```

- [ ] **Step 2: Write the failing tests**

In `ListingServiceTest`: creating a listing from a sale `BUILDING` mandate (`levels = 3, units = 12`) copies both; a `PROPERTY` update on that listing saves `units` and clears `bathrooms` and `parkings` even when posted; an `AMENITIES` update throws `BadRequestException` with the `section` parameter for that listing and succeeds for a villa; `verify` passes a sale building whose six sections are verified (no `LISTING_INCOMPLETE`). In `GetListingEndpointTest`: a sale building's `GET /v1/listings/{id}` returns six `sections` without `AMENITIES`, and a rental building's returns the seven. In `UpdateListingEndpointTest`: the refused amenities save answers 400.

- [ ] **Step 3: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingServiceTest+GetListingEndpointTest+UpdateListingEndpointTest
```

Expected: FAIL — `units` unknown, seven sections for a building, amenities save accepted.

- [ ] **Step 4: Implement**

`ListingEntity`: `val units: Int? = null` after `levels`. `ListingMapper.toListing`: `units = entity.units`; `toListingSummary`: `levels = entity.levels, units = entity.units`.

`ListingService.create`: `levels = mandate.levels, units = mandate.units`.

`ListingService.update`, `ListingSection.PROPERTY` branch:

```kotlin
bathrooms = request.bathrooms.takeIf { !ListingSectionService.soldBuilding(listing) },
parkings = request.parkings.takeIf { !ListingSectionService.soldBuilding(listing) },
units = request.units,
```

and the `AMENITIES` branch starts with `if (ListingSectionService.soldBuilding(listing)) throw invalid("section")`.

`ListingSectionService`:

```kotlin
fun soldBuilding(listing: ListingEntity): Boolean =
    listing.listingType == ListingType.SALE && listing.propertyType == PropertyType.BUILDING

val BUILDING_SECTIONS = SECTIONS - ListingSection.AMENITIES

fun sections(listing: ListingEntity): List<ListingSection> = if (soldBuilding(listing)) BUILDING_SECTIONS else SECTIONS

fun summaries(listing: ListingEntity): List<ListingSectionSummary>  // same body, iterating sections(listing); still reads dao by listing.id
```

Change `verify`'s caller signature: `ListingService.verify` calls `sectionService.summaries(listing)`; `ListingEndpoints.get` calls `service.summaries(listing)` (the listing is already loaded).

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingServiceTest+GetListingEndpointTest+UpdateListingEndpointTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): store a building's units, and drop its amenities section" -m "Refs #274"
```

---

### Task 4: The building pair in the matcher

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V18_2__match-building-scores.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchScore.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mapper/MatchMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt:353-395` (`store`), `:442-465` (`accepted`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`

**Interfaces:**
- Consumes: `MandateEntity.levels/units`, `ListingEntity.levels/units` (Tasks 2 and 3), `Match.unitsScore/levelsScore` (Task 1).
- Produces: `Matcher.WEIGHT_UNITS = 0.20`, `Matcher.WEIGHT_LEVELS = 0.15`; `MatchScore(units = 1.0, levels = 1.0, building = false)` with a `total` that switches weight sets on `building`; `MatchEntity.unitsScore/levelsScore`.

- [ ] **Step 1: Write the migration**

`V18_2__match-building-scores.sql`:

```sql
ALTER TABLE T_MATCH
  ADD COLUMN units_score DOUBLE NOT NULL DEFAULT 1 AFTER rooms_score,
  ADD COLUMN levels_score DOUBLE NOT NULL DEFAULT 1 AFTER units_score;
```

(Check the real table/column names in `V9_0__match.sql` first and match its style; a stored row from before this story is neutral.)

- [ ] **Step 2: Write the failing matcher tests**

In `MatcherTest`, a `buildingSearch` fixture (SALE, `BUILDING`, 10 units, 3 levels) and three listings (10/3, 12/4, 20/3), all SALE `BUILDING`, same city and neighbourhood:

```kotlin
@Test
fun `a building pair scores its counts with the building weights`() {
    val score = matcher.score(buildingSearch, building(units = 10, levels = 3), distances, TODAY)
    assertNotNull(score)
    assertEquals(1.0, score!!.units, DELTA)
    assertEquals(1.0, score.levels, DELTA)
    assertEquals(1.0, score.total, DELTA) // 0.30 + 0.25 + 0.20 + 0.15 + 0.10, typeScore 1.0
    assertTrue(score.building)
}

@Test
fun `a building's counts score their closeness and never rule the pair out`() {
    assertEquals(0.8, matcher.score(buildingSearch, building(units = 12, levels = 4), distances, TODAY)!!.units, DELTA)
    assertEquals(0.6667, matcher.score(buildingSearch, building(units = 12, levels = 4), distances, TODAY)!!.levels, DELTA)
    assertEquals(0.0, matcher.score(buildingSearch, building(units = 20, levels = 3), distances, TODAY)!!.units, DELTA)
    assertEquals(1.0, matcher.score(buildingSearch, building(units = 20, levels = 3), distances, TODAY)!!.levels, DELTA)
}

@Test
fun `a missing count is neutral and does not move the total`() {
    val answered = matcher.score(buildingSearch, building(units = 12, levels = 4), distances, TODAY)!!
    val undeclared = matcher.score(buildingSearch, building(units = null, levels = null), distances, TODAY)!!
    assertEquals(1.0, undeclared.units, DELTA)
    assertEquals(1.0, undeclared.levels, DELTA)
    val unanswered = matcher.score(buildingSearch.copy(units = null, levels = null), building(units = 12, levels = 4), distances, TODAY)!!
    assertEquals(answered.neighborhood, unanswered.neighborhood, DELTA)
    assertEquals(answered.price, unanswered.price, DELTA)
    assertEquals(answered.availability, unanswered.availability, DELTA)
}

@Test
fun `a building pair has no rooms, amenities or furniture gap`() {
    val score = matcher.score(buildingSearch.copy(waterTank = true), building(units = 10, levels = 3), distances, TODAY)!!
    assertEquals(emptyList(), score.missingAmenities)
    assertEquals(1.0, score.rooms, DELTA)
    assertEquals(1.0, score.amenities, DELTA)
    assertEquals(1.0, score.furniture, DELTA)
}

@Test
fun `a rented building keeps today's weights`() {
    val score = matcher.score(buildingSearch.copy(listingType = ListingType.RENTAL), building(units = 10, levels = 3, listingType = ListingType.RENTAL), distances, TODAY)!!
    assertFalse(score.building)
    assertEquals(1.0, score.total, DELTA) // six weights, every partial 1.0
}

@Test
fun `a building against an office keeps the six weights`() {
    val score = matcher.score(buildingSearch, building(units = 10, levels = 3).copy(propertyType = PropertyType.OFFICE), distances, TODAY)!!
    assertFalse(score.building)
    assertEquals(0.85, score.type, DELTA)
}
```

In `MatchServiceTest`: a stored building match carries `unitsScore`/`levelsScore` from the score, and an older row (columns absent from the copy) reads `1.0`.

- [ ] **Step 3: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=MatcherTest+MatchServiceTest
```

Expected: FAIL — `units`, `levels`, `building` unresolved on `MatchScore`.

- [ ] **Step 4: Implement `MatchScore` and `Matcher`**

`MatchScore`: add `units: Double = 1.0`, `levels: Double = 1.0`, `building: Boolean = false`; `total` becomes:

```kotlin
val total: Double
    get() = (
        if (building) {
            Matcher.WEIGHT_NEIGHBORHOOD * neighborhood +
                Matcher.WEIGHT_PRICE * price +
                Matcher.WEIGHT_UNITS * units +
                Matcher.WEIGHT_LEVELS * levels +
                Matcher.WEIGHT_AVAILABILITY * availability
        } else {
            Matcher.WEIGHT_NEIGHBORHOOD * neighborhood +
                Matcher.WEIGHT_PRICE * price +
                Matcher.WEIGHT_ROOMS * rooms +
                Matcher.WEIGHT_AMENITIES * amenities +
                Matcher.WEIGHT_AVAILABILITY * availability +
                Matcher.WEIGHT_FURNITURE * furniture
        }
        ) * type
```

`Matcher`: `const val WEIGHT_UNITS = 0.20`, `const val WEIGHT_LEVELS = 0.15`; a private
`fun buildingPair(search: MandateEntity, listing: ListingEntity) = search.listingType == ListingType.SALE && search.propertyType == PropertyType.BUILDING && listing.propertyType == PropertyType.BUILDING`
and `private fun closeness(wanted: Int?, actual: Int?): Double` implementing `1 - |actual - wanted| / wanted` clamped, `1.0` when either is `null` or `wanted <= 0`. In `partials`, compute `val building = buildingPair(search, listing)` and: `rooms`/`amenities`/`furniture` = `if (building) 1.0 else <today>`, `units = if (building) closeness(search.units, listing.units) else 1.0`, `levels` likewise, `missingAmenities = if (building) emptyList() else <today>`, `type = typeScore(...)`. Leave `ruleOut` and the sibling-rooms rule untouched.

- [ ] **Step 5: Persist them**

`MatchEntity`: `val unitsScore: Double = 1.0`, `val levelsScore: Double = 1.0` after `roomsScore`. `MatchService.store` and `accepted`: `unitsScore = score.units, levelsScore = score.levels`. `MatchMapper.toMatch`: `unitsScore = entity.unitsScore, levelsScore = entity.levelsScore`.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=MatcherTest+MatchServiceTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): score a building pair by its closeness on units and levels" -m "Refs #274"
```

---

### Task 5: The server's copy of a building

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt:70-80` (`details`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateMailLabels.kt` (add `fun building(mandate: MandateEntity): String?`)
- Modify: `modules/ndopify-server/src/main/resources/mandate/contract/search_sale.mjml:76-95`
- Modify: `modules/ndopify-server/src/main/resources/mandate/contract/listing_sale.mjml:79-100`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateContractGenerator.kt:160-185` (`context`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/publisher/ListingContentGenerator.kt:12-100` (prompt), `:109-160` (`generate`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/mail/MandateOfferTemplateTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateContractGeneratorTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/publisher/ListingContentGeneratorTest.kt`

**Interfaces:**
- Consumes: `MandateEntity.levels/units`, `ListingEntity.levels/units`.
- Produces: `MandateMailLabels.building(mandate: MandateEntity): String?` returning `"3 étages · 12 unités"` (only a SALE `BUILDING` with at least one non-null count, `null` otherwise); contract context keys `levels`, `units`; prompt placeholders `{{levels}}`, `{{units}}`.

- [ ] **Step 1: Write the failing tests**

`MandateOfferBotletTest`: a sale building's details line reads `12 unités · 3 étages · 850 m² · disponible le …` and contains no `pièce`; a sale villa's stays today's. `MandateOfferTemplateTest`: the offer e-mail's criteria block shows `Étages` and `Unités` for a building and no amenity row. `MandateContractGeneratorTest`: the generated sale contract of a building contains `Étages` `3` and `Unités` `12` and neither `Chambres` nor `Accès à l'eau`. `ListingContentGeneratorTest`: the prompt sent for a sale building contains `Units: 12` and `Levels: 3` and `Bedrooms: -`, `Bathrooms: -`, `Water Meter: -`; a villa's keeps its numbers.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=MandateOfferBotletTest+MandateOfferTemplateTest+MandateContractGeneratorTest+ListingContentGeneratorTest
```

Expected: FAIL — the counts are absent, the amenities still printed.

- [ ] **Step 3: Implement**

`MandateMailLabels.building`:

```kotlin
// "3 étages · 12 unités", for a building sold as a whole; null for any other mandate
fun building(mandate: MandateEntity): String? =
    if (mandate.listingType != ListingType.SALE || mandate.propertyType != PropertyType.BUILDING) {
        null
    } else {
        listOfNotNull(
            mandate.levels?.let { "$it étages" },
            mandate.units?.let { "$it unités" },
        ).joinToString(" · ").ifEmpty { null }
    }
```

`MandateOfferBotlet.details`: first element becomes `MandateMailLabels.building(mandate)` (and `rooms` only when that is null); `purchase()` unchanged. `MandateOfferMailet`: render the same string where it lists the criteria (locate with `git grep -n "ownershipDocuments" modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mail`).

`MandateContractGenerator.context`: `"levels" to mandate.levels, "units" to mandate.units,` next to `rooms`/`parkings`, and keep `has_amenities` as is (a building mandate stores no amenity). In both `*_sale.mjml`, inside the property block, after the `parkings` section:

```mustache
                        {{#levels}}
                        <p style="margin:0 0 6px 0;">
                            <b>Étages</b> : {{levels}}
                        </p>
                        {{/levels}}
                        {{#units}}
                        <p style="margin:0 0 6px 0;">
                            <b>Unités</b> : {{units}}
                        </p>
                        {{/units}}
```

`ListingContentGenerator`: add `- Units: {{units}}` and `- Levels: {{levels}}` to the prompt's property details, and one instruction line: `- For a building sold as a whole, title and describe it by its units and its levels instead of its bedrooms.` In `generate`, compute `val building = listing.listingType == ListingType.SALE && listing.propertyType == PropertyType.BUILDING` and pass `-` for `{{bedrooms}}`, `{{bathrooms}}`, `{{parkings}}` and every amenity placeholder when `building`, `listing.units`/`listing.levels` for the new ones.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=MandateOfferBotletTest+MandateOfferTemplateTest+MandateContractGeneratorTest+ListingContentGeneratorTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): word a building by its units and its levels in e-mails, Telegram, the contract and the listing text" -m "Refs #274"
```

---

### Task 6: The two wizards' rules

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandate.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyController.kt:50-310`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PropertyForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListing.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyForms.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/PendingMandateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/PendingListingTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyControllerTest.kt`

**Interfaces:**
- Consumes: `CreateMandateRequest.levels/units` (Task 1).
- Produces: `FindPropertyRules.MAX_LEVELS = 30`, `MAX_UNITS = 500`, `buildingSale(listingType, type)`, `needsAmenities(category, type, listingType)`, `asksParkings(type, listingType)`, `levels(value: String?, any: Boolean)`, `units(value: String?, any: Boolean)`; `OfferPropertyRules` the same counts without *Indifférent*; `FindPropertyController.steps(answers: PendingMandate): List<String>` and the same on `OfferPropertyController`; the forms carry `levels`, `units`, `levelsAny`, `unitsAny` (Strings).

- [ ] **Step 1: Write the failing rules tests**

`FindPropertyRulesTest`: `needsAmenities(COMMERCIAL, BUILDING, SALE)` is false and true for RENTAL, for a villa and for a null type; `asksParkings(BUILDING, SALE)` false, `asksParkings(BUILDING, RENTAL)` true; `levels("3", false)` null, `levels("", false)` = `Indiquez le nombre d'étages, ou cochez Indifférent.`, `levels("", true)` null, `levels("31", false)` = `Indiquez un nombre entier, de 1 à 30.`, `levels("3,5", false)` the same, `units("501", false)` = `Indiquez un nombre entier, de 1 à 500.` `PendingMandateTest`: a building sale's `steps()` is the six keys without `amenities`, `firstIncompleteStep()` after the property step is `budget`, and `isComplete(STEP_AMENITIES)` is true (skipped) for that branch, false-path unchanged. `OfferPropertyRulesTest`: `SALE_CATEGORIES_BY_TYPE[BUILDING] == setOf(COMMERCIAL)`, `propertyTypes(COMMERCIAL, SALE)` contains `BUILDING`, `propertyTypes(RESIDENTIAL, SALE)` does not; `typeFits(COMMERCIAL, "BUILDING", SALE)` true, `typeFits(RESIDENTIAL, "BUILDING", SALE)` false, both rental cases today's; `needsParking(COMMERCIAL, BUILDING, SALE)` false, `needsAmenities(...)` false for a building sale only. `FindPropertyControllerTest` and `OfferPropertyControllerTest`: `GET /find-property?step=amenities` on a session holding a sale building redirects to `budget` (302, no amenities view); `POST /find-property/property` with `levels=3&units=12` keeps them in `PendingMandate`, and with `levelsAny=on` stores null; the same two for the seller's `POST /offer-property/features`.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyRulesTest,PendingMandateTest,FindPropertyControllerTest,OfferPropertyRulesTest,PendingListingTest,OfferPropertyControllerTest'
```

Expected: FAIL — the functions and fields don't exist.

- [ ] **Step 3: Implement the buyer's rules**

`FindPropertyRules`: add the constants and

```kotlin
fun buildingSale(listingType: ListingType?, type: PropertyType?): Boolean =
    isSale(listingType) && type == PropertyType.BUILDING

/** A building sale has no amenity step: its answers are dropped, and its step leaves the wizard. */
fun needsAmenities(category: PropertyCategory?, type: PropertyType?, listingType: ListingType?): Boolean =
    !buildingSale(listingType, type)

fun asksParkings(type: PropertyType?, listingType: ListingType?): Boolean =
    type !in WITHOUT_PARKINGS && !buildingSale(listingType, type)

/** A whole number in 1..[MAX_LEVELS] (étages) or 1..[MAX_UNITS]; [any] is the "Indifférent" answer. */
fun levels(value: String?, any: Boolean): String?
fun units(value: String?, any: Boolean): String?
```

with the exact messages of the Global Constraints (and `null` when `any`). `PropertyForm` gains `levels: String? = null, units: String? = null, levelsAny: Boolean = false, unitsAny: Boolean = false`;
`PendingMandate` stores `levels: Int?`, `units: Int?` (plus its answers map), `steps()` filters `STEP_AMENITIES` when `buildingSale(listingType, propertyType)`, `isComplete(step)` returns true for the skipped amenities step, and `toRequest()` sends `levels`/`units` (null for a building's neighbours).

- [ ] **Step 4: Implement the seller's rules and both controllers**

`OfferPropertyRules`: `SALE_CATEGORIES_BY_TYPE` = the residential map filtered like today, with `PropertyType.BUILDING to setOf(PropertyCategory.COMMERCIAL)` overriding the derived residential value; add `buildingSale`, `needsAmenities`, the two count rules (no `any`), and reuse them in `needsParking` (`!buildingSale(...) && residential(category) && ...`). `OfferPropertyForms` gains `levels`, `units`; `PendingListing` the same as `PendingMandate` (steps, completion, request).

`FindPropertyController.STEPS` and `OfferPropertyController.STEPS` stay the seven keys; add
`fun steps(answers: PendingMandate): List<String> = if (FindPropertyRules.buildingSale(answers.listingType, answers.propertyType)) STEPS - STEP_AMENITIES else STEPS`
(the seller's mirrors it on `PendingListing`). Use it wherever the controller currently reads `STEPS` (`isAllowed`, the GET step, `redirect(firstIncompleteStep())`, the review comparison, the `minOf` at `:299`); when the posted step is `amenities` on a building sale, drop the answers as every other skip does (`emptyMap`) and move on.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='FindPropertyRulesTest,PendingMandateTest,FindPropertyControllerTest,OfferPropertyRulesTest,PendingListingTest,OfferPropertyControllerTest'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): ask a building sale its units and its étages, and skip its amenities step" -m "Refs #274"
```

---

### Task 7: The two wizards' pages

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/find-property.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/offer-property.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/js/find-property.js`
- Modify: `modules/ndopify-partner-central/src/main/assets/js/offer-property.js`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/find-property.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/findproperty/FindPropertyTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/offerproperty/OfferPropertyTemplateTest.kt`

**Interfaces:**
- Consumes: `FindPropertyController.steps(...)`, the forms of Task 6; the approved prototype's markup.
- Produces: the rendered steps.

- [ ] **Step 1: Write the failing template tests**

`FindPropertyTemplateTest`: a building sale's property step renders `Nombre d'étages` and `Nombre d'unités` with an `Indifférent` checkbox each and no `Places de parking`; a rental building renders no counts and its parking group; the progress legend reads `Étape 4 sur 6` on the budget step of that branch and `sur 7` otherwise; the review shows `Étages` and `Unités` and no amenity row. `OfferPropertyTemplateTest`: the sale's features step offers `Immeuble entier` under `Commercial` only (`data-sale-categories="COMMERCIAL"`), requires the two counts, asks no `Parking`; the six-step legend; the review's counts.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest=FindPropertyTemplateTest+OfferPropertyTemplateTest
```

Expected: FAIL.

- [ ] **Step 3: Port the prototype's markup**

Copy the counts block, the review rows and the category/description changes from the approved prototype (`find-property.html`, `offer-property.html`) into the Thymeleaf templates, using `th:if`/`th:unless` on `FindPropertyRules.buildingSale(listingType, propertyType)` / `OfferPropertyRules.buildingSale(...)`, `steps` for the step count and `steps.size` for the legends, and the model's errors (`errors['levels']`, `errors['units']`). Keep the amenities `<fieldset>` in the page (a hand-typed `?step=amenities` is answered by a redirect, not a 404). French copy identical to the prototype; `th:value` keeps the posted answers on error.

- [ ] **Step 4: Port the prototype's scripts and CSS**

Take `find-property.js` / `offer-property.js`'s building branch from the approved prototype (`?sample=` seeds are prototype-only: do not port them; do port `isBuildingSale`, `activeSteps`, the counts' validation messages, `toRequest`'s nulls and the skipped amenities answers) and the `.count-fields` rule of `find-property.css`, translating to the production files under `src/main/assets/`.

- [ ] **Step 5: Run the tests and the module build**

```bash
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
```

Expected: PASS (ktlint included).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): the building branch of both wizards" -m "Refs #274"
```

---

### Task 8: The agent's mandate and listing screens

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabels.kt:230-275` (`criteria`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateEditRules.kt:25-160`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/edit.html:185-200`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRules.kt:88-120, 240-260, 380-450`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionForm.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingSectionController.kt:75-200`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt:80-135, 580-620`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing/section.html:190-210`
- Modify: `modules/ndopify-partner-central/src/main/assets/js/listing.js`, `assets/css/listing.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateLabelsTest.kt`, `MandateEditRulesTest.kt`, `MandateEditTemplateTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingRulesTest.kt`, `ListingViewsTest.kt`, `ListingTemplateTest.kt`, `ListingSectionTemplateTest.kt`, `ListingSectionControllerTest.kt`

**Interfaces:**
- Consumes: `Mandate.levels/units`, `Listing.units`, `UpdateListingRequest.units` (Task 1); the server's six-section checklist (Task 3).
- Produces: `MandateLabels.criteria` rows `Étages`/`Unités`; `MandateEditForm.levels/units` (Strings) and `MandateEditRules.request(...)` sending them; `ListingRules.soldBuilding(listing: Listing): Boolean`, `ListingRules.sections(listing: Listing)`, `ListingRules.applies("units", ...)`; `ListingSectionForm.units`.

- [ ] **Step 1: Write the failing tests**

`MandateLabelsTest`: a sale building's criteria are `Secteur, Prix, Disponibilité, Catégorie (Commercial), Type de propriété (Immeuble), Étages 3, Unités 12, Surface` — no `Chambres`, no `Parking`, no amenity group; a rental building's are today's + no counts; a sale villa's unchanged. `MandateEditRulesTest`: a building's `request` sends `levels`/`units` (bounded 1..30 / 1..500, empty → error `Indiquez le nombre d'étages.` / `Indiquez le nombre d'unités.`), rooms/parkings/amenities null/false, and `errors` refuses `31` and `501`; the group summary words them. `MandateEditTemplateTest`: the building's page renders the two inputs, no `Chambres`, no `Parking`, no *Équipements* group. `ListingRulesTest`: `sections(buildingSale)` has no `AMENITIES`; `applies("units", COMMERCIAL, BUILDING)` true for a sale and false for a rental; `applies("parkings"|"bathrooms", ..., BUILDING)` false for a sale; `errors(PROPERTY, ...)` requires `units`/`levels` for a building and ignores the rest; `request(...)` sends both. `ListingViewsTest`: the building's rows are six, progress `0 sections sur 5 vérifiées`, its *Le bien* summary words `Immeuble · 3 étages · 12 unités · 850 m²`. `ListingSectionControllerTest`: `GET /listings/{building}/amenities` redirects to `/listings/{id}` without rendering. `ListingTemplateTest` / `ListingSectionTemplateTest`: the building's checklist has no amenities row; the *Le bien* page shows the two inputs, the *Unités* one required, no *Salles de bains* nor *Places de parking*.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MandateLabelsTest,MandateEditRulesTest,MandateEditTemplateTest,ListingRulesTest,ListingViewsTest,ListingTemplateTest,ListingSectionTemplateTest,ListingSectionControllerTest'
```

Expected: FAIL.

- [ ] **Step 3: Implement the mandate screens**

`MandateLabels.criteria`: after the type row, `mandate.levels?.let { "Étages" to it.toString() }` and `mandate.units?.let { "Unités" to it.toString() }` when `FindPropertyRules.buildingSale(mandate.listingType, mandate.propertyType)`; the rooms/parking rows and `amenityGroups(mandate)` are dropped for that branch (`.takeIf { !building }`). `MandateEditForm` gains `levels`/`units` Strings; `MandateEditRules.request` sends `levels = FindPropertyRules.levels(form.levels, false)?.let { null } ?: form.levels!!.trim().toInt()` — concretely: parse both with the rules, put the ints in the request, null when not a building, and drop rooms/parkings/amenities for a building; `errors` adds the two fields; the summary (`:127-160`) words `3 étages · 12 unités`. `mandate/edit.html`: the two inputs and their errors, and `th:if` hiding the rooms/parking fields and the amenities group for a building (port the prototype).

- [ ] **Step 4: Implement the listing screens**

`ListingRules`: `fun soldBuilding(listing: Listing) = listing.listingType == SALE && listing.propertyType == BUILDING`; `fun sections(listing: Listing) = if (soldBuilding(listing)) SECTIONS - ListingSection.AMENITIES else SECTIONS`; use it in `complete`, `ListingViews.of` (required/rows/progress/missing) and `ListingSectionController` (refuse a slug outside `sections(listing)` with a redirect to `/listings/{id}`); `applies`: `"bathrooms" -> type != LAND && !(soldBuildingPair)`, `"parkings" -> type != ROOM && !(sale && type == BUILDING)`, `"units" -> sale && type == BUILDING`; `errors`/`request`: `units` (Integer) beside `levels`, both required for a building, `bathrooms`/`parkings` sent null. `ListingSectionForm`: `units: String?`. `ListingViews.summary(PROPERTY)`: `Immeuble · 3 étages · 12 unités · 850 m²` for a building (no room). `listing/section.html`: the *Unités* input, the required marker toggling; `listing.html`: nothing but the row count follows the server. Port `listing.js`/`listing.css` from the prototype.

- [ ] **Step 5: Run the tests and the module build**

```bash
cd modules/ndopify-partner-central && mvn verify -Dheadless=true
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): word and edit a building's units and étages, without its amenities" -m "Refs #274"
```

---

### Task 9: The match cards

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt:89-200` (`fit`, `rooms`, `amenities`, `furniture`)
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt:175-200` (`label`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `Match.unitsScore/levelsScore`, `ListingSummary.units/levels` (Task 1); `Mandate.levels/units` (Task 1).
- Produces: `MatchRules.fit` returns the building's units and levels lines instead of rooms, amenities, furniture and type-only; `ListingViews.label` reads `Immeuble · 12 unités · 3 étages`.

- [ ] **Step 1: Write the failing tests**

`MatchRulesTest`: a building pair's `fit` is `[Dans le secteur, Dans le budget, 12 unités, comme demandé, 3 étages, comme demandé, Disponible avant votre achat]`; with `unitsScore = 0.8` the line is `12 unités au lieu de 10`; with a listing whose `units` is null, `Nombre d'unités non précisé`; no line mentions chambres, équipement, ameublement; a villa's `fit` is today's. `MatchViewsTest`: the card title reads `Immeuble · 12 unités · 3 étages`. `MandateTemplateTest`: `mandate.html?id` of a building search renders the two counts in its cards and no amenity line.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MatchRulesTest,MatchViewsTest,MandateTemplateTest'
```

Expected: FAIL.

- [ ] **Step 3: Implement**

`MatchRules.fit`: when `FindPropertyRules.buildingSale(mandate.listingType, mandate.propertyType)`, return
`listOfNotNull(neighborhood(match), price(match, mandate, listing), units(match, mandate, listing), levels(match, mandate, listing), availability(match, sale = true))`; otherwise today's list. Add
`private fun units(match, mandate, listing): FitLine?` and `levels(...)`: `null` when the searched count is null; `Nombre d'unités non précisé` when the listing states none; `"12 unités, comme demandé"` when the partial is `1.0`; `"12 unités au lieu de 10"` otherwise (plural: `1 unité`); étages likewise with `3 étages`. `ListingViews.label`: for a building, `"Immeuble"` + the two counts instead of the rooms label.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-partner-central && mvn test -Dtest='MatchRulesTest,MatchViewsTest,MandateTemplateTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): word a building match by its units and its étages" -m "Refs #274"
```

---

### Task 10: The public site

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt:217-245` (`keyFacts`, `specs`), `:108-130` (`amenities`)
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt` (only if the schema fields need the counts)
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/match/MatchPages.kt:190-260` (`searchSummary`, `reasons`, `criteria`)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchPagesTest.kt`
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/match/MatchTemplateTest.kt`

**Interfaces:**
- Consumes: `Listing.units/levels`, `ListingSummary.units/levels`, `Mandate.levels/units`, `Match.unitsScore/levelsScore` (Task 1).
- Produces: a building's `keyFacts` = `12 unités`, `3 étages`, `850 m²`; `specs` = `Type de bien`, `Unités`, `Étages`, `Surface`; `amenities` empty; the proposition's fit and criteria in units and étages.

- [ ] **Step 1: Write the failing tests**

`ListingPagesTest`: a sale building gives `keyFacts` `[12 unités, 3 étages, 850 m²]` (no bedroom, no bathroom, no parking), `specs` `[Type de bien → Immeuble, Unités → 12, Étages → 3, Surface → 850 m²]`, `amenities` empty even when the row carries `waterMeter = true`; a villa's are today's. `ListingTemplateTest`: the rendered building page has no *salles de bains*, *parkings* nor *Équipements vérifiés* block. `MatchPagesTest`: a building proposition's reasons are `[À Bonapriso…, 240M FCFA…, 12 unités, comme demandé, 3 étages, comme demandé, Disponible avant votre achat]`, its criteria empty, its search summary `Pour votre recherche : immeuble de 12 unités sur 3 étages à Bonapriso, jusqu'à 250 000 000 FCFA.`; `MatchTemplateTest` renders it.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-web && mvn test -Dtest='ListingPagesTest,ListingTemplateTest,MatchPagesTest,MatchTemplateTest'
```

Expected: FAIL.

- [ ] **Step 3: Implement**

`ListingPages`: a private `soldBuilding(listing: Listing)`; `keyFacts`/`specs` branch on it (units/étages, no room/bathroom/parking), `amenities` = `emptyList()` for it. `MatchPages.reasons`: when the mandate is a building sale, add the units and levels lines (same wording as the agent's card) and drop the rooms/furniture ones; `criteria` returns `emptyList()` for it; `searchSummary` words `immeuble de {units} unités sur {levels} étages` (either count omitted when null). Keep the rental and the villa paths untouched.

- [ ] **Step 4: Run the tests and the module build**

```bash
cd modules/ndopify-web && mvn verify -Dheadless=true
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): a building's public page and proposition read its units and étages" -m "Refs #274"
```

---

### Task 11: Release the DTO, then pin it

**Files:**
- Modify: `pom.xml` (`<ndopify-dto.version>`)
- Modify: `modules/*/pom.xml` only if a module overrides the version

**Interfaces:**
- Consumes: the DTO released from `main` by `ndopify-dto-main.yml` (tag `ndopify-dto-<version>`).
- Produces: the released `<ndopify-dto.version>` every module builds against.

- [ ] **Step 1: Wait for the DTO release after the merge**

```bash
gh run list --workflow=ndopify-dto-main.yml --limit 1 --json status,conclusion,headSha
gh release list --limit 3 | grep ndopify-dto
```

Expected: the run for the merge commit succeeded and the `ndopify-dto-<version>` tag exists.

- [ ] **Step 2: Pin the released version**

Set `<ndopify-dto.version>` in the root `pom.xml` to that release's version (no `-SNAPSHOT`).

- [ ] **Step 3: Build everything against it**

```bash
mvn -q -Dheadless=true -DskipTests install
```

Expected: BUILD SUCCESS (every module resolves the released DTO).

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "chore(deps): use the released ndopify-dto with the building counts" -m "Refs #274"
```

---

## Self-review

- **Spec coverage.** AC 1–2 → Tasks 6, 7; AC 3 → Task 2; AC 4–5 → Tasks 3, 7, 8; AC 6–7 → Task 4; AC 8 → Tasks 1, 4, 9; AC 9 → Tasks 5, 8; AC 10 → Tasks 5, 10; AC 11 → Tasks 6, 7; AC 12 → the untouched paths pinned in Tasks 2, 4, 8, 10; AC 13 → Task 11.
- **Type consistency.** `levels`/`units` are `Int?` on the DTO and entity, `Double` partials (`unitsScore`/`levelsScore`) on `Match`, `Double` named `units`/`levels` inside `MatchScore`; the wizards' forms keep Strings.
- **Review Focus tests.** Each line names the task that owns it: rental pair (4), missing counts (4), closeness (4), sendable checklist (3, 8), older building (4, 9, 10).
