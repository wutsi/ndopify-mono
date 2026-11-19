# Direct Listing Photo Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The browser uploads a listing photo straight to S3; ndopify-server only issues a short-lived presigned PUT URL and attaches the photo afterwards, keeping the multipart endpoint as the no-JS / local / failure fallback. The presign / verify mechanics and the browser upload script are built once, at the platform level, so the KYC and profile photo stories reuse them.

**Architecture:** A platform helper on ndopify-server, `platform/storage/upload/DirectUploadService` (`presign(prefix, contentType)`, `verify(path, prefix)`), backed by new `StorageService` capabilities (presigned PUT, object metadata/prefix/delete), and generic DTOs in `common.dto`. The listing domain adds two thin endpoints (`POST /v1/listings/{id}/images/presign`, `POST /v1/listings/{id}/images/attach`) that run its own access and state checks, then call the helper. Partner Central proxies them; a shared `direct-upload.js` (presign → PUT → attach) is used by `listing.js`, which falls back to the existing form post on any failure.

**Tech Stack:** Kotlin, Spring Boot, AWS SDK v1 (`aws-java-sdk-s3` 1.12.797), Apache Tika 4.2.0, Thymeleaf, vanilla JS.

**Spec:** GitHub issue #127 (`gh issue view 127`) — the story holds the design; there is no separate spec file. Follow-ups that reuse this plan's platform code: the KYC and profile photo direct-upload stories (they depend on #127).

## Global Constraints

- Code, comments, logs and docs in English; UI text in French.
- `kotlin.test` assertions; `mockito-kotlin` (`com.nhaarman.mockitokotlin2`) mocks.
- ktlint runs on `validate`; `mvn verify` (tests + JaCoCo gate) must be green in every touched module.
- Conventional commits. A commit touching `modules/ndopify-dto/` touches nothing else; library commits are `feat(dto): …` with `Refs #127` in the body.
- The root `pom.xml` `<ndopify-dto.version>` must never be committed as `-SNAPSHOT`.
- Supported photo types: `image/jpeg`, `image/png`, `image/webp`. Max photo size: 5 MB (`5 * 1024 * 1024` bytes). Max photos per listing: 20 (UI-only, unchanged).
- The existing multipart endpoints (`POST /v1/listings/{id}/images/upload`, `POST /listings/{id}/photos`) are not removed and their behaviour doesn't change.
- **Naming:** the second call is *attach* (the file uploaded to the storage is attached to the listing), never *register* / *create*.
- **Platform vs domain:** `DirectUploadService` holds no business rule — no access check, no status check, no entity. It knows paths, types, sizes and bytes. Each domain does its own checks before calling it, and records the file itself.
- Never serve or modify `src/main/html/`.

## Review Focus

- A browser PUT without the exact signed `Content-Type` gets a 403 from S3 → `direct-upload.js` resolves false and `listing.js` falls back to the multipart post, not a dead end.
- Local storage (`presignedUploadUrl` unsupported) → presign 200 with `{url: null}`, and the fallback keeps local dev working.
- Object over 5 MB, or first bytes that aren't the declared image type → object deleted, no `ListingImage` row, 400 to the caller.
- A path outside the prefix (`listings/102/images/x.png` sent to listing 101) or a traversal (`listings/101/images/../../…`) → 400, nothing read or stored.
- Attaching the same valid path twice → exactly one row.
- `DirectUploadService` stays domain-agnostic: nothing in it mentions listings.
- A missing/failed attach after a successful PUT leaves the S3 object orphaned; nothing in this plan cleans it up.

---

### Task 1: Generic direct-upload DTOs

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/common/dto/PresignUploadRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/common/dto/PresignUploadResponse.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/common/dto/AttachUploadRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/AttachListingImageResponse.kt`
- Modify: `modules/ndopify-dto/CLAUDE.md` (the `common.dto` line: add the direct-upload DTOs)

**Interfaces:**
- Produces: `PresignUploadRequest(contentType: String?)`, `PresignUploadResponse(url: String?, path: String?)`, `AttachUploadRequest(path: String)` — shared by every domain with a direct upload (listing now; KYC and profile next); `AttachListingImageResponse(imageId: Long)` — listing-specific.

- [ ] **Step 1: Write the four data classes**

```kotlin
// common.dto
/** Asks for a short-lived URL to upload a file of [contentType] straight to the storage. */
data class PresignUploadRequest(val contentType: String? = null)

/** Where to PUT the file ([url]) and the [path] to attach afterwards. Both null when the storage can't presign (local). */
data class PresignUploadResponse(val url: String? = null, val path: String? = null)

/** Attaches the file uploaded at [path] (from a [PresignUploadResponse]) to the resource of the endpoint. */
data class AttachUploadRequest(val path: String = "")

// listing.dto
data class AttachListingImageResponse(val imageId: Long = -1)
```

Every field has a default, per `modules/ndopify-dto/CLAUDE.md`.

- [ ] **Step 2: Build the DTO locally**

```bash
cd modules/ndopify-dto && mvn install -DskipTests
```

Expected: BUILD SUCCESS, the module's `-SNAPSHOT` version (see `modules/ndopify-dto/pom.xml`) installed in `~/.m2`.

- [ ] **Step 3: Point the root POM at the local snapshot (uncommitted)**

In the root `pom.xml`, set `<ndopify-dto.version>` to that `-SNAPSHOT`. Do not commit this line.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/common/dto \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/AttachListingImageResponse.kt \
  modules/ndopify-dto/CLAUDE.md
git commit -m "feat(dto): add the direct upload DTOs" -m "Refs #127"
```

---

### Task 2: Storage capabilities for direct upload

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/storage/StorageService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/storage/s3/StorageServiceS3.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/storage/local/StorageServiceLocal.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/storage/s3/StorageServiceS3Test.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/storage/local/StorageServiceLocalTest.kt`

**Interfaces:**
- Produces: `StoredObject(contentType: String?, contentLength: Long)`; `StorageService.presignedUploadUrl(path: String, contentType: String, expirationInSeconds: Int): URL?` (default `null`), `metadata(path: String): StoredObject?`, `readPrefix(path: String, maxBytes: Int): ByteArray`, `delete(path: String)`.

- [ ] **Step 1: Write the failing S3 tests**

Add to `StorageServiceS3Test`:

```kotlin
@Test
fun `presigned upload url`() {
    val expected = URL("https://s3.amazonaws.com/test/listings/1/images/a.jpg?X-Amz-Signature=x")
    doReturn(expected).whenever(s3).generatePresignedUrl(any())

    val url = storage.presignedUploadUrl("listings/1/images/a.jpg", "image/jpeg", 600)

    assertEquals(expected, url)
    val request = ArgumentCaptor.forClass(GeneratePresignedUrlRequest::class.java)
    verify(s3).generatePresignedUrl(request.capture())
    assertEquals("listings/1/images/a.jpg", request.value.key)
    assertEquals(HttpMethod.PUT, request.value.method)
    assertEquals("image/jpeg", request.value.contentType)
    assertEquals(now + 600 * 1000L, request.value.expiration.time)
}

@Test
fun metadata() {
    val meta = ObjectMetadata().apply { contentType = "image/png"; contentLength = 123 }
    doReturn(meta).whenever(s3).getObjectMetadata("test", "listings/1/images/a.png")

    assertEquals(StoredObject("image/png", 123), storage.metadata("listings/1/images/a.png"))
}

@Test
fun `metadata - missing object`() {
    doThrow(AmazonS3Exception("not found").apply { statusCode = 404 })
        .whenever(s3).getObjectMetadata("test", "listings/1/images/a.png")

    assertNull(storage.metadata("listings/1/images/a.png"))
}

@Test
fun `read prefix`() {
    val obj: S3Object = mock()
    doReturn(S3ObjectInputStream(ByteArrayInputStream("hello".toByteArray()), null)).whenever(obj).objectContent
    doReturn(obj).whenever(s3).getObject(any<GetObjectRequest>())

    assertEquals("hello", String(storage.readPrefix("listings/1/images/a.jpg", 16)))

    val request = ArgumentCaptor.forClass(GetObjectRequest::class.java)
    verify(s3).getObject(request.capture())
    assertEquals(0L, request.value.rangeStart)
    assertEquals(15L, request.value.rangeEnd)
}

@Test
fun delete() {
    storage.delete("listings/1/images/a.jpg")

    verify(s3).deleteObject("test", "listings/1/images/a.jpg")
}
```

Add to `StorageServiceLocalTest`:

```kotlin
@Test
fun `metadata, read prefix and delete`() {
    service.store("a/b.txt", ByteArrayInputStream("hello-world".toByteArray()), "text/plain")

    assertEquals(StoredObject("text/plain", 11), service.metadata("a/b.txt"))
    assertEquals("hello", String(service.readPrefix("a/b.txt", 5)))
    assertNull(service.metadata("a/missing.txt"))
    assertEquals(0, service.readPrefix("a/missing.txt", 5).size)

    service.delete("a/b.txt")
    assertNull(service.metadata("a/b.txt"))
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server
mvn test -Dtest='StorageServiceS3Test,StorageServiceLocalTest'
```

Expected: FAIL — unresolved references / abstract members not implemented.

- [ ] **Step 3: Implement the interface and both storages**

In `StorageService.kt`:

```kotlin
data class StoredObject(val contentType: String?, val contentLength: Long)

/**
 * A short-lived URL to PUT [path] with [contentType]. Null when this storage can't presign an upload (ex: local):
 * the caller falls back to uploading the file through the server.
 */
fun presignedUploadUrl(path: String, contentType: String, expirationInSeconds: Int): URL? = null

fun metadata(path: String): StoredObject?

fun readPrefix(path: String, maxBytes: Int): ByteArray

fun delete(path: String)
```

`StorageServiceS3`:

```kotlin
override fun presignedUploadUrl(path: String, contentType: String, expirationInSeconds: Int): URL {
    val request = GeneratePresignedUrlRequest(bucket, path)
        .withMethod(com.amazonaws.HttpMethod.PUT)
        .withContentType(contentType)
        .withExpiration(Date(clock.millis() + expirationInSeconds * 1000L))
    return s3.generatePresignedUrl(request)
}

override fun metadata(path: String): StoredObject? =
    try {
        val meta = s3.getObjectMetadata(bucket, path)
        StoredObject(meta.contentType, meta.contentLength)
    } catch (ex: AmazonS3Exception) {
        if (ex.statusCode == 404) null else throw ex
    }

override fun readPrefix(path: String, maxBytes: Int): ByteArray =
    s3.getObject(GetObjectRequest(bucket, path).withRange(0, maxBytes - 1L))
        .objectContent.use { it.readBytes() }

override fun delete(path: String) {
    s3.deleteObject(bucket, path)
}
```

`StorageServiceLocal`:

```kotlin
override fun metadata(path: String): StoredObject? {
    val file = toFile(path)
    return if (file.isFile) StoredObject(MimeUtils.getMimeTypeFromExtension(file.extension), file.length()) else null
}

override fun readPrefix(path: String, maxBytes: Int): ByteArray {
    val file = toFile(path)
    return if (file.isFile) FileInputStream(file).use { it.readNBytes(maxBytes) } else ByteArray(0)
}

override fun delete(path: String) {
    toFile(path).delete()
}
```

Add `MimeUtils` import to `StorageServiceLocal`.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest='StorageServiceS3Test,StorageServiceLocalTest'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/storage \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/storage
git commit -m "feat(server): add presigned upload and object checks to the storage"
```

---

### Task 3: `DirectUploadService` (platform)

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/storage/upload/DirectUploadService.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (under `ndopify.storage`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/storage/upload/DirectUploadServiceTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the `platform/storage` bullet)

**Interfaces:**
- Consumes: Task 1 `PresignUploadResponse`; Task 2 `StorageService.presignedUploadUrl` / `metadata` / `readPrefix` / `delete`.
- Produces:
  - `DirectUploadService.presign(prefix: String, contentType: String?): PresignUploadResponse`
  - `DirectUploadService.verify(path: String, prefix: String): String` — returns the detected MIME type.
  - `DirectUploadService.IMAGE_MIME_TYPES`, `MAX_BYTES`.

The service is image-only for now (the three users — listing, KYC, profile — all upload JPEG/PNG/WEBP up to 5 MB). Widen it with parameters only when a caller needs another type.

- [ ] **Step 1: Write the failing tests**

`DirectUploadServiceTest` (plain unit test: `storageProvider` mock returning a `storage` mock, `ttl = 600`):

```kotlin
@Test
fun `presign - s3`() {
    doReturn(URL("https://s3.example.com/put")).whenever(storage)
        .presignedUploadUrl(any(), eq("image/jpeg"), eq(600))

    val response = service.presign("listings/101/images", "image/jpeg")

    assertEquals("https://s3.example.com/put", response.url)
    assertTrue(response.path!!.matches(Regex("listings/101/images/[0-9a-f-]{36}\\.jpg")))
}

@Test
fun `presign - local storage returns no url`() {
    doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())

    val response = service.presign("listings/101/images", "image/png")
    assertNull(response.url)
    assertNull(response.path)
}

@Test
fun `presign - content type with parameters`() { ... "IMAGE/PNG; charset=x" → path ends with .png }

@Test
fun `presign - not an image`() {
    assertFailsWith<BadRequestException> { service.presign("listings/101/images", "application/pdf") }
    verify(storage, never()).presignedUploadUrl(any(), any(), any())
}

@Test
fun `verify - returns the detected type`() {
    val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png()).whenever(storage).readPrefix(path, 4096)

    assertEquals("image/png", service.verify(path, "listings/101/images"))
}

@Test
fun `verify - path outside the prefix`() { ... "listings/102/images/<uuid>.png" → BadRequestException, metadata never called }
@Test
fun `verify - traversal`() { ... "listings/101/images/../../kyc/1/images/x.png" → BadRequestException, metadata never called }
@Test
fun `verify - object missing`() { ... metadata null → BadRequestException, delete never }
@Test
fun `verify - too large`() { ... length MAX_BYTES + 1 → BadRequestException, verify(storage).delete(path) }
@Test
fun `verify - bytes are not an image`() { ... readPrefix "not an image" → BadRequestException, verify(storage).delete(path) }
@Test
fun `verify - extension does not match the bytes`() { ... path .png + jpeg bytes → BadRequestException, verify(storage).delete(path) }
```

`png()` / `jpeg()` are small helpers writing a 1×1 `BufferedImage` with `ImageIO`.

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-server
mvn test -Dtest=DirectUploadServiceTest
```

Expected: FAIL — `DirectUploadService` unresolved.

- [ ] **Step 3: Implement the service**

```kotlin
/**
 * Lets a browser upload a file straight to the storage, so its bytes never go through a dyno: [presign] hands out a
 * short-lived PUT URL for a new path under a prefix, and [verify] checks what was uploaded there before the caller
 * attaches it. No business rule here: the calling domain checks who may upload and when, and records the file.
 */
@Service
class DirectUploadService(
    private val storageProvider: StorageServiceProvider,
    @param:Value("\${ndopify.storage.upload-url-ttl-seconds}") private val ttl: Int,
) {
    companion object {
        val IMAGE_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")
        const val MAX_BYTES = 5L * 1024 * 1024
        const val PREFIX_BYTES = 4096
        private val TIKA = Tika()
    }

    /**
     * A PUT URL for `<prefix>/<uuid>.<ext>`, signed for [contentType]. An empty response (HTTP 200) when the storage
     * can't presign (local): the caller falls back to the multipart upload.
     * Errors: 400 `HTTP_INVALID_PARAMETER` (`contentType`) when not JPEG, PNG or WEBP.
     */
    fun presign(prefix: String, contentType: String?): PresignUploadResponse { ... }

    /**
     * Checks the file uploaded at [path] and returns its MIME type, detected from its bytes. Errors, in order, all
     * 400 `HTTP_INVALID_PARAMETER` (`path`): a path [presign] wouldn't have issued under [prefix]; no object; more than
     * [MAX_BYTES]; bytes that aren't JPEG/PNG/WEBP or don't match the path's extension. The object is deleted on the
     * last two, so a rejected file doesn't stay in the storage.
     */
    fun verify(path: String, prefix: String): String { ... }
}
```

`presign`: normalize the type (`split(";").first().trim().lowercase()`), reject outside `IMAGE_MIME_TYPES` (message `"$contentType is not a JPEG, PNG or WEBP image."`, parameter `contentType`, `PARAMETER_TYPE_PAYLOAD`); path `"$prefix/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}"`; `storage.presignedUploadUrl(path, mimeType, ttl)` null → `PresignUploadResponse()`, else `PresignUploadResponse(url.toString(), path)`.

`verify`: match `^${Regex.escape(prefix)}/[0-9a-f-]{36}\.(jpg|png|webp)$` (rejects any other listing's path and any `..`); `metadata` null → 400; `contentLength > MAX_BYTES` → `delete` then 400; `TIKA.detect(readPrefix(path, PREFIX_BYTES))` outside `IMAGE_MIME_TYPES` or with an extension other than the path's → `delete` then 400.

`application.yml`, under `ndopify.storage`:

```yaml
        upload-url-ttl-seconds: 600    # How long a browser has to PUT a file straight to the storage
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=DirectUploadServiceTest
```

Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-server/CLAUDE.md`**

In the `platform/storage` bullet: `upload/DirectUploadService` presigns a browser upload under a prefix and verifies the uploaded file (path, size, sniffed type; deletes a rejected one); domains call it from their own *presign* / *attach* endpoints after their access checks, and keep their multipart endpoint as the fallback. S3 only: `{url: null}` on local.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/storage/upload \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/storage/upload \
  modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): add the direct upload service to the storage platform"
```

---

### Task 4: Presign and attach listing photos on ndopify-server

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/dao/ListingImageRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingImageService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingImageServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PresignListingImageEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/AttachListingImageEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (listing domain paragraph)

**Interfaces:**
- Consumes: Task 1 DTOs; Task 3 `DirectUploadService`.
- Produces:
  - `ListingImageService.presign(listingId: Long, contentType: String?): PresignUploadResponse`
  - `ListingImageService.attach(listingId: Long, path: String): ListingImageEntity`
  - `ListingImageRepository.findByPath(path: String): ListingImageEntity?`
  - `POST /v1/listings/{id}/images/presign` (`PresignUploadRequest`) → `PresignUploadResponse`
  - `POST /v1/listings/{id}/images/attach` (`AttachUploadRequest`) → `AttachListingImageResponse`

- [ ] **Step 1: Write the failing service tests**

Extend `ListingImageServiceTest`; add a `directUpload: DirectUploadService` mock to the constructor (before `clock`). The upload checks themselves are `DirectUploadServiceTest`'s; here only the listing's part:

```kotlin
@Test
fun `presign - under the listing's prefix`() {
    doReturn(listing).whenever(listingService).findById(101L)
    val expected = PresignUploadResponse("https://s3.example.com/put", "listings/101/images/x.jpg")
    doReturn(expected).whenever(directUpload).presign("listings/101/images", "image/jpeg")

    assertEquals(expected, service.presign(101L, "image/jpeg"))
    verify(accessService).ensureCanUpdate(listing)
}

@Test
fun `attach - records a pending image`() {
    val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    doReturn(listing).whenever(listingService).findById(101L)
    doReturn("image/png").whenever(directUpload).verify(path, "listings/101/images")
    doReturn(storage).whenever(storageProvider).get()
    doReturn(URL("https://cdn.example.com/$path")).whenever(storage).publicUrl(path)
    doAnswer { it.arguments[0] }.whenever(dao).save(any())

    val image = service.attach(101L, path)

    assertEquals(path, image.path)
    assertEquals("image/png", image.mimeType)
    assertEquals(ImageStatus.PENDING_VERIFICATION, image.status)
    assertEquals("https://cdn.example.com/$path", image.url)
}

@Test
fun `attach - idempotent`() { ... dao.findByPath(path) → existing; returned, directUpload.verify never, dao.save never }
@Test
fun `attach - rejected upload`() { ... directUpload.verify throws BadRequestException → rethrown, dao.save never }
@Test
fun `presign - access denied`() { ... accessService.ensureCanUpdate throws ForbiddenException → directUpload never called }
```

- [ ] **Step 2: Run the service tests to verify they fail**

```bash
cd modules/ndopify-server
mvn test -Dtest=ListingImageServiceTest
```

Expected: FAIL — `presign` / `attach` unresolved, constructor arity.

- [ ] **Step 3: Implement the repository, service and endpoints**

`ListingImageRepository`: `fun findByPath(path: String): ListingImageEntity?`

`ListingImageService` (prefix `"listings/${listing.id}/images"`, the multipart upload's folder):

`presign(listingId, contentType)`: `listingService.findById` → 404 `LISTING_NOT_FOUND`; `accessService.ensureCanUpdate(listing)` → 403; `directUpload.presign(prefix, contentType)`.

`attach(listingId, path)`: `findById` → 404; `ensureCanUpdate` → 403; `dao.findByPath(path)` → return it (idempotent); `directUpload.verify(path, prefix)` → 400s; save and return a `ListingImageEntity` with `listingId`, `path`, `publicUrl(path).toString()`, `mimeType` = the verified type, `PENDING_VERIFICATION`, `createdAt`/`modifiedAt` from `clock` (same as `upload`).

KDoc each method with its error codes in check order (the 400s are `DirectUploadService.verify`'s).

`ListingEndpoints`:

```kotlin
/** A short-lived URL to upload a photo straight to the storage; no url with local storage (use `/images/upload`). */
@PostMapping("/{id}/images/presign")
fun presignImage(@PathVariable id: Long, @RequestBody request: PresignUploadRequest): PresignUploadResponse =
    imageService.presign(id, request.contentType)

/** Attaches a photo uploaded straight to the storage, at the path given by `/images/presign`. */
@PostMapping("/{id}/images/attach")
fun attachImage(@PathVariable id: Long, @RequestBody request: AttachUploadRequest): AttachListingImageResponse =
    AttachListingImageResponse(imageId = imageService.attach(id, request.path).id ?: -1)
```

- [ ] **Step 4: Write the failing endpoint tests**

`PresignListingImageEndpointTest` (`@Sql(clean.sql, UploadListingImageEndpoint.sql)`, `roles = arrayOf(RoleCode.ADMIN)`, `@MockitoBean storageProvider`, `storage = mock()`, `publicUrl` answer as in `UploadListingImageEndpointTest`; the real `DirectUploadService` runs over the mocked storage):

- `presign` — mock `storage.presignedUploadUrl(any(), any(), any())` → `URL("https://s3.example.com/put")`; POST `{"contentType":"image/jpeg"}`; assert 200, `url`, and `path` matches `listings/101/images/[0-9a-f-]{36}\.jpg`.
- `presign - local` — mock returns null; assert 200 with null `url`/`path`.
- `presign - not an image` — `application/pdf` → 400 `HTTP_INVALID_PARAMETER`.
- `presign - anonymous` — 401 `AUTH_UNAUTHORIZED`, storage never called.
- `presign - unknown listing` — `/v1/listings/999/images/presign` → 404 `LISTING_NOT_FOUND`.

`AttachListingImageEndpointTest` (same setup):

- `attach` — path `listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png`; mock `metadata` → `StoredObject("image/png", 3)`, `readPrefix` → PNG bytes; POST `{"path": ...}` to `/v1/listings/101/images/attach`; assert 200, `imageId`, one row `PENDING_VERIFICATION`, `url` from `publicUrl`.
- `attach - idempotent` — same POST twice; one row.
- `attach - another listing's path` — `listings/102/images/…png` → 400, no row.
- `attach - missing object` — `metadata` null → 400, no row.
- `attach - not an image` — `readPrefix` returns `"nope"` → 400, `verify(storage).delete(path)`, no row.
- `attach - anonymous` — 401, no row.

- [ ] **Step 5: Run the endpoint tests to verify they pass**

Requires local MySQL (see the root `CLAUDE.md`).

```bash
mvn test -Dtest='PresignListingImageEndpointTest,AttachListingImageEndpointTest,ListingImageServiceTest,UploadListingImageEndpointTest'
```

Expected: PASS.

- [ ] **Step 6: Update `modules/ndopify-server/CLAUDE.md`**

In the **listing** domain paragraph, after the existing `POST /{id}/images/upload` description, add: direct upload (`POST /{id}/images/presign` then `POST /{id}/images/attach`, through `DirectUploadService`), its checks order (404, 403, then the upload's 400s), idempotent on `path`, the S3-only capability with the null URL on local, and that the multipart endpoint stays.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): presign and attach listing photos uploaded directly to the storage"
```

---

### Task 5: Partner Central client calls

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt`

**Interfaces:**
- Consumes: Task 4 endpoints and Task 1 DTOs.
- Produces: `NdListingClient.presignImage(id: Long, contentType: String): PresignUploadResponse`, `NdListingClient.attachImage(id: Long, path: String): AttachListingImageResponse`.

- [ ] **Step 1: Write the failing client tests**

```kotlin
@Test
fun presignImage() {
    server.expect(requestTo("http://localhost:8080/v1/listings/501/images/presign"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"contentType":"image/jpeg"}"""))
        .andRespond(withSuccess("""{"url":"https://s3.example.com/put","path":"listings/501/images/a.jpg"}""", MediaType.APPLICATION_JSON))

    val response = client.presignImage(501, "image/jpeg")
    assertEquals("https://s3.example.com/put", response.url)
    assertEquals("listings/501/images/a.jpg", response.path)
    server.verify()
}

@Test
fun attachImage() {
    server.expect(requestTo("http://localhost:8080/v1/listings/501/images/attach"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"path":"listings/501/images/a.jpg"}"""))
        .andRespond(withSuccess("""{"imageId":7}""", MediaType.APPLICATION_JSON))

    assertEquals(7L, client.attachImage(501, "listings/501/images/a.jpg").imageId)
    server.verify()
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=NdListingClientTest
```

Expected: FAIL — unresolved `presignImage` / `attachImage`.

- [ ] **Step 3: Implement both methods**

```kotlin
/** A short-lived URL to upload a photo straight to the storage: no url with local storage. */
fun presignImage(id: Long, contentType: String): PresignUploadResponse =
    rest.post()
        .uri("/v1/listings/{id}/images/presign", id)
        .body(PresignUploadRequest(contentType))
        .retrieve()
        .body<PresignUploadResponse>()
        ?: PresignUploadResponse()

/** Attaches a photo that was uploaded straight to the storage. */
fun attachImage(id: Long, path: String): AttachListingImageResponse =
    rest.post()
        .uri("/v1/listings/{id}/images/attach", id)
        .body(AttachUploadRequest(path))
        .retrieve()
        .body<AttachListingImageResponse>()
        ?: AttachListingImageResponse()
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=NdListingClientTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt
git commit -m "feat(npc): add the presign and attach listing image calls"
```

---

### Task 6: Partner Central proxy endpoints

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoControllerTest.kt`

**Interfaces:**
- Consumes: Task 5 client methods.
- Produces (the contract `direct-upload.js` expects from any page, Task 7):
  - `POST <form action>/presign` — JSON in `PresignUploadRequest`, out `PresignUploadResponse` (200) or the upstream status (403/404/409/500 as an empty body).
  - `POST <form action>/attach` — JSON in `AttachUploadRequest`, out 200 or the upstream status with the server's `ErrorResponse` body.
  - Here: `POST /listings/{id}/photos/presign` and `POST /listings/{id}/photos/attach`.

- [ ] **Step 1: Write the failing controller tests**

Add to `ListingPhotoControllerTest` (MockMvc standalone, mocks as in the existing tests):

```kotlin
@Test
fun `presign - returns the upload url`() {
    // agent signed in, listing 501 is the agent's DRAFT
    doReturn(PresignUploadResponse("https://s3.example.com/put", "listings/501/images/a.jpg"))
        .whenever(listingClient).presignImage(501L, "image/jpeg")

    val result = mockMvc.perform(
        post("/listings/501/photos/presign")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"contentType":"image/jpeg"}""")
    ).andExpect(status().isOk).andReturn()

    assertTrue(result.response.contentAsString.contains("https://s3.example.com/put"))
}

@Test
fun `presign - listing moved on`() { ... listing PENDING_VERIFICATION → 409, client never called }
@Test
fun `presign - not the agent's listing`() { ... ListingAccess.Locked → 404 }
@Test
fun `attach - returns the image id`() {
    doReturn(AttachListingImageResponse(7L)).whenever(listingClient).attachImage(501L, path)

    mockMvc.perform(post("/listings/501/photos/attach").contentType(APPLICATION_JSON).content("""{"path":"$path"}"""))
        .andExpect(status().isOk)
}
@Test
fun `attach - invalid object`() { ... doThrow(HttpClientErrorException.create(400, ..., """{"error":{...}}""", ...)) → 400 with the body }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=ListingPhotoControllerTest
```

Expected: FAIL — 404 on the new routes.

- [ ] **Step 3: Implement the two endpoints**

Keep them in `ListingPhotoController` as `@ResponseBody` methods, next to `upload`:

```kotlin
@PostMapping("/presign", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun presign(
    @PathVariable("id") value: String,
    @RequestBody request: PresignUploadRequest,
): ResponseEntity<PresignUploadResponse> {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    val id = value.toLongOrNull() ?: return ResponseEntity.notFound().build()
    if (draftOrNull(id, agent) == null) return ResponseEntity.status(HttpStatus.CONFLICT).build()
    return try {
        ResponseEntity.ok(listingClient.presignImage(id, request.contentType.orEmpty()))
    } catch (ex: HttpClientErrorException) {
        ResponseEntity.status(ex.statusCode).build()
    }
}

@PostMapping("/attach", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun attach(
    @PathVariable("id") value: String,
    @RequestBody request: AttachUploadRequest,
): ResponseEntity<*> {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    val id = value.toLongOrNull() ?: return ResponseEntity.notFound().build()
    if (draftOrNull(id, agent) == null) return ResponseEntity.status(HttpStatus.CONFLICT).build()
    return try {
        ResponseEntity.ok(listingClient.attachImage(id, request.path))
    } catch (ex: HttpClientErrorException) {
        ResponseEntity.status(ex.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body(ex.responseBodyAsString)
    }
}
```

`draftOrNull` is the existing `draft()` logic without the flash attribute: `ListingAccess.Found` + `ListingRules.editable`, else null. Keep the redirect-based `draft()` for the form post.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=ListingPhotoControllerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoControllerTest.kt
git commit -m "feat(npc): proxy the direct listing photo upload to the browser"
```

---

### Task 7: Shared `direct-upload.js`, used by `listing.js` with fallback

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/js/direct-upload.js`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing/photos.html` (one `<script>` tag, no visual change)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the photos paragraph, and the JS assets list)

**Interfaces:**
- Consumes: Task 6 routes, derived from the photo form's `action` (`/listings/{id}/photos` → `…/presign`, `…/attach`).
- Produces: `window.NdDirectUpload.upload(action, file)` → `Promise<boolean>`: true once attached, false when the direct path can't run. Page-agnostic: the KYC and profile stories reuse it with their own form `action`.

- [ ] **Step 1: Write `direct-upload.js`**

```js
// Uploads one file straight to the storage, for a form whose action also answers "<action>/presign" and
// "<action>/attach" (JSON). Resolves true once attached, false when the direct path can't run (local storage,
// no bucket CORS, expired URL, any error): the caller then posts the file to the form as usual.
(function () {
    function upload(action, file) {
        return fetch(action + "/presign", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ contentType: file.type })
        }).then(function (response) {
            return response.ok ? response.json() : null;
        }).then(function (presigned) {
            if (!presigned || !presigned.url) return false;
            return fetch(presigned.url, {
                method: "PUT",
                headers: { "Content-Type": file.type },
                body: file
            }).then(function (put) {
                if (!put.ok) return false;
                return fetch(action + "/attach", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ path: presigned.path })
                }).then(function (attached) {
                    return attached.ok;
                });
            });
        }).catch(function () {
            return false;
        });
    }

    window.NdDirectUpload = { upload: upload };
})();
```

- [ ] **Step 2: Load it on the photos page**

In `templates/listing/photos.html`, before the `listing.js` tag: `<script th:src="@{/assets/js/direct-upload.js}" defer></script>` (deferred scripts run in order). Only the photos page loads it; `listing.js` must not assume it is there.

- [ ] **Step 3: Use it in `listing.js`**

In `initPhotos`, replace the `send(input, shrunk)` call with a per-photo direct loop:

```js
function sendAll(input, files) {
    if (!window.NdDirectUpload) return send(input, files);
    var action = form.getAttribute("action");
    var i = 0;
    (function next() {
        if (i >= files.length) {
            // Everything is attached: reload to show the photos (as the form post does)
            location.reload();
            return;
        }
        var photo = files[i++];
        window.NdDirectUpload.upload(action, photo).then(function (uploaded) {
            if (uploaded) {
                existing++;
                return next();
            }
            // Not uploaded directly: the remaining photos go through the normal form post
            send(input, files.slice(i - 1));
        });
    })();
}
```

Call `sendAll(input, shrunk)` where `send(input, shrunk)` was. Leave `send()` (the multipart form submit) untouched — it is the fallback. Keep the "Envoi…" count and `announce` messages.

- [ ] **Step 4: Check the fallback manually**

```bash
cd modules/ndopify-partner-central
mvn spring-boot:run
```

With local ndopify-server (local storage), presign answers `{url:null}`: choose a photo and confirm the multipart post still uploads it and the photo appears. With JS disabled the form still posts.

- [ ] **Step 5: Update `modules/ndopify-partner-central/CLAUDE.md`**

In the photos description: `listing.js` first uploads each shrunk photo directly to the storage with `direct-upload.js` (`POST /listings/{id}/photos/presign` + PUT + `POST /listings/{id}/photos/attach`), and falls back to the multipart post when presign has no URL or any step fails; the multipart path is the no-JS path. Describe `direct-upload.js` as the shared browser half of a direct upload: any form whose action answers `/presign` and `/attach`.

- [ ] **Step 6: Lint and test the module**

```bash
mvn verify
```

Expected: GREEN (no JS test runner in this repo; the controller/template tests cover the server side).

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js/direct-upload.js \
  modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js \
  modules/ndopify-partner-central/src/main/resources/templates/listing/photos.html \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): upload listing photos straight to the storage when possible"
```

---

## Verification

- `mvn verify` in `modules/ndopify-server` (needs local MySQL) and `modules/ndopify-partner-central`.
- The module CLAUDE.md files describe the new flow, and `DirectUploadService` / `direct-upload.js` as reusable.
- The root `pom.xml` `<ndopify-dto.version>` is left at the local `-SNAPSHOT` only in the worktree, never committed (`push` reverts it and writes the released-version `fix dependencies` commit).

## Out of scope (do not build)

- Direct upload for KYC images or the profile photo (their own stories, which depend on #127), contracts or mandates.
- A generic `/v1/uploads` endpoint or an upload entity/table.
- Writing to `local` storage from the browser.
- S3 multipart uploads, lifecycle rules or orphan cleanup.
- Any visual change to the photos page, or changes to the 5 MB / 20-photo limits.
