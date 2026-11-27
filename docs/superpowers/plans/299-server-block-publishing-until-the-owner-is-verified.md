# Block publishing until the owner is verified — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refuse to send a VERIFIED listing for publication — from `POST /v1/listings/{id}/publish` and from the 15-minute `ListingJobs.publish` first pass — until its mandate's ownership verdict is `VERIFIED`, answering 409 `LISTING_OWNERSHIP_NOT_VERIFIED` with the verdict's reason codes.

**Architecture:** Add one `ErrorCode` to `ndopify-dto`, then put the gate inside `ListingService.publish(listing: ListingEntity)` (after the `LISTING_NOT_VERIFIED` check) by calling #297's `OwnershipVerdictService.verify(listing.mandate)`. The job's first pass stops using the single `search(VERIFIED, limit=100)` page: it pages the VERIFIED listings by id (`id > last seen`, 100 per page), calls the same `publish(listing)`, counts a `ConflictException` as blocked without a per-listing warning, and stops after 100 successful transitions per tenant per run. Nothing is stored: the verdict is derived on every read.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, MySQL + Flyway SQL fixtures (endpoint tests), `kotlin.test`, `mockito-kotlin`, `TestRestTemplate`.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/299 — the enriched story is the design (its *Scope*, *Acceptance criteria* and *Enriched (groom, 2026-11-27)* sections). #294 is the epic; #297 provides the verdict and its DTOs; #375 is the NPC explanation; #300 the public badge.

## Global Constraints

- Code, comments, logs and docs in English; no user-facing text in this story (`UI: no`).
- `ndopify-dto`'s `Error` has `data: Map<String, Any>?` and one `parameter: Parameter?`; there is no `parameters` list. The reasons go in `data["reasons"]` as `OwnershipVerificationReason` objects (`code`, and `details` for `*_UNUSABLE`).
- The 409 carries codes and document problem codes only: never a customer name, a document id or title, a page, a path or a URL.
- The verdict is derived on every call; no new table, column, migration, cache or stored flag.
- The error code is exactly `LISTING_OWNERSHIP_NOT_VERIFIED = "$PREFIX:listing:ownership-not-verified"` (`urn:wutsi:ndopify:error:listing:ownership-not-verified`).
- The check order is fixed: 404 `LISTING_NOT_FOUND` → 403 `HTTP_ACCESS_DENIED` → 409 `LISTING_NOT_VERIFIED` → 409 `LISTING_OWNERSHIP_NOT_VERIFIED`. Access stays in `ListingService.publish(id)`; the gate lives in `publish(listing)`.
- The job's first pass never logs a per-listing warning for a blocked listing, never sends more than 100 listings for publication per tenant per run, and never re-checks a `PUBLISHING` listing. The second pass and `ListingPublisherService` are unchanged.
- `POST /v1/listings/{id}/verify` stays open to any owner verdict; the public reads and the listing verification are unchanged.
- `ndopify-dto` is a released artifact: the branch ends with the DTO released and `<ndopify-dto.version>` set to the released version, never a `-SNAPSHOT`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. `mvn validate` (ktlint) and the JaCoCo gate must pass.

## Review Focus

1. **Order of the two 409s.** A `DRAFT`/`UNDER_VERIFICATION` listing whose mandate is also unverified must answer `LISTING_NOT_VERIFIED`, never the ownership code; the ownership check runs only after the status check. A non-VERIFIED listing must not even read the verdict.
2. **An in-flight verdict is not a pass.** `PENDING` with no reason (`UPLOADED`/`EXTRACTING` documents) still answers 409, with `data.reasons` empty — never treat an empty reason list as verified.
3. **The batch of 100.** A page of 100 blocked listings must be skipped and the next page fetched; a run must stop at 100 successful transitions, even when the 100th is mid-page. A blocked listing must not starve a publishable one with a higher id.
4. **The verdict flips without a write.** A document reset to `UPLOADED` must block on the next call; a document becoming `EXTRACTED` must unblock on the next job run. No cache, no stored status.
5. **The second pass is untouched.** A `PUBLISHING` listing is published without a verdict read, even if its owner's documents changed after the gate. Existing `PUBLISHED`/`CLOSED` flows are unchanged.

---

### Task 1: The DTO error code

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt` (the listing group)
- Modify: `pom.xml:52` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces: `ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED: String` — used by `ListingService` in Task 2 and by the endpoint tests in Task 3.

- [ ] **Step 1: Add the constant**

In the listing group of `ErrorCode.kt`, immediately after `LISTING_NOT_VERIFIED`:

```kotlin
val LISTING_OWNERSHIP_NOT_VERIFIED = "$PREFIX:listing:ownership-not-verified"
```

- [ ] **Step 2: Build and install the DTO locally**

```bash
cd modules/ndopify-dto && mvn -q install
```

Expected: BUILD SUCCESS. (The DTO has no tests; the constant is exercised by Task 2's tests.)

- [ ] **Step 3: Point the server at the DTO snapshot while developing**

In the root `pom.xml`, set `<ndopify-dto.version>0.0.113-SNAPSHOT</ndopify-dto.version>` (the DTO module's current version).

- [ ] **Step 4: Release the DTO**

Run the developer agent's shared-module release procedure for `ndopify-dto` (the profile's `shared_modules`): push the DTO change, wait for `ndopify-dto-main.yml`, then set `<ndopify-dto.version>` to the released version (`0.0.113`, not `-SNAPSHOT`) and commit it as `fix(deps): update ndopify-dto to <version>`. The branch must not end on a `-SNAPSHOT`.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt pom.xml
git commit -m "feat(dto): add the listing ownership error code" -m "Refs #299"
```

---

### Task 2: The gate in `ListingService`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt`

**Interfaces:**
- Consumes: `OwnershipVerdictService.verify(mandate: MandateEntity): OwnershipVerification`; `OwnershipVerificationStatus.VERIFIED`; `OwnershipVerificationReason` (all from #297).
- Produces: `ListingService.publish(listing: ListingEntity): ListingEntity` — now throws `ConflictException` with `ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED` and `Error.data = mapOf("reasons" to verification.reasons)` when the verdict is not `VERIFIED`. `ListingService.publish(id: Long)` keeps its 404 → 403 → gate order.

- [ ] **Step 1: Write the failing unit tests**

In `ListingServiceTest`, add `private val ownershipVerdictService = mock<OwnershipVerdictService>()` and pass it to the constructor between `accessService` and `clock`. Add:

```kotlin
@Test
fun `a verified listing with a verified owner is sent for publication`() {
    val verified = listing.copy(status = ListingStatus.VERIFIED)
    doReturn(OwnershipVerification(status = OwnershipVerificationStatus.VERIFIED))
        .whenever(ownershipVerdictService).verify(verified.mandate)
    doAnswer { it.getArgument<ListingEntity>(0) }.whenever(dao).save(any())

    val result = service.publish(verified)

    assertEquals(ListingStatus.PUBLISHING, result.status)
    assertEquals(0, result.retries)
}

@Test
fun `a listing not verified is refused before the owner verdict`() {
    val ex = assertFailsWith<ConflictException> { service.publish(listing) }

    assertEquals(ErrorCode.LISTING_NOT_VERIFIED, ex.error.code)
    verifyNoInteractions(ownershipVerdictService)
}

@Test
fun `a verified listing whose owner is not verified is refused with the reasons`() {
    val verified = listing.copy(status = ListingStatus.VERIFIED)
    val reasons = listOf(
        OwnershipVerificationReason("OWNERSHIP_ID_MISSING"),
        OwnershipVerificationReason("OWNERSHIP_DOCUMENT_MISSING"),
    )
    doReturn(OwnershipVerification(status = OwnershipVerificationStatus.PENDING, reasons = reasons))
        .whenever(ownershipVerdictService).verify(verified.mandate)

    val ex = assertFailsWith<ConflictException> { service.publish(verified) }

    assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
    assertEquals(mapOf("reasons" to reasons), ex.error.data)
    verify(dao, never()).save(any())
}

@Test
fun `an in-flight owner verdict with no reason is still refused`() {
    val verified = listing.copy(status = ListingStatus.VERIFIED)
    doReturn(OwnershipVerification(status = OwnershipVerificationStatus.PENDING))
        .whenever(ownershipVerdictService).verify(verified.mandate)

    val ex = assertFailsWith<ConflictException> { service.publish(verified) }

    assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
    assertEquals(mapOf("reasons" to emptyList<OwnershipVerificationReason>()), ex.error.data)
}

@Test
fun `a failed owner verdict is refused with its reasons`() {
    val verified = listing.copy(status = ListingStatus.VERIFIED)
    val reasons = listOf(
        OwnershipVerificationReason("OWNERSHIP_ID_UNUSABLE", listOf("DOCUMENT_UNREADABLE")),
    )
    doReturn(OwnershipVerification(status = OwnershipVerificationStatus.FAILED, reasons = reasons))
        .whenever(ownershipVerdictService).verify(verified.mandate)

    val ex = assertFailsWith<ConflictException> { service.publish(verified) }

    assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, ex.error.code)
    assertEquals(mapOf("reasons" to reasons), ex.error.data)
}

@Test
fun `the access check comes before the owner verdict`() {
    doReturn(Optional.of(listing.copy(status = ListingStatus.VERIFIED))).whenever(dao).findById(101L)
    doThrow(ForbiddenException(Error(ErrorCode.HTTP_ACCESS_DENIED)))
        .whenever(accessService).ensureCanUpdate(any())

    assertFailsWith<ForbiddenException> { service.publish(101L) }
    verifyNoInteractions(ownershipVerdictService)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingServiceTest
```

Expected: compilation failure (`ownershipVerdictService` missing from the constructor, `LISTING_OWNERSHIP_NOT_VERIFIED` unresolved).

- [ ] **Step 3: Implement the gate**

Add `private val ownershipVerdictService: OwnershipVerdictService` to the constructor (between `accessService` and `clock`). In `publish(listing: ListingEntity)`, after the `LISTING_NOT_VERIFIED` check:

```kotlin
val verification = ownershipVerdictService.verify(listing.mandate)
if (verification.status != OwnershipVerificationStatus.VERIFIED) {
    throw ConflictException(
        Error(
            code = ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED,
            data = mapOf("reasons" to verification.reasons),
        )
    )
}
```

Keep the existing `dao.save(listing.copy(status = PUBLISHING, retries = 0, modifiedAt = ...))`. Extend the KDoc of `publish(id)` and `publish(listing)` with the new 409 and the "reasons in `data.reasons`" contract.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingServiceTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingServiceTest.kt
git commit -m "feat(listing): gate publication on the owner verification" -m "Refs #299"
```

---

### Task 3: The endpoint contract and its fixtures

**Files:**
- Modify: `modules/ndopify-server/src/test/resources/db/test/listing/PublishListingEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingEndpointTest.kt`

**Interfaces:**
- Consumes: the gate from Task 2; `ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED` from Task 1.

- [ ] **Step 1: Write the failing endpoint tests**

In `PublishListingEndpointTest` (admin role, as today):

```kotlin
@Test
fun `owner not verified - 409 with the missing reasons`() {
    val response = publish(103L)

    assertEquals(HttpStatus.CONFLICT, response.statusCode)
    assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
    assertEquals(
        listOf(OwnershipReasonCodes.ID_MISSING, OwnershipReasonCodes.DOCUMENT_MISSING),
        reasons(response),
    )
    assertEquals(ListingStatus.VERIFIED, dao.findById(103L).get().status)
}

@Test
fun `failed owner - 409 with its reasons`() {
    val response = publish(104L)

    assertEquals(HttpStatus.CONFLICT, response.statusCode)
    assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
    assertEquals(
        listOf(OwnershipReasonCodes.ID_EXPIRED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH),
        reasons(response),
    )
    assertEquals(ListingStatus.VERIFIED, dao.findById(104L).get().status)
}

@Test
fun `in-flight owner - 409 with no reason`() {
    val response = publish(105L)

    assertEquals(HttpStatus.CONFLICT, response.statusCode)
    assertEquals(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED, response.body?.error?.code)
    assertTrue(reasons(response).isEmpty())
    assertEquals(ListingStatus.VERIFIED, dao.findById(105L).get().status)
}

@Test
fun `not verified wins over the owner verdict`() {
    // Listing 101 is UNDER_VERIFICATION and its mandate has no document: the status check answers first
    val response = publish(101L)

    assertEquals(HttpStatus.CONFLICT, response.statusCode)
    assertEquals(ErrorCode.LISTING_NOT_VERIFIED, response.body?.error?.code)
}

@Test
fun `the ownership 409 carries no name, document nor path`() {
    val body = rest.postForEntity("/v1/listings/104/publish", null, String::class.java).body!!

    assertFalse(body.contains("Ray"), body)
    assertFalse(body.contains("Pierre"), body)
    assertFalse(body.contains("TF-"), body)
    assertFalse(body.contains("mandate/"), body)
    assertFalse(body.contains("1003"), body)
}

private fun reasons(response: ResponseEntity<ErrorResponse>): List<*?> {
    val reasons = response.body!!.error.data!!["reasons"] as List<*>
    return reasons.map { reason -> (reason as Map<*, *>)["code"] }
}
```

The existing `publish` (100), `not verified - 409` (101), `already published - 409` (102), `anonymous - 401`,
`not found - 404` and `other tenant - 404` tests stay; listing 100 passes the gate once Step 3 adds its documents.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=PublishListingEndpointTest
```

Expected: the new tests fail (listings 103–105 do not exist yet) and `publish` (100) fails with
`LISTING_OWNERSHIP_NOT_VERIFIED` (its mandate has no document).

- [ ] **Step 3: Extend the SQL fixture**

In `PublishListingEndpoint.sql`:
- Add mandates 4, 5 and 6 (tenant 1, customer 10, agent 20, `APPROVED`, `LISTING`) and listings:
  - `103` on mandate 4 — `VERIFIED`, no document (verdict `PENDING`, two missing reasons);
  - `104` on mandate 5 — `VERIFIED`, an expired ID and a title naming another person (verdict `FAILED`);
  - `105` on mandate 6 — `VERIFIED`, an `UPLOADED` ID and an `EXTRACTED` valid title (verdict `PENDING`, no reason).
- Add the documents of mandate 1 so listing 100 is publishable:

```sql
INSERT INTO T_MANDATE_DOCUMENT(
    id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at, expires_at,
    version, created_at, modified_at
) VALUES
    (1000, 1, 1, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2099-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1001, 1, 1, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-9","owners":["Ray Sponsible"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1002, 1, 5, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', 'DOCUMENT_EXPIRED',
     '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2020-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
     1, NULL, '2020-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1003, 1, 5, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-8","owners":["Pierre Durand"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1004, 1, 6, 'NATIONAL_ID', 'Carte d''identité', 'UPLOADED', NULL,
     NULL, NULL, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
    (1005, 1, 6, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
     '{"detectedType":"LAND_TITLE","titleNumber":"TF-9","owners":["Ray Sponsible"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
     1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00');
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=PublishListingEndpointTest
```

Expected: PASS (local MySQL required).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/listing/PublishListingEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PublishListingEndpointTest.kt
git commit -m "test(listing): cover the ownership publication gate" -m "Refs #299"
```

---

### Task 4: The job's first pass

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/dao/ListingRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobs.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobsTest.kt`

**Interfaces:**
- Produces:
  - `ListingRepository.findByStatusAndIdGreaterThanOrderByIdAsc(status: ListingStatus, id: Long, pageable: Pageable): List<ListingEntity>`
  - `ListingService.findVerifiedAfter(id: Long, limit: Int): List<ListingEntity>` — the VERIFIED listings with `id > id`, in id order.
- `ListingJobs.publish(): Unit` keeps its signature.

- [ ] **Step 1: Write the failing unit tests**

In `ListingJobsTest`, replace `setUpPublishListings` so that the first pass is mocked through `findVerifiedAfter` and the second through `search`:

```kotlin
private fun setUpPublishListings(verified: List<ListingEntity>, publishing: List<ListingEntity>) {
    doReturn(verified).whenever(service).findVerifiedAfter(any(), any())
    doReturn(publishing).whenever(service).search(
        SearchListingRequest(statuses = listOf(ListingStatus.PUBLISHING), limit = 100)
    )
}
```

Update the existing publish tests to assert `findVerifiedAfter(0L, 100)` where they asserted the VERIFIED `search`, then add:

```kotlin
@Test
fun `publish - a blocked verified listing is skipped quietly and the scan continues`() {
    val blocked = listing1.copy(status = ListingStatus.VERIFIED)
    val publishable = listing2.copy(status = ListingStatus.VERIFIED)
    setUpPublishListings(listOf(blocked, publishable), emptyList())
    doThrow(ConflictException(Error(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED)))
        .whenever(service).publish(blocked)

    jobs.publish()

    verify(service).publish(blocked)
    verify(service).publish(publishable)
}

@Test
fun `publish - a page of 100 blocked listings does not starve the next page`() {
    val blocked = (1L..100L).map { id -> ListingEntity(id = id, status = ListingStatus.VERIFIED) }
    val publishable = ListingEntity(id = 101L, status = ListingStatus.VERIFIED)
    doReturn(blocked, listOf(publishable)).whenever(service).findVerifiedAfter(any(), any())
    doReturn(emptyList<ListingEntity>()).whenever(service).search(any())
    blocked.forEach { listing ->
        doThrow(ConflictException(Error(ErrorCode.LISTING_OWNERSHIP_NOT_VERIFIED)))
            .whenever(service).publish(listing)
    }

    jobs.publish()

    verify(service).findVerifiedAfter(0L, 100)
    verify(service).findVerifiedAfter(100L, 100)
    verify(service).publish(publishable)
}

@Test
fun `publish - stops after 100 publications`() {
    val publishable = (1L..100L).map { id -> ListingEntity(id = id, status = ListingStatus.VERIFIED) }
    doReturn(publishable).whenever(service).findVerifiedAfter(any(), any())
    doReturn(emptyList<ListingEntity>()).whenever(service).search(any())

    jobs.publish()

    verify(service).findVerifiedAfter(0L, 100)
    verify(service, times(100)).publish(any<ListingEntity>())
}
```

Update the multi-tenant test so `findVerifiedAfter` returns one VERIFIED listing per tenant: a page shorter than `LIMIT`
ends the scan, so the job makes one `findVerifiedAfter` call per tenant and no infinite page. Update
`a failing tenant doesn't stop the others` so tenant 1's `findVerifiedAfter` throws and tenant 2 still publishes.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingJobsTest
```

Expected: compilation failure (`findVerifiedAfter` missing).

- [ ] **Step 3: Implement the keyset read and the first pass**

- In `ListingRepository`:
```kotlin
fun findByStatusAndIdGreaterThanOrderByIdAsc(
    status: ListingStatus,
    id: Long,
    pageable: Pageable,
): List<ListingEntity>
```
- In `ListingService`:
```kotlin
/** The VERIFIED listings after [id], in id order: the publish job's keyset page. */
fun findVerifiedAfter(id: Long, limit: Int): List<ListingEntity> =
    dao.findByStatusAndIdGreaterThanOrderByIdAsc(ListingStatus.VERIFIED, id, OffsetLimitPageRequest(0, limit))
```
- In `ListingJobs`, keep `run(...)` for `verify`, `publishing` and `close`, and extract its tenant loop into
  `private fun forEachTenant(job: String, process: (Long) -> Unit)` (same `TenantContext` set/remove and tenant-level
  warning as today). Replace the first line of `publish()` with `runVerified()` and add:

```kotlin
private fun runVerified() {
    forEachTenant("publish") { tenantId -> runVerified(tenantId) }
}

private fun runVerified(tenantId: Long) {
    var lastId = 0L
    var published = 0
    var blocked = 0
    var errors = 0
    while (published < LIMIT) {
        val listings = service.findVerifiedAfter(lastId, LIMIT)
        if (listings.isEmpty()) break
        listings.forEach { listing ->
            lastId = listing.id!!
            try {
                service.publish(listing)
                published++
            } catch (ex: ConflictException) {
                blocked++ // Not VERIFIED anymore, or the owner is not verified: retried next run, no warning
            } catch (ex: Exception) {
                LOGGER.warn("Tenant#$tenantId - Unable to publish Listing#${listing.id}", ex)
                errors++
            }
            if (published >= LIMIT) return@forEach
        }
        if (listings.size < LIMIT) break
    }
    LOGGER.info(
        "Tenant#$tenantId - ListingJobs.publish() completed: $published published, $blocked blocked, $errors errors"
    )
}
```

  Two details are part of the contract: the inner `forEach` stops at `published >= LIMIT` (so the 100th may be
  mid-page, and no extra listing is sent), and a page shorter than `LIMIT` ends the scan (the last page). The
  `PUBLISHING` second pass stays `run("publishing", ListingStatus.PUBLISHING) { ... }`.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd modules/ndopify-server && mvn test -Dtest=ListingJobsTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/dao/ListingRepository.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobs.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/ListingJobsTest.kt
git commit -m "feat(listing): skip unverified owners in the publish job" -m "Refs #299"
```

---

### Task 5: The job's integration test

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/listing/PublishVerifiedListingsJob.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobTest.kt`

**Interfaces:**
- Consumes: `ListingJobs.publish()` (Task 4), the gate (Task 2), #297's verdict.

- [ ] **Step 1: Write the failing test**

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishVerifiedListingsJob.sql"])
class PublishVerifiedListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var publishedNotifier: ListingPublishedNotifier

    @Test
    fun `publishes only the listing whose owner is verified`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())

        jobs.publish()

        assertEquals("PUBLISHED", status(100))
        assertEquals("VERIFIED", status(101))
        assertEquals("VERIFIED", status(102))
    }

    @Test
    fun `a blocked listing is published on the first run after its owner is verified`() {
        doReturn(ListingContentInfo(title = "Appartement à louer")).whenever(generator).generate(any())
        jobs.publish()
        assertEquals("VERIFIED", status(101))

        jdbc.update(
            """
            INSERT INTO T_MANDATE_DOCUMENT(
                id, tenant_id, mandate_id, type, title, status, problem_codes, data, data_version, issued_at,
                expires_at, version, created_at, modified_at
            ) VALUES
                (2000, 1, 2, 'NATIONAL_ID', 'Carte d''identité', 'EXTRACTED', NULL,
                 '{"detectedType":"NATIONAL_ID","number":"AA1","firstName":"Ray","lastName":"Sponsible","expiryDate":"2099-01-01","sidesPresent":["FRONT","BACK"],"valid":true,"fieldConfidences":{"number":0.9,"firstName":0.9,"lastName":0.9}}',
                 1, NULL, '2099-01-01', 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00'),
                (2001, 1, 2, 'LAND_TITLE', 'Titre foncier', 'EXTRACTED', NULL,
                 '{"detectedType":"LAND_TITLE","titleNumber":"TF-9","owners":["Ray Sponsible"],"valid":true,"fieldConfidences":{"titleNumber":0.9,"owners":0.9}}',
                 1, NULL, NULL, 0, '2026-01-01 00:00:00', '2026-01-01 00:00:00')
            """.trimIndent(),
        )

        jobs.publish()

        assertEquals("PUBLISHED", status(101))
        assertEquals("VERIFIED", status(102))
    }

    private fun status(id: Long): String =
        jdbc.queryForObject("SELECT status FROM T_LISTING WHERE id=?", String::class.java, id)!!
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd modules/ndopify-server && mvn test -Dtest=PublishVerifiedListingsJobTest
```

Expected: FAIL — the SQL fixture does not exist.

- [ ] **Step 3: Write the fixture**

One active tenant, city, customer/party `Ray Sponsible`, agent `Kim Possible`, and three `VERIFIED` listings:

| Listing | Mandate | Documents | Verdict |
| --- | --- | --- | --- |
| 100 | 1 | valid `NATIONAL_ID` + `LAND_TITLE` | `VERIFIED` |
| 101 | 2 | none | `PENDING` |
| 102 | 3 | expired ID + title naming `Pierre Durand` | `FAILED` |

Use the same document JSON values as Task 3, with `mandate_id` 1 for ids 1000–1001 and `mandate_id` 3 for ids
1002–1003; mandate 2 has no rows. Add `T_TENANT(... active, public_portal_url ...)` and the
location/customer/agent rows like `PublishListingsJob.sql`.

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd modules/ndopify-server && mvn test -Dtest=PublishVerifiedListingsJobTest
```

Expected: PASS (local MySQL required).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/listing/PublishVerifiedListingsJob.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/PublishVerifiedListingsJobTest.kt
git commit -m "test(listing): cover the publish job's ownership gate" -m "Refs #299"
```

---

### Task 6: Module documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **listing** bullet's *Publish* paragraph, and the **mandate** bullet's *Ownership verdict* paragraph)

**Interfaces:** none.

- [ ] **Step 1: Document the gate**

In the **listing** bullet's *Publish* paragraph, after the existing `LISTING_NOT_VERIFIED` sentence, add: the gate
also answers 409 `LISTING_OWNERSHIP_NOT_VERIFIED`, with `data.reasons` holding #297's verdict reasons, when the
mandate's verdict is not `VERIFIED`; the first pass of `ListingJobs.publish` pages the VERIFIED listings by id,
skips a blocked one quietly, sends at most 100 for publication per tenant and run, and re-checks nothing on the
`PUBLISHING` second pass. In the **mandate** bullet's *Ownership verdict* paragraph, add a sentence that the verdict
also gates publication (#299).

- [ ] **Step 2: Verify**

```bash
mvn verify
```

Expected: BUILD SUCCESS, ktlint clean, coverage gate met (`ndopify-server` needs local MySQL).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document the ownership publication gate" -m "Refs #299"
```

---

## Rollout

- No migration and no backfill. At deploy, a `VERIFIED` listing whose mandate has no documents — or a `PENDING` or
  `FAILED` verdict — stays `VERIFIED` and is no longer sent for publication; the first run after its documents make
  the verdict `VERIFIED` publishes it.
- Already-`PUBLISHED` listings stay up. A `PUBLISHING` listing completes through the unchanged second pass.
- The new error code is released in `ndopify-dto` before the server deploys; the job needs no configuration.
