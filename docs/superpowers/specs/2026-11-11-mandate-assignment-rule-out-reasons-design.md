# Mandate Assignment Rule-Out Reasons — Design

**Status:** Implemented

**Scope:** logs / ops only. Nothing is persisted, exposed through the API, or shown to agents or customers.

## Intent

`MandateDispatcher.dispatch` logs a handful of KV fields (`mandate_status`, `is_night`, `no_candidate`,
`agent_id`), but when no offer is made it never says *which* rule stopped it, and when an offer is made it never
says *why that agent* beat the others. The candidate filters collapse every exclusion into a shorter list:
ops cannot tell a saturated city (`max-load`) from a flooded one (`max-pending`), from agents who already
declined, from the customer's own party. This mirrors the gap fixed for match rule-outs in
`2026-11-11-match-rule-out-reasons-design.md`.

A second gap: the injected `KVLogger` is a `DynamicKVLogger`, which resolves to `null` when there is no HTTP
request and no `KVLoggerThreadLocal`. `AssignmentEventListener` and the ops endpoint set one; `MandateJobs`
does not, so the fields added during scheduled dispatch are currently dropped.

## Goals

- Every dispatch that makes no offer carries a stable reason (`DispatchReason`).
- Every passed-over agent carries a stable reason (`CandidateRuleOut`), with the deciding values.
- The winning agent logs its score breakdown and the runner-up.
- One `DISPATCH_SUMMARY` per dispatch, one `ASSIGN_BATCH` per `dispatchPending` run, one
  `ASSIGN_EXPIRE_SUMMARY` per `expireDue` run.
- The agent selected, the score stored, the assignment saved and the events published are unchanged.
- No schema, DTO or API change.

## Non-goals

- Explaining assignments to agents or customers.
- Persisting the reasons.
- Changing the filters, the weights, or the order in which they are applied.
- Per-candidate `INFO` logging (too much volume in a city with 100 candidates).

## Design

### `DispatchResult.kt`

```kotlin
enum class DispatchReason { MANDATE_NOT_FOUND, NOT_PENDING, ALREADY_PENDING, OVERDUE_MANUAL, NIGHT, NO_CANDIDATE }

enum class CandidateRuleOut { SELF, DECLINED_OR_EXPIRED, AT_CAPACITY, MAX_PENDING }

data class CandidateScore(
    val agent: AgentEntity,
    val total: Double,
    val neighborhood: Double,
    val fairness: Double,
    val rating: Double,
    val responsiveness: Double,
    val capacity: Double,
    val jitter: Double,
)

data class DispatchDiagnostics(
    val candidateCount: Int,                       // merged, before the filters
    val excluded: Map<CandidateRuleOut, Int>,
    val eligible: List<CandidateScore>,
)

sealed interface DispatchResult {
    data class Offered(val assignment: AssignmentEntity, val diagnostics: DispatchDiagnostics) : DispatchResult
    data class Skipped(
        val reason: DispatchReason,
        val detail: String = "",
        val diagnostics: DispatchDiagnostics? = null,
    ) : DispatchResult
}

val DispatchResult.assignmentOrNull: AssignmentEntity?
    get() = (this as? DispatchResult.Offered)?.assignment
```

`dispatch(mandateId): AssignmentEntity?` stays as a one-line wrapper over a new
`internal fun dispatchResult(mandateId): DispatchResult`, exactly like `Matcher.score()` over `evaluate()`, so
existing callers and the ~50 existing test calls are untouched. Both are `@Transactional`: the production path
enters through `dispatch`, while `AssignmentService.dispatchPending` calls `dispatchResult` through the proxy and
keeps its per-mandate transaction.

### `MandateDispatcher`

- The early exits return `Skipped`:
  - not found → `MANDATE_NOT_FOUND` (DEBUG);
  - status ≠ `PENDING_ASSIGNMENT` → `NOT_PENDING` with `mandate_status=…` (DEBUG);
  - a PENDING offer exists → `ALREADY_PENDING` (DEBUG);
  - past `manual-after-days` → `OVERDUE_MANUAL` (INFO, after moving the mandate);
  - night → `NIGHT` with `timezone=…` (INFO);
  - no eligible candidate → `NO_CANDIDATE` with the diagnostics (INFO).
  The existing KV fields are kept, plus `dispatch_reason`.
- `findCandidates` returns a `CandidatePool(eligible, excluded, searched)`:
  - `DECLINED_OR_EXPIRED` is checked before `SELF`, matching the current single `&&` filter;
  - each rejected agent is logged at DEBUG.
- `score` returns `List<CandidateScore>` and records `AT_CAPACITY` (checked first) then `MAX_PENDING` in the
  excluded map. The arithmetic, the filter order and the `jitter` draw are unchanged.
- The winner logs `OFFER` at INFO with `agent_id`, `score`, `runner_up` and `margin`; the runner-up is the
  highest-scoring remaining candidate, omitted when there is only one.
- One `DISPATCH_SUMMARY` at INFO after scoring:

```
>>> Mandate#1 Agent#20 - REJECTED reason=AT_CAPACITY load=5 max=5
>>> Mandate#1 Agent#30 - CANDIDATE score=0.875 neighborhood=1.000 fairness=1.000 rating=0.500 responsiveness=0.500 capacity=1.000 jitter=0.000
>>> Mandate#1 - OFFER agent_id=30 score=0.875 runner_up=20/0.650 margin=0.225
>>> Mandate#1 - DISPATCH_SUMMARY candidates=3 excluded={SELF=1, AT_CAPACITY=1} eligible=1 offered=true agent_id=30
>>> Mandate#2 - NO_OFFER reason=NIGHT timezone=Africa/Douala
```

### `AssignmentService`

- `dispatchPending` switches on `DispatchResult`, counts offered / skipped-by-reason / failed, and logs one
  `ASSIGN_BATCH` at INFO.
- `expireDue` counts failures and logs one `ASSIGN_EXPIRE_SUMMARY` at INFO.

### `MandateJobs`

Each tenant is processed with a `DefaultKVLogger` in `KVLoggerThreadLocal` (set, flushed, removed in `finally`),
like the assignment listeners. This restores the dispatcher's KV fields on scheduled runs.

### Log levels

- Candidate rejections, candidate scores and normal races (`NOT_PENDING`, `ALREADY_PENDING`,
  `MANDATE_NOT_FOUND`) at DEBUG, so raising the package to DEBUG gives the full trace.
- `OVERDUE_MANUAL`, `NIGHT`, `NO_CANDIDATE`, `OFFER`, `DISPATCH_SUMMARY` and the batch summaries at INFO.

## Files

- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/DispatchResult.kt`
- Modify: `.../mandate/server/service/MandateDispatcher.kt`
- Modify: `.../mandate/server/service/AssignmentService.kt`
- Modify: `.../mandate/server/job/MandateJobs.kt`
- Test: `.../mandate/server/service/MandateDispatcherTest.kt`, `AssignmentServiceTest.kt`, `MandateJobsTest.kt`

## Risks

- The filter order must not change: `AT_CAPACITY` is evaluated before `MAX_PENDING`, and `DECLINED_OR_EXPIRED`
  before `SELF`, or the logged reason would differ from the decision.
- `dispatchResult` must stay transactional per mandate when called from `dispatchPending` (a bean-to-bean call),
  otherwise the pessimistic lock and the save lose their transaction.
- The `DefaultKVLogger` on the job thread must be removed in `finally`: executor threads are reused.

## Left to the builder

- Number formatting of the scores in logs (raw doubles, as `MatchService` does).
- Whether `DISPATCH_SUMMARY` is emitted for the early exits (chosen: no, the `NO_OFFER` line carries the reason).
