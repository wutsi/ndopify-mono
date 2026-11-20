# Direct KYC Photo Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The browser uploads a KYC photo (ID front, ID back, selfie) straight to S3 via presign → PUT → attach, keeping the multipart upload as the no-JS / local / failure fallback. The KYC image writes also start checking that the case belongs to the caller's party.

**Architecture:** `ndopify-server`'s party domain adds `POST /v1/kyc/cases/{id}/images/presign` and `/attach`, which run the case's own checks and then use #127's `platform/storage/upload/DirectUploadService`; attach replaces the case's one `KycImage` for the (case, identification type, image type) key and deletes the previous object. Partner Central proxies the two calls and `kyc.js` tries them on *Suivant*, falling back to the existing multipart form post on any failure.

**Tech Stack:** Kotlin, Spring Boot, AWS SDK v1 (`aws-java-sdk-s3`), Apache Tika, Thymeleaf, vanilla JS.

**Spec:** GitHub issue #205 (`gh issue view 205`) — the story holds the confirmed design; there is no separate spec file. #127 (merged) holds the platform code this reuses.

## Global Constraints

- Code, comments, logs and docs in English; UI text in French.
- `kotlin.test` assertions; `mockito-kotlin` (`com.nhaarman.mockitokotlin2`) mocks.
- ktlint runs on `validate`; `mvn verify` (tests + JaCoCo gate) must be green in every touched module. `ndopify-server` needs local MySQL.
- Conventional commits. Each commit touches one library module or none; this story touches no library, so no `ndopify-dto` / `ndopify-web-core` release and no root `pom.xml` `-SNAPSHOT` edit.
- Supported types: `image/jpeg`, `image/png`, `image/webp`. Max size: 5 MB (`5 * 1024 * 1024`). URL TTL: `ndopify.storage.upload-url-ttl-seconds` (600).
- KYC paths: direct upload `kyc/{caseId}/images/{uuid}.{ext}`; multipart `kyc/{caseId}/images/{imageId}.{ext}`. Both stay in the same folder and are read through presigned GETs only — never `publicUrl`.
- KYC image write checks, in order: 404 `KYC_CASE_NOT_FOUND` (including another tenant) → 403 `HTTP_ACCESS_DENIED` (another party) → 400 `KYC_CASE_NOT_PENDING` → the upload's 400s.
- The multipart endpoints stay (`POST /v1/kyc/cases/{id}/images/upload`, `POST /kyc/{step}`); only the added party check changes their behaviour.
- No visual change and no `src/main/html/` change: this is not a UI story, so there is no prototype step.

## Review Focus

- Another party's case: 403 `HTTP_ACCESS_DENIED`, nothing stored or changed (another tenant: 404) — Task 1 (upload) and Task 2 (presign/attach) tests.
- Case not `PENDING_VERIFICATION`: 400 `KYC_CASE_NOT_PENDING`, no storage call — Task 2 tests.
- Replace: the new row is saved before the old object is deleted; deleting the old object uses its own `storageType`; a failed delete is logged and the attach still succeeds — Task 2 tests.
- Same path attached twice: one row, no second `verify` or delete — Task 2 idempotency test.
- Path of another case or a traversal: 400 `HTTP_INVALID_PARAMETER`, nothing recorded — Task 2 test (the platform's traversal cases are already `DirectUploadServiceTest`'s).
- Local storage, a failed PUT/attach or no JavaScript: the multipart form post still uploads and the wizard advances — Task 4 proxy tests plus the manual check in Task 6.
- A step ahead of the first incomplete one: the JSON proxies answer 409, like the form post's redirect — Task 4 test.

---

### Task 1: Close the KYC image writes to the case's own party

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/UploadKycImageEndpointTest.kt`
- Test fixture: `modules/ndopify-server/src/test/resources/db/test/party/UploadKycImageEndpoint.sql`
- Modify: `modules/ndopify-server/CLAUDE.md` (party KYC paragraph)

**Interfaces:**
- Consumes: `CallerService.getPrincipal()` and `CallerService.findPartyIdOrNull(principal)` (existing).
- Produces: `KycService`'s constructor gains `private val callerService: CallerService`; `upload(id, identificationType, imageType, file)` keeps its signature and now throws 403 `HTTP_ACCESS_DENIED` for another party's case. Task 2 reuses the private `ensureOwnCase(case)`.

- [ ] **Step 1: Add the caller's membership and a second party's case to the fixture**

In `UploadKycImageEndpoint.sql`, add party `200` to the `T_PARTY` insert, then:

```sql
-- The caller is user 11, member of party 100; party 200 is another party of the same tenant.
INSERT INTO T_USER(id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-06-10 00:00:00');

INSERT INTO T_MEMBERSHIP(id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false);
```

Add `('kyc-case-200', 1, 200, NULL, 'PENDING_VERIFICATION', NULL, '2024-06-10 00:00:00', '2024-06-10 00:00:00')` to the `T_KYC_CASE` insert.

- [ ] **Step 2: Write the failing access test**

In `UploadKycImageEndpointTest`:

```kotlin
@Test
fun `upload - another party's case`() {
    val response = rest.postForEntity(
        url("kyc-case-200", KycImageType.FRONT),
        request("front.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
        ErrorResponse::class.java,
    )

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    verify(storage, never()).store(any(), any(), any())
    assertEquals(1, imageDao.findAll().count()) // only the fixture's BACK image
}
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd modules/ndopify-server
mvn test -Dtest=UploadKycImageEndpointTest
```

Expected: FAIL — another party's case is accepted (200), not 403.

- [ ] **Step 4: Add the check to `KycService`**

Inject `CallerService` in the constructor and add:

```kotlin
/** The case's own party only: no admin bypass, a KYC document is personal. */
private fun ensureOwnCase(case: KycCaseEntity) {
    val partyId = callerService.findPartyIdOrNull(callerService.getPrincipal())
    if (partyId == null || partyId != case.party.id) {
        throw ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
    }
}
```

In `upload`, move the `ensureImage(file)` call after the case lookup and add the checks in this order:

```kotlin
val case = findById(id)
ensureOwnCase(case)
ensureStatusIsPending(case)
ensureImage(file)
```

Update `upload`'s KDoc to state the order: 404, 403 but for the case's own party, 400 `KYC_CASE_NOT_PENDING`, then 400 `KYC_IMAGE_INVALID_MIME_TYPE`.

- [ ] **Step 5: Run the whole upload test class**

```bash
mvn test -Dtest=UploadKycImageEndpointTest
```

Expected: PASS (the existing tests now run with the user 11 → party 100 membership).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/UploadKycImageEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/party/UploadKycImageEndpoint.sql \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(party): only the KYC case's own party uploads its images" -m "Refs #205"
```

---

### Task 2: Presign and attach KYC photos on ndopify-server

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/KycService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/KycEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/PresignKycImageEndpointTest.kt` (new)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/AttachKycImageEndpointTest.kt` (new)
- Modify: `modules/ndopify-server/CLAUDE.md` (party KYC paragraph)

**Interfaces:**
- Consumes: `DirectUploadService.presign(prefix: String, contentType: String?): PresignUploadResponse` and `verify(path: String, prefix: String): String` (from #127); `KycImageRepository.findByKycCaseAndIdentificationTypeAndImageType`; `KycService.ensureOwnCase` from Task 1.
- Produces:
  - `KycService.prefix(caseId: String): String` — `"kyc/$caseId/images"`, shared with the multipart upload.
  - `KycService.presign(id: String, identificationType: IdentificationType, imageType: KycImageType, contentType: String?): PresignUploadResponse`
  - `KycService.attach(id: String, identificationType: IdentificationType, imageType: KycImageType, path: String): KycImageEntity`
  - `POST /v1/kyc/cases/{id}/images/presign?identificationType=&imageType=` (`PresignUploadRequest`) → `PresignUploadResponse`
  - `POST /v1/kyc/cases/{id}/images/attach?identificationType=&imageType=` (`AttachUploadRequest`) → 200, no body

- [ ] **Step 1: Write the failing presign endpoint test**

`PresignKycImageEndpointTest` follows `UploadKycImageEndpointTest`'s setup (`@Sql` on `clean.sql` + `UploadKycImageEndpoint.sql`, `AuthorizationAwareEndpointIntegrationTest`, `@MockitoBean storageProvider`, `storage = mock`, `doReturn(storage).whenever(storageProvider).get()`):

```kotlin
private fun presign(caseId: String, contentType: String) =
    rest.postForEntity(
        "/v1/kyc/cases/$caseId/images/presign?identificationType=NATIONAL_ID&imageType=FRONT",
        PresignUploadRequest(contentType),
        PresignUploadResponse::class.java,
    )

@Test
fun presign() {
    doReturn(URL("https://s3.example.com/put")).whenever(storage).presignedUploadUrl(any(), any(), any())

    val response = presign("kyc-case-100", "image/jpeg")

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("https://s3.example.com/put", response.body?.url)
    assertTrue(
        response.body!!.path!!.matches(Regex("kyc/kyc-case-100/images/[0-9a-f-]{36}\\.jpg")),
        response.body?.path,
    )
}

@Test
fun `presign - local`() {
    doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())

    val response = presign("kyc-case-100", "image/png")

    assertEquals(HttpStatus.OK, response.statusCode)
    assertNull(response.body?.url)
    assertNull(response.body?.path)
}

@Test
fun `presign - another party`() {
    val response = rest.postForEntity(
        "/v1/kyc/cases/kyc-case-200/images/presign?identificationType=NATIONAL_ID&imageType=FRONT",
        PresignUploadRequest("image/jpeg"),
        ErrorResponse::class.java,
    )

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    verify(storage, never()).presignedUploadUrl(any(), any(), any())
}

@Test
fun `presign - case not pending`() {
    val response = rest.postForEntity(
        "/v1/kyc/cases/case-submitted/images/presign?identificationType=NATIONAL_ID&imageType=FRONT",
        PresignUploadRequest("image/jpeg"),
        ErrorResponse::class.java,
    )

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals(ErrorCode.KYC_CASE_NOT_PENDING, response.body?.error?.code)
    verify(storage, never()).presignedUploadUrl(any(), any(), any())
}
```

Add the same error cases as the upload endpoint: not an image (400 `HTTP_INVALID_PARAMETER`), unknown case (404), another tenant (404), anonymous (401).

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-server
mvn test -Dtest=PresignKycImageEndpointTest
```

Expected: FAIL — 404 on the new route.

- [ ] **Step 3: Implement `prefix` and `presign`**

In `KycService`'s companion object:

```kotlin
/** The folder of a case's photos: the multipart upload and the direct upload share it. */
fun prefix(caseId: String) = "kyc/$caseId/images"
```

Change `upload`'s path to `"${prefix(case.id)}/${image.id}.$extension"` (same string). Add:

```kotlin
/**
 * A short-lived URL to upload a KYC photo straight to the storage, for the case's own party. 404
 * KYC_CASE_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for the case's party, 400 KYC_CASE_NOT_PENDING, then 400
 * HTTP_INVALID_PARAMETER (by [DirectUploadService.presign]) for a type that isn't JPEG, PNG or WEBP. No URL when the
 * storage can't presign (local): the caller falls back to [upload].
 */
fun presign(
    id: String,
    identificationType: IdentificationType,
    imageType: KycImageType,
    contentType: String?,
): PresignUploadResponse {
    val case = findById(id)
    ensureOwnCase(case)
    ensureStatusIsPending(case)
    return directUpload.presign(prefix(case.id), contentType)
}
```

Inject `private val directUpload: DirectUploadService`.

- [ ] **Step 4: Add the presign endpoint**

In `KycEndpoints`:

```kotlin
/** A short-lived URL to upload a KYC photo straight to the storage; no url with local storage. */
@PostMapping("/{id}/images/presign")
fun presign(
    @PathVariable id: String,
    @RequestParam identificationType: IdentificationType,
    @RequestParam imageType: KycImageType,
    @RequestBody request: PresignUploadRequest,
): PresignUploadResponse =
    service.presign(id, identificationType, imageType, request.contentType)
```

- [ ] **Step 5: Run the presign test to verify it passes**

```bash
mvn test -Dtest=PresignKycImageEndpointTest
```

Expected: PASS.

- [ ] **Step 6: Write the failing attach endpoint test**

`AttachKycImageEndpointTest` reuses the same setup and SQL. Add `doReturn(storage).whenever(storageProvider).get(StorageType.LOCAL)` and `doReturn(StorageType.LOCAL).whenever(storage).type()` to the setup, since the fixture's image is `LOCAL`.

```kotlin
private val path = "kyc/kyc-case-100/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

private fun attach(caseId: String, imageType: KycImageType, path: String) =
    rest.postForEntity(
        "/v1/kyc/cases/$caseId/images/attach?identificationType=NATIONAL_ID&imageType=$imageType",
        AttachUploadRequest(path),
        Void::class.java,
    )

@Test
fun `attach - new image`() {
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())

    val response = attach("kyc-case-100", KycImageType.FRONT, path)

    assertEquals(HttpStatus.OK, response.statusCode)
    val case = caseDao.findById("kyc-case-100").orElseThrow()
    val image = imageDao.findByKycCaseAndIdentificationTypeAndImageType(case, IdentificationType.NATIONAL_ID, KycImageType.FRONT)!!
    assertEquals(path, image.path)
    assertEquals("image/png", image.mimeType)
    assertEquals(StorageType.LOCAL, image.storageType)
    assertEquals(1L, image.tenantId)
    verify(storage, never()).publicUrl(any())
}

@Test
fun `attach - replaces the old image and deletes its object`() {
    val old = "kyc/kyc-case-100/images/kyc-image-100-back.png"
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())

    val response = attach("kyc-case-100", KycImageType.BACK, path)

    assertEquals(HttpStatus.OK, response.statusCode)
    val image = imageDao.findById("kyc-image-100-back").orElseThrow()
    assertEquals(path, image.path)
    assertEquals("image/png", image.mimeType)
    assertTrue(image.modifiedAt.after(image.createdAt))
    assertEquals(1, imageDao.findAll().filter { it.imageType == KycImageType.BACK }.size)
    verify(storage).delete(old)
}

@Test
fun `attach - idempotent`() {
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())

    attach("kyc-case-100", KycImageType.FRONT, path)
    attach("kyc-case-100", KycImageType.FRONT, path)

    assertEquals(1, imageDao.findAll().filter { it.imageType == KycImageType.FRONT }.size)
    verify(storage, times(1)).metadata(path)
}

@Test
fun `attach - another case's path`() {
    val other = "kyc/kyc-case-200/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"

    val response = rest.postForEntity(
        "/v1/kyc/cases/kyc-case-100/images/attach?identificationType=NATIONAL_ID&imageType=FRONT",
        AttachUploadRequest(other),
        ErrorResponse::class.java,
    )

    assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    assertEquals(ErrorCode.HTTP_INVALID_PARAMETER, response.body?.error?.code)
    verify(storage, never()).metadata(other)
    assertEquals(0, imageDao.findAll().filter { it.imageType == KycImageType.FRONT }.size)
}

@Test
fun `attach - failed delete still attaches`() {
    val old = "kyc/kyc-case-100/images/kyc-image-100-back.png"
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())
    doThrow(IllegalStateException("storage down")).whenever(storage).delete(old)

    assertEquals(HttpStatus.OK, attach("kyc-case-100", KycImageType.BACK, path).statusCode)
    assertEquals(path, imageDao.findById("kyc-image-100-back").orElseThrow().path)
}
```

Add the error cases: another case's path (400 `HTTP_INVALID_PARAMETER`, no row, `metadata` never), missing object (400), not an image (400, `verify(storage).delete(path)`), too large (metadata length `5 * 1024 * 1024 + 1` → 400, delete), another party (403), not pending (400), unknown case (404), another tenant (404), anonymous (401).

- [ ] **Step 7: Run it to verify it fails**

```bash
mvn test -Dtest=AttachKycImageEndpointTest
```

Expected: FAIL — 404 on the new route.

- [ ] **Step 8: Implement `attach`**

```kotlin
/**
 * Attaches a KYC photo uploaded straight to the storage, replacing the case's image for that (identification type,
 * image type) — one image per step. 404 KYC_CASE_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for the case's party, 400
 * KYC_CASE_NOT_PENDING, then [DirectUploadService.verify]'s 400s. Idempotent on [path]: an image already recorded for
 * it is returned. The replaced object is deleted after the new row is saved; a failed deletion is logged, not fatal.
 */
fun attach(
    id: String,
    identificationType: IdentificationType,
    imageType: KycImageType,
    path: String,
): KycImageEntity {
    val case = findById(id)
    ensureOwnCase(case)
    ensureStatusIsPending(case)

    val existing = kycImageDao.findByKycCaseAndIdentificationTypeAndImageType(case, identificationType, imageType)
    if (existing?.path == path) {
        return existing
    }

    // Storage I/O outside the transaction, like upload()
    val mimeType = directUpload.verify(path, prefix(case.id))
    val storage = storageProvider.get()
    val now = Date(clock.millis())
    val saved = tx.execute {
        kycImageDao.save(
            (existing ?: KycImageEntity(
                kycCase = case,
                identificationType = identificationType,
                imageType = imageType,
                createdAt = now,
                modifiedAt = now,
            )).copy(
                path = path,
                storageType = storage.type(),
                mimeType = mimeType,
                modifiedAt = now,
            )
        )
    }

    val old = existing?.path
    if (existing != null && old != null && old != path) {
        try {
            storageProvider.get(existing.storageType).delete(old)
        } catch (ex: Exception) {
            LOGGER.warn(">>> KYC#${case.id} - Could not delete the replaced image $old", ex)
        }
    }
    return saved
}
```

- [ ] **Step 9: Add the attach endpoint**

```kotlin
/** Attaches a KYC photo uploaded straight to the storage, replacing the case's image for that type. */
@PostMapping("/{id}/images/attach")
fun attach(
    @PathVariable id: String,
    @RequestParam identificationType: IdentificationType,
    @RequestParam imageType: KycImageType,
    @RequestBody request: AttachUploadRequest,
) {
    service.attach(id, identificationType, imageType, request.path)
}
```

Import `AttachUploadRequest`, `PresignUploadRequest`, `PresignUploadResponse` in `KycEndpoints`.

- [ ] **Step 10: Run the attach tests and the whole party test suite**

```bash
mvn test -Dtest='PresignKycImageEndpointTest,AttachKycImageEndpointTest,UploadKycImageEndpointTest,GetKycImageUrlEndpointTest,SubmitKycCaseEndpointTest'
```

Expected: PASS.

- [ ] **Step 11: Update `modules/ndopify-server/CLAUDE.md`**

In the **party** domain's KYC lifecycle paragraph, after the multipart upload, add: the full access check, the direct upload (`presign` then `attach`), the replace semantics and the old object's deletion, the two path shapes, the S3-only capability with `{url: null}` on local, and that the multipart endpoints stay as the fallback. Mention that KYC files are read through presigned GETs only.

- [ ] **Step 12: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(party): presign and attach KYC photos uploaded directly to the storage" -m "Refs #205"
```

---

### Task 3: Partner Central client calls

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdKycClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdKycClientTest.kt`

**Interfaces:**
- Consumes: Task 2's endpoints; the `common.dto` `PresignUploadRequest` / `PresignUploadResponse` / `AttachUploadRequest`.
- Produces:
  - `NdKycClient.presign(caseId: String, identificationType: IdentificationType, imageType: KycImageType, contentType: String): PresignUploadResponse`
  - `NdKycClient.attach(caseId: String, identificationType: IdentificationType, imageType: KycImageType, path: String)` (Unit)

- [ ] **Step 1: Write the failing client tests**

```kotlin
@Test
fun presign() {
    server.expect(
        requestTo("http://localhost:8080/v1/kyc/cases/case-1/images/presign?identificationType=NATIONAL_ID&imageType=FRONT"),
    )
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"contentType":"image/jpeg"}"""))
        .andRespond(
            withSuccess(
                """{"url":"https://s3.example.com/put","path":"kyc/case-1/images/a.jpg"}""",
                MediaType.APPLICATION_JSON,
            ),
        )

    val response = client.presign("case-1", IdentificationType.NATIONAL_ID, KycImageType.FRONT, "image/jpeg")

    assertEquals("https://s3.example.com/put", response.url)
    assertEquals("kyc/case-1/images/a.jpg", response.path)
    server.verify()
}

@Test
fun attach() {
    server.expect(
        requestTo("http://localhost:8080/v1/kyc/cases/case-1/images/attach?identificationType=NATIONAL_ID&imageType=FRONT"),
    )
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"path":"kyc/case-1/images/a.jpg"}"""))
        .andRespond(withSuccess())

    client.attach("case-1", IdentificationType.NATIONAL_ID, KycImageType.FRONT, "kyc/case-1/images/a.jpg")
    server.verify()
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=NdKycClientTest
```

Expected: FAIL — unresolved `presign` / `attach`.

- [ ] **Step 3: Implement both methods**

```kotlin
/** A short-lived URL to upload a KYC photo straight to the storage: no url with local storage. */
fun presign(
    caseId: String,
    identificationType: IdentificationType,
    imageType: KycImageType,
    contentType: String,
): PresignUploadResponse =
    rest.post()
        .uri { uri ->
            uri.path("/v1/kyc/cases/{id}/images/presign")
                .queryParam("identificationType", identificationType)
                .queryParam("imageType", imageType)
                .build(caseId)
        }
        .body(PresignUploadRequest(contentType))
        .retrieve()
        .body<PresignUploadResponse>()
        ?: PresignUploadResponse()

/** Attaches a KYC photo that was uploaded straight to the storage, replacing that step's image. */
fun attach(
    caseId: String,
    identificationType: IdentificationType,
    imageType: KycImageType,
    path: String,
) {
    rest.post()
        .uri { uri ->
            uri.path("/v1/kyc/cases/{id}/images/attach")
                .queryParam("identificationType", identificationType)
                .queryParam("imageType", imageType)
                .build(caseId)
        }
        .body(AttachUploadRequest(path))
        .retrieve()
        .toBodilessEntity()
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=NdKycClientTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdKycClient.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdKycClientTest.kt
git commit -m "feat(npc): add the presign and attach KYC image calls" -m "Refs #205"
```

---

### Task 4: Partner Central proxy endpoints

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycControllerTest.kt`

**Interfaces:**
- Consumes: Task 3's `NdKycClient.presign` / `attach`; the existing `openCase(partyId)` and `KycRules`.
- Produces (the contract `direct-upload.js` expects from the page):
  - `POST /kyc/{step}/presign` — JSON `PresignUploadRequest` in, `PresignUploadResponse` out (200), 409 when there is no open case or the step is ahead, otherwise the upstream status.
  - `POST /kyc/{step}/attach` — JSON `AttachUploadRequest` in, 200 with no body, same 409s, otherwise the upstream status.

- [ ] **Step 1: Write the failing controller tests**

In `KycControllerTest` (the mocked `kycClient`, `currentParty`, `currentAgent` and `given(case)` are already there):

```kotlin
@Test
fun `presign - returns the upload url`() {
    given(case())
    doReturn(PresignUploadResponse("https://s3.example.com/put", "kyc/case-1/images/a.jpg"))
        .whenever(kycClient).presign("case-1", NdKycClient.IDENTIFICATION_TYPE, KycImageType.FRONT, "image/jpeg")

    val result = mvc.perform(
        post("/kyc/front/presign")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"contentType":"image/jpeg"}"""),
    ).andExpect(status().isOk).andReturn()

    assertTrue(result.response.contentAsString.contains("https://s3.example.com/put"))
}

@Test
fun `presign - step ahead of the wizard`() {
    given(case()) // no photo yet: only front is open

    mvc.perform(
        post("/kyc/back/presign")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"contentType":"image/jpeg"}"""),
    ).andExpect(status().isConflict)

    verify(kycClient, never()).presign(any(), any(), any(), any())
}

@Test
fun `presign - no open case`() {
    given(case(status = KycStatus.SUBMITTED))

    mvc.perform(
        post("/kyc/front/presign")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"contentType":"image/jpeg"}"""),
    ).andExpect(status().isConflict)
}

@Test
fun `attach - returns 200`() {
    given(case())
    doReturn(Unit).whenever(kycClient).attach("case-1", NdKycClient.IDENTIFICATION_TYPE, KycImageType.FRONT, "kyc/case-1/images/a.jpg")

    mvc.perform(
        post("/kyc/front/attach")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"path":"kyc/case-1/images/a.jpg"}"""),
    ).andExpect(status().isOk)
}
```

Add a `presign` / `attach` server-refusal test (mock `HttpClientErrorException` 400 → 400, following the existing `upload` error tests).

- [ ] **Step 2: Run them to verify they fail**

```bash
mvn test -Dtest=KycControllerTest
```

Expected: FAIL — 404/405 on the new routes.

- [ ] **Step 3: Implement the two proxies**

```kotlin
/** A short-lived URL to upload a photo straight to the storage, for `kyc.js`. */
@PostMapping("/{step:front|back|selfie}/presign", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun presign(
    @PathVariable step: String,
    @RequestBody request: PresignUploadRequest,
): ResponseEntity<PresignUploadResponse> {
    val party = currentParty.get() ?: throw SignInRequiredException()
    val case = openCase(party.id) ?: return ResponseEntity.status(HttpStatus.CONFLICT).build()
    if (!stepAllowed(step, case)) return ResponseEntity.status(HttpStatus.CONFLICT).build()

    val imageType = KycRules.PHOTOS.getValue(step)
    return try {
        ResponseEntity.ok(
            kycClient.presign(case.id, NdKycClient.IDENTIFICATION_TYPE, imageType, request.contentType.orEmpty()),
        )
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("Presign of KYC case ${case.id} refused: ${ex.statusCode}")
        ResponseEntity.status(ex.statusCode).build()
    } catch (ex: RestClientException) {
        LOGGER.error("Presign of KYC case ${case.id} failed", ex)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}

/** Attaches a photo uploaded straight to the storage, for `kyc.js`. */
@PostMapping("/{step:front|back|selfie}/attach", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun attach(
    @PathVariable step: String,
    @RequestBody request: AttachUploadRequest,
): ResponseEntity<Void> {
    val party = currentParty.get() ?: throw SignInRequiredException()
    val case = openCase(party.id) ?: return ResponseEntity.status(HttpStatus.CONFLICT).build()
    if (!stepAllowed(step, case)) return ResponseEntity.status(HttpStatus.CONFLICT).build()

    val imageType = KycRules.PHOTOS.getValue(step)
    return try {
        kycClient.attach(case.id, NdKycClient.IDENTIFICATION_TYPE, imageType, request.path)
        ResponseEntity.ok().build()
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("Attach of KYC case ${case.id} refused: ${ex.statusCode}")
        ResponseEntity.status(ex.statusCode).build()
    } catch (ex: RestClientException) {
        LOGGER.error("Attach of KYC case ${case.id} failed", ex)
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}

/** The wizard only shows a step once every photo before it is in the case. */
private fun stepAllowed(step: String, case: KycCase): Boolean {
    val photos = KycRules.photos(case)
    val firstIncomplete = KycRules.firstIncompleteStep(photos.keys)
    return KycRules.STEPS.indexOf(step) <= KycRules.STEPS.indexOf(firstIncomplete)
}
```

Import `AttachUploadRequest`, `PresignUploadRequest`, `PresignUploadResponse`, `MediaType`, `RequestBody`, `ResponseBody`, `ResponseEntity`, `HttpStatus`.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=KycControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycControllerTest.kt
git commit -m "feat(npc): proxy the direct KYC photo upload to the browser" -m "Refs #205"
```

---

### Task 5: `kyc.js` on *Suivant*, with the multipart fallback

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/kyc.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (KYC page paragraph and JS assets list)

**Interfaces:**
- Consumes: `window.NdDirectUpload.upload(action, file): Promise<boolean>` (from #127) and Task 4's routes.
- Produces: the browser tries the direct path on submit; no public JS API.

- [ ] **Step 1: Write the failing template test**

In `KycTemplateTest`:

```kotlin
@Test
fun `direct upload script`() {
    given(case())

    val scripts = render(get("/kyc")).select("script[src]").map { it.attr("src") }

    assertTrue(scripts.indexOf("/assets/js/direct-upload.js") >= 0)
    assertTrue(scripts.indexOf("/assets/js/direct-upload.js") < scripts.indexOf("/assets/js/kyc.js"))
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
mvn test -Dtest=KycTemplateTest
```

Expected: FAIL — the script tag is absent.

- [ ] **Step 3: Load the script before `kyc.js`**

In `templates/kyc.html`, between the `partner-central.js` and `kyc.js` tags:

```html
<script th:src="@{/assets/js/direct-upload.js}" defer></script>
```

- [ ] **Step 4: Rewrite `initSubmit` in `kyc.js`**

```js
function initSubmit(form) {
    var submit = $("[data-submit]", form);
    if (!submit) return;
    form.addEventListener("submit", function (event) {
        // Another handler already refused the post ("Suivant" without a photo): leave its button alone.
        if (event.defaultPrevented) return;

        var input = $(".upload__input", form);
        var file = input && input.files && input.files[0];
        var direct = file && window.NdDirectUpload;

        // One submission: a second click would hit an already-submitted case.
        submit.disabled = true;
        submit.textContent = "Envoi en cours…";
        var sending = $("[data-sending]", form);
        if (sending) sending.textContent = "Envoi de votre dossier en cours.";

        if (!direct) return;

        // The photo goes straight to the storage; the form then posts without it, so the server moves on as usual.
        event.preventDefault();
        var status = $("[data-upload-status]", form);
        if (status) status.textContent = "Envoi de la photo en cours…";
        window.NdDirectUpload.upload(form.getAttribute("action"), file)
            .then(function (uploaded) {
                if (uploaded) input.value = "";
                // Native submit: no listener runs again. With no file, the case already has the photo; on failure,
                // the file is posted to the BFF as before.
                form.submit();
            })
            .catch(function () {
                form.submit();
            });
    });
}
```

The `event.defaultPrevented` guard also fixes the existing case where `initNext` refuses a submit but `initSubmit` still disables the button.

- [ ] **Step 5: Run the template and controller tests**

```bash
mvn test -Dtest='KycTemplateTest,KycControllerTest'
```

Expected: PASS.

- [ ] **Step 6: Update `modules/ndopify-partner-central/CLAUDE.md`**

In the KYC page paragraph: `kyc.html` loads `direct-upload.js`; on *Suivant*, `kyc.js` uploads the shrunk photo straight to the storage through `POST /kyc/{step}/presign` → PUT → `POST /kyc/{step}/attach`, clears the input and submits the form (the server redirects as before); any failure posts the file to the form (the multipart fallback), and no JS / local storage keep today's flow. Add `direct-upload.js` to the JS assets list.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/kyc.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/kyc/KycTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): upload KYC photos straight to the storage when possible" -m "Refs #205"
```

---

### Task 6: Verify the whole story

- [ ] **Step 1: Run both modules' builds**

```bash
cd modules/ndopify-server && mvn verify
cd ../ndopify-partner-central && mvn verify
```

Expected: GREEN (ktlint + tests + JaCoCo gate). `ndopify-server` needs local MySQL.

- [ ] **Step 2: Check the diff**

```bash
cd /Users/herve/Projects/ndopify-mono
git status --short          # clean
git diff origin/main --stat # only the party/KYC and Partner Central KYC files + the two CLAUDE.md
git diff origin/main -- pom.xml modules/ndopify-dto modules/ndopify-web-core  # empty
```

- [ ] **Step 3: Manual browser check (optional, if S3 credentials are available)**

Run Partner Central against local ndopify-server (local storage): choose a photo, press *Suivant*; presign answers `{url: null}`, `direct-upload.js` returns false, the form posts the file and the wizard advances. Disable JavaScript: same multipart flow. With S3, the network tab shows presign → PUT to S3 → attach, and the form post carries no photo; replacing a step's photo leaves one image and deletes the old object.

---

## Out of scope (do not build)

- Party access checks on KYC `create`, `submit`, `get` and `search` (they stay tenant-scoped, as today).
- The profile photo and listing photos.
- S3 lifecycle rules, orphan cleanup or bucket-policy changes (ops follow-ups noted on the story).
- Any visual change, any `src/main/html/` change, any `ndopify-dto` / `ndopify-web-core` change.
- A generic upload endpoint or an upload entity/table.
