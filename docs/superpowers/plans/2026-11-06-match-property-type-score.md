# Match Property Type Score Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hard `propertyType` rule-out with a graded multiplier so commercial sibling types can match, while `LAND`, residential cross-types and unknown listing types stay ruled out.

**Architecture:** `Matcher` computes a `type` factor (0.0–1.0) from an explicit compatibility matrix and multiplies the existing weighted sum by it; `0.0` still returns `null`. The factor is stored on `T_MATCH`, exposed on the `Match` DTO, and explained by Partner Central's match fit. The six weights and `minScore` do not change.

**Tech Stack:** Kotlin, Spring Boot, JPA/Hibernate, Flyway, Maven, kotlin.test, mockito-kotlin, Thymeleaf (Partner Central).

**Spec:** `docs/superpowers/specs/2026-11-06-match-property-type-score-design.md`

## Global Constraints

- Weights unchanged: neighbourhood 0.30, price 0.25, rooms 0.15, amenities 0.15, availability 0.10, furniture 0.05. `minScore` stays 0.5.
- `type` values: `null`/`UNKNOWN` search → 1.0; `null`/`UNKNOWN` listing → 0.0; exact → 1.0; `OFFICE`↔`RETAIL` → 0.9; `BUILDING`↔`OFFICE`/`RETAIL` → 0.85; `INDUSTRIAL`↔`OFFICE`/`RETAIL`/`BUILDING` → 0.8; everything else (including `LAND` and residential cross-types) → 0.0.
- `0.0` means ruled out (`Matcher.score` returns `null`), never a stored zero score.
- The `propertyCategory` veto is skipped only when either type is `BUILDING`.
- `cityId`, `listingType`, own-property and budget rule-outs are unchanged.
- DTO change: set `<ndopify-dto.version>` in the root `pom.xml` to `0.0.84-SNAPSHOT` and `mvn install` the DTO module locally before building the server or Partner Central (repository convention; a later "fix dependencies" commit pins the released version).
- Kotlin/ktlint; `kotlin.test` assertions; `mockito-kotlin`; UI text in French, code/docs in English.

## Review Focus

1. A search with no preferred type (`null`/`UNKNOWN`) against a `LAND` listing: the category check still decides, as today. Tested in Task 1.
2. A `BUILDING` search against a listing whose type is `null`: no crash and no accidental match (type `0.0`). Tested in Task 1.
3. `INDUSTRIAL` ↔ `BUILDING`: must be `0.8`, not `0.0` from a generic branch. Tested in Task 1.
4. A `Match` JSON payload without `typeScore` (older server): the DTO default of `1.0` keeps the exact-type explanation. Tested in Task 3.
5. A pre-migration `T_MATCH` row that omits `type_score`: the column default of `1.0` is returned. Tested in Task 2.

---

### Task 1: Type matrix and multiplier

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchScore.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Produces: `MatchScore(..., furniture: Double, type: Double = 1.0, distanceKm: Double? = null, missingAmenities: List<String> = emptyList())`, with `total = (weighted sum) * type`.
- Produces: `Matcher.score(...)` returns `null` when `typeScore(...) == 0.0`; otherwise the `MatchScore` carries the factor.

- [ ] **Step 1: Write the failing tests**

Add to `MatcherTest` (fixtures `search` = residential `APARTMENT`; `commercialSearch`/`commercialListing` are the same fixtures with `propertyCategory = COMMERCIAL`, `propertyType = OFFICE`):

```kotlin
private val commercialSearch = search.copy(
    propertyCategory = PropertyCategory.COMMERCIAL,
    propertyType = PropertyType.OFFICE,
)
private val commercialListing = listingA.copy(
    propertyCategory = PropertyCategory.COMMERCIAL,
    propertyType = PropertyType.OFFICE,
)

@Test
fun `commercial sibling - retail`() {
    val result = score(commercialSearch, commercialListing.copy(propertyType = PropertyType.RETAIL))
    assertEquals(0.9, result?.type, DELTA)
    assertEquals(0.855, result?.total, DELTA) // 0.95 * 0.9
}

@Test
fun `commercial sibling - building`() {
    val result = score(commercialSearch, commercialListing.copy(propertyType = PropertyType.BUILDING))
    assertEquals(0.85, result?.type, DELTA)
    assertEquals(0.8075, result?.total, DELTA)
}

@Test
fun `commercial sibling - industrial`() {
    val result = score(commercialSearch, commercialListing.copy(propertyType = PropertyType.INDUSTRIAL))
    assertEquals(0.8, result?.type, DELTA)
    assertEquals(0.76, result?.total, DELTA)
}

@Test
fun `building matches a building of the other category`() {
    val result = score(
        search.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.BUILDING),
        listingA.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.BUILDING),
    )
    assertEquals(1.0, result?.type, DELTA)
    assertEquals(0.95, result?.total, DELTA)
}

@Test
fun `office matches a building stored residential`() {
    val result = score(
        commercialSearch,
        listingA.copy(propertyCategory = PropertyCategory.RESIDENTIAL, propertyType = PropertyType.BUILDING),
    )
    assertEquals(0.85, result?.type, DELTA)
    assertEquals(0.8075, result?.total, DELTA)
}

@Test
fun `ruled out - land`() {
    assertNull(score(commercialSearch, commercialListing.copy(propertyType = PropertyType.LAND)))
    assertNull(score(commercialSearch.copy(propertyType = PropertyType.LAND), commercialListing))
}

@Test
fun `ruled out - commercial against residential`() {
    assertNull(score(commercialSearch, commercialListing.copy(propertyType = PropertyType.APARTMENT)))
}

@Test
fun `no property type searched - unknown`() {
    val result = score(search.copy(propertyType = PropertyType.UNKNOWN), listingA.copy(propertyType = PropertyType.HOUSE))
    assertEquals(1.0, result?.type, DELTA)
}

@Test
fun `ruled out - listing without property type`() {
    assertNull(score(search, listingA.copy(propertyType = null)))
}
```

Also assert `type == 1.0` in the existing `listing A` test, and keep the existing `ruled out - other property type` and `ruled out - other property category` tests unchanged.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: compile ERROR (`type` unresolved in `MatchScore`).

- [ ] **Step 3: Implement `MatchScore.type` and the multiplier**

Add the trailing parameter after `furniture`, keep the two existing defaults, and multiply the weighted sum:

```kotlin
data class MatchScore(
    val neighborhood: Double,
    val price: Double,
    val rooms: Double,
    val amenities: Double,
    val availability: Double,
    val furniture: Double,
    val type: Double = 1.0,
    val distanceKm: Double? = null,
    val missingAmenities: List<String> = emptyList(),
) {
    val total: Double
        get() = (Matcher.WEIGHT_NEIGHBORHOOD * neighborhood + ... + Matcher.WEIGHT_FURNITURE * furniture) * type
}
```

- [ ] **Step 4: Implement the matrix and veto in `Matcher`**

Signature: `private fun typeScore(search: PropertyType?, listing: PropertyType?): Double`, evaluated in this order: missing search → 1.0; missing listing → 0.0; exact → 1.0; `LAND` vs non-land → 0.0; `BUILDING`/`OFFICE`/`RETAIL` → 0.85; `OFFICE`/`RETAIL` → 0.9; `INDUSTRIAL`/`OFFICE`/`RETAIL`/`BUILDING` → 0.8; else 0.0. Use direct pair checks, not `propertyCategory`.

In `score()`:

```kotlin
val type = typeScore(search.propertyType, listing.propertyType)
if (isRuledOut(search, listing) || type == 0.0) {
    return null
}
return MatchScore(..., type = type)
```

Remove the `propertyType` clause from `isRuledOut`; replace the category comparison with:

```kotlin
listing.propertyCategory != search.propertyCategory &&
    search.propertyType != PropertyType.BUILDING &&
    listing.propertyType != PropertyType.BUILDING
```

Update the `Matcher` KDoc: the rule-outs, the matrix, the multiplier, and a pointer to the design doc.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchScore.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt
git commit -m "feat(match): score the property type instead of ruling it out"
```

---

### Task 2: Persist and expose the type score

**Files:**
- Modify: `pom.xml` (`ndopify-dto.version` → `0.0.84-SNAPSHOT`)
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/Match.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V10_3__match-type-score.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mapper/MatchMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/GetMatchEndpointTest.kt`

**Interfaces:**
- Consumes: `MatchScore.type` from Task 1.
- Produces: `Match.typeScore: Double = 1.0`, `MatchEntity.typeScore: Double = 1.0`, column `T_MATCH.type_score DOUBLE NOT NULL DEFAULT 1.0`.

- [ ] **Step 1: Add the DTO field and install the DTO**

Add `val typeScore: Double = 1.0` after `score` in `Match`. Set the root property and install:

```bash
# pom.xml: <ndopify-dto.version>0.0.84-SNAPSHOT</ndopify-dto.version>
mvn -pl modules/ndopify-dto install -DskipTests
```

- [ ] **Step 2: Write the failing persistence tests**

In `MatchServiceTest`, add:

```kotlin
@Test
fun `matchMandate persists the type score`() {
    doReturn(listOf(listing1)).whenever(listingDao).findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(score(1.0).copy(type = 0.85))
        .whenever(matcher).score(eq(search), eq(listing1), any(), eq(today))

    service.matchMandate(MANDATE_ID)

    val captor = argumentCaptor<MatchEntity>()
    verify(matchDao).save(captor.capture())
    assertEquals(0.85, captor.firstValue.typeScore, 0.000001)
}
```

In `GetMatchEndpointTest.get()`, add `assertEquals(1.0, match.typeScore)`: the SQL seed omits `type_score`, proving the migration default.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest,GetMatchEndpointTest`
Expected: compile ERROR (`typeScore` unresolved).

- [ ] **Step 4: Implement the migration, entity, mapper and store**

Migration:

```sql
ALTER TABLE T_MATCH ADD COLUMN type_score DOUBLE NOT NULL DEFAULT 1.0 AFTER score;
```

Add `val typeScore: Double = 1.0` after `score` in `MatchEntity`, map it in `MatchMapper`, and set `typeScore = score.type` in `MatchService.store`'s `match.copy(...)`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest,GetMatchEndpointTest`
Expected: PASS (endpoint tests need the local MySQL instance).

- [ ] **Step 6: Commit**

```bash
git add pom.xml modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/match/dto/Match.kt \
        modules/ndopify-server/src/main/resources/db/migration/common/V10_3__match-type-score.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/domain/MatchEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mapper/MatchMapper.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/endpoint/GetMatchEndpointTest.kt
git commit -m "feat(match): persist the property type score"
```

---

### Task 3: Explain the type in Partner Central

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchFixtures.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt`

**Interfaces:**
- Consumes: `Match.typeScore` from Task 2.
- Produces: `MatchRules.fit` returns a type line, after rooms: `FitLine(true, "Appartement, comme demandé")` when `typeScore >= 0.999`, else `FitLine(false, "Boutique au lieu de bureau")`.

- [ ] **Step 1: Write the failing tests**

Add to `MatchRulesTest`:

```kotlin
@Test
fun `fit - commercial sibling`() {
    val mandate = MANDATE.copy(
        propertyCategory = PropertyCategory.COMMERCIAL,
        propertyType = PropertyType.OFFICE,
        rooms = null,
    )
    val listing = LISTING_A.copy(propertyCategory = PropertyCategory.COMMERCIAL, propertyType = PropertyType.RETAIL)

    val fit = MatchRules.fit(MATCH_A.copy(typeScore = 0.9), mandate, listing).map { it.text }

    assertTrue("Boutique au lieu de bureau" in fit)
}

@Test
fun `fit - no type in the payload`() {
    // A Match deserialized from an older server has typeScore = 1.0 by default
    val match = Match(mandateId = MANDATE.id, listingId = LISTING_A.id)

    assertTrue("Appartement, comme demandé" in MatchRules.fit(match, MANDATE, LISTING_A).map { it.text })
}
```

Update every existing `MatchRulesTest` exact-fit expectation to include `FitLine(true, "Appartement, comme demandé")` at index 3 (after rooms), shift the later indexes, and set `typeScore = 0.0` in the "studio without rooms" fixture. Update `MatchViewsTest.section` from `6` to `7` fit lines.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MatchRulesTest,MatchViewsTest`
Expected: FAIL (missing type line / wrong count).

- [ ] **Step 3: Implement the fit line**

Import `PropertyType` and add `type(match, mandate, listing)` after `rooms(...)` in the `fit` list. `type` returns `null` when the search type is `null`/`UNKNOWN`; uses `MandateLabels.shortPropertyType` (falling back to `MandateLabels.propertyType`); exact when `match.typeScore >= FULL`; otherwise `"$listed au lieu de ${searched.lowercase()}"`.

Add `typeScore = 1.0` to `MATCH_A`, `MATCH_B` and `MATCH_C` in `MatchFixtures`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MatchRulesTest,MatchViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchFixtures.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchRulesTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/match/MatchViewsTest.kt
git commit -m "feat(partner-central): explain the property type of a match"
```

---

### Task 4: Update the algorithm record and verify

**Files:**
- Modify: `docs/superpowers/plans/2026-10-31-search-listing-matching.md`
- Modify: `modules/ndopify-server/CLAUDE.md` (the `match` domain paragraph)
- Include: `docs/superpowers/specs/2026-11-06-match-property-type-score-design.md`, `docs/superpowers/plans/2026-11-06-match-property-type-score.md`

- [ ] **Step 1: Update the algorithm record**

In the 2026-10-31 plan: remove the property-type rule from *Rule-outs*, add the *Type* row to the partial-scores table (`× 0.9/0.85/0.8`, `0.0` = veto), change `score = Σ weight × partial` to `score = (Σ weight × partial) × type`, add the commercial sibling example, and update the `MatcherTest` bullet list. In `modules/ndopify-server/CLAUDE.md`, replace "another type / category / city / property type" with the new rule summary (type scored, category skipped only for `BUILDING`, `LAND`/residential cross-types vetoed).

- [ ] **Step 2: Run the full relevant suites**

```bash
cd modules/ndopify-server && mvn test -Dtest=MatcherTest,MatchServiceTest,GetMatchEndpointTest,SearchMatchEndpointTest,RematchJobTest,NotifyMatchesJobTest
cd ../ndopify-partner-central && mvn test -Dtest=MatchRulesTest,MatchViewsTest,MatchControllerTest
```

Expected: PASS. Endpoint/job tests need the local MySQL instance.

- [ ] **Step 3: Run ktlint**

```bash
cd modules/ndopify-server && mvn validate
cd ../ndopify-partner-central && mvn validate
```

Expected: PASS (no ktlint violation).

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-11-06-match-property-type-score-design.md \
        docs/superpowers/plans/2026-11-06-match-property-type-score.md \
        docs/superpowers/plans/2026-10-31-search-listing-matching.md \
        modules/ndopify-server/CLAUDE.md
git commit -m "docs(match): record the property type score"
```
