# Match Auto-Proposal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After 24h without the search agent proposing or dismissing a match, propose it to the customer automatically through the existing proposal flow.

**Architecture:** A new `MatchAutoProposalService` queries the stalled `ACTIVE` matches (tenant-scoped), groups them by mandate, and calls the existing `MatchProposalService.propose` per mandate in its own transaction. A new `MatchJobs.autoPropose` scheduled method runs it for every active tenant. No new status, DTO, or migration.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, JUnit 5 / `kotlin.test`, Mockito-Kotlin, GreenMail, MySQL/Flyway.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/113

**Plan file:** `docs/superpowers/plans/2026-11-14-match-auto-propose.md`

## Global Constraints

- Only `ACTIVE` matches are auto-proposed. `SHORTLISTED`, `DISMISSED`, `PROPOSED`, `ACCEPTED`, `REJECTED`, `EXPIRED` are never touched.
- Eligibility: `Match.createdAt <= now - after-hours` (24h, inclusive), `mandate.status = APPROVED`, `listing.status = PUBLISHED`.
- Include own-inventory **and** co-brokerage matches.
- Reuse `MatchProposalService.propose`: it sets `PROPOSED`, `proposedAt`, `listingPrice`, and publishes `MatchProposedEvent`, which sends the existing `MatchProposedMailet` e-mail.
- Group by mandate; at most 20 matches per proposal (the existing `ProposeMatchRequest.matchIds` limit); batch size 100 matches per run.
- 24h is wall-clock from `Match.createdAt`, not business hours and not `notifiedAt`.
- Never overwrite an agent decision. An optimistic-lock conflict (agent acted meanwhile) skips that mandate only.
- No new match status, no DTO change, no Flyway migration, no new e-mail template.
- Jobs are tenant-scoped: `TenantContext` is set per active tenant and removed afterwards; one tenant failing does not stop the others.
- Local and test environment run the job every 15 minutes; prod runs it hourly; the automated test profile (`application-qa.yml`) keeps `cron: "-"`.
- `ndopify-server` is Kotlin with ktlint (4-space indentation); config injection uses `@param:Value`.
- Module tests require local MySQL. Run them from `modules/ndopify-server`.

## Review Focus

1. **Agent acts while the job runs** — the agent proposes/dismisses/shortlists the same match the job loaded. Expected: no overwrite, no duplicate e-mail, that mandate skipped, other mandates still processed. Test: Task 1, `an optimistic-lock conflict skips that mandate`.
2. **More than 20 stalled matches of one mandate** — expected: one e-mail with the best 20; the remaining matches stay `ACTIVE` and are picked up on the next run. Test: Task 1, `keeps the best 20 per mandate`.
3. **Mandate not APPROVED or listing no longer PUBLISHED** — expected: match untouched, no e-mail. Test: Task 3, fixture matches 8 (listing `RENTED`) and 9 (search mandate `CLOSED`).
4. **Co-brokerage match** — expected: the customer receives it in the normal proposal e-mail, which names only the search agent (anti-sautage), never the listing agent. Test: Task 3, fixture match 20 and its subject/body assertions.
5. **Inactive or foreign tenant** — expected: its matches are never touched. Test: Task 2 (tenant loop, one failing tenant) and Task 3 (fixture match 31 in an inactive tenant).

---

### Task 1: Auto-proposal service and stalled-match query

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalService.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (`ndopify.match` block)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalServiceTest.kt`

**Interfaces:**
- Consumes: `MatchProposalService.propose(ids: List<Long>): List<MatchEntity>` (existing).
- Produces:
  - `MatchRepository.findStalled(status: MatchStatus, before: Date, mandateStatus: MandateStatus, listingStatus: ListingStatus, page: Pageable): List<MatchEntity>`
  - `MatchAutoProposalService.autoProposeDue(): Int` — returns the number of matches proposed.

- [ ] **Step 1: Write the failing unit test**

Create `MatchAutoProposalServiceTest.kt`:

```kotlin
package com.wutsi.ndopify.match.server.service

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.mandate.server.domain.MandateEntity
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.springframework.data.domain.PageRequest
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class MatchAutoProposalServiceTest {
    private val dao = mock<MatchRepository>()
    private val proposalService = mock<MatchProposalService>()
    private val clock = Clock.fixed(Instant.parse("2026-11-14T12:00:00Z"), ZoneOffset.UTC)
    private val service = MatchAutoProposalService(
        dao = dao,
        proposalService = proposalService,
        clock = clock,
        afterHours = 24,
        batchSize = 100,
    )

    @Test
    fun `proposes stalled matches grouped by mandate, best score first`() {
        doReturn(
            listOf(
                match(id = 11, mandateId = 1, score = 0.5),
                match(id = 10, mandateId = 1, score = 0.9),
                match(id = 21, mandateId = 2, score = 0.7),
            ),
        ).whenever(dao).findStalled(
            status = MatchStatus.ACTIVE,
            before = Date.from(Instant.parse("2026-11-13T12:00:00Z")),
            mandateStatus = MandateStatus.APPROVED,
            listingStatus = ListingStatus.PUBLISHED,
            page = PageRequest.of(0, 100),
        )
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        assertEquals(3, service.autoProposeDue())

        verify(proposalService).propose(listOf(10L, 11L))
        verify(proposalService).propose(listOf(21L))
    }

    @Test
    fun `keeps the best 20 per mandate`() {
        doReturn((1L..25L).map { match(id = it, mandateId = 1, score = it.toDouble()) })
            .whenever(dao).findStalled(any(), any(), any(), any(), any())
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(any())

        assertEquals(20, service.autoProposeDue())

        verify(proposalService).propose((25L downTo 6L).toList())
    }

    @Test
    fun `an optimistic-lock conflict skips that mandate only`() {
        doReturn(listOf(match(id = 10, mandateId = 1, score = 0.9), match(id = 20, mandateId = 2, score = 0.8)))
            .whenever(dao).findStalled(any(), any(), any(), any(), any())
        doThrow(ObjectOptimisticLockingFailureException(MatchEntity::class.java, 10L))
            .whenever(proposalService).propose(listOf(10L))
        doReturn(emptyList<MatchEntity>()).whenever(proposalService).propose(listOf(20L))

        assertEquals(1, service.autoProposeDue())

        verify(proposalService).propose(listOf(20L))
    }

    private fun match(id: Long, mandateId: Long, score: Double) = MatchEntity(
        id = id,
        mandate = MandateEntity(id = mandateId),
        listing = ListingEntity(id = 1000 + id),
        agentId = 20,
        status = MatchStatus.ACTIVE,
        score = score,
        createdAt = Date.from(Instant.parse("2026-11-13T10:00:00Z")),
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAutoProposalServiceTest`

Expected: FAIL — `MatchAutoProposalService` and `MatchRepository.findStalled` are unresolved.

- [ ] **Step 3: Add the stalled-match query**

In `MatchRepository.kt`, next to the other match queries:

```kotlin
    // The matches an agent left untouched for too long (MatchAutoProposalService): oldest first, tenant-filtered
    @Query(
        "SELECT m FROM MatchEntity m WHERE m.status = :status AND m.createdAt <= :before " +
            "AND m.mandate.status = :mandateStatus AND m.listing.status = :listingStatus " +
            "ORDER BY m.createdAt ASC, m.id ASC"
    )
    fun findStalled(
        @Param("status") status: MatchStatus,
        @Param("before") before: Date,
        @Param("mandateStatus") mandateStatus: MandateStatus,
        @Param("listingStatus") listingStatus: ListingStatus,
        page: Pageable,
    ): List<MatchEntity>
```

- [ ] **Step 4: Implement `MatchAutoProposalService`**

Create the file with this body:

```kotlin
package com.wutsi.ndopify.match.server.service

import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.mandate.dto.MandateStatus
import com.wutsi.ndopify.match.dto.MatchStatus
import com.wutsi.ndopify.match.server.dao.MatchRepository
import com.wutsi.ndopify.match.server.domain.MatchEntity
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import java.time.Clock
import java.util.Date

/**
 * Auto-proposes the matches an agent left untouched for `after-hours`: the customer receives the same proposal e-mail
 * as a manual propose and can answer it. Each mandate is proposed in its own transaction
 * ([MatchProposalService.propose]), so a failure, or an agent answering meanwhile (optimistic lock), skips that mandate
 * only. Called by `MatchJobs.autoPropose`.
 */
@Service
class MatchAutoProposalService(
    private val dao: MatchRepository,
    private val proposalService: MatchProposalService,
    private val clock: Clock,
    @param:Value("\${ndopify.match.auto-propose.after-hours}") private val afterHours: Long,
    @param:Value("\${ndopify.match.auto-propose.batch-size}") private val batchSize: Int,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(MatchAutoProposalService::class.java)
        const val MAX_PER_PROPOSAL = 20 // ProposeMatchRequest.matchIds: @Size(max = 20)
        private const val HOUR_MILLIS = 3_600_000L
    }

    fun autoProposeDue(): Int {
        val before = Date(clock.millis() - afterHours * HOUR_MILLIS)
        val stalled = dao.findStalled(
            status = MatchStatus.ACTIVE,
            before = before,
            mandateStatus = MandateStatus.APPROVED,
            listingStatus = ListingStatus.PUBLISHED,
            page = PageRequest.of(0, batchSize),
        )
        return stalled.groupBy { it.mandate.id!! }.values.sumOf { matches -> propose(matches) }
    }

    private fun propose(matches: List<MatchEntity>): Int {
        val mandateId = matches.first().mandate.id!!
        val ids = matches.sortedByDescending { it.score }.take(MAX_PER_PROPOSAL).map { it.id!! }
        return try {
            proposalService.propose(ids)
            LOGGER.info("Mandate#$mandateId - Auto-proposed ${ids.size} match(es)")
            ids.size
        } catch (ex: Exception) {
            // The agent answered, the mandate closed, the listing was taken down... The next run sees the new state
            LOGGER.warn("Mandate#$mandateId - Unable to auto-propose its matches", ex)
            0
        }
    }
}
```

- [ ] **Step 5: Add the service config to `application.yml`**

Under `ndopify.match`, after the `expire` block:

```yaml
        auto-propose:
            after-hours: 24                 # A match the agent hasn't touched for 24h is proposed to the customer
            batch-size: 100                 # Matches examined per run
```

`application-prod.yml` inherits both values from `application.yml`; do not duplicate them.

- [ ] **Step 6: Run the unit test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchAutoProposalServiceTest`

Expected: PASS (3 tests).

- [ ] **Step 7: Run ktlint**

Run: `cd modules/ndopify-server && mvn validate`

Expected: BUILD SUCCESS, no formatting errors.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/dao/MatchRepository.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalService.kt \
        modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/service/MatchAutoProposalServiceTest.kt
git commit -m "feat(match): auto-propose matches the agent left untouched"
```

---

### Task 2: Scheduled job and cron configuration

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml`
- Modify: `modules/ndopify-server/src/main/resources/application-prod.yml`
- Modify: `modules/ndopify-server/src/test/resources/application-qa.yml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/MatchJobsTest.kt`

**Interfaces:**
- Consumes: `MatchAutoProposalService.autoProposeDue(): Int` (Task 1).
- Produces: `MatchJobs.autoPropose(): Unit`.

- [ ] **Step 1: Write the failing job test**

Create `MatchJobsTest.kt`:

```kotlin
package com.wutsi.ndopify.match.server.job

import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.match.server.service.CoBrokerageNotificationService
import com.wutsi.ndopify.match.server.service.MatchAutoProposalService
import com.wutsi.ndopify.match.server.service.MatchNotificationService
import com.wutsi.ndopify.match.server.service.MatchProposalService
import com.wutsi.ndopify.match.server.service.MatchService
import com.wutsi.ndopify.platform.tenant.TenantContext
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantService
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatchJobsTest {
    private val notificationService = mock<MatchNotificationService>()
    private val coBrokerageNotificationService = mock<CoBrokerageNotificationService>()
    private val proposalService = mock<MatchProposalService>()
    private val autoProposalService = mock<MatchAutoProposalService>()
    private val matchService = mock<MatchService>()
    private val tenantService = mock<TenantService>()
    private val jobs = MatchJobs(
        notificationService = notificationService,
        coBrokerageNotificationService = coBrokerageNotificationService,
        proposalService = proposalService,
        autoProposalService = autoProposalService,
        matchService = matchService,
        tenantService = tenantService,
    )

    private val tenants = mutableListOf<Long?>()

    @BeforeTest
    fun setUp() {
        doReturn(listOf(TenantEntity(id = 1L), TenantEntity(id = 2L)))
            .whenever(tenantService).search(true)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `autoPropose - each active tenant, with its tenant set`() {
        doAnswer {
            tenants.add(TenantContext.get())
            3
        }.whenever(autoProposalService).autoProposeDue()

        jobs.autoPropose()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertNull(TenantContext.get())
    }

    @Test
    fun `autoPropose - a failing tenant doesn't stop the others`() {
        doAnswer {
            tenants.add(TenantContext.get())
            if (TenantContext.get() == 1L) throw IllegalStateException("boom")
            0
        }.whenever(autoProposalService).autoProposeDue()

        jobs.autoPropose()

        assertEquals(listOf<Long?>(1L, 2L), tenants)
        assertNull(TenantContext.get())
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchJobsTest`

Expected: FAIL — `MatchJobs` has no `autoPropose` method and no `autoProposalService` constructor parameter.

- [ ] **Step 3: Add the scheduled job**

In `MatchJobs.kt`, add the dependency and method:

```kotlin
    private val autoProposalService: MatchAutoProposalService,
```

Place it after `proposalService` in the constructor. Then:

```kotlin
    /** Proposes the matches the agents left untouched for 24h: the customers receive them without waiting. */
    @Scheduled(cron = "\${ndopify.match.auto-propose.cron}")
    fun autoPropose() {
        forEachTenant("autoPropose", "match(es)") { autoProposalService.autoProposeDue() }
    }
```

- [ ] **Step 4: Add the cron configuration**

`application.yml`, in the `ndopify.match.auto-propose` block from Task 1:

```yaml
            cron: "30 */15 * * * ?"         # Every 15 minutes, offset from the other 15-minute jobs
```

`application-prod.yml`, under `ndopify.match`:

```yaml
        auto-propose:
            cron: "0 45 * * * ?"            # Every hour at 45 minutes: a 24h delay needs no finer cadence
```

`src/test/resources/application-qa.yml`, under `ndopify.match`:

```yaml
        auto-propose:
            cron: "-" # Never on its own: tests call MatchJobs
```

- [ ] **Step 5: Run the job test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchJobsTest`

Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/job/MatchJobs.kt \
        modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/main/resources/application-prod.yml \
        modules/ndopify-server/src/test/resources/application-qa.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/MatchJobsTest.kt
git commit -m "feat(match): schedule the auto-proposal job"
```

---

### Task 3: End-to-end job test and fixture

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/match/AutoProposeMatchesJob.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/AutoProposeMatchesJobTest.kt`

**Interfaces:**
- Consumes: `MatchJobs.autoPropose()` (Task 2).
- Produces: nothing later tasks use.

- [ ] **Step 1: Create the fixture**

Create `AutoProposeMatchesJob.sql`. Reuse the shape of `ExpireMatchesJob.sql` and `ProposeMatchEndpoint.sql`. The rows that matter:

```sql
INSERT INTO T_TENANT(id, name, domain_name, locales, country_code, public_portal_url, currency_code, monetary_format, active) VALUES
    (1, 'Ndopify', 'localhost', 'fr-CM, en-CM', 'CM', 'https://www.ndopify.com', 'XAF', '#,###,##0 FCFA', true),
    (2, 'Inactive', 'inactive.localhost', 'fr-CM, en-CM', 'CM', NULL, 'XAF', '#,###,##0 FCFA', false);

INSERT INTO T_LOCATION(id, parent_fk, type, country, name, ascii_name, latitude, longitude) VALUES
    (2370201, NULL, 'CITY', 'CM', 'Yaounde', 'Yaounde', 3.8667, 11.5167);

INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name) VALUES
    (100, 1, 'ray.sponsible@gmail.com', 'Ray', 'Sponsible'),
    (101, 1, 'owner@gmail.com', 'Owen', 'Er'),
    (200, 1, 'kim.possible@gmail.com', 'Kim', 'Possible'),
    (300, 1, 'john.doe@gmail.com', 'John', 'Doe'),
    (120, 2, 'other.customer@gmail.com', 'Oth', 'Er'),
    (220, 2, 'other.agent@gmail.com', 'Oth', 'Agent');

INSERT INTO T_CUSTOMER(id, tenant_id, party_id) VALUES
    (10, 1, 100),
    (11, 1, 101),
    (12, 2, 120);

INSERT INTO T_AGENT(id, tenant_id, party_id, city_id) VALUES
    (20, 1, 200, 2370201),
    (30, 1, 300, 2370201),
    (22, 2, 220, 2370201);

-- 1, 2: two searches of customer 10, agent 20   3: closed search   11..20: listings' mandates
INSERT INTO T_MANDATE(id, tenant_id, customer_id, agent_id, status, mandate_type, listing_type, property_category, city_id, property_type, rooms, price, price_periodicity_type, currency_code) VALUES
    (1, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (2, 1, 10, 20, 'APPROVED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (3, 1, 10, 20, 'CLOSED', 'SEARCH', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (11, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (12, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (13, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (14, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (15, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (16, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (17, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (18, 1, 11, 20, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (19, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (20, 1, 11, 30, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF'),
    (99, 2, 12, 22, 'APPROVED', 'LISTING', 'RENTAL', 'RESIDENTIAL', 2370201, 'APARTMENT', '2', 150000, 'MONTHLY', 'XAF');

-- 101..107, 109, 111: published   108: rented
INSERT INTO T_LISTING(id, tenant_id, mandate_id, agent_id, property_category, listing_type, status, city_id, property_type, rooms, price, currency_code, price_periodicity_type, title) VALUES
    (101, 1, 11, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment A'),
    (102, 1, 12, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment B'),
    (103, 1, 13, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment C'),
    (104, 1, 14, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment D'),
    (105, 1, 15, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment E'),
    (106, 1, 16, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment F'),
    (107, 1, 17, 20, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment G'),
    (108, 1, 18, 20, 'RESIDENTIAL', 'RENTAL', 'RENTED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment H'),
    (109, 1, 19, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Apartment I'),
    (111, 1, 20, 30, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Co-brokerage apartment'),
    (201, 2, 99, 22, 'RESIDENTIAL', 'RENTAL', 'PUBLISHED', 2370201, 'APARTMENT', 2, 150000, 'XAF', 'MONTHLY', 'Other tenant');

-- 1, 2, 10, 20: to auto-propose; 3: 23h old; 4: shortlisted; 5: dismissed; 6: already proposed;
-- 7: expired; 8: listing rented; 9: search closed; 31: inactive tenant
INSERT INTO T_MATCH(id, tenant_id, mandate_id, listing_id, agent_id, co_brokerage, status, score, listing_price, created_at, proposed_at) VALUES
    (1, 1, 1, 101, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (2, 1, 1, 102, 20, false, 'ACTIVE', 0.8, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (3, 1, 1, 103, 20, false, 'ACTIVE', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 23 HOUR), NULL),
    (4, 1, 1, 104, 20, false, 'SHORTLISTED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (5, 1, 1, 105, 20, false, 'DISMISSED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (6, 1, 1, 106, 20, false, 'PROPOSED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR)),
    (7, 1, 1, 107, 20, false, 'EXPIRED', 0.7, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), DATE_SUB(UTC_TIMESTAMP(), INTERVAL 30 DAY)),
    (8, 1, 1, 108, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (9, 1, 3, 109, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (10, 1, 2, 104, 20, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (20, 1, 1, 111, 20, true, 'ACTIVE', 0.85, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL),
    (31, 2, 99, 201, 22, false, 'ACTIVE', 0.9, 150000, DATE_SUB(UTC_TIMESTAMP(), INTERVAL 25 HOUR), NULL);
```

- [ ] **Step 2: Write the integration test**

Create `AutoProposeMatchesJobTest.kt`:

```kotlin
package com.wutsi.ndopify.match.server.job

import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.match.dto.MatchStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Sql(value = ["/db/test/clean.sql", "/db/test/match/AutoProposeMatchesJob.sql"])
class AutoProposeMatchesJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: MatchJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun autoPropose() {
        jobs.autoPropose()
        awaitAsyncTasks()

        assertEquals(
            mapOf(
                1L to MatchStatus.PROPOSED,
                2L to MatchStatus.PROPOSED,
                3L to MatchStatus.ACTIVE, // 23h old
                4L to MatchStatus.SHORTLISTED,
                5L to MatchStatus.DISMISSED,
                6L to MatchStatus.PROPOSED, // already proposed
                7L to MatchStatus.EXPIRED,
                8L to MatchStatus.ACTIVE, // listing rented
                9L to MatchStatus.ACTIVE, // search closed
                10L to MatchStatus.PROPOSED,
                20L to MatchStatus.PROPOSED, // co-brokerage
                31L to MatchStatus.ACTIVE, // inactive tenant
            ),
            statuses(),
        )

        assertTrue(smtp.waitForIncomingEmail(5000, 2), "customer e-mails not received")
        assertEquals(2, smtp.receivedMessages.size)
        val subjects = smtp.receivedMessages.map { it.subject }
        assertTrue(subjects.any { it.contains("3 biens") })
        assertTrue(subjects.any { it.contains("un bien") })

        val body = smtp.receivedMessages.first { it.subject.contains("3 biens") }.content.toString()
        assertTrue(body.contains("https://www.ndopify.com/match/1"))
        assertTrue(body.contains("https://www.ndopify.com/match/2"))
        assertTrue(body.contains("https://www.ndopify.com/match/20"))
        assertTrue(body.contains("Kim Possible"))
        assertFalse(body.contains("John Doe")) // anti-sautage: never the counterpart agent
    }

    @Test
    fun `nothing left on the next run`() {
        jobs.autoPropose()
        awaitAsyncTasks()
        val proposedAt = proposedAt()

        jobs.autoPropose()

        assertEquals(proposedAt, proposedAt())
        assertEquals(2, smtp.receivedMessages.size)
    }

    // Read with SQL rather than the repository, whose queries are filtered by the tenant
    private fun statuses(): Map<Long, MatchStatus> =
        jdbc.queryForList("SELECT id, status FROM T_MATCH").associate { row ->
            (row["id"] as Number).toLong() to MatchStatus.valueOf(row["status"] as String)
        }

    private fun proposedAt(): Map<Long, Any?> =
        jdbc.queryForList("SELECT id, proposed_at FROM T_MATCH WHERE status = 'PROPOSED'").associate { row ->
            (row["id"] as Number).toLong() to row["proposed_at"]
        }
}
```

- [ ] **Step 3: Run the integration test**

Run: `cd modules/ndopify-server && mvn test -Dtest=AutoProposeMatchesJobTest`

Expected: PASS (2 tests). If the fixture fails to load, compare its columns with `ExpireMatchesJob.sql` and `ProposeMatchEndpoint.sql`; do not change Tasks 1–2 to make it pass.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/match/AutoProposeMatchesJob.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/job/AutoProposeMatchesJobTest.kt
git commit -m "test(match): cover auto-proposal end to end"
```

---

### Task 4: Documentation

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: the final names and config from Tasks 1–3.
- Produces: nothing.

- [ ] **Step 1: Update the module guide**

In `modules/ndopify-server/CLAUDE.md`:

- In the job list near the top, change `MatchJobs` (match: `notifyAgents` hourly, `rematch` and `expire` daily…) to include `autoPropose` (every 15 minutes; hourly in prod).
- In the **match** domain section, after the **Expiry** paragraph, add:

```markdown
  **Auto-proposal**: `MatchJobs.autoPropose` (every 15 minutes, hourly in prod, `ndopify.match.auto-propose.cron`)
  has `MatchAutoProposalService.autoProposeDue` propose the `ACTIVE` matches of an `APPROVED` search whose listing is
  `PUBLISHED` and that the agent hasn't touched for `after-hours` (24): `batch-size` (100) matches per run, grouped by
  mandate, at most 20 per proposal, through `MatchProposalService.propose` — so the customer gets the normal proposal
  e-mail and can accept/reject it. `SHORTLISTED`, `DISMISSED`, `PROPOSED`, `ACCEPTED`, `REJECTED` and `EXPIRED` are
  never touched; an agent answering meanwhile fails on `@Version` and skips that mandate. No new status nor flag:
  auto-proposals look like manual ones (logged per mandate).
```

- [ ] **Step 2: Run the full module verification**

Run: `cd modules/ndopify-server && mvn verify`

Expected: BUILD SUCCESS, all tests pass, JaCoCo coverage gate passes.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(match): record auto-proposal"
```

---

## Self-Review

- **Spec coverage:** 24h trigger (Task 1), `ACTIVE` only and guards (Task 1 query, Task 3 fixture), co-brokerage (Task 3), grouping and 20 cap (Task 1), reuse of the proposal flow/e-mail (Task 1), no overwrite (Task 1 catch, `@Version`), cron local/test/prod and qa off (Task 2), no schema/DTO/status change (no task adds one), docs (Task 4).
- **Open questions resolved in this plan:** audit flag = none (log only); agent notification = none; shortlisted = excluded; 24h from `createdAt`; include co-brokerage; first deployment drains the backlog 100/run; prod cadence hourly at :45; local/test 15 minutes at :30.
- **Type consistency:** `findStalled` is used with the same five parameters in Task 1’s test and service; `autoProposeDue(): Int` is consumed by `MatchJobs` and its test; the `MatchJobs` constructor order in Task 2’s test matches Task 2’s constructor change.
- **Review Focus:** each of the five entries names its test above.
