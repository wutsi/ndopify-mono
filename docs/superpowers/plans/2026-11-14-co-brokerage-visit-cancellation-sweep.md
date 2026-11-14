# Co-Brokerage Visits — Async Cancellation + Catch-Up Sweep

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the visit cancellation that follows a mandate closing/cancelling asynchronous, and add an hourly catch-up job that cancels any open visits the async path missed.

**Architecture:** `VisitMandateEventListener` becomes an `@Async` `@TransactionalEventListener(AFTER_COMMIT)` (same shape as `VisitEventListener`): it sets `TenantContext`, logs, and never rethrows, so a visit failure can no longer fail or slow the mandate transaction. `VisitService` cancels one visit per transaction through the existing `update()` helper (same pattern as `expireDue`/`markAgentNoShows`). `VisitJobs.cancelEndedMandates` sweeps up to 100 visits per tenant per run whose search mandate, or whose listing's mandate, is `CLOSED`/`CANCELLED`.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (Hibernate multi-tenancy), `@Async`/`@TransactionalEventListener`, scheduled jobs, MySQL integration tests.

**Spec:** `docs/superpowers/specs/2026-11-14-co-brokerage-visits-design.md` (see the revision section this plan adds)

## Global Constraints

- Cancellation stays `VisitParty.SYSTEM`, `cancelled_at = now`, `cancellation_reason = null`; `CHECKED_IN` visits are never cancelled.
- Both sides of a cancelled visit are e-mailed with the neutral « La visite est annulée. »; B is e-mailed too when the visit was confirmed (`scheduledAt != null`) and organized by another agent.
- A failure in the visit domain must never roll back the mandate closing: the async listener catches and logs; the sweep retries.
- The sweep is bounded: 100 visits per tenant per run, ordered by visit id, hourly.
- A single bad visit (optimistic lock, unexpected error) is skipped, never aborting the batch.
- Every read and mail runs with `TenantContext`; `T_VISIT` is `@TenantId`-scoped.

## Review Focus

1. **A mandate closes while its cancellation task fails or the process dies** — the visits must be cancelled by the next sweep, with the neutral e-mails, and `CHECKED_IN` must survive.
2. **A visit changes between the sweep read and the write** (ex: checked in concurrently) — the write must be skipped, not overwrite the newer state.
3. **The first sweep run after deploy** — stale open visits from already-ended mandates are cancelled and their cancellation e-mails are sent; this is accepted back-fill.
4. **The listing side** — a visit organized by another agent on a listing whose listing mandate ended must be cancelled by the sweep even when its search mandate is still `APPROVED`.
5. **A large backlog** — the sweep processes at most 100 per run, so a burst drains over several hourly runs without a mail storm in one transaction.

---

### Task 1: Asynchronous listener + per-visit cancellation

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitMandateEventListener.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt:616-642`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CancelMandateVisitsEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/event/VisitMandateEventListenerTest.kt`

**Interfaces:**
- Produces: `VisitService.cancelForEndedMandate(mandateId: Long): Int` keeps its signature but is no longer batch-transactional.
- Produces: `VisitMandateEventListener.onMandateClosed(event: MandateClosedEvent)` is async, after commit.

- [ ] **Step 1: Make the endpoint test wait for the async task**

Add `awaitAsyncTasks()` immediately after the `rest.postForEntity(...)` in both tests of `CancelMandateVisitsEndpointTest`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn -q -Dtest=CancelMandateVisitsEndpointTest test`
Expected: FAIL — the listener is still synchronous, so this passes today; instead run it after Step 3 to see it pass. (This step is a placeholder for the test-first cycle; the real failing test is the listener test in Step 4.)

- [ ] **Step 3: Convert the listener**

Replace `@EventListener` with `@Async @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`; wrap the call in the `DefaultKVLogger`/`KVLoggerThreadLocal`/`TenantContext` block used by `VisitEventListener`, logging `event`, `tenant_id`, `mandate_id`, `count`, `success`, and swallowing exceptions.

- [ ] **Step 4: Add the listener unit test**

`VisitMandateEventListenerTest` with a mocked `VisitService`: `cancelForEndedMandate` throws `RuntimeException`; the listener call must not throw, and `TenantContext` must be removed afterwards.

- [ ] **Step 5: Make cancellation per-visit**

In `VisitService`, replace the `@Transactional` `cancelForEndedMandate` body with `cancel(dao.findOpenByMandateOrListingMandate(...))`; add `private fun cancel(visits)` that iterates with `update(visit) { ... }` (one transaction per visit) and publishes one `VisitCancelledEvent` per saved visit.

- [ ] **Step 6: Run the tests**

Run: `cd modules/ndopify-server && mvn -q -Dtest=CancelMandateVisitsEndpointTest,VisitServiceTest,VisitMandateEventListenerTest test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitMandateEventListener.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CancelMandateVisitsEndpointTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/event/VisitMandateEventListenerTest.kt
git commit -m "fix(visit): cancel visits asynchronously after a mandate ends"
```

---

### Task 2: Repository query + service sweep

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/visit/EndedMandates.sql`

**Interfaces:**
- Produces: `VisitRepository.findOpenByEndedMandate(visitStatuses: Collection<VisitStatus>, mandateStatuses: Collection<MandateStatus>, page: Pageable): List<VisitEntity>`
- Produces: `VisitService.cancelEndedMandateVisits(limit: Int): Int`

- [ ] **Step 1: Add the fixture**

`/db/test/visit/EndedMandates.sql`:

```sql
UPDATE T_MANDATE SET status = 'CANCELLED' WHERE id IN (1, 4);
UPDATE T_MANDATE SET status = 'CLOSED' WHERE id = 11;
```

- [ ] **Step 2: Write the failing repository test**

In `VisitRepositoryTest`, with `clean.sql` + `VisitFixtures.sql` + `CoBrokerageVisitEndpoint.sql` + `EndedMandates.sql`, assert `findOpenByEndedMandate(REQUESTED/CONFIRMED, CLOSED/CANCELLED, OffsetLimitPageRequest(0, 100, Sort.by("id")))` returns `{1, 3, 5, 6}` — search side (mandate 4: 1, 3, 6), listing side (listing 101: 1, 3, 5), `CHECKED_IN` 2 and `COMPLETED` 4 excluded.

- [ ] **Step 3: Add the service sweep test**

In `VisitServiceTest`: stub `dao.findOpenByEndedMandate(any(), any(), any())` with a requested and a confirmed visit; assert `cancelEndedMandateVisits(100) == 2`, both saved `CANCELLED`/`SYSTEM`, two events.

- [ ] **Step 4: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn -q -Dtest=VisitRepositoryTest,VisitServiceTest test`
Expected: FAIL — `findOpenByEndedMandate` / `cancelEndedMandateVisits` do not exist.

- [ ] **Step 5: Implement the query**

JPQL: open visits where `v.mandateId IN (ended mandates)` OR `v.listingId IN (listings whose mandate is ended)`, no `ORDER BY` in the query (the `Pageable` sort supplies it).

- [ ] **Step 6: Implement the sweep**

`cancelEndedMandateVisits(limit)` reads `OffsetLimitPageRequest(0, limit, Sort.by(Sort.Direction.ASC, "id"))` and delegates to the same private `cancel(visits)`.

- [ ] **Step 7: Run the tests**

Run: `cd modules/ndopify-server && mvn -q -Dtest=VisitRepositoryTest,VisitServiceTest test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt \
    modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt \
    modules/ndopify-server/src/test/resources/db/test/visit/EndedMandates.sql
git commit -m "feat(visit): find the open visits of ended mandates"
```

---

### Task 3: Scheduled catch-up job + configuration + docs

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/job/VisitJobs.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml`
- Modify: `modules/ndopify-server/src/main/resources/application-prod.yml`
- Modify: `modules/ndopify-server/CLAUDE.md`
- Modify: `docs/superpowers/specs/2026-11-14-co-brokerage-visits-design.md`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/job/CancelEndedMandateVisitsJobTest.kt`

**Interfaces:**
- Consumes: `VisitService.cancelEndedMandateVisits(limit: Int): Int`
- Produces: `VisitJobs.cancelEndedMandates()` on `ndopify.visit.cancel-ended-mandates.cron`

- [ ] **Step 1: Write the failing job test**

`CancelEndedMandateVisitsJobTest` with `clean.sql` + `CancelMandateVisitsEndpoint.sql` + `EndedMandates.sql`: after `jobs.cancelEndedMandates()`, visits `1` and `3` (search side / listing side) and `4` (listing side) are `CANCELLED`, `2` stays `CHECKED_IN`; after `awaitAsyncTasks()`, a « la visite de votre bien est annulée » e-mail was sent.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn -q -Dtest=CancelEndedMandateVisitsJobTest test`
Expected: FAIL — `cancelEndedMandates` does not exist.

- [ ] **Step 3: Add the job**

`VisitJobs.cancelEndedMandates()` calls `forEachTenant("cancelEndedMandates", "visit(s)") { service.cancelEndedMandateVisits(CANCEL_LIMIT) }` with `private const val CANCEL_LIMIT = 100` and the cron property.

- [ ] **Step 4: Add the crons**

`application.yml`: `cancel-ended-mandates.cron: "0 30 * * * ?"` (hourly, off the :00 expire and :15 reminder/no-show runs). `application-prod.yml`: `"0 30 */1 * * ?"`.

- [ ] **Step 5: Update the docs**

`ndopify-server/CLAUDE.md`: the listener is async/after-commit and `VisitJobs.cancelEndedMandates` catches up, including the accepted first-run back-fill. The spec gets a dated revision section stating the synchronous guarantee is replaced by async + sweep.

- [ ] **Step 6: Run the focused suites**

Run: `cd modules/ndopify-server && mvn -q -Dtest=CancelEndedMandateVisitsJobTest,VisitJobsTest,CancelMandateVisitsEndpointTest,VisitRepositoryTest,VisitServiceTest,VisitMandateEventListenerTest test`
Expected: PASS.

- [ ] **Step 7: Commit and push**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/job/VisitJobs.kt \
    modules/ndopify-server/src/main/resources/application.yml \
    modules/ndopify-server/src/main/resources/application-prod.yml \
    modules/ndopify-server/CLAUDE.md \
    modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/job/CancelEndedMandateVisitsJobTest.kt \
    docs/superpowers/specs/2026-11-14-co-brokerage-visits-design.md
git commit -m "feat(visit): sweep the visits missed by the async mandate close"
git push origin HEAD:feat/npc-co-brokerage-visits
```
