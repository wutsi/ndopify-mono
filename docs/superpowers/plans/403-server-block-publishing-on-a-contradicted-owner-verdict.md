# Block a contradicted owner verdict at publication — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Never publish a listing whose mandate's ownership verdict carries a contradiction code —
`OWNERSHIP_ID_NAME_MISMATCH`, `OWNERSHIP_DOCUMENT_NAME_MISMATCH`, `OWNERSHIP_PROPERTY_MISMATCH` or
`OWNERSHIP_ID_EXPIRED` — whatever `ndopify.listing.publish.require-owner-verification` says, from the endpoint and from
the publish job, and count that refusal apart in the job's run log.

**Architecture:** `ListingService.publish(listing)` keeps its single choke point. It derives #297's verdict once, still
freezes `ownershipVerified` from it, and now refuses with the existing 409 `LISTING_OWNERSHIP_NOT_VERIFIED` when the
verdict carries a contradiction code **or** when the flag is on and the verdict is not `VERIFIED`. The environment flag
keeps excusing only `PENDING` and `*_UNUSABLE` verdicts. `ListingJobs.publish`'s first pass already calls that method;
it classifies the `ConflictException` it catches from the 409 it carries (never by re-reading the verdict) and reports
a `contradicted` counter beside `blocked` in its per-tenant summary line.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, `kotlin.test`, `mockito-kotlin`, logback `ListAppender` (log
assertion), `TestRestTemplate` against MySQL.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/403 — the enriched story is the design (*Problem*, *Decision*,
*Scope*, *Acceptance criteria*, *Assumptions*). #297 supplies the verdict, #299 the gate, #377 the override, #376
`OWNERSHIP_PROPERTY_MISMATCH` and #392 the flag; all are merged on `main`.

## Global Constraints

- `ndopify-server` only: no `ndopify-dto` change or release, no web-app change, no migration, no entity field, no
  configuration key, no read path (`ListingMapper`, the search, `GET /v1/listings/{id}` untouched).
- The blocked set is exactly four codes, and `OWNERSHIP_ID_EXPIRED` is in it (groom decision, 2026-11-28):
  `OwnershipReasonCodes.ID_NAME_MISMATCH`, `DOCUMENT_NAME_MISMATCH`, `PROPERTY_MISMATCH`, `ID_EXPIRED`.
- The gate is read once, in `ListingService.publish(listing)`; the endpoint and `ListingJobs.publish`'s first pass both
  go through it. A contradiction is always a `FAILED` verdict; `PENDING` and `*_USABLE` stay with the flag.
- The frozen fact is unchanged: the same save writes `ownershipVerified = (verdict == VERIFIED)`, `true` only for
  `VERIFIED` (overridden or not). A refused publication saves nothing.
- The order is unchanged in both modes: 404 `LISTING_NOT_FOUND` → 403 `HTTP_ACCESS_DENIED` → 409
  `LISTING_NOT_VERIFIED` → the verdict guard. A listing not `VERIFIED` never reads the verdict.
- The 409 carries codes only in `error.data["reasons"]` — never a customer name, document id, title, page, path or URL.
  The new job counter is a count only, no per-listing line.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. `mvn validate` (ktlint) and the JaCoCo gate
  must pass; the endpoint and job tests need a local MySQL.
- Code, comments, logs and docs are in English; there is no user-facing text in this story.

## Review Focus

1. **A verdict that is both unusable and contradicted** (`FAILED` with e.g. `OWNERSHIP_ID_UNUSABLE` **and**
   `OWNERSHIP_PROPERTY_MISMATCH`) must refuse — any code in the list is enough. Pinned in Task 1
   (`a contradiction among unusable reasons is refused when the gate is off`).
2. **The flag-off path that must survive**: a `FAILED` verdict whose only reasons are `*_UNUSABLE`, and a `PENDING`
   verdict (no document, in flight), still publish with `ownershipVerified = false`. Pinned in Task 1
   (`an unusable owner is published when the gate is off`) and by listings 103 and 105 in the endpoint tests.
3. **The job must not misclassify a non-ownership 409** — a listing raced out of `VERIFIED` (`LISTING_NOT_VERIFIED`)
   counts *blocked*, not *contradicted*, and blocked listings still do not consume the 100-per-run budget. Pinned in
   Task 2 (`publish - contradicted and other blocked refusals are counted apart`), with the existing
   `a page of 100 blocked listings does not starve the next page` kept green.
4. **A refused contradiction writes nothing** — status stays `VERIFIED`, `ownershipVerified` keeps its value, no save.
   Pinned in Task 1 (`verify(dao, never()).save(any())` in the unit tests, and listing 104 re-read in the endpoint
   test).
5. **The flag-on path and the #377 override are untouched** — every non-`VERIFIED` verdict still refuses with its
   reasons, contradicted or not; a backed override derives `VERIFIED`, passes and freezes `true`. Pinned in Task 1 by
   the unchanged `PublishListingEndpointTest` (listings 104 and 105), and by `OwnershipVerdictServiceTest` for the
   override derivation.

---

### Task 1: The gate in `ListingService`, the endpoint, and the docs

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt:25-26`
  (imports), `:58-61` (companion), `:342-390` (KDoc and `publish(listing)`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt:37-38`
  (import `OwnershipReasonCodes`) and the publish tests (~`:225-340`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingWithoutOwnerVerificationEndpointTest.kt`
  (imports and the tests)
- Modify: `modules/ndopify-server/CLAUDE.md:337-340` (the **ownership verdict** paragraph's last sentence) and
  `:561-567` (the **listing** section's **Publish** paragraph)

**Interfaces:**
- Consumes: `OwnershipVerdictService.verify(mandate): OwnershipVerification` with `status:
  OwnershipVerificationStatus` and `reasons: List<OwnershipVerificationReason>` (`code: String`, `details:
  List<String>`); `OwnershipReasonCodes`; `ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED`; `ConflictException(Error)`.
- Produces: `ListingService.CONTRADICTION_CODES: Set<String>` (`internal`, Task 2 reads it) and a `publish(listing)`
  that refuses with the existing 409 when `contradicted || (requireOwnerVerification && !verified)`.

- [ ] **Step 1: Write the failing unit tests**

In `ListingServiceTest`, add the import
`com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes`, then add these tests next to the gate-off ones:

```kotlin
    @Test
    fun `a contradicted owner is refused when the gate is off`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        listOf(
            OwnershipReasonCodes.ID_NAME_MISMATCH,
            OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH,
            OwnershipReasonCodes.PROPERTY_MISMATCH,
            OwnershipReasonCodes.ID_EXPIRED,
        ).forEach { code ->
            val reasons = listOf(OwnershipVerificationReason(code))
            doReturn(OwnershipVerification(status = OwnershipVerificationStatus.FAILED, reasons = reasons))
                .whenever(ownershipVerdictService).verify(verified.mandate)

            val ex = assertFailsWith<ConflictException> { openService.publish(verified) }

            assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code, code)
            assertEquals(mapOf("reasons" to reasons), ex.error.data, code)
        }
        verify(dao, never()).save(any())
    }

    @Test
    fun `a contradiction among unusable reasons is refused when the gate is off`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        val reasons = listOf(
            OwnershipVerificationReason(OwnershipReasonCodes.ID_UNUSABLE, listOf("DOCUMENT_UNREADABLE")),
            OwnershipVerificationReason(OwnershipReasonCodes.PROPERTY_MISMATCH),
        )
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.FAILED, reasons = reasons))
            .whenever(ownershipVerdictService).verify(verified.mandate)

        val ex = assertFailsWith<ConflictException> { openService.publish(verified) }

        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
        assertEquals(mapOf("reasons" to reasons), ex.error.data)
        verify(dao, never()).save(any())
    }

    @Test
    fun `an unusable owner is published when the gate is off`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        val reasons = listOf(
            OwnershipVerificationReason(OwnershipReasonCodes.ID_UNUSABLE, listOf("DOCUMENT_UNREADABLE")),
        )
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.FAILED, reasons = reasons))
            .whenever(ownershipVerdictService).verify(verified.mandate)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        val result = openService.publish(verified)

        assertEquals(ListingStatus.PUBLISHING, result.status)
        assertEquals(0, result.retries)
        assertFalse(result.ownershipVerified)
    }

    @Test
    fun `a verified owner is published when the gate is off`() {
        val verified = listing.copy(status = ListingStatus.VERIFIED)
        doReturn(OwnershipVerification(status = OwnershipVerificationStatus.VERIFIED))
            .whenever(ownershipVerdictService).verify(verified.mandate)
        doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

        val result = openService.publish(verified)

        assertEquals(ListingStatus.PUBLISHING, result.status)
        assertTrue(result.ownershipVerified)
    }
```

Also rename the existing `a failed owner is published when the gate is off` (a `FAILED` verdict with no reason) to
`a failed owner without a contradiction is published when the gate is off` — it keeps its assertions, it now documents
that an empty reason list is not a contradiction.

- [ ] **Step 2: Write the failing endpoint tests**

In `PublishListingWithoutOwnerVerificationEndpointTest`, add the imports
`com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes` and `org.springframework.http.ResponseEntity`, then
replace `a failed owner is published` with:

```kotlin
    @Test
    fun `a contradicted owner is refused with the reasons`() {
        val response = publish(104L)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
        assertEquals(
            listOf(OwnershipReasonCodes.ID_EXPIRED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH),
            reasons(response),
        )
        val listing = dao.findById(104L).get()
        assertEquals(ListingStatus.VERIFIED, listing.status)
        assertFalse(listing.ownershipVerified)
    }

    @Test
    fun `an in-flight owner is published`() {
        assertEquals(HttpStatus.OK, publish(105L).statusCode)

        val listing = dao.findById(105L).get()
        assertEquals(ListingStatus.PUBLISHING, listing.status)
        assertFalse(listing.ownershipVerified)
    }

    @Test
    fun `the contradiction 409 carries no name, document nor path`() {
        val body = rest.postForEntity("/v1/listings/104/publish", null, String::class.java).body!!

        assertFalse(body.contains("Ray"), body)
        assertFalse(body.contains("Pierre"), body)
        assertFalse(body.contains("TF-"), body)
        assertFalse(body.contains("1003"), body)
    }

    private fun reasons(response: ResponseEntity<ErrorResponse>): List<*> {
        val reasons = response.body!!.error.data!!["reasons"] as List<*>
        return reasons.map { reason -> (reason as Map<*, *>)["code"] }
    }
```

Leave `a pending owner is published` (103), `a verified owner is published` (100), `a listing not verified is still
refused` (101) and `not found - 404` as they are.

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingServiceTest,PublishListingWithoutOwnerVerificationEndpointTest'
```

Expected: FAIL — the four contradiction codes still publish (`PUBLISHING` instead of a `ConflictException`), and listing
104 answers 200 instead of 409.

- [ ] **Step 4: Add the blocked set and the guard**

In `ListingService`, import `com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes`, then declare the set in the
companion (visible to `ListingJobs`, Task 2):

```kotlin
    companion object {
        private val LOGGER = LoggerFactory.getLogger(ListingService::class.java)

        /** The verdict's reason codes that contradict the seller: read by the publication gate and by ListingJobs.publish. */
        internal val CONTRADICTION_CODES: Set<String> = setOf(
            OwnershipReasonCodes.ID_NAME_MISMATCH,
            OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH,
            OwnershipReasonCodes.PROPERTY_MISMATCH,
            OwnershipReasonCodes.ID_EXPIRED,
        )
    }
```

In `publish(listing)`, after `verified` and before the refusal:

```kotlin
        val contradicted = verification.reasons.any { it.code in CONTRADICTION_CODES }
        if (contradicted || (requireOwnerVerification && !verified)) {
            throw ConflictException(
                Error(
                    code = ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED,
                    data = mapOf("reasons" to verification.reasons),
                )
            )
        }
```

Update the two KDocs (`publish(id)` and `publish(listing)`) to say the 409 comes in both modes for a contradiction
code, and only with the flag on otherwise. Do not touch the save, the frozen fact nor the second pass.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingServiceTest,PublishListingWithoutOwnerVerificationEndpointTest,PublishListingEndpointTest'
```

Expected: PASS — the gate-on cases (104 and 105 refused with their reasons) are the regression that must stay green.

- [ ] **Step 6: Document the rule**

In `modules/ndopify-server/CLAUDE.md`, **listing** section's **Publish** paragraph, after the `retries` reset sentence,
add:

> The verdict's contradiction codes (`ListingService.CONTRADICTION_CODES`: `OWNERSHIP_ID_NAME_MISMATCH`,
> `OWNERSHIP_DOCUMENT_NAME_MISMATCH`, `OWNERSHIP_PROPERTY_MISMATCH`, `OWNERSHIP_ID_EXPIRED`) refuse in both modes:
> `ndopify.listing.publish.require-owner-verification` only excuses a `PENDING` or `*_UNUSABLE` verdict, never a
> contradicted one (#403). `ListingJobs.publish` counts a contradicted refusal apart from the other blocked ones in its
> run summary.

In the **ownership verdict** paragraph (mandate section), extend the last sentence:

> The verdict also gates publication (#299): a VERIFIED listing whose owner is not verified is not sent for
> publication, by the endpoint nor by `ListingJobs.publish` — and a verdict carrying a contradiction code
> (`OWNERSHIP_ID_NAME_MISMATCH`, `OWNERSHIP_DOCUMENT_NAME_MISMATCH`, `OWNERSHIP_PROPERTY_MISMATCH`,
> `OWNERSHIP_ID_EXPIRED`) refuses in both modes, whatever `ndopify.listing.publish.require-owner-verification` (#403).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingWithoutOwnerVerificationEndpointTest.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "feat(listing): block a contradicted owner verdict at publication" -m "Refs #403"
```

---

### Task 2: The publish job's summary counters

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobs.kt:1-16` (imports),
  `:55-98` (`runVerified` KDoc, counters, summary line) and a new `isContradicted` helper
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobsTest.kt` (imports, one
  test, one helper)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobWithoutOwnerVerificationTest.kt`
  (the existing publish test, imports, one helper)

**Interfaces:**
- Consumes: `ListingService.CONTRADICTION_CODES` (Task 1); `ConflictException.error.code` (`String`) and
  `ConflictException.error.data: Map<String, Any>?` carrying `"reasons"` as `List<OwnershipVerificationReason>`.
- Produces: the per-tenant first-pass summary line
  `"Tenant#$tenantId - ListingJobs.publish() completed: $published published, $contradicted contradicted, $blocked blocked, $errors errors"`.

- [ ] **Step 1: Write the failing unit test**

In `ListingJobsTest`, add the imports `ch.qos.logback.classic.Logger`,
`ch.qos.logback.classic.spi.ILoggingEvent`, `ch.qos.logback.core.read.ListAppender`,
`org.slf4j.LoggerFactory`, `com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason`,
`com.wutsi.ndopify.mandate.server.service.OwnershipReasonCodes` and `kotlin.test.assertTrue`, then add:

```kotlin
    @Test
    fun `publish - contradicted and other blocked refusals are counted apart`() {
        val contradicted = listing1.copy(status = ListingStatus.VERIFIED)
        val blocked = listing2.copy(status = ListingStatus.VERIFIED)
        val publishable = listing3.copy(status = ListingStatus.VERIFIED)
        setUpPublishListings(listOf(contradicted, blocked, publishable), emptyList())
        doThrow(
            ConflictException(
                Error(
                    code = ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED,
                    data = mapOf("reasons" to listOf(OwnershipVerificationReason(OwnershipReasonCodes.ID_EXPIRED))),
                )
            )
        ).whenever(service).publish(contradicted)
        doThrow(ConflictException(Error(code = ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED)))
            .whenever(service).publish(blocked)

        val lines = capturePublishLog { jobs.publish() }

        assertTrue(
            lines.contains(
                "Tenant#1 - ListingJobs.publish() completed: 1 published, 1 contradicted, 1 blocked, 0 errors"
            ),
            lines.toString(),
        )
    }

    private fun capturePublishLog(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(ListingJobs::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.map { it.formattedMessage }
    }
```

- [ ] **Step 2: Write the failing integration assertion**

In `PublishVerifiedListingsJobWithoutOwnerVerificationTest`, add the same logback imports plus `kotlin.test.assertTrue`,
rename the test to `publishes the pending ones and leaves the contradicted one`, and make it assert listing 102 stays
`VERIFIED` and the run counts it apart:

```kotlin
    @Test
    fun `publishes the pending ones and leaves the contradicted one`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        val lines = capturePublishLog { jobs.publish() }

        assertEquals("PUBLISHED", status(100))
        assertEquals("PUBLISHED", status(101))
        assertEquals("VERIFIED", status(102))
        assertEquals(1, frozen(100))
        assertEquals(0, frozen(101))
        assertEquals(0, frozen(102))
        assertTrue(
            lines.contains(
                "Tenant#1 - ListingJobs.publish() completed: 2 published, 1 contradicted, 0 blocked, 0 errors"
            ),
            lines.toString(),
        )
    }

    private fun capturePublishLog(block: () -> Unit): List<String> {
        val logger = LoggerFactory.getLogger(ListingJobs::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            block()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.map { it.formattedMessage }
    }
```

Also update the class KDoc: the gate off leaves a contradicted verdict unpublished.

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingJobsTest,PublishVerifiedListingsJobWithoutOwnerVerificationTest'
```

Expected: FAIL — the summary line still reads `... published, ... blocked, ... errors` with no `contradicted`, and
listing 102 moves to `PUBLISHED`.

- [ ] **Step 4: Split the counters in `ListingJobs`**

Import `com.wutsi.ndopify.error.dto.ErrorCode` and `com.wutsi.ndopify.mandate.dto.OwnershipVerificationReason`. In
`runVerified(tenantId)`, add `var contradicted = 0` beside `blocked`, classify the catch, and log the four counters:

```kotlin
                } catch (ex: ConflictException) {
                    if (isContradicted(ex)) {
                        contradicted++ // A verdict that contradicts the seller: retried next run like any blocked one
                    } else {
                        blocked++ // Not VERIFIED anymore, or a verdict the flag still refuses: retried next run
                    }
                }
```

```kotlin
        LOGGER.info(
            "Tenant#$tenantId - ListingJobs.publish() completed: $published published, $contradicted contradicted, " +
                "$blocked blocked, $errors errors"
        )
```

Add the helper after `runVerified(tenantId)`:

```kotlin
    /**
     * Whether [ex] is the gate refusing a contradicted verdict: a `LISTING_OWNERSHIP_NOT_VERIFIED` whose reasons carry
     * one of [ListingService.CONTRADICTION_CODES]. Read from the 409, never by re-deriving the verdict.
     */
    private fun isContradicted(ex: ConflictException): Boolean {
        if (ex.error.code != ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED) {
            return false
        }
        val reasons = ex.error.data?.get("reasons") as? List<*> ?: return false
        return reasons.any { reason -> (reason as? OwnershipVerificationReason)?.code in ListingService.CONTRADICTION_CODES }
    }
```

Update the `runVerified` KDoc: a blocked listing is counted *contradicted* when the 409's reasons carry a contradiction
code, *blocked* otherwise; neither consumes the 100-per-run budget.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest='ListingJobsTest,PublishVerifiedListingsJobWithoutOwnerVerificationTest,PublishVerifiedListingsJobTest,CloseListingsJobTest'
```

Expected: PASS — including the existing `a page of 100 blocked listings does not starve the next page`.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobs.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobsTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobWithoutOwnerVerificationTest.kt
git commit -m "feat(listing): count a contradicted owner refusal apart in the publish job" -m "Refs #403"
```

---

## Verification

```bash
cd modules/ndopify-server && mvn verify
```

Expected: BUILD SUCCESS — ktlint, every test against local MySQL, and the JaCoCo gate. No other module changes, no
version bump, no release.

## Self-Review

- **Spec coverage**: the gate and its four codes (Task 1), the frozen fact and the refused-write guard (Task 1), the
  endpoint both modes (Task 1), the job's split counters (Task 2), the docs (Task 1), the untouched DTO/read paths
  (Global Constraints). #377's override needs no code: the gate reads the derived status, and
  `OwnershipVerdictServiceTest` already pins that a backed override yields `VERIFIED`.
- **Step scan**: each step names its test, its command or its exact signature; the condition and the summary-line
  format are the only bodies given, because the spec fixes them verbatim.
- **Type consistency**: `CONTRADICTION_CODES` is `Set<String>` in `ListingService`'s companion; `isContradicted` takes
  the caught `ConflictException` and reads `error.code` / `error.data["reasons"]`; the summary line's four counters are
  the same names in Task 2's tests and implementation.
- **Review Focus**: each of the five lines names the test that pins it (Task 1, Task 2); no uncovered failure mode is
  left without an owning task.
- **Proportion**: two tasks, each a review gate; the plan carries no full production bodies beyond the one guard
  condition and the log line.
