# Publish without the owner verification (feature flag) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make #299's owner-verification publication gate an environment switch
(`ndopify.listing.publish.require-owner-verification`), off in TEST and PROD for now, while freezing whether the owner
was `VERIFIED` when the listing was published in `T_LISTING.ownership_verified` — the fact #300 and #393 read.

**Architecture:** `ListingService` reads the flag once (`@Value`, like `ndopify.listing.min-images`), always derives
#297's verdict, refuses with 409 `LISTING_OWNERSHIP_NOT_VERIFIED` only while the flag is on, and freezes the verdict's
result in the same save that moves a `VERIFIED` listing to `PUBLISHING`. A new column `ownership_verified` (`V22_0`,
`NOT NULL DEFAULT false`) stores the fact, written only there: the `PUBLISHING` second pass and every read path are
untouched.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, MySQL + Flyway (schema migration, SQL fixtures), `kotlin.test`,
`mockito-kotlin`, `TestRestTemplate` against MySQL.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/392 — the enriched story is the design (its *Enriched (groom,
2026-11-27)* section: *Decisions and assumptions*, *Refined acceptance criteria*, *Tests to carry*). #297 supplies the
verdict and #299 the gate; #300's plan (*Task 2: Freeze the fact at the publication gate*) is superseded by this plan's
Task 2 — do not edit that plan, and do not redo its Task 2 when #300 is implemented.

## Global Constraints

- `UI: no`, `Shared code: none`: `ndopify-server` only. No `ndopify-dto` change, no DTO release, no web-app change.
- The key is exactly `ndopify.listing.publish.require-owner-verification`, under `ndopify.listing.publish`, next to
  `cron`: `true` in `application.yml` (the gate is the default), `false` in `application-prod.yml` and in
  `application-test.yml` (TEST is the deployed, client-facing environment and faces the same hard-to-produce papers as
  PROD; local and the `qa` test profile keep the default). Changing it is a deployment and a restart — no runtime
  toggle, no per-tenant value.
- The verdict is derived in both modes. The flag only decides whether a non-`VERIFIED` verdict refuses; the save freezes
  what the verdict said either way.
- The order is fixed and identical in both modes: 404 `LISTING_NOT_FOUND` → 403 `HTTP_ACCESS_DENIED` → 409
  `LISTING_NOT_VERIFIED` → (gate on only) 409 `LISTING_OWNERSHIP_NOT_VERIFIED`. A listing not `VERIFIED` never reads the
  verdict, whichever the flag.
- The frozen fact is written only by `ListingService.publish(listing)`'s save for a `VERIFIED` listing: `true` for a
  `VERIFIED` verdict, `false` otherwise. `ListingPublisherService` (the `PUBLISHING` second pass) writes nothing there;
  no read re-derives it; no backfill.
- The 409 carries codes only: `error.data["reasons"]` (`code`, and `details` for `*_USABLE`) — never a customer name,
  document id, title, page, path or URL.
- Never modify an existing migration: add `V22_0__listing-ownership-verified.sql` after `V21_0__email-address.sql`.
- No read change: `ListingMapper`, `ListingSummary`, the search and `GET /v1/listings/{id}`'s JSON gain nothing; #300 maps
  the field.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. `mvn validate` (ktlint) and the JaCoCo gate must
  pass; `ndopify-server` tests need a local MySQL.
- Code, comments, logs and docs are in English; there is no user-facing text in this story.

## Review Focus

1. **Gate off still enforces the status.** A `DRAFT`/`UNDER_VERIFICATION`/`PUBLISHED` listing must still answer 409
   `LISTING_NOT_VERIFIED` with the flag off, and must not read the ownership verdict. (Task 1: unit test and the
   gate-off endpoint test on listing 101.)
2. **A refused publication writes nothing.** With the gate on, the 409 path must leave the row untouched — status
   `VERIFIED`, `ownershipVerified` as before. (Task 2: `a refused publication freezes nothing`.)
3. **The second pass never writes the frozen fact.** A `PUBLISHING` listing whose mandate has no ownership documents is
   published without a verdict read, and its `ownership_verified` keeps whatever the gate wrote. (Task 2:
   `the second pass does not write the frozen fact`.)
4. **A legacy row reads false.** The column is `NOT NULL DEFAULT false`, and the fixtures insert listings without it;
   nothing backfills a `PUBLISHED` listing from before the gate. (Task 2: `a listing inserted without the column reads
   false`.)
5. **The fact never leaks into a summary.** `ListingSummary` and the search JSON carry no `ownershipVerified`; only
   `GET /v1/listings/{id}` will, through #300. (Task 2: `a summary carries no ownership flag`.)

---

### Task 1: The environment switch in `ListingService`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt:41-53` (constructor and `init`), `:324-365` (`publish`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt:53-71` (constructor) and the publish tests
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingWithoutOwnerVerificationEndpointTest.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml:319-327`, `application-prod.yml:68-71`, `application-test.yml:36-41`
- Modify: `modules/ndopify-server/CLAUDE.md` (the **listing** section's **Publish** paragraph)

**Interfaces:**
- Consumes: `OwnershipVerdictService.verify(mandate: MandateEntity): OwnershipVerification`,
  `OwnershipVerificationStatus.VERIFIED` (already there, #299).
- Produces: `ListingService(..., ownershipVerdictService, requireOwnerVerification: Boolean, clock)` — the Spring bean
  reads `ndopify.listing.publish.require-owner-verification`; `publish(listing: ListingEntity)` refuses with
  `LISTING_OWNERSHIP_NOT_VERIFIED` only when `requireOwnerVerification` is true.

- [ ] **Step 1: Write the failing unit tests**

In `ListingServiceTest`, insert `true` for the new parameter in the existing `service` construction (after
`ownershipVerdictService`), and add a second instance with the gate off:

```kotlin
    private val openService = ListingService(
        dao,
        mandateService,
        agentService,
        customerService,
        publishedNotifier,
        sentBackNotifier,
        sectionService,
        locationService,
        accessService,
        ownershipVerdictService,
        false,
        clock,
    )
```

Then add:

```kotlin
    @Test
    fun `a pending owner is published when the gate is off`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.PENDING))
            .whenever(ownershipVerdictService).verify(verified.mandate)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        val result = openService.publish(verified)

        assertEquals(ListingStatus.PUBLISHING, result.status)
        assertEquals(0, result.retries)
    }

    @Test
    fun `a failed owner is published when the gate is off`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.FAILED))
            .whenever(ownershipVerdictService).verify(verified.mandate)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        val result = openService.publish(verified)

        assertEquals(ListingStatus.PUBLISHING, result.status)
    }

    @Test
    fun `a listing not verified is refused without reading the verdict when the gate is off`() {
        val ex = assertFailsWith<ConflictException> { openService.publish(listing) }

        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, ex.error.code)
        verifyNoInteractions(ownershipVerdictService)
    }
```

- [ ] **Step 2: Write the failing gate-off endpoint test**

Create `PublishListingWithoutOwnerVerificationEndpointTest` (the class-level `@Sql` runs before each test method, so each
test starts from the fixture):

```kotlin
package com.wutsi.ndopify.listing.server.endpoint

import com.wutsi.ndopify.AuthorizationAwareEndpointIntegrationTest
import com.wutsi.ndopify.error.dto.ErrorCode
import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.listing.dto.ListingStatus
import com.wutsi.ndopify.listing.server.dao.ListingRepository
import com.wutsi.ndopify.refdata.dto.RoleCode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** The publication gate off: the verdict no longer refuses, the listing status still does. Fixture: see PublishListingEndpointTest. */
@TestPropertySource(properties = ["ndopify.listing.publish.require-owner-verification=false"])
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])
class PublishListingWithoutOwnerVerificationEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    override val roles = arrayOf(RoleCode.ADMIN) // The changes: see ListingAccessEndpointTest for the others

    @Autowired
    private lateinit var dao: ListingRepository

    @Test
    fun `a pending owner is published`() {
        assertEquals(HttpStatus.OK, publish(103L).statusCode)

        val listing = dao.findById(103L).get()
        assertEquals(ListingStatus.PUBLISHING, listing.status)
        assertEquals(0, listing.retries)
    }

    @Test
    fun `a failed owner is published`() {
        assertEquals(HttpStatus.OK, publish(104L).statusCode)

        assertEquals(ListingStatus.PUBLISHING, dao.findById(104L).get().status)
    }

    @Test
    fun `a verified owner is published`() {
        assertEquals(HttpStatus.OK, publish(100L).statusCode)

        assertEquals(ListingStatus.PUBLISHING, dao.findById(100L).get().status)
    }

    @Test
    fun `a listing not verified is still refused`() {
        val response = publish(101L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(ListingStatus.UNDER_VERIFICATION, dao.findById(101L).get().status)
    }

    @Test
    fun `not found - 404`() {
        val response = publish(999L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(ErrorCode.LISTING_NOT_FOUND, response.body?.error?.code)
    }

    private fun publish(id: Long) =
        rest.postForEntity("/v1/listings/$id/publish", null, ErrorResponse::class.java)
}
```

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingServiceTest,PublishListingWithoutOwnerVerificationEndpointTest'
```

Expected: `ListingServiceTest` FAILS to compile (no such constructor parameter); the endpoint test fails on 103/104
(still 409) once the constructor is fixed.

- [ ] **Step 4: Add the switch and its configuration**

In `ListingService`, after `ownershipVerdictService` and before `clock`:

```kotlin
    @param:Value("\${ndopify.listing.publish.require-owner-verification}")
    private val requireOwnerVerification: Boolean,
```

Add a companion and an `init` block right after the constructor (import `org.slf4j.LoggerFactory`):

```kotlin
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingService::class.java)
    }

    init {
        if (!requireOwnerVerification) {
            LOGGER.warn(
                "ndopify.listing.publish.require-owner-verification=false: the owner-verification publication gate is " +
                    "disabled, a listing is published whatever the ownership verdict of its mandate",
            )
        }
    }
```

In `publish(listing)`, guard the refusal (compute the verdict before, as today) and update both KDocs to say the 409
applies only while the flag is on:

```kotlin
        val verification = ownershipVerdictService.verify(listing.mandate)
        if (requireOwnerVerification && verification.status != OwnershipVerificationStatus.VERIFIED) {
            throw ConflictException(
                Error(
                    code = ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED,
                    data = mapOf("reasons" to verification.reasons),
                )
            )
        }
```

Add the key in the three resources:

`application.yml`, under `ndopify.listing.publish` before `cron`:

```yaml
        publish:
            require-owner-verification: true   # Publication gate: a VERIFIED listing waits until its mandate's owner is VERIFIED
            cron: "0 */15 * * * ?"             # Every 15 minutes: publish listings that are VERIFIED or PUBLISHING
```

`application-prod.yml`, same place:

```yaml
        publish:
            require-owner-verification: false  # The papers are hard to produce: publish whatever the owner verdict; the fact is still frozen
            cron: "0 0 */1 * * ?"              # Every hour: publish listings that are VERIFIED or PUBLISHING
```

`application-test.yml`, under `ndopify:` (after `image`, before `security`):

```yaml
    listing:
        publish:
            require-owner-verification: false  # Same as PROD: publish whatever the owner verdict; the fact is still frozen
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingServiceTest,PublishListingWithoutOwnerVerificationEndpointTest,PublishListingEndpointTest'
```

Expected: PASS (the gate-on endpoint tests are unchanged).

- [ ] **Step 6: Document the switch**

In `modules/ndopify-server/CLAUDE.md`, in the **listing** section's **Publish** paragraph, after the 409 sentence, add:

> `ndopify.listing.publish.require-owner-verification` switches that 409: on by default, off in TEST and PROD for now,
> so there a `VERIFIED` listing is sent for publication whatever the mandate's ownership verdict. The verdict is still
> derived on every publication, and the save still freezes it on the listing.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/main/resources/application-prod.yml \
        modules/ndopify-server/src/main/resources/application-test.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingWithoutOwnerVerificationEndpointTest.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(listing): make the owner-verification publication gate configurable" -m "Refs #392"
```

---

### Task 2: Freeze the owner-verification fact at publication

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V22_0__listing-ownership-verified.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ListingEntity.kt:113` (after `verifiedAt`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt:344-365` (`publish(listing)`'s save)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt` (the publish tests)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingWithoutOwnerVerificationEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobWithoutOwnerVerificationTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the **Publish** paragraph, after Task 1's sentence)

**Interfaces:**
- Consumes: `requireOwnerVerification` and the 409 guard from Task 1; `OwnershipVerificationStatus.VERIFIED`.
- Produces: `ListingEntity.ownershipVerified: Boolean = false` and the column `T_LISTING.ownership_verified`;
  `publish(listing)` writes `ownershipVerified = (verdict == VERIFIED)` in its `PUBLISHING` save.

- [ ] **Step 1: Write the failing gate-on endpoint tests**

In `PublishListingEndpointTest`, add:

```kotlin
    @Test
    fun `publish freezes the ownership verification`() {
        assertEquals(HttpStatus.OK, publish(100L).statusCode)

        assertTrue(dao.findById(100L).get().ownershipVerified)
    }

    @Test
    fun `a refused publication freezes nothing`() {
        assertEquals(HttpStatus.CONFLICT, publish(103L).statusCode)

        val listing = dao.findById(103L).get()
        assertEquals(ListingStatus.VERIFIED, listing.status)
        assertFalse(listing.ownershipVerified)
    }

    @Test
    fun `a listing inserted without the column reads false`() {
        assertFalse(dao.findById(102L).get().ownershipVerified)
    }
```

- [ ] **Step 2: Extend the gate-off endpoint test**

In `PublishListingWithoutOwnerVerificationEndpointTest`, in `a pending owner is published` and
`a failed owner is published`, assert `assertFalse(listing.ownershipVerified)`; in `a verified owner is published`,
assert `assertTrue(dao.findById(100L).get().ownershipVerified)`.

- [ ] **Step 3: Write the failing unit assertions**

In `ListingServiceTest`, assert `assertTrue(result.ownershipVerified)` in
`a verified listing with a verified owner is sent for publication`, and `assertFalse(result.ownershipVerified)` in
`a pending owner is published when the gate is off`.

- [ ] **Step 4: Write the failing job tests**

In `PublishVerifiedListingsJobTest`, add:

```kotlin
    @Test
    fun `the gate freezes the ownership verification`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        jobs.publish()

        assertEquals(1, frozen(100))
        assertEquals(0, frozen(101))
    }

    @Test
    fun `the second pass does not write the frozen fact`() {
        // A PUBLISHING listing whose mandate has no ownership document: only the second pass sees it
        jdbc.update(
            """
            INSERT INTO T_LISTING(
                id, tenant_id, mandate_id, agent_id, status, city_id, neighborhood_id, retries, modified_at,
                ownership_verified
            ) VALUES (103, 1, 2, 20, 'PUBLISHING', 2370201, 111, 0, '2026-01-01', 0)
            """.trimIndent(),
        )
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        jobs.publish()

        assertEquals("PUBLISHED", status(103))
        assertEquals(0, frozen(103))
    }

    private fun frozen(id: Long): Int =
        jdbc.queryForObject("SELECT ownership_verified FROM T_LISTING WHERE id=?", Int::class.java, id)!!
```

Create `PublishVerifiedListingsJobWithoutOwnerVerificationTest`:

```kotlin
package com.wutsi.ndopify.listing.server.job

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.BaseEndpointIntegrationTest
import com.wutsi.ndopify.listing.server.service.ListingPublishedNotifier
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentGenerator
import com.wutsi.ndopify.listing.server.service.publisher.ListingContentInfo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.jdbc.Sql
import kotlin.test.Test
import kotlin.test.assertEquals

/** The publish job's first pass with the gate off: every VERIFIED listing is sent, and the verdict is still frozen. */
@TestPropertySource(properties = ["ndopify.listing.publish.require-owner-verification=false"])
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishVerifiedListingsJob.sql"])
class PublishVerifiedListingsJobWithoutOwnerVerificationTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var publishedNotifier: ListingPublishedNotifier

    @Test
    fun `publishes every VERIFIED listing and freezes the verdict`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        jobs.publish()

        assertEquals("PUBLISHED", status(100))
        assertEquals("PUBLISHED", status(101))
        assertEquals("PUBLISHED", status(102))
        assertEquals(1, frozen(100))
        assertEquals(0, frozen(101))
        assertEquals(0, frozen(102))
    }

    private fun status(id: Long): String =
        jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=?", String::class.java, id)!!

    private fun frozen(id: Long): Int =
        jdbc.queryForObject("SELECT ownership_verified FROM T_LISTING WHERE id=?", Int::class.java, id)!!
}
```

- [ ] **Step 5: Write the failing search guard**

In `SearchListingEndpointTest`, add (and import `kotlin.test.assertFalse`):

```kotlin
    @Test
    fun `a summary carries no ownership flag`() {
        val body = rest.getForEntity("/v1/listings?mandate-id=1", String::class.java).body!!

        assertFalse(body.contains("ownershipVerified"), body)
        assertEquals(listOf(100L), search("mandate-id=1"))
    }
```

- [ ] **Step 6: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest='PublishListingEndpointTest,PublishVerifiedListingsJobTest,SearchListingEndpointTest'
```

Expected: FAIL to compile — `ownershipVerified` is unresolved on `ListingEntity`.

- [ ] **Step 7: Add the migration and the entity field**

Create `V22_0__listing-ownership-verified.sql`:

```sql
-- #392: the owner-verification fact, frozen when the publication gate saves the listing (#297/#299). Every existing
-- row reads false: a listing published before the gate has no frozen fact, never a backfill, never un-badged.
ALTER TABLE T_LISTING ADD COLUMN ownership_verified BOOLEAN NOT NULL DEFAULT false;
```

In `ListingEntity`, right after `verifiedAt`:

```kotlin
    /** Frozen when the publication gate saves the listing (#392): the verdict it read, true only for VERIFIED. Never re-derived. */
    val ownershipVerified: Boolean = false,
```

- [ ] **Step 8: Freeze the verdict in the gate's save**

In `ListingService.publish(listing)`, capture the verdict once and use it for both the guard and the save:

```kotlin
        val verification = ownershipVerdictService.verify(listing.mandate)
        val verified = verification.status == OwnershipVerificationStatus.VERIFIED
        if (requireOwnerVerification && !verified) {
            throw ConflictException(
                Error(
                    code = ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED,
                    data = mapOf("reasons" to verification.reasons),
                )
            )
        }

        return dao.save(
            listing.copy(
                status = ListingStatus.PUBLISHING,
                retries = 0, // The publication's own attempts (ListingPublisherService.MAX_RETRIES)
                modifiedAt = Date(clock.millis()),
                ownershipVerified = verified,
            )
        )
```

Leave `ListingPublisherService` and every read path untouched.

- [ ] **Step 9: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingServiceTest,PublishListingEndpointTest,PublishListingWithoutOwnerVerificationEndpointTest,PublishVerifiedListingsJobTest,PublishVerifiedListingsJobWithoutOwnerVerificationTest,SearchListingEndpointTest'
```

Expected: PASS.

- [ ] **Step 10: Document the frozen fact**

In `modules/ndopify-server/CLAUDE.md`, in the **Publish** paragraph, after Task 1's sentence, add:

> The same save freezes `ownershipVerified` (`T_LISTING.ownership_verified`, `V22_0`) from the verdict just read — `true`
> only for `VERIFIED`, `false` otherwise, whichever the flag. The `PUBLISHING` second pass writes nothing there, so a
> listing published before #299, or already `PUBLISHING` at deploy, reads `false` (no backfill, no un-badging); only
> #300 maps the fact, and `ListingSummary`/the search stay untouched.

- [ ] **Step 11: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V22_0__listing-ownership-verified.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/domain/ListingEntity.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingWithoutOwnerVerificationEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobWithoutOwnerVerificationTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/SearchListingEndpointTest.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(listing): freeze the owner verification at publication" -m "Refs #392"
```

---

## Verification

```bash
cd modules/ndopify-server && mvn verify
```

Expected: BUILD SUCCESS — ktlint, all tests (against local MySQL), and the JaCoCo gate. No other module changes, no
version bump, no release.

## Self-Review

- **Spec coverage**: the flag (Task 1), the gate in both modes (Tasks 1–2), the frozen fact both ways (Task 2), the
  migration without backfill (Task 2), the startup WARN (Task 1), the unchanged order/access and the no-leak 409
  (Tasks 1–2), the untouched search/summary (Task 2), no web/DTO change (neither task).
- **No re-derivation**: only `publish(listing)` writes `ownershipVerified`; the second pass, the mapper and the reads
  are explicitly asserted untouched.
- **Assumption to confirm at approval**: the flag is `false` in `application-test.yml` as well as `application-prod.yml`
  (TEST is the client-facing environment with the same hard-to-produce papers). If TEST should stay gated, drop that one
  line; no code or test changes.
