# KYC cases and identifications: its party or an admin — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Only a KYC case's own party or an admin reads the case, submits it, or gets one of its image URLs; a
non-admin searches and creates cases and identifications only for its own party.

**Architecture:** A new `KycAccessService` in `ndopify-server`'s party domain holds the rule. `KycEndpoints` runs it
for the reads (`get`, `search`, image URL), and `KycService` / `IdentificationService` run it for the changes
(`submit`, `create`). `KycService.findById` and `search` stay unchecked so the server's own reads (`KycJobs`,
`KycEventListener`, `AgentService.sendWelcomeEmail`, `CreateAgentEndpointTest`) keep working. The image writes keep
their stronger rule: the case's own party only, no admin bypass.

**Tech Stack:** Kotlin, Spring Boot, `TestRestTemplate` against MySQL, JUnit 5 / `kotlin.test`, mockito-kotlin,
Flyway test fixtures under `db/test/party/`.

**Spec:** GitHub issue #352 (enriched body: *Scope*, *Acceptance criteria*, *Decisions and assumptions*, *Risks*).

## Global Constraints

- `UI: no` — no prototype, no HTML/Thymeleaf change. `Shared code: none` — no DTO change, no library release.
- A caller that may not access a case or image gets **403 `HTTP_ACCESS_DENIED`**, never 404. Another tenant and an
  unknown id keep their 404 (`@TenantId` / `findById`), and the 404 is checked before the 403.
- **Admin** is `CallerService.isAdmin` exactly: an `admin-console` application token with the `admin` role. An admin
  may read, submit and create for any party. The image writes (`upload`, `presign`, `attach`) stay the case's own
  party's only — no admin bypass.
- A non-admin's search is **silently forced** to its own party, whatever `partyId` it passes. A non-admin with no
  party (no membership, or a membership without a party) gets 403 for search/get/submit/create.
- The checks stay out of `KycService.findById` and `KycService.search`; the server's internal reads have no caller.
- No new endpoint, no migration, no response-shape change.
- Test conventions: `kotlin.test` assertions, mockito-kotlin mocks, one endpoint test class per operation with its SQL
  fixture. Test users: **11** is party 100 (add `T_USER` + `T_MEMBERSHIP`), **12** is a user with no membership.
  Admin cases reuse user 11 with `roles = arrayOf(RoleCode.ADMIN)`.
- `mvn verify` green (ktlint, tests, JaCoCo); ndopify-server tests need a local MySQL.

## Review Focus

1. **A non-admin of party A asks for party B's case, submits it, or asks for its image URL** → 403, nothing written,
   no URL. Tasks 2, 4, 5.
2. **A non-admin searches with no `partyId`, its own, or another party's** → own cases only, never a 403 for the
   foreign id. Task 3.
3. **An admin of another tenant-less party reads, submits and creates for any party** → allowed. Tasks 2–7.
4. **A caller with no party** → 403, never a 500/NPE. Tasks 2–7.
5. **The server's own KYC reads still work with no caller** (`AgentService.sendWelcomeEmail` on an async listener
   thread, `KycJobs`, `KycEventListener`, `CreateAgentEndpointTest`). Tasks 3, 6, 7.

---

### Task 1: `KycAccessService` — the one rule

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycAccessService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/KycAccessServiceTest.kt`

**Interfaces — Produces:**

```kotlin
/**
 * Who may read or create a party's KYC data: the party itself (JWT user → membership → party) or an admin
 * ([CallerService.isAdmin]). The image writes stay the party's alone ([ensureIsParty], no admin bypass: a KYC
 * document is personal). 401 AUTH_UNAUTHORIZED without a token; 403 HTTP_ACCESS_DENIED otherwise.
 */
@Service
class KycAccessService(private val callerService: CallerService) {
    fun ensureIsParty(partyId: Long?)
    fun ensureIsPartyOrAdmin(partyId: Long?)
    fun ensureCanRead(case: KycCaseEntity)       // delegates to ensureIsPartyOrAdmin(case.party.id)
    fun ensureCanRead(image: KycImageEntity)     // delegates to ensureCanRead(image.kycCase)
    fun restrict(request: SearchKycCaseRequest): SearchKycCaseRequest
}
```

- `ensureIsParty`: `getPrincipal()` (401 without a token); `partyId != null && findPartyIdOrNull(principal) ==
  partyId`, else `ForbiddenException(Error(ErrorCode.HTTP_ACCESS_DENIED))`. No admin check.
- `ensureIsPartyOrAdmin`: return when `isAdmin(principal)`, else the `ensureIsParty` check.
- `restrict`: return `request` unchanged for an admin; else `request.copy(partyId = findPartyIdOrNull(principal))`,
  and 403 when that id is null. It never inspects the request's `partyId`.

- [ ] **Step 1: Write the failing test** `KycAccessServiceTest` (mock `CallerService`, principal; `assertDenied`
  asserts `ForbiddenException.error.code == ErrorCode.HTTP_ACCESS_DENIED`):

```kotlin
class KycAccessServiceTest {
    private val callerService = mock<CallerService>()
    private val principal = mock<JwtPrincipal>()
    private val service = KycAccessService(callerService)

    @BeforeEach
    fun setUp() = doReturn(principal).whenever(callerService).getPrincipal()

    private fun assertDenied(block: () -> Unit) {
        val ex = assertFailsWith<ForbiddenException> { block() }
        assertEquals(ErrorCode.HTTP_ACCESS_DENIED, ex.error.code)
    }

    @Test fun `admin may read any party`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        service.ensureIsPartyOrAdmin(999L) // no throw
    }

    @Test fun `party may read itself`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        service.ensureIsPartyOrAdmin(100L)
    }

    @Test fun `party may not read another party`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertDenied { service.ensureIsPartyOrAdmin(200L) }
    }

    @Test fun `party without a party is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.ensureIsPartyOrAdmin(100L) }
    }

    @Test fun `admin may not write an image of another party`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertDenied { service.ensureIsParty(200L) }
    }

    @Test fun `search is forced to the caller party`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(100L)
        assertEquals(100L, service.restrict(SearchKycCaseRequest(partyId = 999L)).partyId)
    }

    @Test fun `search of a party-less caller is denied`() {
        whenever(callerService.findPartyIdOrNull(principal)).thenReturn(null)
        assertDenied { service.restrict(SearchKycCaseRequest()) }
    }

    @Test fun `admin search is not restricted`() {
        whenever(callerService.isAdmin(principal)).thenReturn(true)
        val request = SearchKycCaseRequest(partyId = 999L)
        assertSame(request, service.restrict(request))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=KycAccessServiceTest`
Expected: FAIL — `KycAccessService` does not exist / does not compile.

- [ ] **Step 3: Implement `KycAccessService`** with the signatures above. A private
  `accessDenied() = ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))` keeps the three throws
  identical. Import `KycCaseEntity`, `KycImageEntity`, `SearchKycCaseRequest`, `CallerService`, `ForbiddenException`,
  `Error`, `ErrorCode`.

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=KycAccessServiceTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycAccessService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/KycAccessServiceTest.kt
git commit -m "feat(server): add the KYC access rule" -m "Refs #352"
```

---

### Task 2: `GET /v1/kyc/cases/{id}` — the case's party or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetKycCaseEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/party/GetKycCaseEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureCanRead(case: KycCaseEntity)` (Task 1).

- [ ] **Step 1: Update the fixture** — add party 200 (tenant 1), case `kyc-case-201` (party 200), and the caller rows:

```sql
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (200, 1, 'other.party@gmail.com', 'Other', 'Party', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-201', 1, 200, NULL, 'PENDING_VERIFICATION', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

- [ ] **Step 2: Write the failing tests** in `GetKycCaseEndpointTest` — add `private var admin = false`,
  `override val roles get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")`, and:

```kotlin
@Test fun `another party's case - forbidden`() {
    val response = rest.getForEntity("/v1/kyc/cases/kyc-case-201", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}

@Test fun `admin reads another party's case`() {
    admin = true
    val response = rest.getForEntity("/v1/kyc/cases/kyc-case-201", GetKycCaseResponse::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(200L, response.body!!.case.partyId)
}

@Test fun `caller without a party - forbidden`() {
    userId = 12L
    val response = rest.getForEntity("/v1/kyc/cases/kyc-case-100", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

The two existing success tests (`with payment method`, `without payment method`) run as user 11, party 100, and stay
green; `case not found` and `case from another tenant is not found` stay 404.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetKycCaseEndpointTest`
Expected: FAIL — the two new 403 tests get 200, the admin test is 403.

- [ ] **Step 4: Implement** — add `private val accessService: KycAccessService` to `KycEndpoints` and check after the
  load, before mapping:

```kotlin
@GetMapping("/{id}")
fun get(@PathVariable id: String): GetKycCaseResponse {
    val case = service.findById(id)
    accessService.ensureCanRead(case)
    return GetKycCaseResponse(case = mapper.toKycCase(case))
}
```

KDoc the method: `/** The case's own party or an admin only ([KycAccessService]). */` (404 before 403).

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetKycCaseEndpointTest`
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetKycCaseEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/GetKycCaseEndpoint.sql
git commit -m "feat(server): only the KYC case's party or an admin reads it" -m "Refs #352"
```

---

### Task 3: `GET /v1/kyc/cases` — a non-admin sees only its own party

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchKycCaseEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/party/SearchKycCaseEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.restrict(request): SearchKycCaseRequest` (Task 1).

- [ ] **Step 1: Update the fixture** — add the user/membership rows:

```sql
-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);
```

- [ ] **Step 2: Write the failing tests / update the expectations** in `SearchKycCaseEndpointTest` (add the
  `admin`/`roles` getter as in Task 2):

```kotlin
// existing `filter by status`: expect only "kyc-case-100-a"
// existing `filter by multiple statuses`: expect 2, ids 100-a and 100-b
// existing `limit and offset` (no partyId): expect listOf("kyc-case-100-a")
// existing `returns empty list when party has no cases` -> replace with:
@Test fun `another partyId is ignored`() {
    val response = rest.getForEntity("/v1/kyc/cases?partyId=999", SearchKycCaseResponse::class.java)
    assertEquals(2, response.body!!.cases.size)
    assertTrue(response.body!!.cases.all { it.partyId == 100L })
}

@Test fun `another party's cases are not returned`() {
    val response = rest.getForEntity("/v1/kyc/cases?partyId=101", SearchKycCaseResponse::class.java)
    assertEquals(setOf("kyc-case-100-a", "kyc-case-100-b"), response.body!!.cases.map { it.id }.toSet())
}

@Test fun `admin filters by another party`() {
    admin = true
    val response = rest.getForEntity("/v1/kyc/cases?partyId=101", SearchKycCaseResponse::class.java)
    assertEquals(listOf("kyc-case-101-a"), response.body!!.cases.map { it.id })
}

@Test fun `admin sees every case`() {
    admin = true
    val response = rest.getForEntity("/v1/kyc/cases?limit=10", SearchKycCaseResponse::class.java)
    assertEquals(3, response.body!!.cases.size)
}

@Test fun `caller without a party is denied`() {
    userId = 12L
    val response = rest.getForEntity("/v1/kyc/cases", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

`filter by party`, `filter by party and status` and `newest case first` (all `partyId=100`) keep their expectations.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchKycCaseEndpointTest`
Expected: FAIL — a non-admin still sees other parties' cases; the no-party test gets 200.

- [ ] **Step 4: Implement** — restrict before searching; keep the limit cap:

```kotlin
@GetMapping
fun search(@ModelAttribute request: SearchKycCaseRequest): SearchKycCaseResponse {
    val restricted = accessService.restrict(request)
    val cases = service.search(restricted.copy(limit = OffsetLimitPageRequest.limit(restricted.limit)))
    return SearchKycCaseResponse(cases = cases.map { case -> mapper.toKycCaseSummary(case) })
}
```

KDoc: `/** A non-admin only gets its own party's cases ([KycAccessService.restrict]). */`

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchKycCaseEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchKycCaseEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/SearchKycCaseEndpoint.sql
git commit -m "feat(server): restrict a KYC case search to the caller's party" -m "Refs #352"
```

---

### Task 4: `GET /v1/kyc/cases/images/{id}/url` — the image's case's party or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetKycImageUrlEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/party/GetKycImageUrlEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureCanRead(image: KycImageEntity)` (Task 1).

**Interfaces — Produces:** `KycService.getImageUrl(image: KycImageEntity): URL` (replaces
`getImageUrl(imageId: String)`; its only caller is this endpoint).

- [ ] **Step 1: Update the fixture** — add party 200, its case and image, and the caller rows:

```sql
INSERT INTO T_PARTY(id, tenant_id, email, first_name, last_name, created_at, modified_at) VALUES
    (200, 1, 'other.party@gmail.com', 'Other', 'Party', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);

INSERT INTO T_KYC_CASE(id, tenant_id, party_id, score, status, error_code, created_at, modified_at) VALUES
    ('kyc-case-201', 1, 200, NULL, 'PENDING_VERIFICATION', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_KYC_IMAGE(id, tenant_id, case_id, identification_type, image_type, storage_type, path, mime_type, created_at, modified_at) VALUES
    ('kyc-image-201-front', 1, 'kyc-case-201', 'NATIONAL_ID', 'FRONT', 'LOCAL',
     'kyc/kyc-case-201/images/kyc-image-201-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00');
```

- [ ] **Step 2: Write the failing tests** in `GetKycImageUrlEndpointTest` (add the `admin`/`roles` getter):

```kotlin
@Test fun `another party's image - forbidden`() {
    val response = rest.getForEntity("/v1/kyc/cases/images/kyc-image-201-front/url", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    verify(storage, never()).generatePresignedUrl(any(), any())
}

@Test fun `admin gets another party's image`() {
    admin = true
    doReturn(URL("https://storage.example.com/kyc/kyc-case-201/images/kyc-image-201-front.jpg?sig=abc"))
        .whenever(storage).generatePresignedUrl(eq("kyc/kyc-case-201/images/kyc-image-201-front.jpg"), eq(3600))

    val response = rest.getForEntity("/v1/kyc/cases/images/kyc-image-201-front/url", GetKycImageUrlResponse::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("https://storage.example.com/kyc/kyc-case-201/images/kyc-image-201-front.jpg?sig=abc", response.body?.url)
}

@Test fun `caller without a party - forbidden`() {
    userId = 12L
    val response = rest.getForEntity("/v1/kyc/cases/images/kyc-image-100-front/url", ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
}
```

`url`, `image not found`, `no content` and `image from another tenant is not found` keep their expectations; the
no-content case is the caller's own `kyc-image-100-back`, so it still reaches `KYC_IMAGE_NO_CONTENT`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetKycImageUrlEndpointTest`
Expected: FAIL — another party's image still returns a URL; the no-party test gets 200.

- [ ] **Step 4: Implement** — the endpoint loads the image, checks access, then asks the service for the URL; the
  service keeps only the URL generation:

```kotlin
@GetMapping("/images/{id}/url")
fun imageUrl(@PathVariable id: String): GetKycImageUrlResponse {
    val image = service.findImageById(id)
    accessService.ensureCanRead(image)
    return GetKycImageUrlResponse(service.getImageUrl(image).toString())
}
```

```kotlin
/** A short-lived URL of [image]; the endpoint has already checked who may read it. */
fun getImageUrl(image: KycImageEntity): URL {
    if (image.path == null) {
        throw NotFoundException(
            error = Error(code = ErrorCode.KYC_IMAGE_NO_CONTENT, message = "Image#${image.id} has no content")
        )
    }
    return storageProvider.get(image.storageType).generatePresignedUrl(image.path, imageUrlTTL)
}
```

KDoc the endpoint: `/** The image's case's own party or an admin only ([KycAccessService]); 404 KYC_IMAGE_NOT_FOUND
before 403, 404 KYC_IMAGE_NO_CONTENT after. */`

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetKycImageUrlEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/GetKycImageUrlEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/GetKycImageUrlEndpoint.sql
git commit -m "feat(server): only the KYC image's party or an admin gets its url" -m "Refs #352"
```

---

### Task 5: `POST /v1/kyc/cases/{id}/submit` — the case's party or an admin

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SubmitKycCaseEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/party/SubmitKycCaseEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureIsPartyOrAdmin(partyId: Long?)` (Task 1).

- [ ] **Step 1: Update the fixture** — add the caller rows (the cases stay as they are):

```sql
-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'complete@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);
```

- [ ] **Step 2: Write the failing tests** — the existing lifecycle tests keep running as an admin (default), so they
  exercise the admin path; add the non-admin tests:

```kotlin
private var admin = true
override val roles: Array<String>
    get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

@Test fun `party submits its own case`() {
    admin = false
    val response = submit("kyc-case-complete")
    assertEquals(HttpStatus.OK, response.statusCode)
    assertStatus(KycStatus.SUBMITTED, "kyc-case-complete")
}

@Test fun `party cannot submit another party's case`() {
    admin = false
    val response = submitWithError("kyc-case-no-selfie") // party 200
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertStatus(KycStatus.PENDING_VERIFICATION, "kyc-case-no-selfie")
}

@Test fun `caller without a party cannot submit`() {
    admin = false
    userId = 12L
    val response = submitWithError("kyc-case-complete")
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertStatus(KycStatus.PENDING_VERIFICATION, "kyc-case-complete")
}
```

`case not found` and `case of another tenant` stay 404 (as admin, the default).

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SubmitKycCaseEndpointTest`
Expected: FAIL — the non-admin tests get 200 instead of 403.

- [ ] **Step 4: Implement** — in `KycService.submit`, check after the load, before the status:

```kotlin
val case = findById(id)
accessService.ensureIsPartyOrAdmin(case.party.id)
ensureStatusIsPending(case)
```

Update its KDoc: errors in order 404 `KYC_CASE_NOT_FOUND`, 403 `HTTP_ACCESS_DENIED` but for the case's own party or an
admin, 400 `KYC_CASE_NOT_PENDING`, 400 `KYC_CASE_MISSING_IDENTIFICATION_IMAGE`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=SubmitKycCaseEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SubmitKycCaseEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/SubmitKycCaseEndpoint.sql
git commit -m "feat(server): only the KYC case's party or an admin submits it" -m "Refs #352"
```

---

### Task 6: `POST /v1/kyc/cases` — a non-admin creates only for its own party

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/CreateKycCaseEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/party/CreateKycCaseEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureIsPartyOrAdmin(partyId: Long?)` (Task 1).

- [ ] **Step 1: Update the fixture** — add the caller rows:

```sql
-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);
```

- [ ] **Step 2: Write the failing tests** (add the `admin`/`roles` getter):

```kotlin
@Test fun `another party - forbidden`() {
    val request = CreateKycCaseRequest(partyId = 200L, identificationType = IdentificationType.RESIDENT_CARD)
    val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertEquals(0L, dao.count())
}

@Test fun `admin creates for another party`() {
    admin = true
    val request = CreateKycCaseRequest(partyId = 200L, identificationType = IdentificationType.RESIDENT_CARD)
    val response = rest.postForEntity("/v1/kyc/cases", request, CreateKycCaseResponse::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(200L, dao.findById(response.body!!.caseId).orElseThrow().party.id)
}

@Test fun `caller without a party - forbidden`() {
    userId = 12L
    val request = CreateKycCaseRequest(partyId = 100L)
    val response = rest.postForEntity("/v1/kyc/cases", request, ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertEquals(0L, dao.count())
}
```

`with payment method` (party 100, user 11) stays green; `party not found` stays 404 (the party lookup runs before the
access check).

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateKycCaseEndpointTest`
Expected: FAIL — the forbidden tests create the case instead of answering 403.

- [ ] **Step 4: Implement** — check after the party lookup, in `KycService.create(request)` only (the internal
  `create(party, identificationType)` overload used by agent sign-up stays unchecked):

```kotlin
@Transactional
fun create(request: CreateKycCaseRequest): KycCaseEntity {
    val party = partyService.findById(request.partyId)
    accessService.ensureIsPartyOrAdmin(party.id)
    return create(party, request.identificationType)
}
```

Add its KDoc: 404 `PARTY_NOT_FOUND`, then 403 `HTTP_ACCESS_DENIED` but for that party or an admin.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateKycCaseEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/CreateKycCaseEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/CreateKycCaseEndpoint.sql
git commit -m "feat(server): create a KYC case only for the caller's party" -m "Refs #352"
```

---

### Task 7: `POST /v1/identifications` — a non-admin creates only for its own party

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/IdentificationService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/CreateIdentificationEndpointTest.kt`
- Modify fixture: `modules/ndopify-server/src/test/resources/db/test/party/CreateIdentificationEndpoint.sql`

**Interfaces — Consumes:** `KycAccessService.ensureIsPartyOrAdmin(partyId: Long?)` (Task 1).

- [ ] **Step 1: Update the fixture** — add party 200 and the caller rows:

```sql
INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (200, 'other.party@gmail.com', 'Other', 'Party', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

-- The caller is user 11, member of party 100; user 12 has no membership.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00'),
    (12, 'no.party@gmail.com', false, '2024-06-10 00:00:00');
INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);
```

- [ ] **Step 2: Write the failing tests** (add the `admin`/`roles` getter):

```kotlin
@Test fun `another party - forbidden`() {
    val request = CreateIdentificationRequest(
        partyId = 200L, type = IdentificationType.NATIONAL_ID, issuingCountryCode = "CM",
        imageTypes = listOf(KycImageType.FRONT),
    )
    val response = rest.postForEntity("/v1/identifications", request, ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertEquals(0L, dao.count())
}

@Test fun `admin creates for another party`() {
    admin = true
    val request = CreateIdentificationRequest(
        partyId = 200L, type = IdentificationType.NATIONAL_ID, issuingCountryCode = "CM",
        imageTypes = listOf(KycImageType.FRONT),
    )
    val response = rest.postForEntity("/v1/identifications", request, Void::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals(1, dao.findAll().count { it.party.id == 200L })
}

@Test fun `caller without a party - forbidden`() {
    userId = 12L
    val request = CreateIdentificationRequest(
        partyId = 100L, type = IdentificationType.NATIONAL_ID, issuingCountryCode = "CM",
        imageTypes = listOf(KycImageType.FRONT),
    )
    val response = rest.postForEntity("/v1/identifications", request, ErrorResponse::class.java)
    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertEquals(0L, dao.count())
}
```

`identifications` (party 100, user 11), the two `@Valid` 400 tests and `party not found` keep their expectations.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateIdentificationEndpointTest`
Expected: FAIL — the forbidden tests create the identification instead of answering 403.

- [ ] **Step 4: Implement** — inject `private val accessService: KycAccessService` and check after the party lookup:

```kotlin
@Transactional
fun create(request: CreateIdentificationRequest): IdentificationEntity {
    val party = partyService.findById(request.partyId)
    accessService.ensureIsPartyOrAdmin(party.id)
    // ... unchanged
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=CreateIdentificationEndpointTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/IdentificationService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/CreateIdentificationEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/party/CreateIdentificationEndpoint.sql
git commit -m "feat(server): create an identification only for the caller's party" -m "Refs #352"
```

---

### Task 8: Replace the internal helpers and document the rule

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt` (image writes)
- Modify: `modules/ndopify-server/CLAUDE.md` (party domain, KYC paragraph)

**Interfaces — Consumes:** `KycAccessService.ensureIsParty(partyId: Long?)` (Task 1).

- [ ] **Step 1: Replace `KycService.ensureOwnCase`** — the image writes (`upload`, `presign`, `attach`) call
  `accessService.ensureIsParty(case.party.id)` instead, and the private `ensureOwnCase` plus the now-unused
  `callerService` constructor parameter and its `CallerService` / `ForbiddenException` imports are removed. The
  image-write KDocs keep "403 HTTP_ACCESS_DENIED but for the case's own party". (Task 1's unit test already pins the
  no-admin-bypass behavior; the existing `UploadKycImageEndpointTest` / `PresignKycImageEndpointTest` /
  `AttachKycImageEndpointTest` stay green.)

- [ ] **Step 2: Update `modules/ndopify-server/CLAUDE.md`** — in the party domain KYC paragraph, replace:

```
  Tenant-scoped (see Multi-Tenancy below). The image writes are the case's own party's only (`CallerService`, through
  the token's membership; no admin bypass): 404 `KYC_CASE_NOT_FOUND` (another tenant too) → 403 `HTTP_ACCESS_DENIED` →
  400 `KYC_CASE_NOT_PENDING` → the upload's 400s. `create`, `submit`, `get` and `search` stay tenant-scoped only.
```

with:

```
  Tenant-scoped (see Multi-Tenancy below). The image writes are the case's own party's only (`KycAccessService`,
  through the token's membership; no admin bypass): 404 `KYC_CASE_NOT_FOUND` (another tenant too) → 403
  `HTTP_ACCESS_DENIED` → 400 `KYC_CASE_NOT_PENDING` → the upload's 400s. `get`, `submit`, `search` and `create` are
  for the case's own party or an admin (`KycAccessService`): a non-admin's search is forced to its own party (whatever
  `partyId` it passes), and a non-admin creates only for its own party; `POST /v1/identifications` follows the same
  create rule.
```

- [ ] **Step 3: Run the image-write tests to verify the refactor changed nothing**

Run: `cd modules/ndopify-server && mvn test -Dtest=UploadKycImageEndpointTest,PresignKycImageEndpointTest,AttachKycImageEndpointTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt \
        modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document who may read or create KYC data" -m "Refs #352"
```

---

### Task 9: Full module verification

- [ ] **Step 1: Run the module's gate** (a local MySQL must be running):

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint clean, every test green (including `KycAccessServiceTest`, the six KYC/identification
endpoint classes and the untouched `CreateAgentEndpointTest` / `KycJobsTest`), JaCoCo thresholds met.

- [ ] **Step 2: Confirm Partner Central's KYC client still builds and its tests pass** (no code change expected,
  but its wizard reads, submits and creates only the agent's own party: acceptance #12)

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: BUILD SUCCESS (`NdKycClientTest` and the KYC controller tests unchanged and green).

- [ ] **Step 3: Confirm nothing outside the story changed**

Run: `git status --porcelain && git diff --stat origin/main...HEAD`
Expected: only the files listed in Tasks 1–8 (the module's KYC service/endpoint/access service, the party fixtures,
the endpoint tests and the module `CLAUDE.md`); no DTO, web-app, prototype, migration or root `pom.xml` change.
