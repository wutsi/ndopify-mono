# Match Rule-Out Reasons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `MatchService` log *why* a (search, listing) pair was ruled out, plus an aggregated reason summary per matching batch, without changing which pairs match.

**Architecture:** `Matcher` returns a sealed `MatchResult` (`Scored` or `RuledOut(rule, detail)`) from a new `evaluate()`, keeping `score()` as a thin wrapper so existing callers and tests are untouched. `MatchService` logs the rule at DEBUG, logs `BELOW_MIN_SCORE` at INFO, and emits one `MATCH_SUMMARY` line per batch.

**Tech Stack:** Kotlin, Spring Boot, mockito-kotlin, `kotlin.test`, Maven.

**Spec:** `docs/superpowers/specs/2026-11-11-match-rule-out-reasons-design.md`

## Global Constraints

- Logs, code comments and identifiers in English (`CLAUDE.md:203`).
- Do not change weights, rule-outs, `min-score`, scoring, or co-brokerage eligibility: the same pairs must match or not match before and after.
- Reasons are not persisted, not exposed in a DTO, and never sent to agents or customers.
- `Matcher` stays pure: no logger, no I/O, no state.
- Match package coverage gate: `jacoco.threshold.line` 0.95, `jacoco.threshold.class` 0.90.
- Unit tests only (no MySQL needed): `cd modules/ndopify-server && mvn test -Dtest=MatcherTest,MatchServiceTest`.
- Conventional commits, as in `docs/superpowers/plans/2026-11-10-co-brokerage-eligibility.md`.

## Review Focus

1. A pair that fails several rules reports the rule the current predicate chain actually hits first (`PROPERTY_TYPE` before `OWN_PROPERTY`), and still does not match.
2. A co-brokerage candidate set where every listing is ruled out still logs a summary with `matched=0`.
3. An existing non-ACTIVE row reports `NOT_ACTIVE`, not `BELOW_MIN_SCORE`, and is neither updated nor deleted.
4. An ACTIVE co-brokerage row below `min-score` reports `BELOW_MIN_SCORE`, is kept, and is not deleted.
5. An exception on one pair (optimistic lock / duplicate insert) does not stop the batch nor the summary.

---

### Task 1: `Matcher` returns the rule-out reason

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchResult.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt:65-108`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt`

**Interfaces:**
- Produces:
  - `enum class MatchRuleOut { OWN_PROPERTY, LISTING_TYPE, CITY, CATEGORY, PROPERTY_TYPE, SIBLING_ROOMS, CURRENCY, PERIODICITY, OVER_BUDGET }`
  - `sealed interface MatchResult { data class Scored(val score: MatchScore) : MatchResult; data class RuledOut(val rule: MatchRuleOut, val detail: String) : MatchResult }`
  - `Matcher.evaluate(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>, today: LocalDate): MatchResult`
  - `Matcher.score(...)` keeps returning `MatchScore?`, implemented as `(evaluate(...) as? MatchResult.Scored)?.score`

- [ ] **Step 1: Write the failing tests**

In `MatcherTest.kt`, add the helper next to the existing `score` helper (`:100-101`), using the same-package
types directly:

```kotlin
private fun rule(search: MandateEntity, listing: ListingEntity): MatchRuleOut? =
    (matcher.evaluate(search, listing, distances, TODAY) as? MatchResult.RuledOut)?.rule
```

Extend each existing ruled-out test with a reason assertion, keeping its `assertNull`:

- `ruled out - over budget` (`:247-249`): `assertEquals(MatchRuleOut.OVER_BUDGET, rule(search, listingA.copy(price = 290_000)))`
- `ruled out - other listing type` (`:342-344`): `assertEquals(MatchRuleOut.LISTING_TYPE, rule(search, listingA.copy(listingType = ListingType.SALE)))`
- `ruled out - other city` (`:347-349`): `assertEquals(MatchRuleOut.CITY, rule(search, listingA.copy(cityId = 200L)))`
- `ruled out - other property category` (`:352-354`): `assertEquals(MatchRuleOut.CATEGORY, rule(search, listingA.copy(propertyCategory = PropertyCategory.COMMERCIAL)))`
- `ruled out - other currency` (`:357-359`): `assertEquals(MatchRuleOut.CURRENCY, rule(search, listingA.copy(currencyCode = "EUR")))`
- `ruled out - other price periodicity` (`:362-364`): `assertEquals(MatchRuleOut.PERIODICITY, rule(search, listingA.copy(pricePeriodicityType = PeriodicityType.WEEKLY)))`
- `ruled out - the customer's own property` (`:367-369`): `assertEquals(MatchRuleOut.OWN_PROPERTY, rule(search, ...))` with the same listing expression
- `ruled out - land` (`:172-175`): assert `PROPERTY_TYPE` for both calls
- `ruled out - housing sibling with other rooms` (`:270-276`): assert `SIBLING_ROOMS` for all three calls

Add a precedence test:

```kotlin
@Test
fun `ruled out - property type is reported before the own-property rule`() {
    val listing = listingA.copy(
        mandate = listingA.mandate.copy(customerId = search.customerId),
        propertyType = PropertyType.LAND,
    )

    assertEquals(MatchRuleOut.PROPERTY_TYPE, rule(search, listing))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: compilation fails with `Unresolved reference: MatchRuleOut` / `evaluate`.

- [ ] **Step 3: Create `MatchResult.kt`**

```kotlin
package com.wutsi.ndopify.match.server.service

/** Why [Matcher] ruled a (search mandate, listing) pair out. One rule wins: the first one the chain hits. */
enum class MatchRuleOut {
    OWN_PROPERTY,
    LISTING_TYPE,
    CITY,
    CATEGORY,
    PROPERTY_TYPE,
    SIBLING_ROOMS,
    CURRENCY,
    PERIODICITY,
    OVER_BUDGET,
}

/** The outcome of [Matcher.evaluate]: a [score][Scored], or the [rule][RuledOut] that rejected the pair. */
sealed interface MatchResult {
    data class Scored(val score: MatchScore) : MatchResult

    /** [detail] holds the deciding values as `key=value` pairs, for logs only. */
    data class RuledOut(val rule: MatchRuleOut, val detail: String) : MatchResult
}
```

- [ ] **Step 4: Refactor `Matcher.score` into `evaluate`**

Replace the body of `score` (`:69-94`) with a delegation, and add `evaluate` plus the reason-returning rule chain:

```kotlin
fun score(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>, today: LocalDate): MatchScore? =
    (evaluate(search, listing, distances, today) as? MatchResult.Scored)?.score

fun evaluate(search: MandateEntity, listing: ListingEntity, distances: Map<Long, Double>, today: LocalDate): MatchResult {
    val type = typeScore(search.propertyType, listing.propertyType)
    if (type == 0.0) {
        return MatchResult.RuledOut(
            MatchRuleOut.PROPERTY_TYPE,
            "search=${search.propertyType} listing=${listing.propertyType}",
        )
    }
    ruleOut(search, listing)?.let { return it }
    val rooms = rooms(search, listing)
    if (type < 1.0 && listing.propertyType in HOUSING && rooms < MIN_SIBLING_ROOMS) {
        return MatchResult.RuledOut(
            MatchRuleOut.SIBLING_ROOMS,
            "search=${search.rooms} listing=${listing.rooms}",
        )
    }
    return MatchResult.Scored(
        MatchScore(
            neighborhood = neighborhood(search, listing, distances),
            price = price(search, listing),
            rooms = rooms,
            amenities = amenities(search, listing),
            availability = availability(search, listing, today),
            furniture = furniture(search, listing),
            type = type,
            distanceKm = distanceKm(search, listing, distances),
            missingAmenities = requested(search, listing).filterNot { (_, has) -> has }.map { (key, _) -> key },
        ),
    )
}
```

Replace `isRuledOut` (`:96-108`) with an ordered `ruleOut` that preserves the exact same `&&` chain and order:

```kotlin
private fun ruleOut(search: MandateEntity, listing: ListingEntity): MatchResult.RuledOut? = when {
    listing.mandate.customerId == search.customerId ->
        MatchResult.RuledOut(MatchRuleOut.OWN_PROPERTY, "customer=${search.customerId}")
    listing.listingType != search.listingType ->
        MatchResult.RuledOut(MatchRuleOut.LISTING_TYPE, "search=${search.listingType} listing=${listing.listingType}")
    listing.cityId != search.cityId ->
        MatchResult.RuledOut(MatchRuleOut.CITY, "search=${search.cityId} listing=${listing.cityId}")
    categoryMismatch(search, listing) ->
        MatchResult.RuledOut(MatchRuleOut.CATEGORY, "search=${search.propertyCategory} listing=${listing.propertyCategory}")
    search.price > 0 && listing.currencyCode != search.currencyCode ->
        MatchResult.RuledOut(MatchRuleOut.CURRENCY, "search=${search.currencyCode} listing=${listing.currencyCode}")
    search.price > 0 && listing.pricePeriodicityType != search.pricePeriodicityType ->
        MatchResult.RuledOut(
            MatchRuleOut.PERIODICITY,
            "search=${search.pricePeriodicityType} listing=${listing.pricePeriodicityType}",
        )
    search.price > 0 && listing.price > search.price * (1.0 + priceTolerance) ->
        MatchResult.RuledOut(
            MatchRuleOut.OVER_BUDGET,
            "price=${listing.price} budget=${search.price} tolerance=$priceTolerance",
        )
    else -> null
}
```

Update the class KDoc (`:12-30`) to say the reason is returned rather than discarded.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchResult.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatcherTest.kt
git commit -m "feat(match): return the rule-out reason from the matcher"
```

---

### Task 2: `MatchService` logs the reason and a per-batch summary

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt:184-288`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt`

**Interfaces:**
- Consumes: `Matcher.evaluate`, `MatchResult.Scored`, `MatchResult.RuledOut`, `MatchRuleOut` (Task 1).
- Produces: `private enum class StoreOutcome { STORED, BELOW_MIN_SCORE, NOT_ACTIVE }` and
  `private fun matchAll(pairs: List<Triple<MandateEntity, ListingEntity, Map<Long, Double>>>, today: LocalDate, scope: String, phase: String): Int`.

- [ ] **Step 1: Update the existing tests to the new Matcher API**

In `MatchServiceTest.kt`, add a helper next to `score` (`:105-107`):

```kotlin
private fun scored(total: Double): MatchResult = MatchResult.Scored(score(total))
private val ruledOut = MatchResult.RuledOut(MatchRuleOut.PROPERTY_TYPE, "search=APARTMENT listing=LAND")
```

Replace every `doReturn(score(...)).whenever(matcher).score(...)` with
`doReturn(scored(...)).whenever(matcher).evaluate(...)` (25 sites, `:120-464`), every
`doReturn(null).whenever(matcher).score(...)` with `doReturn(ruledOut).whenever(matcher).evaluate(...)`
(`:219`, `:451`), and every `verify(matcher).score(...)` / `verify(matcher, never()).score(...)`
with `.evaluate(...)` (`:323`, `:336`, `:349-350`, `:368`).

Add a test pinning the co-brokerage summary path does not change storage:

```kotlin
@Test
fun `matchMandate logs the reasons of unruled candidates without storing them`() {
    doReturn(emptyList<ListingEntity>()).whenever(listingDao)
        .findByAgentIdAndStatus(AGENT_ID, ListingStatus.PUBLISHED)
    doReturn(listOf(otherListing)).whenever(listingDao)
        .findByCityIdAndListingTypeAndStatusAndAgentIdNot(CITY_ID, ListingType.RENTAL, ListingStatus.PUBLISHED, AGENT_ID)
    doReturn(ruledOut).whenever(matcher).evaluate(any(), any(), any(), any())

    assertEquals(0, service.matchMandate(MANDATE_ID))

    verify(matchDao, never()).save(any<MatchEntity>())
    verify(matchDao, never()).delete(any<MatchEntity>())
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchServiceTest`
Expected: FAIL — `MatchService` still calls `matcher.score`, so the stubs on `evaluate` return `null` from the mock and the assertions do not hold.

- [ ] **Step 3: Use `evaluate` and return an outcome from `store`**

In `MatchService.kt`, replace `matchAll` (`:269-288`) and `store` (`:291-332`) with:

```kotlin
private enum class StoreOutcome { STORED, BELOW_MIN_SCORE, NOT_ACTIVE }

private fun matchAll(
    pairs: List<Triple<MandateEntity, ListingEntity, Map<Long, Double>>>,
    today: LocalDate,
    scope: String,
    phase: String,
): Int {
    var matched = 0
    var belowMinScore = 0
    var notActive = 0
    val ruledOut = mutableMapOf<MatchRuleOut, Int>()
    var conflicts = 0
    pairs.forEach { (mandate, listing, distances) ->
        try {
            when (val result = matcher.evaluate(mandate, listing, distances, today)) {
                is MatchResult.RuledOut -> {
                    ruledOut.merge(result.rule, 1, Int::plus)
                    LOGGER.debug(
                        ">>> Mandate#${mandate.id} Listing#${listing.id} - NO_MATCH reason=${result.rule} ${result.detail}",
                    )
                }
                is MatchResult.Scored -> when (store(mandate, listing, result.score)) {
                    StoreOutcome.STORED -> {
                        matched++
                        LOGGER.info(">>> Mandate#${mandate.id} Listing#${listing.id} - MATCH score=${result.score.total}")
                    }
                    StoreOutcome.BELOW_MIN_SCORE -> {
                        belowMinScore++
                        LOGGER.info(
                            ">>> Mandate#${mandate.id} Listing#${listing.id} - " +
                                "NO_MATCH reason=BELOW_MIN_SCORE score=${result.score.total} min=$minScore",
                        )
                    }
                    StoreOutcome.NOT_ACTIVE -> {
                        notActive++
                        LOGGER.debug(">>> Mandate#${mandate.id} Listing#${listing.id} - NO_MATCH reason=NOT_ACTIVE")
                    }
                }
            }
        } catch (_: ObjectOptimisticLockingFailureException) {
            conflicts++ // The agent dismissed it meanwhile
        } catch (_: DataIntegrityViolationException) {
            conflicts++ // Another run inserted the same pair meanwhile
        }
    }
    LOGGER.info(
        ">>> $scope - MATCH_SUMMARY phase=$phase candidates=${pairs.size} matched=$matched " +
            "below_min_score=$belowMinScore not_active=$notActive ruled_out=$ruledOut",
    )
    return matched
}

// Returns the outcome of the pair: nothing is deleted when the pair isn't ACTIVE, nor for co-brokerage
private fun store(mandate: MandateEntity, listing: ListingEntity, score: MatchScore): StoreOutcome {
    val coBrokerage = listing.agent.id != mandate.agentId
    val existing = dao.findByMandateAndListing(mandate, listing)
    if (existing != null && existing.status != MatchStatus.ACTIVE) {
        return StoreOutcome.NOT_ACTIVE
    }
    if (score.total < minScore) {
        // A co-brokerage row was valid when created and its own-inventory situation is unchanged: kept (sticky)
        if (existing != null && !coBrokerage) {
            dao.delete(existing)
        }
        return StoreOutcome.BELOW_MIN_SCORE
    }

    val now = Date(clock.millis())
    val match = existing ?: MatchEntity(
        mandate = mandate,
        listing = listing,
        agentId = mandate.agentId!!,
        coBrokerage = coBrokerage,
        status = MatchStatus.ACTIVE,
        createdAt = now,
    )
    dao.save(
        match.copy(
            coBrokerage = coBrokerage,
            score = score.total,
            typeScore = score.type,
            neighborhoodScore = score.neighborhood,
            priceScore = score.price,
            roomsScore = score.rooms,
            amenitiesScore = score.amenities,
            availabilityScore = score.availability,
            furnitureScore = score.furniture,
            distanceKm = score.distanceKm,
            missingAmenities = score.missingAmenities,
            listingPrice = listing.price,
            modifiedAt = now,
        )
    )
    return StoreOutcome.STORED
}
```

`MatchRuleOut` stays limited to the `Matcher` rules; `BELOW_MIN_SCORE` and `NOT_ACTIVE` are logged as literals
because only `MatchService` decides them.

- [ ] **Step 4: Pass the scope and phase at the call sites**

- `matchMandate` own pass (`:199`): `matchAll(own.map { listing -> Triple(mandate, listing, distances) }, today, "Mandate#$mandateId", "own")`
- `matchMandate` co-brokerage pass (`:216`): `matchAll(candidates.map { listing -> Triple(mandate, listing, distances) }, today, "Mandate#$mandateId", "co-brokerage")`
- `matchListing` (`:266`): `matchAll(mandates.map { mandate -> Triple(mandate, listing, distances(mandate)) }, today, "Listing#$listingId", "listing")`

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatcherTest,MatchServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchService.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchResult.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchServiceTest.kt
git commit -m "feat(match): log why a match was ruled out"
```

---

### Task 3: Full verification and spec status

**Files:**
- Modify: `docs/superpowers/specs/2026-11-11-match-rule-out-reasons-design.md` (status only)

- [ ] **Step 1: Run the module's unit suite**

Run: `cd modules/ndopify-server && mvn test`
Expected: PASS.

- [ ] **Step 2: Run the coverage gate**

Run: `cd modules/ndopify-server && mvn verify -DskipTests=false`
Expected: PASS, `jacoco.threshold.line` 0.95 and `jacoco.threshold.class` 0.90 met. If the gate fails on `MatchResult`, add the missing rule assertion to `MatcherTest` rather than excluding the file.

- [ ] **Step 3: Update the spec status**

Change `**Status:** Proposed — awaiting approval` to `**Status:** Implemented` in the design doc.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-11-11-match-rule-out-reasons-design.md
git commit -m "docs(match): record rule-out reasons as implemented"
```
