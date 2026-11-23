# De-flake UpdateAgentEndpointTest Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `UpdateAgentEndpointTest.update` pass at any wall-clock instant by pinning the agent fixture's timestamps, so `ndopify-server-main` stops failing on a race between the MySQL and JVM clocks.

**Architecture:** Test-only change in `ndopify-server`. The SQL fixture that seeds agents 100–102 gets explicit `created_at` / `modified_at` values (the `T_PARTY` fixture's `'2024-06-10 00:00:00'`) instead of leaving them to the column defaults, and the test asserts the stored `createdAt` is exactly that instant. No production, schema, DTO or config change.

**Tech Stack:** Kotlin, Spring Boot `@SpringBootTest` endpoint test over `TestRestTemplate` and MySQL, `@Sql` fixtures, `kotlin.test`, `mockito-kotlin`. Run from `modules/ndopify-server`.

**Spec:** GitHub issue #270 — the story body is the design artifact (context, acceptance criteria, assumptions A1–A4).

## Global Constraints

- `modules/ndopify-server` test sources and fixtures only. No production code, no Flyway migration, no `ndopify-dto`, no `ndopify-web-core`, no prototype, no UI, no root `pom.xml` change.
- Only `UpdateAgentEndpointTest.kt` and `db/test/agent/UpdateAgentEndpoint.sql` change. No other test class' fixture or assertions.
- The pinned instant is exactly `'2024-06-10 00:00:00'` in the fixture, asserted as `Date.from(Instant.parse("2024-06-10T00:00:00Z"))` — the datasource connects with `serverTimezone=UTC` (`application.yml:37`), the same pattern `SearchContractEndpointTest` and `GetContractEndpointTest` already use.
- Assertions use `kotlin.test` (`assertEquals`, `assertTrue`), never `org.junit.jupiter.api.Assertions.*`.
- `Thread.sleep(100)` leaves `update`: it never separated the two timestamps (both came from the same captured `now`) and the pinned fixture makes it moot.
- Endpoint tests need the local MySQL the module's tests already require.
- Commit message: `test(agent): de-flake UpdateAgentEndpointTest timestamps`, body `Refs #270`.

## Review Focus

1. **The assertion must still catch the regression.** If `AgentService.update` copies the update instant into `createdAt`, the exact equality against the 2024 instant must fail. Do not replace it with a `createdAt < now` or a mere presence check.
2. **The expected instant is UTC.** `Date.from(Instant.parse("2024-06-10T00:00:00Z"))` matches the DATETIME read under `serverTimezone=UTC`; parsing the fixture date in the JVM's default zone would fail on a non-UTC machine.
3. **The other four tests of the class.** Agents 101 and 102 are seeded by the same statement and gain the same dates; their version / biography / neighbourhood assertions must stay green, and the class' `version = 0` fixtures must keep version 0.
4. **Wall-clock independence.** Nothing left in `update` may compare a JVM instant captured before `@Sql` with a database-defaulted timestamp — including `modifiedAt >= createdAt`, whose left side is the mocked clock's `now` and whose right side is now the 2024 fixture.
5. **A single green run proves nothing.** The class must be run repeatedly (Step 6) after the change; a one-off `mvn test` cannot evidence a race fix.

---

### Task 1: Pin the agent fixture's timestamps and assert them exactly

**Files:**
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/UpdateAgentEndpointTest.kt:45-78`
- Modify: `modules/ndopify-server/src/test/resources/db/test/agent/UpdateAgentEndpoint.sql:22-25`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/UpdateAgentEndpointTest.kt`

**Interfaces:**
- Consumes: `POST /v1/agents/{id}` (existing, `UpdateAgentRequest`), `AgentRepository.findById` (existing), `AgentEntity.createdAt` / `modifiedAt` (`java.util.Date`), the mocked `Clock` bean already stubbed to the test's `now`.
- Produces: nothing other code consumes. The fixture's agents 100–102 carry `created_at = modified_at = '2024-06-10 00:00:00'`.

- [ ] **Step 1: Replace the wall-clock assertion with the exact, seeded instant**

In `UpdateAgentEndpointTest.update`, replace `assertTrue(agent.createdAt.time < now)` (line 69) with the equality against the value the fixture will seed, and add the imports:

```kotlin
import java.time.Instant
import java.util.Date
```

```kotlin
        assertEquals(AgentEntity.DEFAULT_RATING, agent.rating) // Read-only: an agent can't rate itself
        assertEquals(Date.from(Instant.parse("2024-06-10T00:00:00Z")), agent.createdAt)
        assertTrue(agent.modifiedAt.time >= agent.createdAt.time)
```

Leave `assertTrue(party.modifiedAt.time < now)` and the `now` field/stub untouched: the party fixture already pins its dates, and the update still needs the stubbed clock.

- [ ] **Step 2: Run the class to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateAgentEndpointTest`

Expected: FAIL in `update` — `expected: java.util.Date<...2024-06-10T00:00:00.000Z> but was: java.util.Date<...<today>>`: the fixture still lets `T_AGENT.created_at` default to MySQL's `NOW()`.

- [ ] **Step 3: Pin the fixture's timestamps**

In `db/test/agent/UpdateAgentEndpoint.sql`, give the agents the same explicit dates as `T_PARTY` (line 17):

```sql
INSERT INTO T_AGENT(id, party_id, created_at, modified_at) VALUES
    (100, 100, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (101, 101, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (102, 102, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

Every agent seeded by the file gets the dates so the statement stays one insert; no other test class names this file (`grep -rn "UpdateAgentEndpoint.sql" modules/ndopify-server/src`).

- [ ] **Step 4: Run the class to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=UpdateAgentEndpointTest`

Expected: PASS — `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.

- [ ] **Step 5: Drop the now-pointless sleep**

Delete `Thread.sleep(100)` (line 47) from `update`. Nothing in the test measures elapsed real time any more; the mocked clock fixes `modifiedAt` at `now` and the fixture fixes `createdAt` at 2024.

- [ ] **Step 6: Prove it with repeated runs**

Run:

```bash
cd modules/ndopify-server
for i in $(seq 1 10); do
  mvn -q test -Dtest=UpdateAgentEndpointTest || exit 1
  echo "run $i: PASS"
done
```

Expected: ten `run N: PASS` lines, no failure. One green run is not evidence for a race fix; ten consecutive runs of the class are.

- [ ] **Step 7: Run the module verification**

Run: `cd modules/ndopify-server && mvn verify`

Expected: BUILD SUCCESS — ktlint, the whole test suite (MySQL running) and the JaCoCo gate pass; every other test of `UpdateAgentEndpointTest` stays green.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/endpoint/UpdateAgentEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/agent/UpdateAgentEndpoint.sql
git commit -m "test(agent): de-flake UpdateAgentEndpointTest timestamps" -m "Refs #270"
```
