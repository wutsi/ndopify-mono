# Visit Request Expiry Tied to the Requested Slot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Issue:** [wutsi/ndopify-mono#115 — Extend visit request expiry](https://github.com/wutsi/ndopify-mono/issues/115)

**Goal:** A visit request no longer expires 48 h after it is made regardless of the slot: it expires once it is at least
`expire-after` old **and** its requested slot has passed, so an agent has until the requested date to agree on a time.

**Architecture:** The rule is "expire at `max(createdAt + expireAfter, requestedAt)`", expressed as two `AND`ed
conditions on existing columns — no migration, no new column. `VisitService.expireDue()` uses a new derived query; the
listing and mandate stats, which count a `REQUESTED` visit as *upcoming* only while it can't have expired, apply the
same rule so they agree with the job. The web visit page stops promising "sous 48 h".

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA (derived queries + JPQL), MySQL, Thymeleaf, Maven, `kotlin.test`,
Mockito-Kotlin, `@Sql` fixtures, Jsoup.

**Spec:** the grooming of #115 (comment on the issue, 2026-11-14) — option B. Lifecycle reference:
`docs/superpowers/plans/2026-11-02-visit-management.md`.

## Global Constraints

- **Expiry rule:** a `REQUESTED` visit expires when `createdAt < now - expireAfter` **and** `requestedAt < now`. Both
  must hold. Nothing else changes: `CONFIRMED`/`CHECKED_IN` never expire.
- **`ndopify.visit.policy.expire-after` stays `48h`** and keeps its name; its meaning becomes "the minimum delay before
  a request can expire". No per-tenant setting.
- **Unchanged behaviour:** each visit expires in its own transaction, a visit scheduled meanwhile fails on `@Version` and
  is skipped, `VisitExpiredEvent` → both sides e-mailed after commit, `EXPIRED` is not an open status (the customer can
  ask again). The job cron is unchanged (hourly).
- **No migration, no backfill.** In-flight `REQUESTED` visits simply get the new rule.
- **Stats agree with the job:** a `REQUESTED` visit is *upcoming* while `createdAt > now - expireAfter` **or**
  `requestedAt > now` (the exact negation of the expiry rule).
- **Copy (French), `ndopify-web` `visit.html`:**
  - REQUESTED notice: `L'agent vous confirme la visite par e-mail : il peut vous proposer une autre heure.`
  - EXPIRED paragraph: `Votre agent n'a pas confirmé votre demande à temps : elle a expiré.`
  - No "48 h" left in user-facing visit copy. `mail/visit/expired.mjml` is already correct — leave it.
- Kotlin formatted by ktlint; assertions via `kotlin.test`; tests named in backticks.

## Review Focus

- **Late request for a near slot:** created Mon 23:00 for Tue 08:00 → still `REQUESTED` until Wed 23:00 (the 48 h
  floor wins). Pinned by the repository test row `7203` (Task 1).
- **Far slot:** created long ago, slot still ahead → not expired. Pinned by row `7202` (Task 1) and by the stats rows
  (Task 2).
- **Stats before the job runs:** a request past both limits is not *upcoming* even if still `REQUESTED` (row `7105` in
  the existing fixtures keeps covering it).
- **Concurrent schedule:** the existing `a visit changed meanwhile is skipped when expiring` test keeps covering it,
  updated to the new query.
- **No stale promise in the UI:** the REQUESTED and EXPIRED states render without "48" (Task 3).

---

### Task 1: Expire on the later of `createdAt + expireAfter` and `requestedAt`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt:84-85`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt:377-392`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitPolicyProperties.kt` (KDoc of `expireAfter`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/job/VisitJobs.kt` (class KDoc: "not scheduled in 48 h")
- Modify: `modules/ndopify-server/src/main/resources/application.yml:352,357`, `application-prod.yml:102` (comments only)
- Create: `modules/ndopify-server/src/test/resources/db/test/visit/ExpireDueVisits.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepositoryTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt:134-155`

**Interfaces:**
- Produces: `VisitRepository.findByStatusAndCreatedAtBeforeAndRequestedAtBefore(status: VisitStatus, createdBefore: Date, requestedBefore: Date): List<VisitEntity>` — replaces `findByStatusAndCreatedAtBefore`, which is removed.

- [ ] **Step 1: Write the failing repository test**

Fixture `ExpireDueVisits.sql` (loaded after `/db/test/clean.sql` and `/db/test/visit/VisitFixtures.sql`), same column
list as `ListingStatsVisits.sql`:

```sql
INSERT INTO T_VISIT(
    id, tenant_id, match_id, mandate_id, listing_id, agent_id, customer_id, status, type,
    requested_at, scheduled_at, created_at
) VALUES
    (7201, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2000-01-01 09:00:00', NULL, '2000-01-01 09:00:00'), -- both passed: due
    (7202, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2099-01-01 09:00:00', NULL, '2000-01-01 09:00:00'), -- slot ahead
    (7203, 1, 1, 1, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2000-01-01 09:00:00', NULL, NOW()),                 -- too recent
    (7204, 1, 1, 1, 101, 20, 10, 'CONFIRMED', 'ON_SITE', '2000-01-01 09:00:00', '2099-01-01 09:00:00', '2000-01-01 09:00:00');
```

In `VisitRepositoryTest` (autowire `VisitRepository dao`):

```kotlin
@Test
@Sql(value = ["/db/test/clean.sql", "/db/test/visit/VisitFixtures.sql", "/db/test/visit/ExpireDueVisits.sql"])
fun `due requests are past both the minimum delay and the requested slot`() {
    val now = System.currentTimeMillis()
    val due = dao.findByStatusAndCreatedAtBeforeAndRequestedAtBefore(
        VisitStatus.REQUESTED,
        Date(now - Duration.ofHours(48).toMillis()),
        Date(now),
    )

    assertEquals(listOf(7201L), due.map { it.id })
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRepositoryTest`
Expected: compilation FAILURE — `findByStatusAndCreatedAtBeforeAndRequestedAtBefore` unresolved.

- [ ] **Step 3: Replace the repository method**

In `VisitRepository`, replace `findByStatusAndCreatedAtBefore` by the derived query from **Interfaces**. Comment:
`// The requests old enough and whose slot passed: expired when not scheduled in time`.

- [ ] **Step 4: Update the service unit tests**

In `VisitServiceTest`, both expiry tests stub the new method; rename and pin the arguments. The test clock is
`2026-12-01T09:00:00Z`, so `createdBefore = 2026-11-29T09:00:00Z` and `requestedBefore = 2026-12-01T09:00:00Z`:

```kotlin
@Test
fun `expire the requests at least 48h old whose slot passed`() {
    val other = visit.copy(id = 2L)
    doReturn(listOf(visit, other)).whenever(dao).findByStatusAndCreatedAtBeforeAndRequestedAtBefore(
        eq(VisitStatus.REQUESTED),
        eq(Date.from(Instant.parse("2026-11-29T09:00:00Z"))),
        eq(Date.from(Instant.parse("2026-12-01T09:00:00Z"))),
    )

    assertEquals(2, service.expireDue())

    assertEquals(VisitStatus.EXPIRED, saved().status)
}
```

`a visit changed meanwhile is skipped when expiring`: same stub with `any(), any()` for the dates; assertions unchanged.

- [ ] **Step 5: Implement `VisitService.expireDue(): Int`**

Compute `now = clock.millis()` once; call the new query with `(VisitStatus.REQUESTED, Date(now - policy.expireAfter.toMillis()), Date(now))`.
The rest of the body is unchanged. KDoc: "Expires the REQUESTED visits at least [VisitPolicyProperties.expireAfter] old
**and** whose requested slot passed, …".

- [ ] **Step 6: Update the descriptions**

- `VisitPolicyProperties.expireAfter` KDoc: `/** The minimum delay before a request not scheduled can expire: it expires once also past its requested slot. */`
- `application.yml` `expire-after: 48h` comment: `# Minimum age of a request before it expires, once its requested slot has also passed`
- Cron comments (`application.yml`, `application-prod.yml`): `# Every hour: visit requests not scheduled, 48 h old and past their requested slot`
- `VisitJobs` class KDoc: replace "a request not scheduled in 48 h expires" with "a request not scheduled expires once 48 h old and past its requested slot".

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='VisitRepositoryTest,VisitServiceTest,VisitJobsTest'`
Expected: PASS. Then `grep -rn findByStatusAndCreatedAtBefore modules/` returns nothing.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(visit): expire a request once 48 h old and past its requested slot (#115)"
```

---

### Task 2: Stats count a request as upcoming until it can expire

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/dao/VisitRepository.kt:29-70` (`countUpcomingByListingIds`, `countUpcomingByMandateIds`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingStatsService.kt:26-34`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateStatsService.kt:28-36`
- Modify: `modules/ndopify-server/src/test/resources/db/test/visit/ListingStatsVisits.sql`
- Modify: `modules/ndopify-server/src/test/resources/db/test/mandate/MandateStatsEndpoint.sql`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingStatsEndpointTest.kt:23-31`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateStatsEndpointTest.kt:21`

**Interfaces:**
- Consumes: the expiry rule of Task 1 (no code dependency).
- Produces: both `countUpcomingBy…Ids` take one more trailing parameter `now: Date`; the `REQUESTED` branch of the JPQL
  becomes `(v.status = :requested AND (v.createdAt > :expireBefore OR v.requestedAt > :now))`.

- [ ] **Step 1: Add the fixture rows (an old request whose slot is ahead)**

- `ListingStatsVisits.sql`: `(7112, 1, 3, 1, 103, 20, 10, 'REQUESTED', 'ON_SITE', '2099-01-01 09:00:00', NULL, '2000-01-01 09:00:00')`
- `MandateStatsEndpoint.sql`: `(7111, 1, 8, 3, 101, 20, 10, 'REQUESTED', 'ON_SITE', '2099-01-01 09:00:00', NULL, '2000-01-01 09:00:00')`

- [ ] **Step 2: Update the assertions**

- `ListingStatsEndpointTest` `visits - upcoming, completed, stale and closed`: `assertEquals(1, stats[2].upcomingVisits) // old request, slot ahead`
- `MandateStatsEndpointTest`: `assertEquals(listOf(3, 1), stats.map { it.upcomingVisits })`

- [ ] **Step 3: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='ListingStatsEndpointTest,MandateStatsEndpointTest'`
Expected: FAIL — `expected:<1> but was:<0>` and `expected:<[3, 1]> but was:<[3, 0]>`.

- [ ] **Step 4: Implement the query and the callers**

Change both JPQL queries and signatures as in **Interfaces**. In `ListingStatsService.search` and
`MandateStatsService`, pass `Date(now)` as the new last argument (they already compute `now = clock.millis()`).

- [ ] **Step 5: Run the stats and visit tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='ListingStatsEndpointTest,MandateStatsEndpointTest,*Stats*Test,Visit*Test'`
Expected: PASS (row `7105`, old request with a past slot, is still not upcoming).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(stats): a visit request is upcoming until it can expire (#115)"
```

---

### Task 3: The web visit page no longer promises 48 h

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/visit.html:126-127,247`
- Modify: `modules/ndopify-web/src/main/html/visite.html` (the prototype: same two sentences, if present)
- Test: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt`

- [ ] **Step 1: Write the failing template tests**

```kotlin
@Test
fun `requested promises no delay`() {
    val doc = render(of(visit(VisitStatus.REQUESTED, VisitType.ON_SITE)))

    val notice = doc.selectFirst("[data-visit-state=REQUESTED] .notice")?.text()
    assertEquals("L'agent vous confirme la visite par e-mail : il peut vous proposer une autre heure.", notice)
}

@Test
fun `expired says the agent did not confirm in time`() {
    val doc = render(of(visit(VisitStatus.EXPIRED, VisitType.ON_SITE)))

    val text = doc.selectFirst("[data-visit-state=EXPIRED]")!!.text()
    assertTrue(text.contains("Votre agent n'a pas confirmé votre demande à temps : elle a expiré."))
    assertFalse(text.contains("48"))
}
```

Jsoup's `text()` turns `&nbsp;` into U+00A0: write the template with plain spaces around `:` or normalise in the
assertion (`.replace(' ', ' ')`), consistently with the existing tests of this class.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest=VisitTemplateTest`
Expected: FAIL on both (current copy says "sous 48 h").

- [ ] **Step 3: Change the copy**

Replace the two sentences in `visit.html` (and in `src/main/html/visite.html` when it carries them) with the exact copy
from **Global Constraints**.

- [ ] **Step 4: Run the web tests to verify they pass**

Run: `cd modules/ndopify-web && mvn test -Dtest='Visit*Test'`
Expected: PASS. `grep -rn "48&nbsp;h\|48 h" modules/ndopify-web/src/main` returns nothing.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web
git commit -m "feat(web): the visit page no longer promises a 48 h confirmation (#115)"
```
