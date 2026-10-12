# Partner Central KYC page (`/kyc`) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve the KYC prototype (`src/main/html/kyc.html`) as a real, server-rendered page at `/kyc` in
`ndopify-partner-central` (the BFF), so a signed-in agent can upload the front and back of their ID card and a
selfie, submit their case, and then see where it stands, all against the real `ndopify-server` KYC API.

**Architecture:** This works like the *Rejoindre* wizard (`JoinController`): one Thymeleaf page (`kyc.html`),
`GET /kyc?etape=…` shows a step and `POST /kyc/{step}` handles it. The difference is that each photo goes
straight to the agent's PENDING case on `ndopify-server` (`/v1/kyc/cases/{id}/images/upload`), so the server-side
case is the wizard's state. No photo bytes are kept in the HTTP session, and the URL is never trusted, because a
step is only shown once the case has every earlier photo. The page finds the agent's party by looking up the
signed-in user (`GET /v1/users/{id}`, which embeds the party), then the party's case with a "newest case first"
search. Once the case has been submitted, the page shows its status
(*en attente* / *vérifié* / *rejeté*) instead of the wizard. `kyc.js` adds progressive enhancement on top:
photos are shrunk in the browser before upload, and the selfie can be taken with the in-page camera. Every step
still works without JS.

**Tech Stack:** Kotlin, Spring Boot (MVC + Security + RestClient), Thymeleaf, Apache Tika (`tika-core`, already a
partner-central dependency), JUnit 5 + mockito-kotlin + `kotlin.test`, standalone MockMvc, `MockRestServiceServer`,
Jsoup (template tests). Server side: Spring Data JPA, Flyway-seeded MySQL integration tests.

**Spec:** There is no written spec. The prototype *is* the spec:
`modules/ndopify-partner-central/src/main/html/kyc.html` (markup and copy), plus `initKycWizard` / `kycUpload` /
`kycCamera` in `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js` (behaviour). Every
`PROTOTYPE:` comment in those files marks something this plan replaces with a real server call. **Never edit or
delete anything under `src/main/html/`**: it stays as the reference.

## Decisions made in this plan (review these first)

1. **Upload per step, not all at the end.** The prototype says "the real page uploads the three photos, then
   shows the confirmation". Here each step uploads its own photo instead, because a no-JS form can't keep three
   files between pages, and putting photo bytes in the HTTP session is a bad idea. A PENDING case accepts
   replacements (`KycService.upload` upserts per image type), so "Modifier" from the review just re-uploads.
2. **The agent's party is found by looking up the signed-in user, not through a JWT claim.** Partner Central
   only knows `userId` (from the JWT). The new `GET /v1/users/{id}` returns the user with its `Party` embedded
   (`UserEntity.party`). That gives the party id and the header's e-mail and initials in one call, so no
   separate party client is needed. Compared with adding a `partyId` claim to the token, the token format
   doesn't change. Existing tokens never expire, but they already carry `userId`, so they keep working, and the
   party is always the current one rather than the one at sign-in. The cost is one `ndopify-server` call per
   request, which the header's party lookup needed anyway. The new DTOs (`User`, `GetUserResponse`) still
   mean **ndopify-dto 0.0.25 has to be released** before merge (Task 1 / Task 14).
3. **"Current case" means the party's newest case.** `KycService.search` sorts by `createdAt` descending
   (today it sorts by its UUID `id`, which means nothing).
4. **Submitting requires the document's sides on the server, and SELFIE in the BFF.** The server requires the
   FRONT, plus the BACK for two-sided documents (`NATIONAL_ID`, `DRIVER_LICENSE`, `RESIDENT_CARD`). A passport
   only has a front, so "always FRONT + BACK" would be wrong, and "FRONT only" would let a national ID through
   without its back. The server stays selfie-optional (`SubmitKycCaseEndpointTest` asserts that on purpose). The
   BFF only opens `NATIONAL_ID` cases, so its wizard requires all three photos, as the prototype does.
   CLAUDE.md already claims the server requires FRONT and BACK, but it checks nothing today. Task 4 adds the
   check, using the existing national-ID fixtures plus two new passport cases.
5. **Resubmitting after a rejection creates a new case** (`POST /kyc/recommencer` → `POST /v1/kyc/cases`). A
   REJECTED case's images are frozen.
6. **The old multi-page KYC templates (`templates/kyc/*.html`) and their JS (`initKycStatus`, `initKycDocuments`,
   `initKycReview`) are deleted.** No controller serves them, and `kyc.html` replaces them. These are served
   resources, not the `src/main/html/` prototype.
7. **The review thumbnails use presigned URLs** from `GET /v1/kyc/cases/images/{id}/url`, put straight into
   `<img src>`. The BFF only ever asks for image ids that belong to the signed-in agent's own case (that server
   endpoint isn't scoped to a case).

## Global Constraints

- UI copy is French, taken verbatim from the prototype wherever the prototype has it.
- Never edit or delete files under `modules/ndopify-partner-central/src/main/html/`.
- `ndopify-partner-central` calls `ndopify-server` only over HTTP. It never takes a build dependency on it.
- Tests use `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertNull`, `assertFailsWith`), never
  `org.junit.jupiter.api.Assertions.*`.
- Server `@SpringBootTest` mocks use `@MockitoBean`, never `@MockBean`.
- Never modify an existing Flyway migration. (This plan needs no new migration: `T_USER.party_id` already exists.)
- Accepted photo types: `image/jpeg`, `image/png`, `image/webp`, detected from the bytes and never taken from the
  browser's claimed type. Max photo size: 5 MB (5 × 1024 × 1024 bytes).
- Partner-central multipart limit: 10 MB, the same as ndopify-server's `10240KB`.
- Identification type is always `IdentificationType.NATIONAL_ID`, the same as `AgentService.create`.
- Keep README badge blocks intact. Edit READMEs only with targeted edits.
- Run `mvn antrun:run@ktlint-format` in each touched module before committing. `mvn verify` must pass
  (JaCoCo gates: partner-central class threshold is 0.94).

## Review Focus

1. **Phone photos over 5 MB, with no JS.** This is common: a 12 MP JPEG is 4–8 MB. Up to 10 MB the agent gets
   the French size error on the same step. Above 10 MB, `MaxUploadSizeExceededException` turns into a redirect
   to the same step with the same error, not a Whitelabel page or a reset connection. Pinned in Task 8 (5 MB
   check) and Task 11 (multipart limit handler + `max-swallow-size`).
2. **A signed-in user with no party, or one that's gone.** For example, a cross-tenant admin with no party, a
   deleted user, or a user of another tenant (`GET /v1/users/{id}` → 404). Every `/kyc` route must redirect to
   `/logout`, which clears the cookie and lands on the login page, and must never return a 500. Pinned in
   Task 5 (`CurrentParty`), Task 8 (`show`) and Tasks 9–10 (each POST).
3. **A double submit or two tabs.** If `POST /kyc/revue`, an upload, or a "Modifier" hits a case that is
   already SUBMITTED, the agent lands on the status page, not an error. Pinned in Task 9 (upload on a submitted
   case) and Task 10 (submit with server `KYC_CASE_NOT_PENDING`).
4. **A non-image renamed `.jpg`, or a lying `Content-Type`.** Tika sniffs the bytes: a PDF sent as
   `image/jpeg` is rejected with "Format non accepté…" and never reaches the server. Pinned in Task 9.
5. **Restarting after a rejection.** "Soumettre de nouveaux documents" creates exactly one new case, and only
   from REJECTED. A repeated POST, or a POST on a PENDING or VERIFIED case, creates nothing. The new wizard
   starts empty, without the rejected case's photos. Pinned in Task 10.

---

## File Structure

`ndopify-dto`
- Create `src/main/kotlin/com/wutsi/ndopify/security/dto/User.kt` and `GetUserResponse.kt` (already in the working tree).

`ndopify-server`
- Create `security/server/endpoint/UserEndpoints.kt` (`GET /v1/users/{id}`), `security/server/mapper/UserMapper.kt`;
  modify `UserService` (`findById`), `UserRepository` (`isVisible`), `application.yml` (springdoc). All already in
  the working tree.
- Modify `party/server/service/KycService.kt`: newest-first search; `submit` requires FRONT (+ BACK for two-sided documents) with content.
- Tests: `GetUserEndpointTest` (+ its SQL, already in the working tree), `SearchKycCaseEndpointTest`,
  `SubmitKycCaseEndpointTest`.

`ndopify-partner-central` (package `com.wutsi.ndopify.npc`)
- Create `client/ndopify/NdUserClient.kt`: `GET /v1/users/{id}` (null on 404).
- Create `service/security/CurrentParty.kt`: the signed-in user's `Party` (via `NdUserClient`), or null.
- Create `client/ndopify/NdKycClient.kt`: `/v1/kyc/cases` (current, get, create, upload, submit, imageUrl).
- Create `ui/kyc/KycRules.kt`: pure rules (status panel, photos of a case, first incomplete step, photo
  validation, rejection reason, initials).
- Create `ui/kyc/SubmittedKyc.kt`: session marker for the confirmation view.
- Create `ui/kyc/KycController.kt`: `GET /kyc`, `POST /kyc/{recto|verso|selfie}`, `POST /kyc/revue`,
  `POST /kyc/recommencer`.
- Create `ui/kyc/KycUploadErrorHandler.kt`: `@ControllerAdvice` for uploads over the multipart limit.
- Create `src/main/resources/templates/kyc.html`.
- Create `src/main/resources/public/assets/js/kyc.js`.
- Modify `src/main/resources/public/assets/js/partner-central.js`: drop the old KYC initialisers; the user menu
  keeps server-rendered values.
- Replace `src/main/resources/public/assets/css/partner-central.css` with the prototype's copy (the diff is only
  KYC additions; see Task 12).
- Modify `src/main/resources/application.yml`: multipart limits, `server.tomcat.max-swallow-size`.
- Delete `src/main/resources/templates/kyc/{index,revue,confirmation,verification}.html`.
- Tests: `client/ndopify/NdUserClientTest`, `service/security/CurrentPartyTest`,
  `client/ndopify/NdKycClientTest`, `ui/kyc/KycRulesTest`, `ui/kyc/KycControllerTest`,
  `ui/kyc/KycTemplateTest`, `ui/kyc/KycUploadErrorHandlerTest`.

Repo root
- Modify `pom.xml`: `ndopify-dto.version` (Task 1, Task 14).
- Modify `CLAUDE.md`: KYC page, `GET /v1/users/{id}`, submit rule (Task 14).

---

### Task 1: `User` and `GetUserResponse` DTOs (ndopify-dto)

These already exist in the working tree. This task checks them and commits them.

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/security/dto/User.kt` (`id`, `email`, `party: Party?`, `createdAt`)
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/security/dto/GetUserResponse.kt` (`user: User`)
- Modify: `pom.xml` (root), property `ndopify-dto.version`

**Interfaces:**
- Produces: `GetUserResponse(user: User)`, and `User.party: Party?` holds the full `Party` (`id`, `firstName`,
  `lastName`, `email`, …). Partner Central gets the party id *and* the header's e-mail and initials from it.

ndopify-dto has no tests of its own. This is covered by the server's `GetUserEndpointTest` in Task 2.

- [ ] **Step 1: Re-stage the DTOs**

The index holds stale copies (the staged `GetUserResponse.kt` contains `AuthenticateResponse`, and the staged
`UserEndpoints.kt` contains `OptEndpoints`). Check with `git diff --cached`. Each file's working-tree content is
the correct one, so stage it again:

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/security/dto/User.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/security/dto/GetUserResponse.kt
```

- [ ] **Step 2: Point the reactor at the snapshot**

The root `pom.xml` must have `<ndopify-dto.version>0.0.25-SNAPSHOT</ndopify-dto.version>` (the version in
`modules/ndopify-dto/pom.xml`). It's already changed in the working tree. Task 14 switches it to the released
`0.0.25`.

- [ ] **Step 3: Build and install the DTO**

Run: `cd modules/ndopify-dto && mvn install -Dheadless=true`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/security/dto/User.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/security/dto/GetUserResponse.kt pom.xml
git commit -m "feat(dto): User and GetUserResponse"
```

---

### Task 2: `GET /v1/users/{id}` on the server

This also already exists in the working tree. This task checks it and commits it.

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/endpoint/UserEndpoints.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/mapper/UserMapper.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/service/UserService.kt` (`findById`, `findByIdOrNull`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/dao/UserRepository.kt` (`isVisible`)
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (springdoc `security` group: `/v1/users/**`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/security/server/endpoints/GetUserEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/security/GetUserEndpoint.sql`

**Interfaces:**
- Consumes: `User`, `GetUserResponse` (Task 1).
- Produces: `GET /v1/users/{id}` → `GetUserResponse`, with the user's party embedded (or `party: null`). It fails
  with 404 `USER_NOT_FOUND` when the user is unknown, deleted, or belongs to another home tenant than the
  resolved one. Users with no home tenant (cross-tenant) are visible. Partner Central's `NdUserClient` (Task 5)
  relies on the 404.

Users aren't `@TenantId`-filtered (see `UserEntity.tenantId`). `UserService.findByIdOrNull` therefore checks the
user's home tenant with `UserRepository.isVisible`, a count query, **before** it loads the entity. The order
matters: `UserEntity.party` is eager and `PartyEntity` is `@TenantId`-filtered, so loading a user of another
tenant fails. Never call `UserRepository.findById` directly for a request-supplied id. Go through
`UserService.findById`.

- [ ] **Step 1: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetUserEndpointTest`
Expected: PASS. The tests cover these cases: a user with a party, a user without a party (cross-tenant), unknown
(999), deleted (3), and another tenant (4).

- [ ] **Step 2: Commit**

Re-stage `UserEndpoints.kt` first, because the index holds a stale copy (see Task 1, Step 1).

```bash
git add modules/ndopify-server
git commit -m "feat(server): GET /v1/users/{id}"
```

---

### Task 3: KYC case search returns the newest case first

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt` (`search`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SearchKycCaseEndpointTest.kt`

**Interfaces:**
- Produces: `GET /v1/kyc/cases?partyId=X&limit=1` returns the party's most recently created case. Partner
  Central's `NdKycClient.current` (Task 6) relies on this.

- [ ] **Step 1: Write the failing test**

Append to `SearchKycCaseEndpointTest` (in the fixture, `kyc-case-100-b` was created on 2024-06-11 and
`kyc-case-100-a` on 2024-06-10):

```kotlin
    @Test
    fun `newest case first`() {
        val response = rest.getForEntity(
            "/v1/kyc/cases?partyId=100&limit=1",
            SearchKycCaseResponse::class.java,
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(listOf("kyc-case-100-b"), response.body!!.cases.map { it.id })
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=SearchKycCaseEndpointTest`
Expected: `newest case first` FAILS (`[kyc-case-100-a]`, because ids sort alphabetically).

- [ ] **Step 3: Implement**

In `KycService.search`, replace `dao.findAll(spec, Sort.by("id"))` with:

```kotlin
        // Newest first: a party's current case is the one it opened last (a rejected case is followed by a new one).
        return dao.findAll(spec, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='SearchKycCaseEndpointTest,KycJobsTest'`
Expected: PASS (the other search tests use `containsAll`, so they don't depend on order).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server
git commit -m "feat(server): search KYC cases newest first"
```

---

### Task 4: Submitting a KYC case requires the document's sides

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt` (`submit`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/SubmitKycCaseEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/party/SubmitKycCaseEndpoint.sql` (it already
  has `kyc-case-missing-back` and `kyc-case-back-no-content`, both `NATIONAL_ID`; add two `PASSPORT` cases)

**Interfaces:**
- Produces: `POST /v1/kyc/cases/{id}/submit` returns 400 with `ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE`
  when a side that the case's `identificationType` requires is missing or has no content (`path == null`):
  - `NATIONAL_ID`, `DRIVER_LICENSE`, `RESIDENT_CARD` (two-sided): FRONT and BACK
  - `PASSPORT`, `TAX_ID`, `UNKNOWN`: FRONT only
  - SELFIE is always optional on the server.

  Partner Central maps this code to a French message in Task 10. It only opens `NATIONAL_ID` cases, so its
  wizard still asks for both sides.
- `KycCaseVerifierIdentification` needs no change: `AbstractImageBasedKycCaseVerifier` only reads the case's
  images whose type it supports, so a passport case with only a front is verified from its front.

- [ ] **Step 1: Write the failing tests**

In `SubmitKycCaseEndpoint.sql`, add two parties to the `T_PARTY` insert:

```sql
    (500, 1, 'passport@gmail.com', 'Passport', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    (600, 1, 'passport.no.front@gmail.com', 'PassportNoFront', 'Party', 1, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
```

add two cases to the `T_KYC_CASE` insert (`identification_type` 2 = `PASSPORT`):

```sql
    ('kyc-case-passport', 1, 500, 2, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('kyc-case-passport-no-front', 1, 600, 2, NULL, 1, NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
```

and add these images to the `T_KYC_IMAGE` insert. The passport has only a front. The other case has only a selfie:

```sql
    ('passport-front', 1, 'kyc-case-passport', 2, 1, 1, 'kyc/kyc-case-passport/images/passport-front.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
    ('passport-no-front-selfie', 1, 'kyc-case-passport-no-front', 2, 3, 1, 'kyc/kyc-case-passport-no-front/images/passport-no-front-selfie.jpg', 'image/jpeg', '2024-06-10 00:00:00', '2024-06-10 00:00:00'),
```

(Keep the commas right: the last row of each insert ends with `;`.)

Append to `SubmitKycCaseEndpointTest`:

```kotlin
    @Test
    fun `national ID without back - bad request`() {
        val response = submitWithError("kyc-case-missing-back")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE, response.body?.error?.code)
        assertStatus(KycStatus.PENDING, "kyc-case-missing-back")
    }

    @Test
    fun `national ID whose back has no content - bad request`() {
        val response = submitWithError("kyc-case-back-no-content")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE, response.body?.error?.code)
        assertStatus(KycStatus.PENDING, "kyc-case-back-no-content")
    }

    @Test
    fun `passport with front only - submitted, a passport has no back`() {
        val response = submit("kyc-case-passport")

        assertEquals(HttpStatus.OK, response.statusCode)
        assertStatus(KycStatus.SUBMITTED, "kyc-case-passport")
    }

    @Test
    fun `passport without front - bad request`() {
        val response = submitWithError("kyc-case-passport-no-front")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE, response.body?.error?.code)
        assertStatus(KycStatus.PENDING, "kyc-case-passport-no-front")
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=SubmitKycCaseEndpointTest`
Expected: the three `bad request` tests FAIL with `expected:<400 BAD_REQUEST> but was:<200 OK>`. The passport
front-only test already passes, because nothing is checked today. It pins the rule against over-tightening.

- [ ] **Step 3: Implement**

In `KycService.submit`, after `ensureStatusIsPending(case)`:

```kotlin
        ensureIdentificationImages(case)
```

Add next to the other `ensure*` helpers (`IdentificationType` and `KycImageType` are already imported):

```kotlin
    /** The sides of the identification document must be uploaded before submitting. The selfie is optional. */
    private fun ensureIdentificationImages(case: KycCaseEntity) {
        val uploaded = case.images.filter { image -> image.path != null }.map { image -> image.imageType }
        val missing = requiredImages(case.identificationType).filter { type -> type !in uploaded }
        if (missing.isNotEmpty()) {
            throw BadRequestException(
                error = Error(
                    code = ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE,
                    message = "Case#${case.id} (${case.identificationType}) is missing: ${missing.joinToString()}",
                )
            )
        }
    }

    /** Two-sided documents need their front and back; the others (e.g. a passport) only their front. */
    private fun requiredImages(type: IdentificationType): List<KycImageType> = when (type) {
        IdentificationType.NATIONAL_ID,
        IdentificationType.DRIVER_LICENSE,
        IdentificationType.RESIDENT_CARD,
        -> listOf(KycImageType.FRONT, KycImageType.BACK)

        else -> listOf(KycImageType.FRONT)
    }
```

Also fix the `submit` KDoc. It says "Requires the front and back"; make it "Requires the document's front, and
its back for two-sided documents (see [requiredImages]). The selfie is optional."

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='SubmitKycCaseEndpointTest,VerifyKycCaseEndpointTest,KycJobsTest'`
Expected: PASS

- [ ] **Step 5: Full server verify, then commit**

Run: `cd modules/ndopify-server && mvn antrun:run@ktlint-format && mvn verify`
Expected: `BUILD SUCCESS` (coverage gates included).

```bash
git add modules/ndopify-server
git commit -m "feat(server): submitting a KYC case requires the document's front (and back if two-sided)"
```

---

### Task 5: `NdUserClient` and `CurrentParty` in Partner Central

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdUserClient.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/service/security/CurrentParty.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdUserClientTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/service/security/CurrentPartyTest.kt`

**Interfaces:**
- Consumes: `GET /v1/users/{id}` (Task 2). `JwtAuthenticationFilter` already puts a `JwtAuthentication` whose
  principal is a `JwtPrincipal` into the `SecurityContextHolder`, and `JwtPrincipal.getUserId()` already exists.
- Produces:
  - `NdUserClient.get(id: Long): User?`: null on a 404 (`USER_NOT_FOUND`). Other errors are thrown as
    `HttpClientErrorException`.
  - `@Service class CurrentParty { fun get(): Party? }`: the signed-in agent's party. It's null when nobody is
    signed in, the user is gone, or the user has no party (e.g. a cross-tenant admin). Controllers inject it, and
    tests mock it, instead of reading `SecurityContextHolder` directly. Each call is one server round-trip, so
    call it once per request and pass the `Party` along.
- **Security:** `NdUserClient.get` is only ever called with the signed-in user's own `userId` from the JWT, never
  with an id from the request. `ndopify-server` doesn't authorize `GET /v1/users/{id}` yet (`permitAll`), and the
  response holds the party's PII.

- [ ] **Step 1: Write the failing tests**

`NdUserClientTest.kt`:

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NdUserClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdUserClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdUserClient("http://localhost:8080", builder)
    }

    @Test
    fun get() {
        server.expect(requestTo("http://localhost:8080/v1/users/11"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"user":{"id":11,"email":"ray@gmail.com","party":{"id":100,"firstName":"Ray","lastName":"Sponsible","email":"ray@gmail.com"}}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val user = client.get(11)

        assertEquals(11L, user?.id)
        assertEquals(100L, user?.party?.id)
        assertEquals("Ray", user?.party?.firstName)
        server.verify()
    }

    @Test
    fun `get unknown user`() {
        server.expect(requestTo("http://localhost:8080/v1/users/11"))
            .andRespond(
                withStatus(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"urn:wutsi:ndopify:error:user:not-found"}}"""),
            )

        assertNull(client.get(11))
    }

    @Test
    fun `get with another error`() {
        server.expect(requestTo("http://localhost:8080/v1/users/11"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST))

        assertFailsWith<HttpClientErrorException> { client.get(11) }
    }
}
```

`CurrentPartyTest.kt`:

```kotlin
package com.wutsi.ndopify.npc.service.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdUserClient
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.security.dto.JwtPrincipal
import com.wutsi.ndopify.security.dto.User
import org.junit.jupiter.api.AfterEach
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CurrentPartyTest {
    private val userClient = mock<NdUserClient>()
    private val currentParty = CurrentParty(userClient)

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    private fun signIn(userId: Long) {
        val token = JWT.create().withIssuer(JwtDecoder.ISSUER).withClaim(JwtPrincipal.CLAIM_USER_ID, userId)
            .sign(Algorithm.none())
        SecurityContextHolder.setContext(SecurityContextImpl(JwtAuthentication(JwtPrincipal(JWT.decode(token)))))
    }

    @Test
    fun party() {
        signIn(11L)
        doReturn(User(id = 11L, party = Party(id = 100L))).whenever(userClient).get(11L)

        assertEquals(100L, currentParty.get()?.id)
    }

    @Test
    fun `user without party`() {
        signIn(11L)
        doReturn(User(id = 11L, party = null)).whenever(userClient).get(11L)

        assertNull(currentParty.get())
    }

    @Test
    fun `user not found`() {
        signIn(11L)
        doReturn(null).whenever(userClient).get(11L)

        assertNull(currentParty.get())
    }

    @Test
    fun anonymous() {
        assertNull(currentParty.get())
        verify(userClient, never()).get(any())
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='NdUserClientTest,CurrentPartyTest'`
Expected: compile failure, `Unresolved reference: NdUserClient` / `CurrentParty`.

- [ ] **Step 3: Implement `NdUserClient`**

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.security.dto.GetUserResponse
import com.wutsi.ndopify.security.dto.User
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server user endpoints (`/v1/users`). Users of another home tenant than `X-Tenant-ID`
 * (see `RestConfiguration`) are reported as not found.
 */
@Service
class NdUserClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The user with its party, or null when it doesn't exist (any more). */
    fun get(id: Long): User? =
        try {
            rest.get()
                .uri("/v1/users/{id}", id)
                .retrieve()
                .body<GetUserResponse>()
                ?.user
        } catch (ex: HttpClientErrorException) {
            if (ex.statusCode == HttpStatus.NOT_FOUND) null else throw ex
        }
}
```

- [ ] **Step 4: Implement `CurrentParty`**

```kotlin
package com.wutsi.ndopify.npc.service.security

import com.wutsi.ndopify.npc.client.ndopify.NdUserClient
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.security.dto.JwtPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/** The signed-in agent's party, looked up from the user in the access-token cookie (see [JwtAuthenticationFilter]). */
@Service
class CurrentParty(private val userClient: NdUserClient) {
    /** Null when nobody is signed in, or the user is gone or has no party: those must sign in again. */
    fun get(): Party? {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? JwtPrincipal
            ?: return null
        return userClient.get(principal.getUserId())?.party
    }
}
```

- [ ] **Step 5: Run them to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='NdUserClientTest,CurrentPartyTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): CurrentParty looks up the signed-in agent's party"
```

---

### Task 6: `NdKycClient`

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdKycClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdKycClientTest.kt`

**Interfaces:**
- Consumes: the server's newest-first search (Task 3).
- Produces:
  - `NdKycClient.current(partyId: Long): KycCase?`: the newest case, with images, or null
  - `NdKycClient.get(id: String): KycCase`
  - `NdKycClient.create(partyId: Long): String`: the new case id (`NATIONAL_ID`)
  - `NdKycClient.upload(caseId: String, imageType: KycImageType, content: ByteArray, mimeType: String)`
  - `NdKycClient.submit(caseId: String)`
  - `NdKycClient.imageUrl(imageId: String): String`
  - All of them throw `HttpClientErrorException` on 4xx, the same as the other clients.

- [ ] **Step 1: Write the failing client test**

`NdKycClientTest.kt`:

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NdKycClientTest {
    private lateinit var server: MockRestServiceServer
    private lateinit var client: NdKycClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder()
        server = MockRestServiceServer.bindTo(builder).build()
        client = NdKycClient("http://localhost:8080", builder)
    }

    @Test
    fun current() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases?partyId=100&limit=1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"cases":[{"id":"case-1","partyId":100,"status":"PENDING"}]}""", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases/case-1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(
                withSuccess(
                    """{"case":{"id":"case-1","partyId":100,"status":"PENDING","images":[{"id":"img-1","imageType":"FRONT"}]}}""",
                    MediaType.APPLICATION_JSON,
                ),
            )

        val case = client.current(100)

        assertEquals("case-1", case?.id)
        assertEquals(KycStatus.PENDING, case?.status)
        assertEquals(listOf(KycImageType.FRONT), case?.images?.map { it.imageType })
        server.verify()
    }

    @Test
    fun `current when the party has no case`() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases?partyId=100&limit=1"))
            .andRespond(withSuccess("""{"cases":[]}""", MediaType.APPLICATION_JSON))

        assertNull(client.current(100))
        server.verify()
    }

    @Test
    fun create() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("""{"partyId":100,"identificationType":"NATIONAL_ID"}"""))
            .andRespond(withSuccess("""{"caseId":"case-2"}""", MediaType.APPLICATION_JSON))

        assertEquals("case-2", client.create(100))
        server.verify()
    }

    @Test
    fun upload() {
        server.expect(
            requestTo("http://localhost:8080/v1/kyc/cases/case-1/images/upload?identificationType=NATIONAL_ID&imageType=SELFIE"),
        )
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"multipartFile\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Content-Type: image/jpeg")))
            .andRespond(withSuccess())

        client.upload("case-1", KycImageType.SELFIE, byteArrayOf(1, 2, 3), "image/jpeg")
        server.verify()
    }

    @Test
    fun `upload on a case that is not pending`() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("http://localhost:8080/v1/kyc/cases/case-1/images/upload")))
            .andRespond(
                withStatus(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"error":{"code":"urn:wutsi:ndopify:error:kyc-case:not-pending"}}"""),
            )

        assertFailsWith<HttpClientErrorException> {
            client.upload("case-1", KycImageType.FRONT, byteArrayOf(1), "image/png")
        }
    }

    @Test
    fun submit() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases/case-1/submit"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess())

        client.submit("case-1")
        server.verify()
    }

    @Test
    fun imageUrl() {
        server.expect(requestTo("http://localhost:8080/v1/kyc/cases/images/img-1/url"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("""{"url":"https://s3/img-1.jpg"}""", MediaType.APPLICATION_JSON))

        assertEquals("https://s3/img-1.jpg", client.imageUrl("img-1"))
        server.verify()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdKycClientTest`
Expected: compile failure, `Unresolved reference: NdKycClient`.

- [ ] **Step 3: Implement `NdKycClient`**

```kotlin
package com.wutsi.ndopify.npc.client.ndopify

import com.wutsi.ndopify.party.dto.CreateKycCaseRequest
import com.wutsi.ndopify.party.dto.CreateKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycCaseResponse
import com.wutsi.ndopify.party.dto.GetKycImageUrlResponse
import com.wutsi.ndopify.party.dto.IdentificationType
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.SearchKycCaseResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Client for the ndopify-server KYC case endpoints (`/v1/kyc/cases`). Cases are tenant-scoped (`X-Tenant-ID`, see
 * `RestConfiguration`). Agents always verify with their national ID card, as `AgentService.create` opens their
 * first case with it.
 */
@Service
class NdKycClient(
    @Value("\${ndopify.server.api.base-url}") baseUrl: String,

    builder: RestClient.Builder,
) {
    companion object {
        val IDENTIFICATION_TYPE = IdentificationType.NATIONAL_ID
    }

    private val rest: RestClient = builder
        .baseUrl(baseUrl)
        .build()

    /** The party's current case, the one it opened last, with its images; null when it has none. */
    fun current(partyId: Long): KycCase? {
        val summary = rest.get()
            .uri { uri -> uri.path("/v1/kyc/cases").queryParam("partyId", partyId).queryParam("limit", 1).build() }
            .retrieve()
            .body<SearchKycCaseResponse>()
            ?.cases
            ?.firstOrNull()
            ?: return null
        return get(summary.id)
    }

    fun get(id: String): KycCase =
        (
            rest.get()
                .uri("/v1/kyc/cases/{id}", id)
                .retrieve()
                .body<GetKycCaseResponse>()
                ?: GetKycCaseResponse()
            ).case

    /** Opens a new PENDING case for [partyId] and returns its id. */
    fun create(partyId: Long): String =
        rest.post()
            .uri("/v1/kyc/cases")
            .body(CreateKycCaseRequest(partyId = partyId, identificationType = IDENTIFICATION_TYPE))
            .retrieve()
            .body<CreateKycCaseResponse>()
            ?.caseId
            ?: ""

    /**
     * Adds (or replaces) the [imageType] photo of a PENDING case. [mimeType] is sent as the part's content type: the
     * server rejects anything that isn't `image/` with `IDENTIFICATION_IMAGE_INVALID_MIME_TYPE`.
     */
    fun upload(caseId: String, imageType: KycImageType, content: ByteArray, mimeType: String) {
        val file = object : ByteArrayResource(content) {
            // A file name makes the part a file upload (a `MultipartFile`) on the server side.
            override fun getFilename(): String = imageType.name.lowercase()
        }
        val body = MultipartBodyBuilder()
            .apply { part("multipartFile", file).contentType(MediaType.parseMediaType(mimeType)) }
            .build()

        rest.post()
            .uri { uri ->
                uri.path("/v1/kyc/cases/{id}/images/upload")
                    .queryParam("identificationType", IDENTIFICATION_TYPE)
                    .queryParam("imageType", imageType)
                    .build(caseId)
            }
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(body)
            .retrieve()
            .toBodilessEntity()
    }

    /** PENDING → SUBMITTED. Fails with `KYC_CASE_NOT_PENDING` or `KYC_CASE_MISSING_IDENTIFICATION_IMAGE`. */
    fun submit(caseId: String) {
        rest.post()
            .uri("/v1/kyc/cases/{id}/submit", caseId)
            .retrieve()
            .toBodilessEntity()
    }

    /** A short-lived URL of the image, for an `<img src>`. */
    fun imageUrl(imageId: String): String =
        rest.get()
            .uri("/v1/kyc/cases/images/{id}/url", imageId)
            .retrieve()
            .body<GetKycImageUrlResponse>()
            ?.url
            ?: ""
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=NdKycClientTest`
Expected: PASS. If `upload`'s `Content-Type: image/jpeg` match fails, print the body with
`content().string(...)` and check the part header. `MultipartBodyBuilder` writes it per part.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): KYC client"
```

---

### Task 7: `KycRules`, the wizard's pure rules

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycRulesTest.kt`

**Interfaces:**
- Produces (an `object KycRules`):
  - step names `STEP_FRONT = "recto"`, `STEP_BACK = "verso"`, `STEP_SELFIE = "selfie"`, `STEP_REVIEW = "revue"`,
    `STEP_CONFIRMATION = "confirmation"`, `STEPS: List<String>` (the 4 wizard steps), and
    `PHOTOS: Map<String, KycImageType>` (upload step → image type, in step order)
  - status panels `STATUS_PENDING = "attente"`, `STATUS_VERIFIED = "verifie"`, `STATUS_REJECTED = "rejete"`
  - `MAX_PHOTO_BYTES: Long`, `ACCEPTED_TYPES: Set<String>`
  - `fun status(case: KycCase?): String?`: null means "show the wizard"
  - `fun photos(case: KycCase?): Map<KycImageType, KycImage>`: only a PENDING case's photos
  - `fun firstIncompleteStep(photos: Set<KycImageType>): String`
  - `fun photo(size: Long, mimeType: String?): String?`: a French error, or null
  - `fun rejectionReason(errorCode: String?): String`
  - `fun initials(firstName: String?, lastName: String?): String`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KycRulesTest {
    private val front = KycImage(id = "f", imageType = KycImageType.FRONT)
    private val back = KycImage(id = "b", imageType = KycImageType.BACK)

    @Test
    fun status() {
        assertNull(KycRules.status(null))
        assertNull(KycRules.status(KycCase(status = KycStatus.PENDING)))
        assertNull(KycRules.status(KycCase(status = KycStatus.CANCELLED)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.SUBMITTED)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.IN_PROGRESS)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.REQUIRES_MANUAL_REVIEW)))
        assertEquals("attente", KycRules.status(KycCase(status = KycStatus.UNKNOWN)))
        assertEquals("verifie", KycRules.status(KycCase(status = KycStatus.VERIFIED)))
        assertEquals("rejete", KycRules.status(KycCase(status = KycStatus.REJECTED)))
    }

    @Test
    fun `photos of a pending case only`() {
        val images = listOf(front, back)

        assertEquals(setOf(KycImageType.FRONT, KycImageType.BACK), KycRules.photos(KycCase(status = KycStatus.PENDING, images = images)).keys)
        assertTrue(KycRules.photos(KycCase(status = KycStatus.REJECTED, images = images)).isEmpty())
        assertTrue(KycRules.photos(null).isEmpty())
    }

    @Test
    fun firstIncompleteStep() {
        assertEquals("recto", KycRules.firstIncompleteStep(emptySet()))
        assertEquals("verso", KycRules.firstIncompleteStep(setOf(KycImageType.FRONT)))
        assertEquals("recto", KycRules.firstIncompleteStep(setOf(KycImageType.BACK, KycImageType.SELFIE)))
        assertEquals("selfie", KycRules.firstIncompleteStep(setOf(KycImageType.FRONT, KycImageType.BACK)))
        assertEquals("revue", KycRules.firstIncompleteStep(setOf(KycImageType.FRONT, KycImageType.BACK, KycImageType.SELFIE)))
    }

    @Test
    fun photo() {
        assertNull(KycRules.photo(1000, "image/jpeg"))
        assertNull(KycRules.photo(1000, "image/png"))
        assertNull(KycRules.photo(KycRules.MAX_PHOTO_BYTES, "image/webp"))
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", KycRules.photo(1000, "application/pdf"))
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", KycRules.photo(1000, null))
        assertEquals(
            "Cette photo pèse 6,2 Mo. La taille maximale est de 5 Mo.",
            KycRules.photo((6.2 * 1024 * 1024).toLong(), "image/jpeg"),
        )
    }

    @Test
    fun rejectionReason() {
        assertEquals("votre carte d'identité n'est plus valide.", KycRules.rejectionReason(KycErrorCode.EXPIRED))
        assertEquals("vos documents n'ont pas pu être vérifiés.", KycRules.rejectionReason(null))
        assertEquals("vos documents n'ont pas pu être vérifiés.", KycRules.rejectionReason("SOMETHING_NEW"))
    }

    @Test
    fun initials() {
        assertEquals("RS", KycRules.initials("ray", "Sponsible"))
        assertEquals("R", KycRules.initials("Ray", ""))
        assertEquals("NP", KycRules.initials(null, null))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycRulesTest`
Expected: compile failure, `Unresolved reference: KycRules`.

- [ ] **Step 3: Implement**

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import java.util.Locale

/** Rules of the identity verification page (`kyc.html`), kept free of HTTP so they can be tested on their own. */
object KycRules {
    const val STEP_FRONT = "recto"
    const val STEP_BACK = "verso"
    const val STEP_SELFIE = "selfie"
    const val STEP_REVIEW = "revue"
    const val STEP_CONFIRMATION = "confirmation"

    val STEPS = listOf(STEP_FRONT, STEP_BACK, STEP_SELFIE, STEP_REVIEW)

    /** The photo each upload step adds to the case, in the order of the steps. */
    val PHOTOS: Map<String, KycImageType> = linkedMapOf(
        STEP_FRONT to KycImageType.FRONT,
        STEP_BACK to KycImageType.BACK,
        STEP_SELFIE to KycImageType.SELFIE,
    )

    const val STATUS_PENDING = "attente"
    const val STATUS_VERIFIED = "verifie"
    const val STATUS_REJECTED = "rejete"

    const val MAX_PHOTO_BYTES: Long = 5L * 1024 * 1024
    val ACCEPTED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    /**
     * The status panel shown instead of the wizard, or null while the agent still has documents to send: no case
     * yet, a PENDING one, or a cancelled one (the next upload opens a new case). Any status this page doesn't
     * know about reads as "en attente", since there is nothing for the agent to do.
     */
    fun status(case: KycCase?): String? = when (case?.status) {
        null, KycStatus.PENDING, KycStatus.CANCELLED -> null
        KycStatus.VERIFIED -> STATUS_VERIFIED
        KycStatus.REJECTED -> STATUS_REJECTED
        else -> STATUS_PENDING
    }

    /** The photos already in the case, by type. Only a PENDING case's photos count: the others are frozen. */
    fun photos(case: KycCase?): Map<KycImageType, KycImage> =
        if (case?.status == KycStatus.PENDING) case.images.associateBy { image -> image.imageType } else emptyMap()

    /** The first upload step whose photo is missing, or the review once they're all in. */
    fun firstIncompleteStep(photos: Set<KycImageType>): String =
        PHOTOS.entries.firstOrNull { (_, type) -> type !in photos }?.key ?: STEP_REVIEW

    /** Checks a photo from its real (sniffed) type and size; null when it's acceptable. */
    fun photo(size: Long, mimeType: String?): String? = when {
        mimeType !in ACCEPTED_TYPES -> "Format non accepté. Utilisez une photo JPG, PNG ou WEBP."
        size > MAX_PHOTO_BYTES -> {
            val mb = String.format(Locale.FRANCE, "%.1f", size / (1024.0 * 1024.0))
            "Cette photo pèse $mb Mo. La taille maximale est de 5 Mo."
        }
        else -> null
    }

    /** Ends "Motif : …" on the rejected status, from the case's error code (set by the ID / selfie verification). */
    fun rejectionReason(errorCode: String?): String = when (errorCode) {
        KycErrorCode.EXPIRED -> "votre carte d'identité n'est plus valide."
        KycErrorCode.TYPE_MISMATCH -> "le document envoyé n'est pas une carte nationale d'identité."
        KycErrorCode.INVALID -> "les informations de votre carte n'ont pas pu être lues."
        KycErrorCode.LOW_SCORE ->
            "vos photos ne sont pas assez nettes, ou votre selfie ne correspond pas à la photo de votre carte."
        else -> "vos documents n'ont pas pu être vérifiés."
    }

    /** Initials for the header's account button; "NP" (Ndopify Partner) when the name is unknown. */
    fun initials(firstName: String?, lastName: String?): String =
        listOfNotNull(firstName, lastName)
            .mapNotNull { name -> name.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "NP" }
}
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycRulesTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): KYC wizard rules"
```

---

### Task 8: `KycController`: `GET /kyc` (wizard, status, confirmation)

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt`
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/SubmittedKyc.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycControllerTest.kt`

**Interfaces:**
- Consumes: `CurrentParty.get()` (Task 5); `NdKycClient.current/imageUrl` (Task 6);
  `KycRules` (Task 7).
- Produces (the model contract that `kyc.html` in Task 12 renders):
  - always: `view` (`"wizard"` | `"status"` | `"confirmation"`), `email: String?`, `initials: String`
  - wizard: `step`, `stepNumber` (1–4), `stepCount` (4), `returning: Boolean`, `error: String?`,
    `photoUrls: Map<String, String>` (keyed by `KycImageType.name`: the current step's photo on an upload step,
    all three on the review)
  - status: `status` (`"attente"` | `"verifie"` | `"rejete"`), `rejectionReason: String?` (when rejected)
  - `data class SubmittedKyc(val caseId: String) : Serializable` with `SESSION_ATTRIBUTE = "npc.submitted-kyc"`
  - The private helpers `wizard(...)`, `redirect(step)`, `errorCode(ex)` are reused by Tasks 9–11.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.KycStatus
import com.wutsi.ndopify.party.dto.Party
import com.wutsi.ndopify.refdata.dto.KycErrorCode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.view.InternalResourceViewResolver
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KycControllerTest {
    companion object {
        const val PARTY_ID = 100L
    }

    private val kycClient = mock<NdKycClient>()
    private val currentParty = mock<CurrentParty>()
    private lateinit var session: MockHttpSession
    private lateinit var mvc: MockMvc

    private val front = KycImage(id = "img-front", imageType = KycImageType.FRONT)
    private val back = KycImage(id = "img-back", imageType = KycImageType.BACK)
    private val selfie = KycImage(id = "img-selfie", imageType = KycImageType.SELFIE)

    private fun case(status: KycStatus = KycStatus.PENDING, images: List<KycImage> = emptyList(), errorCode: String? = null) =
        KycCase(id = "case-1", partyId = PARTY_ID, status = status, images = images, errorCode = errorCode)

    private fun given(case: KycCase?) {
        doReturn(case).whenever(kycClient).current(PARTY_ID)
    }

    @BeforeEach
    fun setUp() {
        doReturn(Party(id = PARTY_ID, firstName = "Ray", lastName = "Sponsible", email = "ray@gmail.com"))
            .whenever(currentParty).get()
        whenever(kycClient.imageUrl(any())).thenAnswer { invocation -> "https://s3/${invocation.getArgument<String>(0)}.jpg" }

        // The view is named after its URL ("kyc" at /kyc); the default resolver would take that for a loop.
        mvc = MockMvcBuilders.standaloneSetup(KycController(kycClient, currentParty))
            .setViewResolvers(InternalResourceViewResolver("/templates/", ".html"))
            .build()
        session = MockHttpSession()
    }

    private fun model(result: MvcResult) = result.modelAndView!!.model

    @Test
    fun `new case - first step`() {
        given(case())

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("kyc", result.modelAndView?.viewName)
        assertEquals("wizard", model(result)["view"])
        assertEquals("recto", model(result)["step"])
        assertEquals(1, model(result)["stepNumber"])
        assertEquals(4, model(result)["stepCount"])
        assertEquals(false, model(result)["returning"])
        assertNull(model(result)["error"])
        assertEquals(emptyMap<String, String>(), model(result)["photoUrls"])
        assertEquals("ray@gmail.com", model(result)["email"])
        assertEquals("RS", model(result)["initials"])
    }

    @Test
    fun `never skips a missing photo`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?etape=revue").session(session)).andReturn()

        assertEquals("verso", model(result)["step"])
    }

    @Test
    fun `unknown step - first step`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=hack").session(session)).andReturn()

        assertEquals("recto", model(result)["step"])
    }

    @Test
    fun `upload step shows its photo`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?etape=recto").session(session)).andReturn()

        assertEquals(mapOf("FRONT" to "https://s3/img-front.jpg"), model(result)["photoUrls"])
    }

    @Test
    fun review() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=revue").session(session)).andReturn()

        assertEquals("revue", model(result)["step"])
        assertEquals(4, model(result)["stepNumber"])
        assertEquals(
            mapOf("FRONT" to "https://s3/img-front.jpg", "BACK" to "https://s3/img-back.jpg", "SELFIE" to "https://s3/img-selfie.jpg"),
            model(result)["photoUrls"],
        )
    }

    @Test
    fun `returning to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=verso&retour=revue").session(session)).andReturn()

        assertEquals("verso", model(result)["step"])
        assertEquals(true, model(result)["returning"])
    }

    @Test
    fun `no case yet - first step, nothing created`() {
        given(null)

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("recto", model(result)["step"])
        verify(kycClient, never()).create(any())
    }

    @Test
    fun `submitted - pending status`() {
        given(case(status = KycStatus.SUBMITTED, images = listOf(front, back, selfie)))

        val result = mvc.perform(get("/kyc?etape=recto").session(session)).andReturn()

        assertEquals("status", model(result)["view"])
        assertEquals("attente", model(result)["status"])
        verify(kycClient, never()).imageUrl(any())
    }

    @Test
    fun `verified status`() {
        given(case(status = KycStatus.VERIFIED))

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("verifie", model(result)["status"])
    }

    @Test
    fun `rejected status with its reason`() {
        given(case(status = KycStatus.REJECTED, errorCode = KycErrorCode.EXPIRED))

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("rejete", model(result)["status"])
        assertEquals("votre carte d'identité n'est plus valide.", model(result)["rejectionReason"])
    }

    @Test
    fun confirmation() {
        given(case(status = KycStatus.SUBMITTED))
        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = "case-1"))

        val result = mvc.perform(get("/kyc?etape=confirmation").session(session)).andReturn()

        assertEquals("confirmation", model(result)["view"])
    }

    @Test
    fun `confirmation without a submission - status`() {
        given(case(status = KycStatus.SUBMITTED))

        val result = mvc.perform(get("/kyc?etape=confirmation").session(session)).andReturn()

        assertEquals("status", model(result)["view"])
    }

    @Test
    fun `no party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        val result = mvc.perform(get("/kyc").session(session)).andReturn()

        assertEquals("redirect:/logout", result.modelAndView?.viewName)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycControllerTest`
Expected: compile failure, `Unresolved reference: KycController`.

- [ ] **Step 3: Implement `SubmittedKyc`**

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import java.io.Serializable

/** Set once the agent has submitted [caseId]: the confirmation (`/kyc?etape=confirmation`) is shown while it's there. */
data class SubmittedKyc(val caseId: String) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.submitted-kyc"
    }
}
```

- [ ] **Step 4: Implement `KycController` (GET only for now)**

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import com.wutsi.ndopify.error.dto.ErrorResponse
import com.wutsi.ndopify.npc.client.ndopify.NdKycClient
import com.wutsi.ndopify.npc.service.security.CurrentParty
import com.wutsi.ndopify.npc.ui.kyc.KycRules.PHOTOS
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEPS
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_CONFIRMATION
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_FRONT
import com.wutsi.ndopify.npc.ui.kyc.KycRules.STEP_REVIEW
import com.wutsi.ndopify.party.dto.KycCase
import com.wutsi.ndopify.party.dto.KycImage
import com.wutsi.ndopify.party.dto.KycImageType
import com.wutsi.ndopify.party.dto.Party
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.client.HttpClientErrorException

/**
 * The agent's identity verification, on a single page (`kyc.html`), in three views:
 * - wizard: four steps, [STEPS] (recto → verso → selfie → revue). `GET /kyc?etape=…` shows a step. Each upload
 *   step posts its photo to `POST /kyc/{step}`, which adds it straight to the agent's PENDING case on
 *   ndopify-server, so the case is the wizard's state and the session holds no photos. `POST /kyc/revue`
 *   submits the case.
 * - confirmation: `?etape=confirmation`, right after submitting (while [SubmittedKyc] is in the session).
 * - status: once the case is no longer PENDING (en attente / vérifié / rejeté), whatever the URL says. A rejected
 *   case can be restarted (`POST /kyc/recommencer`), which opens a new case.
 *
 * The URL is never trusted: a step is only shown once the case has every photo before it.
 */
@Controller
@RequestMapping("/kyc")
class KycController(
    private val kycClient: NdKycClient,
    private val currentParty: CurrentParty,
) {
    companion object {
        private val LOGGER = LoggerFactory.getLogger(KycController::class.java)

        const val VIEW = "kyc"
        const val RETURN_TO_REVIEW = "revue"

        // No party for the signed-in user (gone, other tenant, or party-less like an admin): have them sign in again.
        const val SIGN_OUT = "redirect:/logout"
    }

    @GetMapping
    fun show(
        @RequestParam(required = false) etape: String?,
        @RequestParam(required = false) retour: String?,
        session: HttpSession,
        model: Model,
    ): String {
        val party = currentParty.get() ?: return SIGN_OUT
        val case = kycClient.current(party.id)
        val status = KycRules.status(case)

        val submitted = session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE) as SubmittedKyc?
        if (etape == STEP_CONFIRMATION && status != null && submitted?.caseId == case?.id) {
            header(party, model)
            model.addAttribute("view", "confirmation")
            return VIEW
        }
        if (status != null) {
            return status(party, status, case!!, model)
        }

        val photos = KycRules.photos(case)
        val requested = STEPS.indexOf(etape).coerceAtLeast(0)
        val allowed = STEPS.indexOf(KycRules.firstIncompleteStep(photos.keys))
        return wizard(party, STEPS[minOf(requested, allowed)], photos, retour, null, model)
    }

    private fun status(party: Party, status: String, case: KycCase, model: Model): String {
        header(party, model)
        model.addAttribute("view", "status")
        model.addAttribute("status", status)
        if (status == KycRules.STATUS_REJECTED) {
            model.addAttribute("rejectionReason", KycRules.rejectionReason(case.errorCode))
        }
        return VIEW
    }

    private fun wizard(
        party: Party,
        step: String,
        photos: Map<KycImageType, KycImage>,
        retour: String?,
        error: String?,
        model: Model,
    ): String {
        header(party, model)
        model.addAttribute("view", "wizard")
        model.addAttribute("step", step)
        model.addAttribute("stepNumber", STEPS.indexOf(step) + 1)
        model.addAttribute("stepCount", STEPS.size)
        model.addAttribute("error", error)
        // "Revenir à la revue" instead of "Suivant": only once every photo is in.
        model.addAttribute(
            "returning",
            retour == RETURN_TO_REVIEW && step != STEP_REVIEW && KycRules.firstIncompleteStep(photos.keys) == STEP_REVIEW,
        )

        // Photos already in the case: an upload step previews its own, the review shows all three.
        val shown = if (step == STEP_REVIEW) photos.values else listOfNotNull(photos[PHOTOS[step]])
        model.addAttribute("photoUrls", shown.associate { image -> image.imageType.name to kycClient.imageUrl(image.id) })
        return VIEW
    }

    private fun header(party: Party, model: Model) {
        model.addAttribute("email", party.email)
        model.addAttribute("initials", KycRules.initials(party.firstName, party.lastName))
    }

    private fun redirect(step: String): String =
        if (step == STEP_FRONT) "redirect:/kyc" else "redirect:/kyc?etape=$step"

    private fun errorCode(ex: HttpClientErrorException): String? =
        try {
            ex.getResponseBodyAs(ErrorResponse::class.java)?.error?.code
        } catch (ignored: Exception) {
            null
        }
}
```

(`redirect`, `errorCode` and `LOGGER` are unused until Task 9. If ktlint or the compiler flags them, add them in
Task 9 instead.)

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycControllerTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): KYC page wizard, status and confirmation views"
```

---

### Task 9: `POST /kyc/{recto|verso|selfie}`: photo uploads

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycControllerTest.kt`

**Interfaces:**
- Consumes: `NdKycClient.current/create/get/upload` (Task 6), `KycRules.photo/photos/firstIncompleteStep` (Task 7),
  `wizard(...)`, `redirect(...)`, `errorCode(...)` (Task 8).
- Produces: the multipart form field name **`photo`** (the template in Task 12 and `kyc.js` in Task 13 post it),
  and the hidden field `retour`. Also `fun toMessage(code: String?): String` (private), which Task 10 extends.

- [ ] **Step 1: Write the failing tests**

Add these imports to `KycControllerTest`: `org.springframework.mock.web.MockMultipartFile`,
`org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart`,
`org.springframework.http.HttpStatus`, `org.springframework.web.client.HttpClientErrorException`,
`com.nhaarman.mockitokotlin2.doThrow`, `com.nhaarman.mockitokotlin2.eq`,
`com.wutsi.ndopify.error.dto.Error`, `com.wutsi.ndopify.error.dto.ErrorCode`, `com.wutsi.ndopify.error.dto.ErrorResponse`.
Then add these members:

```kotlin
    // Magic numbers are enough for Tika to tell the type from the bytes.
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10) + ByteArray(64)
    private val pdf = "%PDF-1.4\n".toByteArray() + ByteArray(64)

    private fun photo(bytes: ByteArray, contentType: String = "image/jpeg") =
        MockMultipartFile("photo", "photo.jpg", contentType, bytes)

    private fun serverError(code: String) = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST, "Bad Request", org.springframework.http.HttpHeaders.EMPTY,
        com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(ErrorResponse(error = Error(code = code))),
        null,
    )

    @Test
    fun `upload front - next step`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient).upload(eq("case-1"), eq(KycImageType.FRONT), eq(jpeg), eq("image/jpeg"))
        assertEquals("redirect:/kyc?etape=verso", result.modelAndView?.viewName)
    }

    @Test
    fun `upload selfie - review`() {
        given(case(images = listOf(front, back)))

        val result = mvc.perform(multipart("/kyc/selfie").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient).upload(eq("case-1"), eq(KycImageType.SELFIE), any(), eq("image/jpeg"))
        assertEquals("redirect:/kyc?etape=revue", result.modelAndView?.viewName)
    }

    @Test
    fun `replace a photo from the review - back to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).param("retour", "revue").session(session)).andReturn()

        assertEquals("redirect:/kyc?etape=revue", result.modelAndView?.viewName)
    }

    @Test
    fun `no new file but the photo is already in - next step, nothing uploaded`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(multipart("/kyc/recto").session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("redirect:/kyc?etape=verso", result.modelAndView?.viewName)
    }

    @Test
    fun `no file - error`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/recto").session(session)).andReturn()

        assertEquals("recto", model(result)["step"])
        assertEquals("Ajoutez une photo pour continuer.", model(result)["error"])
    }

    @Test
    fun `pdf sent as a jpeg - format error, nothing uploaded`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/recto").file(photo(pdf, "image/jpeg")).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", model(result)["error"])
    }

    @Test
    fun `photo over 5 MB - size error`() {
        given(case())
        val big = jpeg + ByteArray(KycRules.MAX_PHOTO_BYTES.toInt())

        val result = mvc.perform(multipart("/kyc/recto").file(photo(big)).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("Cette photo pèse 5,0 Mo. La taille maximale est de 5 Mo.", model(result)["error"])
    }

    @Test
    fun `upload a step ahead of a missing photo - back to the missing one`() {
        given(case())

        val result = mvc.perform(multipart("/kyc/selfie").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `no case yet - one is opened`() {
        given(null)
        doReturn("case-new").whenever(kycClient).create(PARTY_ID)
        doReturn(KycCase(id = "case-new", status = KycStatus.PENDING)).whenever(kycClient).get("case-new")

        mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient).upload(eq("case-new"), eq(KycImageType.FRONT), any(), any())
    }

    @Test
    fun `case already submitted - status, nothing uploaded`() {
        given(case(status = KycStatus.SUBMITTED))

        val result = mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).session(session)).andReturn()

        verify(kycClient, never()).upload(any(), any(), any(), any())
        verify(kycClient, never()).create(any())
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `case submitted meanwhile (other tab) - status`() {
        given(case())
        doThrow(serverError(ErrorCode.KYC_CASE_NOT_PENDING)).whenever(kycClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `server error - generic error on the step`() {
        given(case())
        doThrow(serverError("unexpected")).whenever(kycClient).upload(any(), any(), any(), any())

        val result = mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("recto", model(result)["step"])
        assertEquals("Envoi impossible pour le moment. Réessayez dans quelques instants.", model(result)["error"])
    }

    @Test
    fun `upload without party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        val result = mvc.perform(multipart("/kyc/recto").file(photo(jpeg)).session(session)).andReturn()

        assertEquals("redirect:/logout", result.modelAndView?.viewName)
    }
```

(Check how `JoinControllerTest` builds its `HttpClientErrorException` and copy that helper if it's simpler.)

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycControllerTest`
Expected: the new tests FAIL (404 / `modelAndView` null: there's no handler for `POST /kyc/recto`).

- [ ] **Step 3: Implement**

Add these imports to `KycController`: `org.apache.tika.Tika`, `org.springframework.web.bind.annotation.PathVariable`,
`org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.multipart.MultipartFile`,
`com.wutsi.ndopify.error.dto.ErrorCode`, `com.wutsi.ndopify.party.dto.KycStatus`. Add `private val TIKA = Tika()`
to the companion. Then add:

```kotlin
    @PostMapping("/{step:recto|verso|selfie}")
    fun upload(
        @PathVariable step: String,
        @RequestParam(required = false) photo: MultipartFile?,
        @RequestParam(required = false) retour: String?,
        model: Model,
    ): String {
        val party = currentParty.get() ?: return SIGN_OUT
        val case = openCase(party.id) ?: return "redirect:/kyc"
        val photos = KycRules.photos(case)
        val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
        if (STEPS.indexOf(step) > STEPS.indexOf(firstIncomplete)) return redirect(firstIncomplete)

        val imageType = PHOTOS.getValue(step)
        if (photo == null || photo.isEmpty) {
            // "Suivant" on a step whose photo is already in the case just moves on.
            if (imageType !in photos) {
                return wizard(party, step, photos, retour, "Ajoutez une photo pour continuer.", model)
            }
        } else {
            val content = photo.bytes
            // The browser's content type is a claim; the bytes are the truth.
            val mimeType = TIKA.detect(content)
            KycRules.photo(photo.size, mimeType)?.let { error -> return wizard(party, step, photos, retour, error, model) }
            try {
                kycClient.upload(case.id, imageType, content, mimeType)
            } catch (ex: HttpClientErrorException) {
                LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

                val code = errorCode(ex)
                if (code == ErrorCode.KYC_CASE_NOT_PENDING) return "redirect:/kyc"
                return wizard(party, step, photos, retour, toMessage(code), model)
            }
        }

        val done = photos.keys + imageType
        val next = if (retour == RETURN_TO_REVIEW) STEP_REVIEW else STEPS[STEPS.indexOf(step) + 1]
        return redirect(minOf(next, KycRules.firstIncompleteStep(done), compareBy { STEPS.indexOf(it) }))
    }

    /**
     * The PENDING case photos go to: the current one, or a new one when the agent has none yet (or it was
     * cancelled). Null once the current case has been submitted, since its photos can't change any more.
     */
    private fun openCase(partyId: Long): KycCase? {
        val case = kycClient.current(partyId)
        return when (case?.status) {
            KycStatus.PENDING -> case
            null, KycStatus.CANCELLED -> kycClient.get(kycClient.create(partyId))
            else -> null
        }
    }

    private fun toMessage(code: String?): String = when (code) {
        ErrorCode.IDENTIFICATION_IMAGE_INVALID_MIME_TYPE -> "Format non accepté. Utilisez une photo JPG, PNG ou WEBP."
        else -> "Envoi impossible pour le moment. Réessayez dans quelques instants."
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycControllerTest`
Expected: PASS. If `pdf sent as a jpeg` fails because Tika returns `application/pdf` only with more bytes, make
the fixture a real minimal PDF header (`%PDF-1.4\n%âãÏÓ\n`).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): KYC photo uploads, one per step"
```

---

### Task 10: `POST /kyc/revue` (submit) and `POST /kyc/recommencer` (restart after a rejection)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycControllerTest.kt`

**Interfaces:**
- Consumes: `NdKycClient.submit/create` (Task 6), `SubmittedKyc` (Task 8), `toMessage` (Task 9), the server's
  `KYC_CASE_MISSING_IDENTIFICATION_IMAGE` (Task 4).
- Produces: the form actions `/kyc/revue` and `/kyc/recommencer` used by the template (Task 12).

- [ ] **Step 1: Write the failing tests**

Add the import `org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post`, then:

```kotlin
    @Test
    fun submit() {
        given(case(images = listOf(front, back, selfie)))

        val result = mvc.perform(post("/kyc/revue").session(session)).andReturn()

        verify(kycClient).submit("case-1")
        assertEquals("redirect:/kyc?etape=confirmation", result.modelAndView?.viewName)
        assertEquals(SubmittedKyc(caseId = "case-1"), session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE))
    }

    @Test
    fun `submit with a missing photo - back to it`() {
        given(case(images = listOf(front, back)))

        val result = mvc.perform(post("/kyc/revue").session(session)).andReturn()

        verify(kycClient, never()).submit(any())
        assertEquals("redirect:/kyc?etape=selfie", result.modelAndView?.viewName)
    }

    @Test
    fun `submit twice - status`() {
        given(case(images = listOf(front, back, selfie)))
        doThrow(serverError(ErrorCode.KYC_CASE_NOT_PENDING)).whenever(kycClient).submit(any())

        val result = mvc.perform(post("/kyc/revue").session(session)).andReturn()

        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
        assertNull(session.getAttribute(SubmittedKyc.SESSION_ATTRIBUTE))
    }

    @Test
    fun `submit a case that is no longer pending - status`() {
        given(case(status = KycStatus.SUBMITTED, images = listOf(front, back, selfie)))

        val result = mvc.perform(post("/kyc/revue").session(session)).andReturn()

        verify(kycClient, never()).submit(any())
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `server says a photo is missing - error on the review`() {
        given(case(images = listOf(front, back, selfie)))
        doThrow(serverError(ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE)).whenever(kycClient).submit(any())

        val result = mvc.perform(post("/kyc/revue").session(session)).andReturn()

        assertEquals("revue", model(result)["step"])
        assertEquals(
            "Il manque une ou plusieurs photos. Ajoutez-les pour pouvoir envoyer votre dossier.",
            model(result)["error"],
        )
    }

    @Test
    fun `restart a rejected case - new case`() {
        given(case(status = KycStatus.REJECTED))

        val result = mvc.perform(post("/kyc/recommencer").session(session)).andReturn()

        verify(kycClient).create(PARTY_ID)
        assertEquals("redirect:/kyc", result.modelAndView?.viewName)
    }

    @Test
    fun `restart a pending case - nothing created`() {
        given(case(status = KycStatus.PENDING))

        mvc.perform(post("/kyc/recommencer").session(session)).andReturn()

        verify(kycClient, never()).create(any())
    }

    @Test
    fun `restart a verified case - nothing created`() {
        given(case(status = KycStatus.VERIFIED))

        mvc.perform(post("/kyc/recommencer").session(session)).andReturn()

        verify(kycClient, never()).create(any())
    }

    @Test
    fun `submit without party - signed out`() {
        doReturn(null).whenever(currentParty).get()

        assertEquals("redirect:/logout", mvc.perform(post("/kyc/revue").session(session)).andReturn().modelAndView?.viewName)
        assertEquals("redirect:/logout", mvc.perform(post("/kyc/recommencer").session(session)).andReturn().modelAndView?.viewName)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycControllerTest`
Expected: the new tests FAIL. `POST /kyc/revue` doesn't match `{step:recto|verso|selfie}`, so there's no handler.

- [ ] **Step 3: Implement**

```kotlin
    @PostMapping("/$STEP_REVIEW")
    fun submit(session: HttpSession, model: Model): String {
        val party = currentParty.get() ?: return SIGN_OUT
        val case = kycClient.current(party.id)
        if (case?.status != KycStatus.PENDING) return "redirect:/kyc"

        val photos = KycRules.photos(case)
        val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
        if (firstIncomplete != STEP_REVIEW) return redirect(firstIncomplete)

        try {
            kycClient.submit(case.id)
        } catch (ex: HttpClientErrorException) {
            LOGGER.error("Server error: ${ex.statusCode} ${ex.responseBodyAsString}", ex)

            val code = errorCode(ex)
            if (code == ErrorCode.KYC_CASE_NOT_PENDING) return "redirect:/kyc"
            return wizard(party, STEP_REVIEW, photos, null, toMessage(code), model)
        }

        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = case.id))
        return redirect(STEP_CONFIRMATION)
    }

    /** "Soumettre de nouveaux documents" on a rejected case: a new case, and the wizard starts over empty. */
    @PostMapping("/recommencer")
    fun restart(): String {
        val party = currentParty.get() ?: return SIGN_OUT
        if (kycClient.current(party.id)?.status == KycStatus.REJECTED) {
            kycClient.create(party.id)
        }
        return "redirect:/kyc"
    }
```

Extend `toMessage`:

```kotlin
        ErrorCode.KYC_CASE_MISSING_IDENTIFICATION_IMAGE ->
            "Il manque une ou plusieurs photos. Ajoutez-les pour pouvoir envoyer votre dossier."
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): submit a KYC case, restart after a rejection"
```

---

### Task 11: Uploads over the multipart limit

**Files:**
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycUploadErrorHandler.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt` (`show`)
- Modify: `modules/ndopify-partner-central/src/main/resources/application.yml`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycUploadErrorHandlerTest.kt`
- Test: `KycControllerTest` (one new test)

**Interfaces:**
- Produces: `GET /kyc?etape={step}&erreur=taille` shows that step with
  "Cette photo dépasse la taille maximale de 5 Mo." (only when that step is the one actually shown).

Why: Spring rejects a multipart request over `max-file-size` before any controller runs. Without a handler the
agent gets the error page, and with Tomcat's default `max-swallow-size` (2 MB) the connection may even be reset.

- [ ] **Step 1: Write the failing tests**

`KycUploadErrorHandlerTest.kt`:

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.multipart.MaxUploadSizeExceededException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KycUploadErrorHandlerTest {
    private val handler = KycUploadErrorHandler()
    private val ex = MaxUploadSizeExceededException(10L * 1024 * 1024)

    @Test
    fun `kyc photo step - back to it with the size error`() {
        assertEquals("redirect:/kyc?etape=verso&erreur=taille", handler.tooLarge(MockHttpServletRequest("POST", "/kyc/verso"), ex))
    }

    @Test
    fun `any other upload - not handled here`() {
        assertFailsWith<MaxUploadSizeExceededException> {
            handler.tooLarge(MockHttpServletRequest("POST", "/somewhere/else"), ex)
        }
    }
}
```

Append to `KycControllerTest`:

```kotlin
    @Test
    fun `photo over the upload limit - size error on its step`() {
        given(case(images = listOf(front)))

        val result = mvc.perform(get("/kyc?etape=verso&erreur=taille").session(session)).andReturn()

        assertEquals("verso", model(result)["step"])
        assertEquals("Cette photo dépasse la taille maximale de 5 Mo.", model(result)["error"])
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='KycUploadErrorHandlerTest,KycControllerTest'`
Expected: compile failure for `KycUploadErrorHandler`, and the controller test's `error` is null.

- [ ] **Step 3: Implement the handler**

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * A photo over `spring.servlet.multipart.max-file-size` is rejected before [KycController] runs (no handler is
 * known yet, so this advice has to be global): send the agent back to the step with the size error.
 */
@ControllerAdvice
class KycUploadErrorHandler {
    companion object {
        private val KYC_UPLOAD = Regex("^/kyc/(${KycRules.PHOTOS.keys.joinToString("|")})$")
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun tooLarge(request: HttpServletRequest, ex: MaxUploadSizeExceededException): String {
        val step = KYC_UPLOAD.find(request.requestURI)?.groupValues?.get(1) ?: throw ex
        return "redirect:/kyc?etape=$step&erreur=taille"
    }
}
```

- [ ] **Step 4: Show the error in `show`**

Add `@RequestParam(required = false) erreur: String?` to `KycController.show` (after `retour`), and replace its
last line with:

```kotlin
        val shown = STEPS[minOf(requested, allowed)]
        val error = if (erreur == ERROR_TOO_LARGE && shown == etape) "Cette photo dépasse la taille maximale de 5 Mo." else null
        return wizard(party, shown, photos, retour, error, model)
```

with `const val ERROR_TOO_LARGE = "taille"` in the companion.

- [ ] **Step 5: Configure the limits**

In partner-central `application.yml`, replace the `spring.servlet.multipart` block and add the Tomcat limit
under `server`:

```yaml
    servlet:
        multipart:
            # Same as ndopify-server. Photos over 5 MB are refused by KycController with a proper message;
            # beyond this limit KycUploadErrorHandler does.
            max-file-size: 10MB
            max-request-size: 10MB
```

```yaml
server:
    port: 8081
    tomcat:
        # Lets Tomcat read past the multipart limit so the redirect reaches the browser instead of a reset connection.
        max-swallow-size: 20MB
```

(Keep the existing `server.error` and `server.compression` keys.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='KycUploadErrorHandlerTest,KycControllerTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central
git commit -m "feat(npc): friendly error for KYC photos over the upload limit"
```

---

### Task 12: The `kyc.html` template (plus CSS, and removing the old KYC templates)

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/kyc.html`
- Delete: `modules/ndopify-partner-central/src/main/resources/templates/kyc/` (all four files)
- Replace: `modules/ndopify-partner-central/src/main/resources/public/assets/css/partner-central.css`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycTemplateTest.kt`

**Interfaces:**
- Consumes: the model contract from Task 8 (`view`, `step`, `stepNumber`, `stepCount`, `returning`, `error`,
  `photoUrls`, `status`, `rejectionReason`, `email`, `initials`), the form field `photo` and the actions
  `/kyc/{step}`, `/kyc/revue`, `/kyc/recommencer` (Tasks 9–10).
- Produces: the DOM hooks `kyc.js` (Task 13) relies on, all copied from the prototype: `body[data-page="kyc"]`,
  `[data-step]`, `[data-side]`, `.upload__input`, `[data-preview]`, `[data-placeholder]`, `[data-upload-label]`,
  `[data-upload-status]`, `.field__error` / `[data-error-text]`, `[data-camera-*]`, `[data-next]`, `[data-submit]`,
  `[data-sending]`.

- [ ] **Step 1: Write the failing template test**

Use the same engine setup as `JoinTemplateTest` (copy its `SpringTemplateEngine` + `ClassLoaderTemplateResolver`
+ `ThymeleafViewResolver` block), and the same mocks and fixtures as `KycControllerTest` (`front`/`back`/`selfie`,
`case()`, `given()`, `jpeg`/`pdf`, `photo()`).

```kotlin
package com.wutsi.ndopify.npc.ui.kyc

// imports: as KycControllerTest, plus org.jsoup.Jsoup, org.jsoup.nodes.Document,
// org.thymeleaf.spring6.SpringTemplateEngine, org.thymeleaf.spring6.view.ThymeleafViewResolver,
// org.thymeleaf.templateresolver.ClassLoaderTemplateResolver,
// org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, kotlin.test.assertNotNull, kotlin.test.assertTrue

/**
 * Renders `templates/kyc.html` through [KycController] with a real Thymeleaf engine, to catch template mistakes
 * (expressions, conditions, views) that [KycControllerTest] can't see.
 */
class KycTemplateTest {
    // ... fields, case(), given(), setUp() exactly as KycControllerTest, but with the Thymeleaf view resolver ...

    private fun render(request: MockHttpServletRequestBuilder): Document =
        Jsoup.parse(mvc.perform(request.session(session)).andReturn().response.contentAsString)

    @Test
    fun front() {
        given(case())

        val page = render(get("/kyc"))

        assertEquals("Étape 1 sur 4", page.selectFirst("[data-progress-label]")?.text())
        assertEquals(listOf("recto"), page.select("[data-step]").map { it.attr("data-step") })
        val form = page.selectFirst("form[data-kyc-form]")!!
        assertEquals("/kyc/recto", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals("photo", page.selectFirst(".upload__input")?.attr("name"))
        assertNull(page.selectFirst("[data-preview] img"))
        assertNull(page.selectFirst("[data-prev]")) // the first step has nowhere to go back to
        assertEquals("Suivant", page.selectFirst("[data-next]")?.text())
        assertTrue(page.selectFirst(".field__error")!!.hasAttr("hidden"))
        assertEquals("ray@gmail.com", page.selectFirst("[data-user-email]")?.text())
        assertEquals("RS", page.selectFirst("[data-user-initials]")?.text())
        assertNull(page.selectFirst("[data-status]"))
    }

    @Test
    fun `back with its photo`() {
        given(case(images = listOf(front, back)))

        val page = render(get("/kyc?etape=verso"))

        assertEquals("https://s3/img-back.jpg", page.selectFirst("[data-preview] img")?.attr("src"))
        assertTrue(page.selectFirst("[data-placeholder]")!!.hasAttr("hidden"))
        assertEquals("Changer la photo", page.selectFirst("[data-upload-label]")?.text())
        assertEquals("/kyc", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun `selfie works without JS`() {
        given(case(images = listOf(front, back)))

        val page = render(get("/kyc?etape=selfie"))

        val input = page.selectFirst("#selfie-file")!!
        assertEquals("user", input.attr("capture"))
        assertTrue(!page.selectFirst("[data-camera-fallback]")!!.hasAttr("hidden")) // the file input is visible
        assertTrue(page.selectFirst("[data-camera-start]")!!.hasAttr("hidden")) // kyc.js reveals the camera
    }

    @Test
    fun `step with an error`() {
        given(case())

        val page = render(multipart("/kyc/recto").file(photo(pdf)))

        val error = page.selectFirst(".field__error")!!
        assertTrue(!error.hasAttr("hidden"))
        assertEquals("Format non accepté. Utilisez une photo JPG, PNG ou WEBP.", error.selectFirst("[data-error-text]")?.text())
        assertEquals("true", page.selectFirst(".upload__input")?.attr("aria-invalid"))
    }

    @Test
    fun review() {
        given(case(images = listOf(front, back, selfie)))

        val page = render(get("/kyc?etape=revue"))

        assertEquals("/kyc/revue", page.selectFirst("form[data-kyc-form]")?.attr("action"))
        assertEquals(
            listOf("https://s3/img-front.jpg", "https://s3/img-back.jpg", "https://s3/img-selfie.jpg"),
            page.select("[data-review-image] img").map { it.attr("src") },
        )
        assertEquals("/kyc?etape=verso&retour=revue", page.selectFirst("[data-edit=verso]")?.attr("href"))
        assertEquals("Envoyer mon dossier", page.selectFirst("[data-submit]")?.text())
    }

    @Test
    fun `returning to the review`() {
        given(case(images = listOf(front, back, selfie)))

        val page = render(get("/kyc?etape=verso&retour=revue"))

        assertEquals("revue", page.selectFirst("input[name=retour]")?.`val`())
        assertEquals("Revenir à la revue", page.selectFirst("[data-next]")?.text())
        assertEquals("/kyc?etape=revue", page.selectFirst("[data-prev]")?.attr("href"))
    }

    @Test
    fun confirmation() {
        given(case(status = KycStatus.SUBMITTED))
        session.setAttribute(SubmittedKyc.SESSION_ATTRIBUTE, SubmittedKyc(caseId = "case-1"))

        val page = render(get("/kyc?etape=confirmation"))

        assertEquals("Dossier envoyé", page.selectFirst("#confirmation-title")?.text())
        assertEquals("/kyc", page.selectFirst("[data-show-status]")?.attr("href"))
        assertNull(page.selectFirst("form[data-kyc-form]"))
    }

    @Test
    fun `pending status`() {
        given(case(status = KycStatus.IN_PROGRESS))

        val page = render(get("/kyc"))

        assertNotNull(page.selectFirst("[data-status=attente]"))
        assertNull(page.selectFirst("[data-status=verifie]"))
        assertNull(page.selectFirst("form[data-kyc-form]"))
        assertNull(page.selectFirst(".progress"))
    }

    @Test
    fun `verified status`() {
        given(case(status = KycStatus.VERIFIED))

        val page = render(get("/kyc"))

        assertEquals("/", page.selectFirst("[data-status=verifie] a.btn")?.attr("href"))
    }

    @Test
    fun `rejected status`() {
        given(case(status = KycStatus.REJECTED, errorCode = KycErrorCode.LOW_SCORE))

        val page = render(get("/kyc"))

        val panel = page.selectFirst("[data-status=rejete]")!!
        assertTrue(panel.text().contains("Motif : vos photos ne sont pas assez nettes"))
        assertEquals("/kyc/recommencer", panel.selectFirst("form")?.attr("action"))
    }
}
```

(Write out the elided fields and `setUp()` in full. Don't leave the comment in.)

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=KycTemplateTest`
Expected: FAIL with `TemplateInputException: Error resolving template [kyc]`.

- [ ] **Step 3: Write `templates/kyc.html`**

Start from the prototype `src/main/html/kyc.html` (copy it, never move it) and convert it the same way
`rejoindre.html` was converted from its prototype:

1. `<html lang="fr" xmlns:th="http://www.thymeleaf.org">`. Asset URLs become `th:href="@{/assets/...}"` /
   `th:src="@{/assets/...}"`. Scripts: `@{/assets/js/partner-central.js}` and `@{/assets/js/kyc.js}`, both
   `defer`. `<body data-page="kyc">`.
2. Header: brand link `th:href="@{/}"`. `<span aria-hidden="true" data-user-initials th:text="${initials}">NP</span>`,
   `<li class="user-menu__email" data-user-email th:text="${email}" th:if="${email}"></li>`, and the logout link
   `th:href="@{/logout}"`.
3. Intro: `<div class="page-intro" th:if="${view != 'confirmation'}">`. The warning alert inside it only
   renders `th:if="${view == 'wizard'}"`.
4. Progress (`th:if="${view == 'wizard'}"`): label `th:text="|Étape ${stepNumber} sur ${stepCount}|"`, plus
   `th:attr="aria-valuenow=${stepNumber},aria-valuemax=${stepCount},aria-valuetext=|Étape ${stepNumber} sur ${stepCount}|"`,
   and the bar `th:style="|transform: scaleX(${stepNumber * 1.0 / stepCount})|"`.
5. The form (`th:if="${view == 'wizard'}"`):
   `<form class="form" method="post" enctype="multipart/form-data" novalidate data-kyc-form th:action="@{/kyc/{step}(step=${step})}">`
   (multipart on every step, the review included: it carries no file, and Spring parses it all the same).
   Inside it, `<input type="hidden" name="retour" value="revue" th:if="${returning}">`.
6. Each of the four `<fieldset class="step">` blocks is copied verbatim from the prototype, gets
   `th:if="${step == 'recto'}"` (`'verso'`, `'selfie'`, `'revue'`), and loses its `hidden` attribute. On each
   upload fieldset (`recto` → `FRONT`, `verso` → `BACK`, `selfie` → `SELFIE`, written here for `recto`):
   - file input: `name="photo"` (all three; the prototype had `front` / `back` / `selfie`), plus
     `th:attr="aria-invalid=${error != null} ? 'true' : null"`
   - `<img class="upload__image" th:if="${photoUrls['FRONT']}" th:src="${photoUrls['FRONT']}" alt="Aperçu du recto de votre carte">`
     as the preview's first child (copy the class `placeImage()` uses in the prototype JS)
   - placeholder: `th:hidden="${photoUrls['FRONT'] != null}"` (`hidden` is one of Thymeleaf's boolean attributes)
   - button label: `th:text="${photoUrls['FRONT']} ? 'Changer la photo' : 'Choisir une photo du recto'"`
   - error `<p class="field__error" ... th:hidden="${error == null}">` with `<span data-error-text th:text="${error}">`
   - actions: "Précédent" becomes a link, not a button. It goes back to the review when `returning`, otherwise
     to the previous step, so each fieldset hard-codes its own previous step:
     - `recto`: `<a class="btn btn--outline" data-prev th:if="${returning}" th:href="@{/kyc(etape='revue')}">Précédent</a>`
       (the first step has nowhere else to go back to)
     - `verso`: `<a class="btn btn--outline" data-prev th:href="${returning} ? @{/kyc(etape='revue')} : @{/kyc}">Précédent</a>`
     - `selfie`: the same, with `@{/kyc(etape='verso')}` as the second URL
     - `revue`: `<a class="btn btn--outline" data-prev th:href="@{/kyc(etape='selfie')}">Précédent</a>`

     "Suivant" is
     `<button class="btn btn--primary" type="submit" data-next th:text="${returning} ? 'Revenir à la revue' : 'Suivant'">Suivant</button>`,
     with no `aria-disabled`.
   - selfie only: the camera elements keep their prototype markup, but **`[data-camera-start]` gets `hidden`**
     and **`[data-camera-fallback]` loses `hidden`**. Without JS the agent uses the phone's camera app through
     `<input type="file" capture="user">`, and `kyc.js` swaps them when the in-page camera can be used.
7. Review fieldset: each `<div data-review-image="front">` contains
   `<img class="review__thumb" th:src="${photoUrls['FRONT']}" alt="Photo du recto de votre carte">`
   (`review__thumb review__thumb--portrait` for the selfie). The `[data-review-missing]` paragraphs and the
   `[data-incomplete]` alert are dropped: the controller never shows the review with a photo missing. The
   "Modifier" buttons become links: `<a class="link-button review__edit" data-edit="recto" th:href="@{/kyc(etape='recto',retour='revue')}">`.
   The submit is `<button class="btn btn--primary" type="submit" data-submit>Envoyer mon dossier</button>`. Show
   `error` as an `alert alert--error` above the hint (`th:if="${error}"`).
8. Confirmation section: `th:if="${view == 'confirmation'}"`, no `hidden`. The button becomes
   `<a class="btn btn--primary" data-show-status th:href="@{/kyc}">Voir le statut de mon dossier</a>`.
9. Status block: `<div th:if="${view == 'status'}">`. Each panel gets `th:if="${status == 'attente'}"` etc., and
   no `hidden`. Verified: `href="index.html"` becomes `th:href="@{/}"`. Rejected: replace the `PROTOTYPE` comment
   and hard-coded reason with `<p th:text="|Motif : ${rejectionReason}|">`, using the prototype's
   `&nbsp;` before the colon (`'Motif : '`). The restart button becomes
   `<form method="post" th:action="@{/kyc/recommencer}"><button class="btn btn--primary" type="submit">Soumettre de nouveaux documents</button></form>`.

Remove every `data-view` attribute: the server now renders only the active view.

- [ ] **Step 4: Port the CSS**

The served `partner-central.css` is the prototype's minus the KYC-only rules (`.upload__preview--portrait`,
`.selfie__video`, `.selfie__guide`, `.review__thumb--portrait`, `.review--media`). Checked on 2026-10-11:
`diff src/main/html/assets/css/partner-central.css src/main/resources/public/assets/css/partner-central.css`
shows only deletions on the served side. Re-run that diff. If it still only shows `<` lines, copy:

```bash
cd modules/ndopify-partner-central
diff src/main/html/assets/css/partner-central.css src/main/resources/public/assets/css/partner-central.css | grep '^>' || \
  cp src/main/html/assets/css/partner-central.css src/main/resources/public/assets/css/partner-central.css
```

If there are `>` lines, the served file has its own changes: port the five KYC blocks by hand instead.

- [ ] **Step 5: Delete the old KYC templates**

```bash
git rm modules/ndopify-partner-central/src/main/resources/templates/kyc/index.html \
       modules/ndopify-partner-central/src/main/resources/templates/kyc/revue.html \
       modules/ndopify-partner-central/src/main/resources/templates/kyc/confirmation.html \
       modules/ndopify-partner-central/src/main/resources/templates/kyc/verification.html
```

(Only these. `src/main/html/kyc/` and `src/main/html/kyc.html` are the prototype and stay.)

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='KycTemplateTest,KycControllerTest'`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add -A modules/ndopify-partner-central/src/main/resources modules/ndopify-partner-central/src/test
git commit -m "feat(npc): kyc.html template, replacing the old KYC mockup templates"
```

---

### Task 13: `kyc.js` progressive enhancement and cleaning up `partner-central.js`

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/partner-central.js`

**Interfaces:**
- Consumes: the DOM hooks from Task 12.
- Produces: no server contract. Every form still posts the field `photo` to the same action.

There's no JS unit-test setup in this repo. This task is verified in a real browser (Step 4).

- [ ] **Step 1: Write `kyc.js`**

```js
/*
 * kyc.html enhancements. Every step works without this file: the form posts its photo to /kyc/{step}.
 * - photos are checked (type, 5 MB) and shrunk in the browser before they're posted, so a 12 MP phone photo
 *   doesn't hit the upload limit, and the preview shows the chosen photo at once;
 * - the selfie step uses the front camera in the page (getUserMedia) when it can, under an oval guide; otherwise
 *   the <input type="file" capture="user"> that the page already shows is kept.
 * Ported from the prototype (src/main/html/assets/js/partner-central.js: kycUpload, kycCamera).
 */
(function () {
    "use strict";

    var ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp"];
    var MAX_FILE_BYTES = 5 * 1024 * 1024;
    // Long enough for the ID card text to stay readable by the verification.
    var MAX_SIDE = 2000;
    var SELFIE_MAX_HEIGHT = 1000;

    function $(selector, root) {
        return (root || document).querySelector(selector);
    }

    function setError(step, message) {
        var input = $(".upload__input", step);
        var error = $(".field__error", step);
        if (!error) return;
        $("[data-error-text]", error).textContent = message;
        error.hidden = !message;
        if (message) input.setAttribute("aria-invalid", "true");
        else input.removeAttribute("aria-invalid");
    }

    function showPreview(step, blob) {
        var preview = $("[data-preview]", step);
        var img = $("img", preview);
        if (!img) {
            img = document.createElement("img");
            img.className = "upload__image";
            img.alt = preview.getAttribute("data-alt");
            preview.insertBefore(img, preview.firstChild);
        }
        img.hidden = false;
        img.src = URL.createObjectURL(blob);
        $("[data-placeholder]", step).hidden = true;
        var label = $("[data-upload-label]", step);
        if (label) label.textContent = "Changer la photo";
        var status = $("[data-upload-status]", step);
        if (status) status.textContent = preview.getAttribute("data-alt").replace("Aperçu", "Photo") + " ajoutée.";
    }

    // Puts a photo in the step's file input, so the normal form post sends it. False when the browser can't.
    function useFile(step, file) {
        try {
            var transfer = new DataTransfer();
            transfer.items.add(file);
            $(".upload__input", step).files = transfer.files;
            return true;
        } catch (e) {
            return false;
        }
    }

    function shrink(file, callback) {
        var url = URL.createObjectURL(file);
        var img = new Image();
        img.onload = function () {
            URL.revokeObjectURL(url);
            var scale = Math.min(1, MAX_SIDE / Math.max(img.width, img.height));
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(img.width * scale);
            canvas.height = Math.round(img.height * scale);
            canvas.getContext("2d").drawImage(img, 0, 0, canvas.width, canvas.height);
            canvas.toBlob(function (blob) {
                callback(blob ? new File([blob], "photo.jpg", { type: "image/jpeg" }) : null);
            }, "image/jpeg", 0.85);
        };
        img.onerror = function () {
            URL.revokeObjectURL(url);
            callback(null);
        };
        img.src = url;
    }

    function initUpload(step) {
        var input = $(".upload__input", step);
        if (!input) return;
        input.addEventListener("change", function () {
            var file = input.files && input.files[0];
            if (!file) return;
            if (ACCEPTED_TYPES.indexOf(file.type) === -1) {
                setError(step, "Format non accepté. Utilisez une photo JPG, PNG ou WEBP.");
                input.value = "";
                return;
            }
            setError(step, "");
            shrink(file, function (small) {
                // Can't shrink (or can't swap the file): post the original and let the server check it.
                var chosen = small && useFile(step, small) ? small : file;
                if (chosen.size > MAX_FILE_BYTES) {
                    var size = (chosen.size / (1024 * 1024)).toFixed(1).replace(".", ",");
                    setError(step, "Cette photo pèse " + size + " Mo. La taille maximale est de 5 Mo.");
                    input.value = "";
                    return;
                }
                showPreview(step, chosen);
            });
        });
    }

    function cameraProblem(error) {
        var name = error && error.name;
        if (name === "NotAllowedError" || name === "SecurityError") {
            return "Vous n'avez pas autorisé l'accès à la caméra. Autorisez-le dans les réglages de votre " +
                "navigateur, ou prenez votre selfie avec l'appareil photo de votre téléphone.";
        }
        if (name === "NotFoundError" || name === "OverconstrainedError") {
            return "Aucune caméra n'a été trouvée. Prenez votre selfie avec l'appareil photo de votre téléphone.";
        }
        if (name === "NotReadableError") {
            return "La caméra est utilisée par une autre application. Fermez-la et réessayez, ou prenez votre " +
                "selfie avec l'appareil photo de votre téléphone.";
        }
        return "La caméra n'a pas pu démarrer. Prenez votre selfie avec l'appareil photo de votre téléphone.";
    }

    function initCamera(step) {
        var video = $("[data-camera-video]", step);
        if (!video) return;
        var guide = $("[data-camera-guide]", step);
        var start = $("[data-camera-start]", step);
        var shoot = $("[data-camera-shoot]", step);
        var fallback = $("[data-camera-fallback]", step);
        var problem = $("[data-camera-problem]", step);
        var preview = $("[data-preview]", step);
        var stream = null;

        var supported = window.isSecureContext && navigator.mediaDevices && navigator.mediaDevices.getUserMedia &&
            typeof DataTransfer === "function";
        if (!supported) return; // keep the file input the page already shows

        // The in-page camera replaces the file input; the input stays in the form to carry the photo.
        fallback.hidden = true;
        start.hidden = false;
        if ($("img", preview)) start.textContent = "Reprendre la photo";

        function setLive(on) {
            var photo = $("img", preview);
            video.hidden = !on;
            guide.hidden = !on;
            shoot.hidden = !on;
            start.hidden = on;
            if (photo) photo.hidden = on;
            $("[data-placeholder]", step).hidden = on || !!photo;
        }

        function stop() {
            if (stream) stream.getTracks().forEach(function (track) { track.stop(); });
            stream = null;
            video.srcObject = null;
            setLive(false);
        }

        function useFallback(message) {
            stop();
            start.hidden = true;
            fallback.hidden = false;
            $("[data-camera-problem-text]", problem).textContent = message;
            problem.hidden = false;
        }

        start.addEventListener("click", function () {
            problem.hidden = true;
            start.disabled = true;
            navigator.mediaDevices
                .getUserMedia({ video: { facingMode: "user", width: { ideal: 1280 }, height: { ideal: 960 } }, audio: false })
                .then(function (opened) {
                    start.disabled = false;
                    stream = opened;
                    video.srcObject = opened;
                    setLive(true);
                    video.play().catch(function () { /* autoplay attribute covers it */ });
                    shoot.focus();
                })
                .catch(function (error) {
                    start.disabled = false;
                    useFallback(cameraProblem(error));
                });
        });

        shoot.addEventListener("click", function () {
            var vw = video.videoWidth;
            var vh = video.videoHeight;
            if (!vw || !vh) return;
            // Crop the centre of the frame to the 3:4 portrait of the preview; drawn as the camera sees it (not mirrored).
            var sw = Math.min(vw, vh * 3 / 4);
            var sh = sw * 4 / 3;
            var scale = Math.min(1, SELFIE_MAX_HEIGHT / sh);
            var canvas = document.createElement("canvas");
            canvas.width = Math.round(sw * scale);
            canvas.height = Math.round(sh * scale);
            canvas.getContext("2d").drawImage(video, (vw - sw) / 2, (vh - sh) / 2, sw, sh, 0, 0, canvas.width, canvas.height);
            stop();
            canvas.toBlob(function (blob) {
                var file = blob && new File([blob], "selfie.jpg", { type: "image/jpeg" });
                if (!file || !useFile(step, file)) {
                    useFallback("La photo n'a pas pu être prise. Prenez votre selfie avec l'appareil photo de votre téléphone.");
                    return;
                }
                showPreview(step, file);
                start.textContent = "Reprendre la photo";
                $("[data-next]", step).focus();
            }, "image/jpeg", 0.85);
        });

        // Never leave the camera on when the agent leaves the page.
        window.addEventListener("pagehide", stop);
    }

    function initSubmit(form) {
        var submit = $("[data-submit]", form);
        if (!submit) return;
        form.addEventListener("submit", function () {
            // One submission: a second click would hit an already-submitted case.
            submit.disabled = true;
            submit.textContent = "Envoi en cours…";
            var sending = $("[data-sending]", form);
            if (sending) sending.textContent = "Envoi de votre dossier en cours.";
        });
    }

    document.addEventListener("DOMContentLoaded", function () {
        if (document.body.getAttribute("data-page") !== "kyc") return;
        var form = $("[data-kyc-form]");
        if (!form) return;
        var step = $("[data-step]", form);
        initUpload(step);
        initCamera(step);
        initSubmit(form);
    });
})();
```

- [ ] **Step 2: Clean up `partner-central.js` (the served one, not the prototype)**

In `src/main/resources/public/assets/js/partner-central.js`:
1. Delete `initKycStatus`, `initKycDocuments`, `initKycReview` and their `"kyc-status"`, `"kyc-documents"`,
   `"kyc-review"` entries in `pages`. Then delete any helper that only they used: check each of `focusStep`,
   `setProgress`, `toThumbnail`, `placeImage`, `announce`, `setFieldError`, `MAX_FILE_BYTES`, `ACCEPTED_TYPES`,
   and the `KEYS.front/back/...` entries with `grep -n '<name>' partner-central.js`, and delete only those with
   no caller left.
2. In `initUserMenu`, keep the server-rendered account details: only fall back to the sessionStorage email when
   the page rendered none:

```js
        var emailEl = $("[data-user-email]");
        // Pages that know the agent (kyc.html) render the email and initials; the others still use the login's copy.
        if (!emailEl || !emailEl.textContent.trim()) {
            var email = store.get(KEYS.email);
            $("[data-user-initials]").textContent = initialsFromEmail(email);
            if (emailEl) {
                if (email) emailEl.textContent = email;
                else emailEl.hidden = true;
            }
        }
```

(This replaces the existing `var email = ...` through the end of the `if (emailEl) {...}` block.)

- [ ] **Step 3: Build**

Run: `cd modules/ndopify-partner-central && mvn antrun:run@ktlint-format && mvn verify`
Expected: `BUILD SUCCESS`, with the JaCoCo gates met.

- [ ] **Step 4: Check it in the browser**

Start MySQL, Mailpit, `ndopify-server` (`mvn spring-boot:run`, port 8080) and partner-central
(`mvn spring-boot:run`, port 8081). Sign up through `/rejoindre`, then sign in at `http://localhost:8081/login`
with the e-mailed code. Then check:

1. Login lands on `/kyc` at "Étape 1 sur 4". The header shows your initials and e-mail.
2. Pick a 6–8 MB phone JPEG for the recto. The preview appears at once, and "Suivant" moves on to the verso
   (the photo was shrunk below 5 MB).
3. With JS disabled, the same file shows "Cette photo pèse … Mo…", and a 12 MB file shows "Cette photo dépasse
   la taille maximale de 5 Mo.". Neither shows an error page.
4. Selfie on `localhost` (a secure context): "Activer la caméra" opens the camera with the oval guide, and
   "Prendre la photo" fills the preview. If you deny the permission, the file input appears with the message.
5. On the review, the three thumbnails load. "Modifier" on the verso, a new photo, then "Revenir à la revue"
   shows the new verso.
6. "Envoyer mon dossier" shows the confirmation. "Voir le statut" shows *en attente*. Going back to the review
   URL shows the status.
7. Run `curl -X POST localhost:8080/v1/kyc/cases/<id>/verify -H 'X-Tenant-ID: 1'` (or wait for `KycJobs`).
   *vérifié* or *rejeté* shows with its reason. On *rejeté*, "Soumettre de nouveaux documents" restarts at
   "Étape 1 sur 4" with no photos.

Report anything that doesn't behave as described, and don't call the task done until every item works.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js
git commit -m "feat(npc): kyc.js shrinks photos and drives the selfie camera"
```

---

### Task 14: Docs and the ndopify-dto release

**Files:**
- Modify: `CLAUDE.md`
- Modify: `pom.xml` (root), property `ndopify-dto.version`

- [ ] **Step 1: Update CLAUDE.md**

Make targeted edits:
- `### ndopify-partner-central`: replace "The KYC templates (`templates/kyc/`) exist but no controller serves
  them yet (login redirects to `/kyc`)." with a paragraph describing `KycController` (`ui/kyc/`): one page
  `kyc.html`, steps `recto` → `verso` → `selfie` → `revue`, each photo uploaded to the agent's PENDING case
  (no photos in the session), the party found by looking up the signed-in user (`CurrentParty` → `GET /v1/users/{id}`)
  and its newest case, status view once submitted, `POST /kyc/recommencer` opens a new case after a rejection,
  users with no party are sent to `/logout`, and `kyc.js` only enhances (shrinks photos, in-page selfie camera).
- **party** domain: replace "`SUBMITTED` (requires FRONT + BACK; images frozen)" with "`SUBMITTED` (requires
  FRONT, plus BACK for two-sided documents: `NATIONAL_ID`, `DRIVER_LICENSE`, `RESIDENT_CARD`; SELFIE optional
  server-side, Partner Central requires it; images frozen)". Mention that `GET /v1/kyc/cases` returns the newest case first.
- **security** domain: add `GET /v1/users/{id}` (`UserEndpoints`): the user with its party embedded. It returns
  404 `USER_NOT_FOUND` for a user of another home tenant, which `UserService.findById` checks with
  `UserRepository.isVisible` before loading, because the eager `party` is `@TenantId`-filtered. Like the rest
  of the API, it isn't authorized yet (`permitAll`).

- [ ] **Step 2: Release ndopify-dto 0.0.25. This is a human step: ask before doing it.**

Releasing publishes an artifact. Ask the user to run their usual maven-release flow for `ndopify-dto` (the one
behind commits like "[maven-release-plugin] prepare release ndopify-dto-0.0.24"). Once it's released, set the
root `pom.xml` back to a released version:

```xml
        <ndopify-dto.version>0.0.25</ndopify-dto.version>
```

- [ ] **Step 3: Full build**

Run: `mvn install -Dheadless=true` (repo root)
Expected: `BUILD SUCCESS` for all modules.

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md pom.xml
git commit -m "docs: partner-central KYC page; use ndopify-dto 0.0.25"
```
