# Owner-verification override (ops escape hatch) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An admin can store a reasoned override on a LISTING mandate so that the owner verdict #297 derives, the
publication gate #299 and the mandate page #298 treat the owner as verified until the override is cleared or loses its
supporting documents.

**Architecture:** Three audit columns on `T_MANDATE`; `OwnershipVerdictService.verify` returns `VERIFIED` with
`overridden = true` when the mandate has a backed override, and computes the existing verdict otherwise. A new
admin-checked `@OpsEndpoint` sets and clears it, refusing a mandate without at least one identity and one ownership
document. Partner Central words the overridden verdict as *Vérifiée par Ndopify*, ported from the approved prototype;
the public badge (#300) is untouched.

**Tech Stack:** Kotlin, Spring Boot 4 / Spring Framework 7, Spring Data JPA, Flyway (MySQL), Thymeleaf, `TestRestTemplate`
against MySQL for the server, jsoup template tests for Partner Central, `kotlin.test` + `mockito-kotlin`.

**Spec:** story #377 — https://github.com/wutsi/ndopify-mono/issues/377 — the enriched body (*Why and who*, *The
override*, *Visibility*, *Acceptance criteria*, *Decisions and assumptions*) and the approved prototype:
`modules/ndopify-partner-central/src/main/html/v2/mandate.html`, `.../assets/js/mandate-ownership.js`, `.../README.md`
(the #377 change row and per-state review table).

## Global Constraints

- `UI: yes`, `Shared code: ndopify-dto`: `OwnershipVerification.overridden`, `OverrideOwnershipVerificationRequest` and
  `ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS` are released before the server and Partner Central deploy.
  Build locally against the DTO's current `-SNAPSHOT` (`<ndopify-dto.version>` in the root `pom.xml`; never commit a
  `-SNAPSHOT`), then the developer's shared-module procedure releases it and a `fix dependencies:` commit sets the
  released version.
- **Admin** is `CallerService.isAdmin` exactly: an `admin-console` token with the `admin` role (`RoleCode.ADMIN`). No
  new role, no Partner Central admin, no bypass.
- The ops routes are 404 when `ndopify.endpoints.ops` is false (PROD). TEST/local keep it true.
- Errors in order: 401 (security filter, no token) → 403 `HTTP_ACCESS_DENIED` (non-admin) → 404 `MANDATE_NOT_FOUND` →
  400 `MANDATE_NOT_LISTING` → 409 `MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS`.
- Types that back an override, any status: identity = `NATIONAL_ID`, `PASSPORT`; ownership = `LAND_TITLE`,
  `CERTIFICATE_OF_OWNERSHIP`.
- Never log a customer name, document id, page, path, URL or extracted value. The reason is admin-typed and is the only
  free text logged; the mandate page never renders it.
- French UI; code, comments and docs in English. Every page works without JavaScript.
- `#299` and the automatic publish job are unchanged: they already call `OwnershipVerdictService.verify`.
- Tests: `kotlin.test` assertions, `mockito-kotlin` mocks, one SQL fixture per server endpoint test. `mvn verify` in
  each touched module (ktlint, tests, JaCoCo); `ndopify-server` needs local MySQL.

## Review Focus

1. **A non-admin must never write**, whatever the mandate id: 403 before the mandate is loaded, and nothing stored.
   (Task 5.)
2. **An override without both document kinds must not verify**: set-time 409, read-time fallback to the computed
   verdict when the pieces are gone. (Tasks 4 and 5.)
3. **A normal `VERIFIED` verdict must not claim `overridden`**: `false` by default, `true` only for an override, so
   older consumers and #300 are unaffected. (Tasks 1, 2 and 4.)
4. **The reason is mandatory and bounded**: blank or over 500 characters is 400 and writes nothing; no page or HTML
   carries it. (Task 5 and Task 2.)
5. **The gate sees the override**: setting it lets a `FAILED` mandate's `VERIFIED` listing publish; clearing it restores
   the 409. (Tasks 4 and 5.)

---

## File Structure

| Module | Create | Modify |
|---|---|---|
| ndopify-dto | `mandate/dto/OverrideOwnershipVerificationRequest.kt` | `mandate/dto/OwnershipVerification.kt`, `error/dto/ErrorCode.kt` |
| ndopify-partner-central | — | `ui/mandate/MandateOwnershipView.kt`, `ui/mandate/MandateOwnershipViews.kt`, `templates/fragments/mandate-ownership.html`, `test/.../MandateOwnershipViewsTest.kt`, `test/.../MandateTemplateTest.kt` |
| ndopify-server | `db/migration/common/V22_0__mandate-ownership-override.sql`, `mandate/server/service/OwnershipDocumentTypes.kt`, `mandate/server/service/MandateOwnershipOverrideService.kt`, `mandate/server/endpoint/MandateOwnershipOpsEndpoints.kt`, `test/.../OverrideOwnershipEndpointTest.kt`, `test/.../OverrideOwnershipEndpointDisabledTest.kt`, `test/.../OverrideOwnershipPublishEndpointTest.kt`, `test/.../OverrideOwnershipPublishJobTest.kt` | `mandate/server/domain/MandateEntity.kt`, `mandate/server/service/OwnershipVerdictService.kt`, `test/.../OwnershipVerdictServiceTest.kt`, `test/.../PublishVerifiedListingsJobTest.kt` (optional new fixture use), `CLAUDE.md` |

---

### Task 1: ndopify-dto — the `overridden` flag, the request and the error code

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerification.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OverrideOwnershipVerificationRequest.kt`

**Interfaces:**
- Produces: `OwnershipVerification(status: OwnershipVerificationStatus = UNKNOWN, reasons: List<OwnershipVerificationReason> = emptyList(), overridden: Boolean = false)`.
- Produces: `OverrideOwnershipVerificationRequest(reason: String = "")` with `@field:NotBlank` and `@field:Size(max = 500)`.
- Produces: `ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS` (URN `urn:wutsi:ndopify:error:mandate:ownership-override-without-documents`).

- [ ] **Step 1: Add the flag**

In `OwnershipVerification.kt`, add `val overridden: Boolean = false` after `reasons`, and extend the KDoc: `true` only
when an admin override decided the verdict; a consumer must not infer it from the documents.

- [ ] **Step 2: Add the request**

```kotlin
package com.wutsi.ndopify.mandate.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** The admin's mandatory justification for overriding a LISTING mandate's owner verdict (#377). */
data class OverrideOwnershipVerificationRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val reason: String = "",
)
```

- [ ] **Step 3: Add the error code**

In `ErrorCode.kt`, in the mandate group, add:

```kotlin
val MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS = "$PREFIX:mandate:ownership-override-without-documents"
```

- [ ] **Step 4: Build the DTO**

Run: `mvn -pl modules/ndopify-dto install -DskipTests` from the repository root.
Expected: BUILD SUCCESS. The DTO has no tests.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OwnershipVerification.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/OverrideOwnershipVerificationRequest.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): add the owner-verification override flag and error code"
```

---

### Task 2: ndopify-partner-central — port the approved prototype's overridden state

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt`

**Interfaces:**
- Consumes: `OwnershipVerification.overridden` (Task 1).
- Produces: `OwnershipSectionView(..., overridden: Boolean, overriddenText: String?)`; every existing constructor
  passes `false` / `null` except an overridden `VERIFIED`.

- [ ] **Step 1: Write the failing view test**

Add to `MandateOwnershipViewsTest`:

```kotlin
@Test
fun `an overridden verified verdict has its own pill and copy`() {
    val view = MandateOwnershipViews.section(
        mandate,
        OwnershipVerification(VERIFIED, emptyList(), overridden = true),
        listingId = 501,
        labels = labels,
    )

    assertEquals("Vérifiée par Ndopify", view.tagLabel)
    assertEquals("success", view.tagStyle)
    assertTrue(view.overridden)
    assertEquals("Ndopify a vérifié le propriétaire après examen de sa situation.", view.overriddenText)
    assertEquals(
        "Ndopify vérifie que le vendeur est bien le propriétaire du bien.",
        view.intro,
    )
    assertEquals(501L, view.listingId)
    assertTrue(view.reasons.isEmpty())
}
```

And keep the existing `verified has no date and links the listing` asserting `tagLabel == "Vérifié"`, `intro` containing
`"à partir des pièces de la section Documents"`, `overridden == false`, `overriddenText == null`.

- [ ] **Step 2: Run it to see it fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest`
Expected: compilation failure — `overridden` / `overriddenText` are unresolved.

- [ ] **Step 3: Implement the view model**

In `MandateOwnershipView.kt`, add to `OwnershipSectionView`:

```kotlin
val overridden: Boolean,
val overriddenText: String?,
```

In `MandateOwnershipViews.kt`:
- add the constants
  `OVERRIDDEN_TAG = "Vérifiée par Ndopify"` and
  `OVERRIDDEN_TEXT = "Ndopify a vérifié le propriétaire après examen de sa situation."`;
- build the intro as today for the normal states, and for an overridden `VERIFIED` as
  `"Ndopify vérifie que ${MandateRules.words(...).the} est bien le propriétaire du bien."` (no documents clause);
- set `tagLabel` to `OVERRIDDEN_TAG` and `tagStyle` to `"success"` when
  `status == VERIFIED && verification.overridden`, otherwise the existing `TAGS` entry;
- set `overridden = overridden` and `overriddenText = if (overridden) OVERRIDDEN_TEXT else null`;
- pass `overridden = false` and `overriddenText = null` on the unavailable branch.

- [ ] **Step 4: Run the view test**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateOwnershipViewsTest`
Expected: PASS.

- [ ] **Step 5: Write the failing template test**

Add to `MandateTemplateTest`:

```kotlin
@Test
fun `an overridden owner verification has its own state`() {
    doReturn(OwnershipVerification(OwnershipVerificationStatus.VERIFIED, emptyList(), overridden = true))
        .whenever(mandateClient).ownership(201)

    val section = verificationSection()

    assertEquals("Vérifiée par Ndopify", section.selectFirst(".status-tag")!!.text())
    assertTrue(section.text().contains("Ndopify a vérifié le propriétaire après examen de sa situation."))
    assertFalse(section.text().contains("à partir des pièces de la section Documents"))
    assertTrue(section.select("a[href^='/listings']").isEmpty())
    assertFalse(Regex("\\d{2}/\\d{2}/\\d{4}").containsMatchIn(section.text()))
}
```

Add a second assertion in the same test (or a sibling) with
`doReturn(501L).whenever(listingClient).findIdByMandate(201, APPROVED.agentId!!)` and assert the
`a[href='/listings/501']` link. Keep the existing normal-VERIFIED test unchanged.

- [ ] **Step 6: Implement the fragment**

In `templates/fragments/mandate-ownership.html`, inside the existing `VERIFIED` success alert, after the title
`Propriétaire vérifié`, add:

```html
<p th:if="${section.overriddenText}" th:text="${section.overriddenText}" data-ownership-overridden>Ndopify a vérifié
    le propriétaire après examen de sa situation.</p>
```

The intro and the pill already bind to `section.intro` / `section.tagLabel`; do not add a second success block and do
not touch the publication hint or the listing link. The copy is the approved prototype's.

- [ ] **Step 7: Run the template test and the module verification**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateTemplateTest`
Then: `cd modules/ndopify-partner-central && mvn verify`
Expected: PASS; ktlint, all tests and the coverage gate green.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViews.kt \
        modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-ownership.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateOwnershipViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/mandate/MandateTemplateTest.kt
git commit -m "feat(npc): show an overridden owner verification"
```

---

### Task 3: ndopify-server — the mandate's audit columns

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V22_0__mandate-ownership-override.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt`

**Interfaces:**
- Produces: `MandateEntity.ownershipOverrideReason: String?`, `ownershipOverrideBy: Long?`, `ownershipOverrideAt: Date?`,
  columns `ownership_override_reason`, `ownership_override_by`, `ownership_override_at`.

- [ ] **Step 1: Add the migration**

```sql
-- #377: the admin's audited override of a LISTING mandate's owner verdict. The three fields are set and cleared
-- together; a NULL reason means no override. No history table: the set/clear log line is the trace of the action.
ALTER TABLE T_MANDATE
    ADD COLUMN ownership_override_reason VARCHAR(500) NULL,
    ADD COLUMN ownership_override_by BIGINT NULL,
    ADD COLUMN ownership_override_at DATETIME NULL;
```

- [ ] **Step 2: Add the entity fields**

In `MandateEntity`, beside the other nullable display/audit fields (near `cancellationCode`):

```kotlin
val ownershipOverrideReason: String? = null,
val ownershipOverrideBy: Long? = null,
val ownershipOverrideAt: Date? = null,
```

- [ ] **Step 3: Run an existing mandate test to migrate and compile**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateOwnershipEndpointTest` (MySQL up).
Expected: PASS; Flyway applies `V22_0` and the verdict still answers as before.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/resources/db/migration/common/V22_0__mandate-ownership-override.sql \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateEntity.kt
git commit -m "feat(server): store the owner-verification override on the mandate"
```

---

### Task 4: ndopify-server — the verdict honours a backed override

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipDocumentTypes.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictServiceTest.kt`

**Interfaces:**
- Consumes: `MandateEntity.ownershipOverrideReason` (Task 3).
- Produces: `OwnershipDocumentTypes.IDENTITY: Set<DocumentType>` and `OwnershipDocumentTypes.OWNERSHIP: Set<DocumentType>`.
- Produces: `OwnershipVerdictService.verify(mandate)` answering
  `OwnershipVerification(VERIFIED, emptyList(), overridden = true)` when the mandate has a non-null reason and the
  documents still hold both kinds; otherwise the computed verdict with `overridden = false`.

- [ ] **Step 1: Write the failing tests**

Add to `OwnershipVerdictServiceTest`:

```kotlin
@Test
fun `an override with both kinds verifies the owner`() {
    val mandate = mandate(ownershipOverrideReason = "Titre au nom de son père, vérifié en agence")
    doReturn(
        listOf(
            document(DocumentType.NATIONAL_ID, DocumentStatus.FAILED),
            document(DocumentType.LAND_TITLE, DocumentStatus.FAILED),
        ),
    ).whenever(documentDao).findByMandateIdOrderByIdDesc(MANDATE_ID)

    val verification = service.verify(mandate)

    assertVerdict(verification, OwnershipVerificationStatus.VERIFIED)
    assertTrue(verification.overridden)
    assertTrue(verification.reasons.isEmpty())
}

@Test
fun `an override without one kind does not apply`() {
    val mandate = mandate(ownershipOverrideReason = "Titre au nom de son père, vérifié en agence")
    doReturn(listOf(document(DocumentType.NATIONAL_ID, DocumentStatus.FAILED)))
        .whenever(documentDao).findByMandateIdOrderByIdDesc(MANDATE_ID)

    val verification = service.verify(mandate)

    assertVerdict(verification, OwnershipVerificationStatus.FAILED)
    assertFalse(verification.overridden)
}

@Test
fun `a normal verdict is not overridden`() {
    val verification = evaluate(
        document(DocumentType.NATIONAL_ID, DocumentStatus.EXTRACTED, nationalId("Jean Paul", "Mbarga", "2030-01-01")),
        document(DocumentType.LAND_TITLE, DocumentStatus.EXTRACTED, landTitle("Jean Paul Mbarga")),
    )

    assertFalse(verification.overridden)
}
```

Add a fourth test, `an override applies again when the missing kind is back`:

```kotlin
@Test
fun `an override applies again when the missing kind is back`() {
    val mandate = mandate(ownershipOverrideReason = "Titre au nom de son père, vérifié en agence")
    doReturn(listOf(document(DocumentType.NATIONAL_ID, DocumentStatus.FAILED)))
        .whenever(documentDao).findByMandateIdOrderByIdDesc(MANDATE_ID)
    assertFalse(service.verify(mandate).overridden)

    doReturn(
        listOf(
            document(DocumentType.NATIONAL_ID, DocumentStatus.FAILED),
            document(DocumentType.CERTIFICATE_OF_OWNERSHIP, DocumentStatus.FAILED),
        ),
    ).whenever(documentDao).findByMandateIdOrderByIdDesc(MANDATE_ID)
    assertTrue(service.verify(mandate).overridden)
}
```

Add a log assertion after the first test: `assertTrue(logger.entries.contains("ownership_overridden" to "true"))`.
The existing `evaluate(...)` helper calls the pure `evaluate`; the new tests call `verify(mandate)` with the mocked
`documentDao`, `TenantContext` and tenant/customer reads set up as in the existing `verify`-based tests. Add a
`private fun mandate(ownershipOverrideReason: String? = null) = MandateEntity(id = MANDATE_ID, customerId = 10L,
mandateType = MandateType.LISTING, ownershipOverrideReason = ownershipOverrideReason)` and the `TenantContext` /
`customerService` stubs the other `verify` tests use.

- [ ] **Step 2: Run them to see them fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=OwnershipVerdictServiceTest`
Expected: compilation failure — `ownershipOverrideReason` / `overridden` are unresolved.

- [ ] **Step 3: Add the shared type sets**

```kotlin
package com.wutsi.ndopify.mandate.server.service

import com.wutsi.ndopify.mandate.dto.DocumentType

/** The document types that back an owner verdict (#297) and an override of it (#377). */
object OwnershipDocumentTypes {
    val IDENTITY: Set<DocumentType> = setOf(DocumentType.NATIONAL_ID, DocumentType.PASSPORT)
    val OWNERSHIP: Set<DocumentType> = setOf(DocumentType.LAND_TITLE, DocumentType.CERTIFICATE_OF_OWNERSHIP)
}
```

Replace the private `IDENTITY_TYPES` / `OWNERSHIP_TYPES` in `OwnershipVerdictService` with these constants.

- [ ] **Step 4: Honour the override in `verify`**

At the start of `verify`, keep the tenant check, then load the documents once:

```kotlin
val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant in context")
val documents = documentDao.findByMandateIdOrderByIdDesc(mandate.id!!)

val verification = if (mandate.hasBackedOverride(documents)) {
    OwnershipVerification(status = OwnershipVerificationStatus.VERIFIED, overridden = true)
} else {
    val tenant = tenantService.findById(tenantId)
    val today = LocalDate.now(clock.withZone(ZoneId.of(tenant.timeZone)))
    val customerName = customerService.findById(mandate.customerId).party.fullName()
    evaluate(documents, customerName, today)
}
```

Add:

```kotlin
/** The override only applies while both kinds of piece still exist, in any status: it is always backed. */
private fun MandateEntity.hasBackedOverride(documents: List<MandateDocumentEntity>): Boolean =
    ownershipOverrideReason != null &&
        documents.any { it.type in OwnershipDocumentTypes.IDENTITY } &&
        documents.any { it.type in OwnershipDocumentTypes.OWNERSHIP }
```

Add `logger.add("ownership_overridden", verification.overridden)` to the existing one-line verdict log.

- [ ] **Step 5: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=OwnershipVerdictServiceTest`
Expected: PASS, including the existing cases (their verdicts are unchanged and now carry `overridden = false`).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipDocumentTypes.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/OwnershipVerdictServiceTest.kt
git commit -m "feat(server): honour a backed owner-verification override"
```

---

### Task 5: ndopify-server — the admin's set/clear ops endpoints

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateOwnershipOverrideService.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateOwnershipOpsEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/OverrideOwnershipEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/OverrideOwnershipEndpointDisabledTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/OverrideOwnershipPublishEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/OverrideOwnershipPublishJobTest.kt`

**Interfaces:**
- Consumes: `OwnershipDocumentTypes` (Task 4), `MandateEntity.ownershipOverride*` (Task 3),
  `OverrideOwnershipVerificationRequest` and `ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS` (Task 1),
  `CallerService.getPrincipal()` / `isAdmin`, `ForbiddenException`, `NotFoundException`, `BadRequestException`,
  `ConflictException`, `KVLogger`, `Clock`.
- Produces: `POST /v1/mandates/{id}/ownership/override` (body `OverrideOwnershipVerificationRequest`, 204) and
  `POST /v1/mandates/{id}/ownership/override/clear` (204). Both `@OpsEndpoint`.

- [ ] **Step 1: Write the failing service/endpoint test**

Create `OverrideOwnershipEndpointTest` with `@Sql(value = ["/db/test/clean.sql",
"/db/test/mandate/GetMandateOwnershipEndpoint.sql"])` and `roles`/`application` overrides. Mandate 102 is `FAILED` with
an expired ID and a title in someone else's name; 101 has an identity document and only a lease; 106 is a SEARCH
mandate; 200 belongs to tenant 2; 103 belongs to another agent.

```kotlin
@Test
fun `an admin sets and clears the override`() {
    admin = true
    assertEquals(HttpStatus.NO_CONTENT, override(102, "Titre au nom de son père, vérifié en agence").statusCode)

    val set = get(102)
    assertEquals(OwnershipVerificationStatus.VERIFIED, set.status)
    assertTrue(set.overridden)
    assertTrue(set.reasons.isEmpty())
    assertEquals("Titre au nom de son père, vérifié en agence", jdbc.queryForObject(
        "SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java,
    ))
    assertEquals(11L, jdbc.queryForObject("SELECT ownership_override_by FROM T_MANDATE WHERE id=102", Long::class.java))
    assertNotNull(jdbc.queryForObject("SELECT ownership_override_at FROM T_MANDATE WHERE id=102", Date::class.java))

    assertEquals(HttpStatus.NO_CONTENT, clear(102).statusCode)
    val cleared = get(102)
    assertEquals(OwnershipVerificationStatus.FAILED, cleared.status)
    assertFalse(cleared.overridden)
    assertEquals(listOf(OwnershipReasonCodes.ID_EXPIRED, OwnershipReasonCodes.DOCUMENT_NAME_MISMATCH),
        cleared.reasons.map { it.code })
    assertNull(jdbc.queryForObject("SELECT ownership_override_reason FROM T_MANDATE WHERE id=102", String::class.java))
}
```

Add these cases: `an agent is denied` / `a customer is denied` / `an e-signature party is denied` (403
`HTTP_ACCESS_DENIED`, the audit columns stay null), `anonymous - 401`, `unknown mandate - 404`, `other tenant - 404`,
`a search mandate - 400` (`MANDATE_NOT_LISTING`, set and clear), `no ownership document - 409`
(`MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS` on 101, nothing written), `a blank reason - 400`
(`HTTP_INVALID_PARAMETER`), `a reason over 500 - 400`, `clearing without an override is a no-op` (204). Use
`rest.postForEntity(url, OverrideOwnershipVerificationRequest(reason), ErrorResponse::class.java)` and a private
`assertStatus` as in `GetMandateOwnershipEndpointTest`. For the missing-identity side, insert a title-only LISTING
mandate (e.g. id 108 with a single `LAND_TITLE`) with `jdbc.update` in the test (or add it to a copied fixture) and
assert the same 409: both kinds are required, and one test alone does not pin the other side.

- [ ] **Step 2: Run it to see it fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=OverrideOwnershipEndpointTest`
Expected: compilation failure — the endpoints and the request's use are unresolved.

- [ ] **Step 3: Implement `MandateOwnershipOverrideService`**

```kotlin
@Service
class MandateOwnershipOverrideService(
    private val dao: MandateRepository,
    private val documentDao: MandateDocumentRepository,
    private val logger: KVLogger,
    private val clock: Clock,
) {
    /** 404 MANDATE_NOT_FOUND → 400 MANDATE_NOT_LISTING → 409 MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS. */
    @Transactional
    fun set(mandateId: Long, reason: String, userId: Long) {
        val mandate = load(mandateId)
        ensureBacked(mandate)
        dao.save(
            mandate.copy(
                ownershipOverrideReason = reason,
                ownershipOverrideBy = userId,
                ownershipOverrideAt = Date(clock.millis()),
            ),
        )
        log(mandateId, "set", userId, reason)
    }

    /** 404 MANDATE_NOT_FOUND → 400 MANDATE_NOT_LISTING. Idempotent: no override, no write. */
    @Transactional
    fun clear(mandateId: Long, userId: Long) {
        val mandate = load(mandateId)
        if (mandate.ownershipOverrideReason != null) {
            dao.save(mandate.copy(
                ownershipOverrideReason = null,
                ownershipOverrideBy = null,
                ownershipOverrideAt = null,
            ))
        }
        log(mandateId, "clear", userId, null)
    }
}
```

`load` uses `dao.findByIdForUpdate(id)` and throws `NotFoundException(MANDATE_NOT_FOUND)` / `BadRequestException(
MANDATE_NOT_LISTING)`. `ensureBacked` loads `documentDao.findByMandateIdOrderByIdDesc(id)` and throws
`ConflictException(MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS)` unless both
`OwnershipDocumentTypes.IDENTITY` and `OwnershipDocumentTypes.OWNERSHIP` are present. `log` adds `mandate_id`,
`ownership_override` (`set` / `clear`), `user_id` and, for a set, `reason`, then `logger.log()`; nothing else.

- [ ] **Step 4: Implement the endpoint**

```kotlin
@RestController
@RequestMapping("/v1/mandates")
class MandateOwnershipOpsEndpoints(
    private val service: MandateOwnershipOverrideService,
    private val callerService: CallerService,
) {
    @OpsEndpoint
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/ownership/override")
    fun override(@PathVariable id: Long, @RequestBody @Valid request: OverrideOwnershipVerificationRequest) {
        service.set(id, request.reason, ensureAdmin().getUserId())
    }

    @OpsEndpoint
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/{id}/ownership/override/clear")
    fun clear(@PathVariable id: Long) {
        service.clear(id, ensureAdmin().getUserId())
    }

    /** 401 without a token is the security filter's; a non-admin is 403 before the mandate is read. */
    private fun ensureAdmin(): JwtPrincipal {
        val principal = callerService.getPrincipal()
        if (!callerService.isAdmin(principal)) {
            throw ForbiddenException(Error(code = ErrorCode.HTTP_ACCESS_DENIED))
        }
        return principal
    }
}
```

- [ ] **Step 5: Run the endpoint test**

Run: `cd modules/ndopify-server && mvn test -Dtest=OverrideOwnershipEndpointTest`
Expected: PASS.

- [ ] **Step 6: Write the disabled and publication tests**

`OverrideOwnershipEndpointDisabledTest`: same fixture, `@TestPropertySource(properties =
["ndopify.endpoints.ops=false"])`, admin token, both routes 404 `HTTP_NOT_FOUND` and nothing written; `GET
/v1/mandates/102/ownership` still answers.

`OverrideOwnershipPublishEndpointTest`: `@Sql(value = ["/db/test/clean.sql",
"/db/test/listing/PublishListingEndpoint.sql"])`, `roles = arrayOf(RoleCode.ADMIN)`. Mandate 5 / listing 104 is
`VERIFIED` with a `FAILED` owner (expired ID, title in another name):

```kotlin
@Test
fun `an override lets the gate publish`() {
    assertEquals(HttpStatus.CONFLICT, rest.postForEntity("/v1/listings/104/publish", null, ErrorResponse::class.java).statusCode)

    assertEquals(HttpStatus.NO_CONTENT, rest.postForEntity(
        "/v1/mandates/5/ownership/override",
        OverrideOwnershipVerificationRequest("Titre au nom de son père, vérifié en agence"),
        Void::class.java,
    ).statusCode)

    assertEquals(HttpStatus.OK, rest.postForEntity("/v1/listings/104/publish", null, Void::class.java).statusCode)
    assertEquals(ListingStatus.PUBLISHING, listingDao.findById(104L).get().status)
}
```

`OverrideOwnershipPublishJobTest`: `@Sql(value = ["/db/test/clean.sql", "/db/test/listing/PublishListingEndpoint.sql"])`,
the same `ListingContentGenerator` / `ListingPublishedNotifier` `@MockitoBean` setup as
`PublishVerifiedListingsJobTest`. Run `jobs.publish()`, assert listing 104 stays `VERIFIED`; set mandate 5's three audit
columns with `JdbcTemplate`; run `jobs.publish()`; assert listing 104 is `PUBLISHED`. This pins the automatic publish
job on the same verdict.

- [ ] **Step 7: Run the server tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=OverrideOwnershipEndpointTest,OverrideOwnershipEndpointDisabledTest,OverrideOwnershipPublishEndpointTest,OverrideOwnershipPublishJobTest,PublishListingEndpointTest,PublishVerifiedListingsJobTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateOwnershipOverrideService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/endpoint/MandateOwnershipOpsEndpoints.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/OverrideOwnershipEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/OverrideOwnershipEndpointDisabledTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/OverrideOwnershipPublishEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/OverrideOwnershipPublishJobTest.kt
git commit -m "feat(server): add the admin owner-verification override endpoints"
```

---

### Task 6: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the *Ownership verdict* paragraph of the mandate domain)

**Interfaces:**
- Consumes: everything above.

- [ ] **Step 1: Document the override**

In the mandate domain's *Ownership verdict* paragraph, add: the three audit fields and the migration; the
`@OpsEndpoint`s `POST /v1/mandates/{id}/ownership/override` (with a mandatory reason) and `…/override/clear`, off in
prod and admin-only locally until #360's interceptor; the precondition (at least one identity and one ownership
document, any status) and its 409; the read guard (the override only applies while both kinds are present); the
`overridden` flag and the mandate page's *Vérifiée par Ndopify* state; #300 keeps the generic public wording; no
change to #299's gate.

- [ ] **Step 2: Run the module verifications**

Run, in order, with local MySQL up:

```bash
cd modules/ndopify-dto && mvn verify
cd ../ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS in all three — ktlint, tests, JaCoCo gate. No production code outside the tasks above.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document the owner-verification override"
```

---

## Self-Review

- **Spec coverage:** storage and audit (Task 3), verdict effect and read guard (Task 4), set/clear with reason and
  precondition (Task 5), access rules (Task 5), #299 and job effect (Tasks 4 and 5), mandate-page state (Task 2), no
  leak (Tasks 2 and 5), docs (Task 6). The public badge #300 is explicitly untouched, as decided.
- **Step scan:** each step names one file/command and a checkable result; no step says "handle edge cases".
- **Type consistency:** `OwnershipVerification.overridden`, `OverrideOwnershipVerificationRequest.reason`,
  `MandateEntity.ownershipOverride*`, `OwnershipDocumentTypes.IDENTITY/OWNERSHIP`, `OwnershipSectionView.overridden/
  overriddenText`, `ErrorCode.MANDATE_OWNERSHIP_OVERRIDE_WITHOUT_DOCUMENTS` are used with the same names throughout.
- **Review Focus:** every line has a test in its owning task (non-admin 403; backed/unbacked; `overridden` false for a
  normal verdict; blank/oversized reason and no page leak; publish allowed after set and 409 after clear).
- **Proportion:** the plan names interfaces, tests and fixtures; it does not transcribe bodies beyond the algorithms
  that the tests do not determine (the override short-circuit and the guard).
