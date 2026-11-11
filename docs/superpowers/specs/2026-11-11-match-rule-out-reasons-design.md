# Match Rule-Out Reasons — Design

**Status:** Proposed — awaiting approval

**Scope:** logs / ops only. The reason is not persisted, not exposed through the API, and never shown to
agents or customers.

## Intent

`MatchService.matchAll` logs a `NO_MATCH` line that only carries the score (`MatchService.kt:279`). When a
pair is ruled out, `Matcher.score` has already collapsed the cause into `null`, so production logs say *that*
a listing didn't match, never *why*. Ops cannot tell a wrong city from an over-budget listing, a wrong property
type, a score under `ndopify.match.min-score`, or a row the agent already dismissed.

## Goals

- Every `NO_MATCH` log carries a stable, machine-readable reason and the deciding values.
- One `INFO` summary per matching batch with reason counts.
- No change to which pairs match, to their score, or to their stored state.
- No schema, DTO or API change.

## Non-goals

- Explaining non-matches to agents or customers.
- Persisting the reason on `T_MATCH`.
- Changing weights, rule-outs, `min-score`, or co-brokerage eligibility.
- Per-pair `INFO` logging (too much volume on co-brokerage candidate sets).

## Approaches considered

- **A — chosen: `Matcher` returns a result.** `MatchResult` is either `Scored(score)` or
  `RuledOut(rule, detail)`; `MatchService` logs it. The rules are evaluated once and stay a single source of
  truth. Cost: the ~25 `matcher.score(...)` stubs in `MatchServiceTest` become `evaluate(...)`.
- **B — keep `score()`, add `explain()`.** No test churn, but the rules are evaluated twice on failure (the
  common case) or two public methods must be kept in sync.
- **C — log inside `Matcher`.** Rejected: makes a pure scorer impure, cannot cover the `min-score` and
  `NOT_ACTIVE` cases decided in `MatchService`, and is harder to test.

## Design

### `Matcher`

```kotlin
enum class MatchRuleOut {
    OWN_PROPERTY, LISTING_TYPE, CITY, CATEGORY, PROPERTY_TYPE,
    SIBLING_ROOMS, CURRENCY, PERIODICITY, OVER_BUDGET,
}

sealed interface MatchResult {
    data class Scored(val score: MatchScore) : MatchResult
    data class RuledOut(val rule: MatchRuleOut, val detail: String) : MatchResult
}

fun evaluate(search, listing, distances, today): MatchResult
```

- `score()` stays as a one-line convenience returning `(evaluate(...) as? Scored)?.score`, so the existing
  `MatcherTest` assertions keep working; new tests assert the rule.
- `isRuledOut` becomes an ordered `ruleOut()` returning the first failing `RuledOut`, with a `detail` such as
  `price=265000 budget=250000 tolerance=0.10` or `search=APARTMENT listing=LAND`.
- The current predicate order is preserved, so the match / no-match decision cannot change. Note that today
  `typeScore == 0.0` is checked before `isRuledOut` (`Matcher.kt:75-78`): the reported rule follows that
  effective order, so `PROPERTY_TYPE` wins over a simultaneous own-property or listing-type conflict.
- `SIBLING_ROOMS` covers the `MIN_SIBLING_ROOMS` rule (`Matcher.kt:79-82`).
- No logger, no I/O, no state.

### `MatchService`

- `matchAll` switches on `evaluate(...)`:
  - `RuledOut` → `LOGGER.debug` with `reason` and `detail`;
  - `Scored` → `store(...)`.
- `store` returns a `StoreOutcome`: `STORED`, `BELOW_MIN_SCORE`, `NOT_ACTIVE`.
  - `BELOW_MIN_SCORE` → `LOGGER.info`, with the score and the threshold: this is the actionable near miss.
  - `NOT_ACTIVE` → `LOGGER.debug`: expected when the agent dismissed or shortlisted the row.
- `matchAll` accumulates `Map<MatchRuleOut, Int>` plus matched / below-min / not-active counts and logs one
  summary line at the end. `matchMandate` and `matchListing` pass a phase label (`own`, `co-brokerage`,
  `listing`).

Example output:

```
>>> Mandate#1 Listing#42 - NO_MATCH reason=OVER_BUDGET price=265000 budget=250000 tolerance=0.10
>>> Mandate#1 Listing#42 - NO_MATCH reason=BELOW_MIN_SCORE score=0.42 min=0.50
>>> Mandate#1 - MATCH_SUMMARY phase=co-brokerage candidates=40 matched=2 below_min=3 not_active=1 ruled_out={OVER_BUDGET=18, PROPERTY_TYPE=16}
```

### Log levels

- Per-pair rule-outs at `DEBUG`, so raising the match package to DEBUG gives the full trace.
- `BELOW_MIN_SCORE` and the summary at `INFO`, so production always shows why a search ended with nothing
  without flooding the log.

## Files

- Modify `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/Matcher.kt`
- Create `.../service/MatchRuleOut.kt` (enum, and `MatchResult` if kept top-level)
- Modify `.../service/MatchService.kt`
- Modify `MatcherTest.kt` and `MatchServiceTest.kt`

## Risks

- The ~25 `Matcher` stubs in `MatchServiceTest` change from `score(...)` to `evaluate(...)`: mechanical.
- Co-brokerage candidate sets make per-pair `DEBUG` lines numerous; mitigated by the level and the summary.
- Reason ordering must not alter the null / non-null decision; `MatcherTest` stays the guard.

## Left to the builder

- `detail` as a formatted `String` (chosen: readable in logs) or a `Map<String, Any?>`.
- Whether the summary is logged inside `matchAll` (needs the phase label) or by each caller.
