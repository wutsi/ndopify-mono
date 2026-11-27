# The listing mandate owner's ownership verdict — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve `GET /v1/mandates/{id}/ownership` — a derived `PENDING` / `VERIFIED` / `FAILED` verdict on whether a LISTING mandate's customer is the person its ID names and an owner its title names — from the mandate's extracted documents, with no new table, job or write.

**Architecture:** `OwnershipVerdictService` (mandate domain) loads the mandate's documents, decodes each typed `DocumentData` with `DocumentDataCodec`, and classifies two checks — identity and ownership — against `customer.party.fullName()` through `KycUtils.verifyName`, and identity expiry against `LocalDate.now` in the tenant's time zone. Its pure `evaluate(documents, customerName, today)` core is unit-tested; `verify(mandate)` wires the repositories, the clock and the log line. `MandateEndpoints` adds the read, checks access with `MandateAccessService.ensureCanUpdate` and the LISTING type, and returns the DTO.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA, Jackson 3 (`tools.jackson`), `kotlin.test` + `mockito-kotlin`, endpoint tests with `TestRestTemplate` against MySQL and Flyway SQL fixtures.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/297 — the enriched story is the design (its *The rule*, *Verdict*, *Acceptance criteria*, *Groomed 2026-11-27* and *Risks* sections). #294 is the epic decision; #327 / #328 / #330 are where the documents and their extraction come from.

## Global Constraints

- Code, comments, logs and docs in English; user-facing text is #298's and #299's, not this story's.
- No new table, no migration, no scheduled job, no write, no event: the verdict is derived on every read.
- Reason codes are plain strings; a new code never needs a `ndopify-dto` release.
- Never log a name, a document number, a path or any extracted value — ids, types and codes only (the #327 rule).
- An `EXTRACTED` document is trusted: the verdict never re-runs `DocumentChecks`; the `DOCUMENT_EXPIRED` warning alone never decides.
- "Today" is `LocalDate.now(clock.withZone(ZoneId.of(tenant.timeZone)))`, never `LocalDate.now()`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`. `mvn validate` (ktlint) and the JaCoCo gate must pass.
- `ndopify-dto` is a released artifact: the server builds against `0.0.111-SNAPSHOT` locally while developing; the branch ends with the DTO released and `<ndopify-dto.version>` in the root `pom.xml` set to the released version, never a `-SNAPSHOT` (the developer agent's shared-module release procedure).

## Review Focus

- An ID expiring **today** in the tenant's zone (`Africa/Douala`, UTC+1) is valid; at 23:30Z the tenant is already on the next day, so the same `expiresAt` must be judged on the tenant's date.
- `KycUtils.verifyName` scores **95–100 whenever one name's tokens are contained in the other's** (missing middle name, extra initial, reversed order) — a namesake sharing a surname and one first name passes; a clearly different person must not (and the sample evaluation records the real numbers).
- A page change resets a document to `UPLOADED`; the next read must flip a previously `VERIFIED` mandate back to `PENDING` — no cache, no stored verdict.
- A customer party with a blank name must never be `VERIFIED` (`verifyName` returns 0).
- Two unusable documents carrying the same problem code must yield **one** deduplicated detail, in document order (the repository returns newest first).

---

### Task 1: The DTOs and the error code

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerificationStatus.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerificationReason.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerification.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/GetOwnershipVerificationResponse.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt` (mandate group, beside `MANDATE_NOT_FOUND`)
- Modify: `pom.xml:52` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces: `OwnershipVerificationStatus { UNKNOWN, PENDING, VERIFIED, FAILED }`; `OwnershipVerificationReason(code: String, details: List<String>)`; `OwnershipVerification(status, reasons)`; `GetOwnershipVerificationResponse(verification)`; `ErrorCode.MANDATE_NOT_LISTING`.

- [ ] **Step 1: Write the four DTOs**

```kotlin
// OwnershipVerificationStatus.kt
package com.wutsi.ndopify.mandate.dto

/**
 * Whether a LISTING mandate's owner is verified (#297): every document read on the mandate already passed
 * `DocumentChecks` when it is `EXTRACTED`. `UNKNOWN` is the DTO's default and is never returned by the endpoint.
 */
enum class OwnershipVerificationStatus {
    UNKNOWN,
    PENDING,
    VERIFIED,
    FAILED,
}
```

```kotlin
// OwnershipVerificationReason.kt
package com.wutsi.ndopify.mandate.dto

/**
 * One reason a check failed: [code] is a plain string (`OWNERSHIP_ID_MISSING`...), so a new code needs no DTO
 * release; [details] carries the failed document's `problemCodes` for `*_UNUSABLE`, and is empty otherwise.
 */
data class OwnershipVerificationReason(
    val code: String = "",
    val details: List<String> = emptyList(),
)
```

```kotlin
// OwnershipVerification.kt
package com.wutsi.ndopify.mandate.dto

/** Whether the owner of a LISTING mandate is verified, and why not; status and codes only, never a document. */
data class OwnershipVerification(
    val status: OwnershipVerificationStatus = OwnershipVerificationStatus.UNKNOWN,
    val reasons: List<OwnershipVerificationReason> = emptyList(),
)
```

```kotlin
// GetOwnershipVerificationResponse.kt
package com.wutsi.ndopify.mandate.dto

data class GetOwnershipVerificationResponse(val verification: OwnershipVerification = OwnershipVerification())
```

- [ ] **Step 2: Add the error code**

In `ErrorCode.kt`, after `MANDATE_NOT_FOUND`:

```kotlin
val MANDATE_NOT_LISTING = "$PREFIX:mandate:not-listing"
```

- [ ] **Step 3: Build the DTO locally and point the server at it**

```bash
cd modules/ndopify-dto && mvn -q install
```

Set `<ndopify-dto.version>0.0.111-SNAPSHOT</ndopify-dto.version>` in the root `pom.xml` while developing.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerification*.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/GetOwnershipVerificationResponse.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): add the ownership verification contract" -m "Refs #297"
```

- [ ] **Step 5: Release the DTO**

Run the developer agent's shared-module release procedure for `ndopify-dto` (the profile's `shared_modules`): push the DTO change, wait for `ndopify-dto-main.yml`, then commit the released `<ndopify-dto.version>` (`fix dependencies: ndopify-dto <version>`). The branch must not end on a `-SNAPSHOT`.

---

### Task 2: `OwnershipVerdictService` and its pure core

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipReasonCodes.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictService.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictServiceTest.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (under `ndopify.mandate`, after `document:`)

**Interfaces:**
- Consumes: `MandateDocumentRepository.findByMandateIdOrderByIdDesc(mandateId): List<MandateDocumentEntity>`, `DocumentDataCodec.read(json, type, dataVersion)`, `CustomerService.findById(id): CustomerEntity`, `TenantService.findById(id): TenantEntity`, `KycUtils.verifyName(input, db): Double` (0..1), `TenantContext.get()`.
- Produces: `OwnershipVerdictService.verify(mandate: MandateEntity): OwnershipVerification` (used by Task 3 and by #299); `internal fun evaluate(documents: List<MandateDocumentEntity>, customerName: String, today: LocalDate): OwnershipVerification`.

- [ ] **Step 1: Write the failing unit test**

Build the service with a real codec (`DocumentDataCodec(JsonHelper(JsonMapper()))`), mocked collaborators and `nameThreshold = 90.0`, and call `evaluate` directly (`today = LocalDate.of(2026, 11, 27)`). Use one helper:

```kotlin
private fun document(
    type: DocumentType,
    status: DocumentStatus,
    data: DocumentData? = null,
    problemCodes: List<String> = emptyList(),
) = MandateDocumentEntity(
    id = nextId++,
    mandateId = 1,
    type = type,
    title = "Doc",
    status = status,
    problemCodes = problemCodes,
    data = data?.let { codec.write(it) },
    dataVersion = data?.let { 1 },
)
```

with `nationalId(firstName, lastName, expiryDate)` / `landTitle(vararg owners)` helpers building `NationalId(valid = true, fieldConfidences = ...)` / `LandTitle(valid = true, ...)`. Every case is `evaluate(listOf(...docs), "Jean Paul Mbarga", TODAY)`, and asserts the status and the reasons' `code` + `details`:

| Test | Documents | Expected |
| --- | --- | --- |
| `an exact ID and title verify the owner` | ID `Jean Paul` / `Mbarga`, expiry 2030; title owner `Jean Paul Mbarga` | `VERIFIED`, no reasons |
| `case, accents and a hyphen match` | ID `JEAN-PAUL` / `MBARGA`, expiry 2030; title `Jean Paul Mbarga` | `VERIFIED` |
| `a missing middle name matches` | ID `Jean` / `Mbarga` (scores 95); title `Jean Paul Mbarga` | `VERIFIED` |
| `another person does not match` | ID `Pierre` / `Durand` (scores 48.2726); title `Jean Paul Mbarga` | `FAILED`, `[OWNERSHIP_ID_NAME_MISMATCH]` |
| `an expired ID fails` | ID expiry 2020-01-01, `EXTRACTED`, problemCodes `[DOCUMENT_EXPIRED]`; valid title | `FAILED`, `[OWNERSHIP_ID_EXPIRED]` |
| `a valid passport next to an expired ID verifies` | expired ID + passport expiry 2030 naming the customer; valid title | `VERIFIED` |
| `an unusable ID reports its problem codes` | ID `FAILED`, problemCodes `[DOCUMENT_PAGE_MISSING]`; valid title | `FAILED`, `[OWNERSHIP_ID_UNUSABLE(details=[DOCUMENT_PAGE_MISSING])]` |
| `two unusable IDs deduplicate their codes` | two `FAILED` IDs: the first `[DOCUMENT_UNREADABLE]`, the second `[DOCUMENT_UNREADABLE, DOCUMENT_PAGE_MISSING]` | `FAILED`, one `OWNERSHIP_ID_UNUSABLE` with `details=[DOCUMENT_UNREADABLE, DOCUMENT_PAGE_MISSING]` |
| `a title naming someone else fails` | valid ID; title `Pierre Durand` | `FAILED`, `[OWNERSHIP_DOCUMENT_NAME_MISMATCH]` |
| `a lease is not ownership evidence` | valid ID; `LEASE_CONTRACT` `EXTRACTED` | `PENDING`, `[OWNERSHIP_DOCUMENT_MISSING]` |
| `a utility bill is not ownership evidence` | valid ID; `UTILITY_BILL` `EXTRACTED` | `PENDING`, `[OWNERSHIP_DOCUMENT_MISSING]` |
| `an uploaded ID keeps the verdict pending` | ID `UPLOADED`; valid title | `PENDING`, no reasons |
| `an extracting ID keeps the verdict pending` | ID `EXTRACTING`; valid title | `PENDING`, no reasons |
| `a missing ID is pending` | valid title only | `PENDING`, `[OWNERSHIP_ID_MISSING]` |
| `a missing title is pending` | valid ID only | `PENDING`, `[OWNERSHIP_DOCUMENT_MISSING]` |
| `a failed ID wins over an extracting title` | ID `FAILED` `[DOCUMENT_UNREADABLE]`; title `EXTRACTING` | `FAILED`, `[OWNERSHIP_ID_UNUSABLE(details=[DOCUMENT_UNREADABLE])]` |
| `both checks fail, ID first` | expired ID; title `Pierre Durand` | `FAILED`, `[OWNERSHIP_ID_EXPIRED, OWNERSHIP_DOCUMENT_NAME_MISMATCH]` |
| `the threshold decides` | ID `Jean` / `Mbarga` (95) at `nameThreshold = 96.0` | `FAILED`, `[OWNERSHIP_ID_NAME_MISMATCH]` |
| `a certificate of ownership counts` | valid ID; `CERTIFICATE_OF_OWNERSHIP` owner the customer | `VERIFIED` |
| `an EXTRACTED document is not re-checked` | ID expiry 2030 with problemCodes `[DOCUMENT_EXPIRED]`; valid title | `VERIFIED` |
| `a blank customer name never verifies` | valid ID and title, `customerName = ""` | `FAILED`, `[OWNERSHIP_ID_NAME_MISMATCH, OWNERSHIP_DOCUMENT_NAME_MISMATCH]` |
| `a title with no owner is a name mismatch` | valid ID; `LandTitle(owners = emptyList())` | `FAILED`, `[OWNERSHIP_DOCUMENT_NAME_MISMATCH]` |
| `an undecodable payload is unusable` | an ID built directly with `data = "[]"` and `EXTRACTED`; valid title | `FAILED`, `[OWNERSHIP_ID_UNUSABLE]` |

Add two `verify()` tests that mock `tenantService.findById` → `timeZone = "Africa/Douala"`, `customerService.findById` → party `Jean Paul Mbarga`, `documentDao`, and a fixed `Clock` at `2026-01-01T23:30:00Z`, with `TenantContext.set(1L)` around them (`TenantContext.remove()` after): an ID expiry `2026-01-02` verifies, and an expiry `2026-01-01` fails `OWNERSHIP_ID_EXPIRED`.

- [ ] **Step 2: Run it and see it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=OwnershipVerdictServiceTest`
Expected: a compile error — `OwnershipVerdictService` and `OwnershipReasonCodes` don't exist.

- [ ] **Step 3: Write `OwnershipReasonCodes`**

```kotlin
package com.wutsi.ndopify.mandate.server.service

/** The reason codes of an ownership verdict; plain strings, like `DocumentProblemCodes` (no DTO release per code). */
object OwnershipReasonCodes {
    const val ID_MISSING = "OWNERSHIP_ID_MISSING"
    const val ID_UNUSABLE = "OWNERSHIP_ID_UNUSABLE"
    const val ID_EXPIRED = "OWNERSHIP_ID_EXPIRED"
    const val ID_NAME_MISMATCH = "OWNERSHIP_ID_NAME_MISMATCH"
    const val DOCUMENT_MISSING = "OWNERSHIP_DOCUMENT_MISSING"
    const val DOCUMENT_UNUSABLE = "OWNERSHIP_DOCUMENT_UNUSABLE"
    const val DOCUMENT_NAME_MISMATCH = "OWNERSHIP_DOCUMENT_NAME_MISMATCH"
}
```

- [ ] **Step 4: Write `OwnershipVerdictService`**

Class signature:

```kotlin
@Service
class OwnershipVerdictService(
    private val documentDao: MandateDocumentRepository,
    private val codec: DocumentDataCodec,
    private val customerService: CustomerService,
    private val tenantService: TenantService,
    private val logger: KVLogger,
    private val clock: Clock,
    @param:Value("\${ndopify.mandate.ownership.name-threshold}") private val nameThreshold: Double,
) {
    fun verify(mandate: MandateEntity): OwnershipVerification
    internal fun evaluate(documents: List<MandateDocumentEntity>, customerName: String, today: LocalDate): OwnershipVerification
}
```

The classification (the tests pin it; implement it exactly):

- A **check** is one of: `Pass`, `Missing` (no document of a qualifying type), `InFlight` (a qualifying document is `UPLOADED` / `EXTRACTING`), `Expired`, `Mismatch`, `Unusable(details)`.
- Identity candidates are `NATIONAL_ID` / `PASSPORT`; ownership candidates are `LAND_TITLE` / `CERTIFICATE_OF_OWNERSHIP`. Anything else is ignored entirely.
- Identity: `Pass` when an `EXTRACTED` candidate decodes to `IdentityData`, its `expiresAt()` is not before `today` and `"${firstName} ${lastName}"` matches. Ownership: `Pass` when an `EXTRACTED` candidate decodes to `TitleData` and one of its `owners` matches.
- After the pass test, in this order: an `InFlight` candidate → `InFlight`; an `EXTRACTED` candidate whose payload doesn't decode (or, for an ID, whose `expiresAt()` is null) → `Unusable`; an `EXTRACTED` ID whose expiry is before `today` → `Expired`; any other `EXTRACTED` candidate → `Mismatch`; otherwise (only `FAILED` / `UNKNOWN` rows) → `Unusable`.
- `matches(name) = KycUtils.verifyName(name, customerName) * 100 >= nameThreshold`; a blank name or owner matches nothing.
- `details` = `candidates.flatMap { it.problemCodes }.distinct()` (document order).
- Status: both `Pass` → `VERIFIED`; any `Expired` / `Mismatch` / `Unusable` → `FAILED`; otherwise `PENDING`.
- Reasons: `Pass` and `InFlight` contribute none; each other outcome contributes one reason (ID check first, then ownership).
- `verify`: load the tenant through `TenantContext.get()!!`, `today` in its `ZoneId`, the customer's `party.fullName()`, and `documentDao.findByMandateIdOrderByIdDesc(mandate.id!!)`; then `logger.add("mandate_id", …)`, `add("ownership_status", status.name)`, `add("ownership_reason_codes", reasons.map { it.code })`, `logger.log()`. Never add a name, a number or a path.

- [ ] **Step 5: Add the property to `application.yml`**

Under `ndopify.mandate`, after `document:`:

```yaml
        ownership:
            name-threshold: 90             # 0..100: below it, an ID or a title does not name the customer (as KYC)
```

- [ ] **Step 6: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=OwnershipVerdictServiceTest`
Expected: PASS, every case.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipReasonCodes.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictServiceTest.kt \
        modules/ndopify-server/src/main/resources/application.yml
git commit -m "feat(server): derive a listing mandate's ownership verdict" -m "Refs #297"
```

---

### Task 3: `GET /v1/mandates/{id}/ownership`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateOwnershipEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/resources/db/test/mandate/GetMandateOwnershipEndpoint.sql`

**Interfaces:**
- Consumes: `OwnershipVerdictService.verify(mandate)` (Task 2), `MandateService.findById(id)`, `MandateAccessService.ensureCanUpdate(mandate)`, `GetOwnershipVerificationResponse` (Task 1).
- Produces: the endpoint `MandateEndpoints.getOwnership(id: Long): GetOwnershipVerificationResponse`.

- [ ] **Step 1: Write the SQL fixture**

`GetMandateOwnershipEndpoint.sql`, following `GetMandateDocumentEndpoint.sql`: tenant 1 (`Africa/Douala` by default) + tenant 2; city 2370201; party 100 = the customer `Customer One` (user 13, whose membership party is 100), party 200 = agent 20 (user 11, the caller), party 300 = agent 30 (user 12); `T_CUSTOMER(10)`, `T_AGENT(20, 30)`, and mandates:

| Mandate | tenant | agent | type | customer | documents | expected |
| --- | --- | --- | --- | --- | --- | --- |
| 100 | 1 | 20 | LISTING | 10 | `NATIONAL_ID` `EXTRACTED` `{"firstName":"Customer","lastName":"One","expiryDate":"2099-01-01",...}`, `LAND_TITLE` `EXTRACTED` `{"owners":["Customer One"],...}` | `VERIFIED` |
| 101 | 1 | 20 | LISTING | 10 | the same ID, `LEASE_CONTRACT` `EXTRACTED` | `PENDING` `OWNERSHIP_DOCUMENT_MISSING` |
| 102 | 1 | 20 | LISTING | 10 | `NATIONAL_ID` expired 2020 + `LAND_TITLE` owners `Pierre Durand` | `FAILED` `OWNERSHIP_ID_EXPIRED` + `OWNERSHIP_DOCUMENT_NAME_MISMATCH` |
| 103 | 1 | 30 | LISTING | 10 | as 100 | 403 for agent 20; 200 for an admin |
| 104 | 1 | — (`PENDING` assignment to 20) | LISTING | 10 | as 100 | 403 for agent 20 |
| 106 | 1 | 20 | SEARCH | 10 | as 100 | 400 `MANDATE_NOT_LISTING` |
| 107 | 1 | 30 | SEARCH | 10 | as 100 | 403 (not 400) for agent 20 |
| 200 | 2 | — | LISTING | 90 | — | 404 |

The customer's 403 needs no fixture row: `GET /v1/mandates/100/ownership` with `userId = 13` (whose membership party is 100, the customer of mandate 100).

Give mandate 100's `NATIONAL_ID` the id `1000` (the *derived on every read* test reloads it). Tenant 2 also needs its party 90 and customer 90.

Give every document row its `data` JSON with `"valid":true` and the `fieldConfidences` the type requires, `data_version = 1`; use 2099 / 2020 dates so the test is time-independent.

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
@Sql(value = ["/db/test/clean.sql", "/db/test/mandate/GetMandateOwnershipEndpoint.sql"])
class GetMandateOwnershipEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    private var admin = false
    override val roles: Array<String>
        get() = if (admin) arrayOf(RoleCode.ADMIN) else arrayOf("ROLE_USER")

    @Test
    fun `the agent verifies the owner`() {
        val response = rest.getForEntity("/v1/mandates/100/ownership", GetOwnershipVerificationResponse::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(OwnershipVerificationStatus.VERIFIED, response.body!!.verification.status)
        assertTrue(response.body!!.verification.reasons.isEmpty())
    }
    // ... one test per row above: PENDING, FAILED, 403 (other agent, offered agent, customer), 401 (anonymousUser),
    // 404 (999 and 200), 400 (106), 403 before 400 (107), 200 for an admin (103, admin = true).
}
```

Add a `no leak` test reading `String::class.java` for 102 and asserting the body does not contain `"Customer"`, `"Pierre"`, `"mandate/100/documents"`, `"TF-"` nor a document id.

Add a `derived on every read` test: read 100 (`VERIFIED`), then `@Autowired` the `MandateDocumentRepository` (declare it on the test class), load document 1000 (the ID), `save(it.copy(status = DocumentStatus.UPLOADED))`, and read again — `PENDING`. Nothing caches the verdict.

- [ ] **Step 3: Run it and see it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateOwnershipEndpointTest`
Expected: FAIL — 404 from Spring (no mapping for `/ownership`).

- [ ] **Step 4: Add the endpoint**

In `MandateEndpoints`, inject `OwnershipVerdictService` and add:

```kotlin
@GetMapping("/{id}/ownership")
fun getOwnership(@PathVariable id: Long): GetOwnershipVerificationResponse {
    val mandate = service.findById(id)
    access.ensureCanUpdate(mandate) // The assigned agent or an admin; never the customer nor the offered agent
    if (mandate.mandateType != MandateType.LISTING) {
        throw BadRequestException(error = Error(code = ErrorCode.MANDATE_NOT_LISTING))
    }
    return GetOwnershipVerificationResponse(verification = ownershipService.verify(mandate))
}
```

The order is 401 (security filter) → 404 → 403 → 400, so keep the two checks in that order.

- [ ] **Step 5: Run the endpoint test**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateOwnershipEndpointTest`
Expected: PASS (needs the local MySQL, per the module instructions).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateOwnershipEndpointTest.kt \
        modules/ndopify-server/src/test/resources/db/test/mandate/GetMandateOwnershipEndpoint.sql
git commit -m "feat(server): read a listing mandate's ownership verdict" -m "Refs #297"
```

---

### Task 4: Sample evaluation (acceptance criterion 13)

**Files:**
- Modify: this plan, section *Sample evaluation* below.

**Interfaces:** none — a measurement, not code.

- [ ] **Step 1: Collect the samples**

Identity samples are already in `modules/ndopify-server/src/test/resources/files/identity/` (`cm/cni-front.png` + `cm/cni-back.png`, `cm/passport.webp`). Retrieve the *titre foncier* and the *certificat de propriété* attached to #297. Do **not** commit any sample: they are personal documents.

- [ ] **Step 2: Run the real pipeline locally**

With MySQL, Mailpit and `DEEPSEEK_API_KEY` set, start `ndopify-server`; create a LISTING mandate whose customer's party name is exactly the name printed on the samples; upload the CNI front+back as one `NATIONAL_ID` document (`POST /v1/mandates/{id}/documents/upload`), the passport as a `PASSPORT` document, and each land document as its own `LAND_TITLE` / `CERTIFICATE_OF_OWNERSHIP` document (the 5-document cap: use a second mandate if needed). Trigger the extraction through `POST /v1/mandate-documents/extract?type=…` (an ops endpoint, on locally), then read `GET /v1/mandates/{id}/documents/{documentId}` for the extracted values and `GET /v1/mandates/{id}/ownership` for the verdict.

- [ ] **Step 3: Compute each score and fill the table**

The response carries codes, never a score: compute `KycUtils.verifyName(extractedName, customerName) * 100` in a throwaway local test (never committed, deleted afterwards), or bisect with `ndopify.mandate.ownership.name-threshold`. Fill in:

| Sample | Type | Extracted name / owners | Customer name | Score | Document status | Verdict | Reason |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CM CNI (front + back) | NATIONAL_ID | | | | | | |
| CM passport | PASSPORT | | | | | | |
| *Titre foncier* (#297 attachment) | LAND_TITLE | | | | | | |
| *Certificat de propriété* (#297 attachment) | CERTIFICATE_OF_OWNERSHIP | | | | | | |

- [ ] **Step 4: Decide the threshold**

Keep the 90 default unless a sample with a genuinely matching name scores below it — then lower `ndopify.mandate.ownership.name-threshold`'s default to that score's floor in `application.yml` and write the reason here. A sample naming another person must stay below the threshold. Record the outcome in a comment on #297.

- [ ] **Step 5: Commit the filled table**

```bash
git add docs/superpowers/plans/297-server-decide-whether-a-listing-mandate-s-owner-is-verified.md
git commit -m "docs(server): record the ownership name-match sample evaluation" -m "Refs #297"
```

---

### Task 5: Module documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **mandate** domain bullet, after the *Documents* / *Extraction* paragraphs)

**Interfaces:** none.

- [ ] **Step 1: Document the verdict**

Add one paragraph to the mandate domain bullet: `GET /v1/mandates/{id}/ownership` (`MandateEndpoints`, agent or admin like the document reads; 400 `MANDATE_NOT_LISTING` for a SEARCH mandate) derives `PENDING` / `VERIFIED` / `FAILED` through `OwnershipVerdictService` from the mandate's `EXTRACTED` documents — an ID (`NATIONAL_ID` / `PASSPORT`) not expired in the tenant's time zone and naming `customer.party.fullName()` (`KycUtils.verifyName`, `ndopify.mandate.ownership.name-threshold` 90), and a `LAND_TITLE` / `CERTIFICATE_OF_OWNERSHIP` whose `owners` name the same customer; `UTILITY_BILL`, `LEASE_CONTRACT`, `TAX_RECEIPT`, `SURVEY_PLAN`, `POWER_OF_ATTORNEY` and the rest are not ownership evidence; a decisive failure wins over a check still in flight; the reasons (`OwnershipReasonCodes`) are plain strings and `*_UNUSABLE` carries the document's `problemCodes`; nothing is stored, so a page change flips the verdict at once; the answer never carries a document, a page or a name.

- [ ] **Step 2: Verify**

Run: `mvn verify` (root, per the project profile; `ndopify-server` needs MySQL). Expected: BUILD SUCCESS, ktlint clean, coverage gate met.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document the ownership verdict" -m "Refs #297"
```
