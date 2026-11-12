# Mandate Assignment Rule-Out Reasons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make mandate dispatch log *why* no offer was made and *why each agent was passed over*, plus a per-dispatch and per-batch summary, without changing who is assigned.

**Architecture:** `MandateDispatcher` returns an internal `DispatchResult` (`Offered` or `Skipped(reason)`) plus `DispatchDiagnostics` (candidate count, excluded counts, scored candidates). `dispatch(): AssignmentEntity?` stays as a wrapper so existing callers keep working. `AssignmentService` aggregates the results into batch summaries, and `MandateJobs` installs a `DefaultKVLogger` on the scheduler thread.

**Tech Stack:** Kotlin, Spring Boot, slf4j, mockito-kotlin, `kotlin.test`, Maven.

**Spec:** `docs/superpowers/specs/2026-11-11-mandate-assignment-rule-out-reasons-design.md`

## Global Constraints

- Logs, code comments and identifiers in English (`CLAUDE.md`).
- Do not change the filters, their order, the weights, the jitter draw, or the selected agent.
- Reasons are not persisted, not exposed in a DTO, and never sent to agents or customers.
- `dispatch()` keeps its `AssignmentEntity?` signature; existing tests keep compiling.
- Unit tests only: `cd modules/ndopify-server && mvn test -Dtest=...`.
- Coverage gate: `jacoco.threshold.line` 0.95, `jacoco.threshold.class` 0.90.
- Conventional commits.

## Review Focus

1. An agent at `max-load` **and** at `max-pending` reports `AT_CAPACITY`, not `MAX_PENDING` (the current filter order).
2. An agent who declined **and** is the customer's party reports `DECLINED_OR_EXPIRED`, not `SELF`.
3. `dispatchPending` still starts one transaction per mandate when it calls `dispatchResult` bean-to-bean.
4. A single candidate is offered with no `runner_up` in the log, and still receives the same score.
5. `MandateJobs` removes the `KVLogger` thread-local on both success and failure.

---

### Task 1: `DispatchResult` + dispatcher reasons

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/DispatchResult.kt`
- Modify: `.../mandate/server/service/MandateDispatcher.kt`
- Test: `.../mandate/server/service/MandateDispatcherTest.kt`

**Interfaces:**
- Produces:
  - `enum class DispatchReason { MANDATE_NOT_FOUND, NOT_PENDING, ALREADY_PENDING, OVERDUE_MANUAL, NIGHT, NO_CANDIDATE }`
  - `enum class CandidateRuleOut { SELF, DECLINED_OR_EXPIRED, AT_CAPACITY, MAX_PENDING }`
  - `data class CandidateScore(agent, total, neighborhood, fairness, rating, responsiveness, capacity, jitter)`
  - `data class DispatchDiagnostics(candidateCount, excluded, eligible)`
  - `sealed interface DispatchResult { Offered(assignment, diagnostics); Skipped(reason, detail, diagnostics?) }`
  - `val DispatchResult.assignmentOrNull: AssignmentEntity?`
  - `internal fun MandateDispatcher.dispatchResult(mandateId: Long): DispatchResult`
  - `MandateDispatcher.dispatch(mandateId): AssignmentEntity?` delegates to `dispatchResult(...).assignmentOrNull`

- [ ] **Step 1: Write the failing reason tests**

Add to `MandateDispatcherTest`:

```kotlin
private fun result(id: Long = MANDATE_ID) = dispatcher().dispatchResult(id)
private fun skipped(id: Long = MANDATE_ID): DispatchReason =
    (result(id) as DispatchResult.Skipped).reason
```

Extend the existing "nothing to dispatch" tests with the reason, and add candidate-reason assertions:

- unknown mandate → `MANDATE_NOT_FOUND`
- `ASSIGNED` / `MANUAL_ASSIGNMENT` / `CANCELLED` → `NOT_PENDING`
- existing PENDING offer → `ALREADY_PENDING`
- overdue → `OVERDUE_MANUAL` and the mandate still moves to `MANUAL_ASSIGNMENT`
- night → `NIGHT`
- no candidate → `NO_CANDIDATE`
- offer → `DispatchResult.Offered`, and `diagnostics.excluded[AT_CAPACITY] == 1` when one of two agents is loaded
- declined + self → `diagnostics.excluded[DECLINED_OR_EXPIRED] == 1`, `SELF` absent
- at capacity + max pending → `AT_CAPACITY` wins

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDispatcherTest`
Expected: compilation fails on `dispatchResult` / `DispatchReason`.

- [ ] **Step 3: Create `DispatchResult.kt`**

Implement the interfaces above. `assignmentOrNull` is an extension property.

- [ ] **Step 4: Refactor `MandateDispatcher`**

- `dispatch` → `@Transactional fun dispatch(id) = dispatchResult(id).assignmentOrNull`
- `dispatchResult` → `@Transactional internal fun` holding the current body; each early `return null` becomes
  `return skip(id, reason, detail)`.
- `skip` adds `dispatch_reason` to the KV logger, logs DEBUG for `MANDATE_NOT_FOUND` / `NOT_PENDING` /
  `ALREADY_PENDING`, INFO otherwise, and returns `Skipped`.
- `findCandidates` returns a private `CandidatePool(eligible, excluded, searched)`, logging each
  `DECLINED_OR_EXPIRED` / `SELF` at DEBUG. Preserve `excludeIds` before `SELF`.
- `score` takes the excluded map / returns `List<CandidateScore>`; filter order stays capacity then pending.
  Log `CANDIDATE` at DEBUG with the breakdown.
- After scoring, log `DISPATCH_SUMMARY` and, on a winner, `OFFER` with runner-up and margin.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDispatcherTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/DispatchResult.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDispatcher.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDispatcherTest.kt
git commit -m "feat(mandate): log why an agent was not assigned"
```

---

### Task 2: Batch summaries + job-thread logger

**Files:**
- Modify: `.../mandate/server/service/AssignmentService.kt`
- Modify: `.../mandate/server/job/MandateJobs.kt`
- Test: `.../mandate/server/service/AssignmentServiceTest.kt`, `.../mandate/server/job/MandateJobsTest.kt`

**Interfaces:**
- Consumes: `DispatchResult`, `DispatchReason`, `dispatchResult` (Task 1).
- Produces: no public API change; `dispatchPending(): Int` and `expireDue(): Int` keep their signatures.

- [ ] **Step 1: Write the failing tests**

`AssignmentServiceTest`:
- `dispatchPending` still returns the number of offers when the dispatcher returns `Offered`, and 0 when every
  result is `Skipped`; one failing mandate does not stop the others (already covered — extend it to the
  `dispatchResult` stub).
- `expireDue` still returns the number expired when one save throws.

`MandateJobsTest`:
- after `jobs.dispatch()`, `KVLoggerThreadLocal.get()` is `null` (both success and failure).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentServiceTest,MandateJobsTest`
Expected: compilation fails on the new stubs / `KVLoggerThreadLocal` assertions.

- [ ] **Step 3: Implement the batch summaries**

`dispatchPending`: stub/consume `dispatcher.dispatchResult(id)`, count `Offered`, merge `Skipped.reason`, count
failures, log `ASSIGN_BATCH`, return the offered count.

`expireDue`: count failures, log `ASSIGN_EXPIRE_SUMMARY`, return the expired count.

- [ ] **Step 4: Install the job-thread logger**

`MandateJobs.forEachTenant`: create a `DefaultKVLogger`, set it in `KVLoggerThreadLocal`, add `job` / `tenant_id` /
`count`, flush and remove in `finally`, and `setException` on failure.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentServiceTest,MandateJobsTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/job/MandateJobs.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/job/MandateJobsTest.kt
git commit -m "feat(mandate): summarize assignment batches and log them from jobs"
```

---

### Task 3: Full verification and spec status

**Files:**
- Modify: `docs/superpowers/specs/2026-11-11-mandate-assignment-rule-out-reasons-design.md` (status only)

- [ ] **Step 1: Run the module's unit suite**

Run: `cd modules/ndopify-server && mvn test`
Expected: PASS.

- [ ] **Step 2: Run the coverage gate**

Run: `cd modules/ndopify-server && mvn verify -DskipTests=false`
Expected: PASS at 0.95 line / 0.90 class.

- [ ] **Step 3: Update the spec status**

Change `**Status:** Proposed` to `**Status:** Implemented`.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-11-11-mandate-assignment-rule-out-reasons-design.md
git commit -m "docs(mandate): record assignment rule-out reasons as implemented"
```
