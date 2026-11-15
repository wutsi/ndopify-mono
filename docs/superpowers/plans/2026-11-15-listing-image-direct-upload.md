# Direct Listing Photo Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The browser uploads a listing photo straight to S3; ndopify-server only issues a short-lived presigned PUT URL and records the photo afterwards, keeping the multipart endpoint as the no-JS / local / failure fallback.

**Architecture:** Two new ndopify-server endpoints (`POST /v1/listings/{id}/images/presign`, `POST /v1/listings/{id}/images`), backed by new `StorageService` capabilities (presigned PUT, object metadata/prefix/delete). Partner Central proxies them to `listing.js`, which tries the direct path per photo and falls back to the existing form post on any failure.

**Tech Stack:** Kotlin, Spring Boot, AWS SDK v1 (`aws-java-sdk-s3` 1.12.797), Apache Tika 4.2.0, Thymeleaf, vanilla JS.

**Spec:** GitHub issue #127 (`gh issue view 127`) — the story holds the design; there is no separate spec file.

## Global Constraints

- Code, comments, logs and docs in English; UI text in French.
- `kotlin.test` assertions; `mockito-kotlin` (`com.nhaarman.mockitokotlin2`) mocks.
- ktlint runs on `validate`; `mvn verify` (tests + JaCoCo gate) must be green in every touched module.
- Conventional commits. A commit touching `modules/ndopify-dto/` touches nothing else; library commits are `feat(dto): …` with `Refs #127` in the body.
- The root `pom.xml` `<ndopify-dto.version>` must never be committed as `-SNAPSHOT`.
- Supported photo types: `image/jpeg`, `image/png`, `image/webp`. Max photo size: 5 MB (`5 * 1024 * 1024` bytes). Max photos per listing: 20 (UI-only, unchanged).
- The existing multipart endpoints (`POST /v1/listings/{id}/images/upload`, `POST /listings/{id}/photos`) are not removed and their behaviour doesn't change.
- Never serve or modify `src/main/html/`.

## Review Focus

- A browser PUT without the exact signed `Content-Type` gets a 403 from S3 → `listing.js` must fall back to the multipart post, not show a dead end.
- Local storage (`presignedUploadUrl` unsupported) → presign 200 with `{url: null}`, and the fallback keeps local dev working.
- Object over 5 MB, or first bytes that aren't the declared image type → registered object deleted, no `ListingImage` row, 400 to the caller.
- A path for another listing (`listings/102/images/x.png` sent to listing 101) or a traversal (`listings/101/images/../../…`) → 400, nothing read or stored.
- Registering the same valid path twice → exactly one row.
- A missing/failed registration after a successful PUT leaves the S3 object orphaned; nothing in this plan cleans it up.

---

### Task 1: DTOs for presign and registration

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/PresignListingImageRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/PresignListingImageResponse.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/CreateListingImageRequest.kt`
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/CreateListingImageResponse.kt`

**Interfaces:**
- Produces: `PresignListingImageRequest(contentType: String?)`, `PresignListingImageResponse(url: String?, path: String?)`, `CreateListingImageRequest(path: String)`, `CreateListingImageResponse(imageId: Long)`.

- [ ] **Step 1: Write the four data classes**

```kotlin
data class PresignListingImageRequest(val contentType: String? = null)

data class PresignListingImageResponse(val url: String? = null, val path: String? = null)

data class CreateListingImageRequest(val path: String = "")

data class CreateListingImageResponse(val imageId: Long = -1)
```

Every field has a default, per `modules/ndopify-dto/CLAUDE.md`.

- [ ] **Step 2: Build the DTO locally**

```bash
cd modules/ndopify-dto && mvn install -DskipTests
```

Expected: BUILD SUCCESS, `0.0.90-SNAPSHOT` installed in `~/.m2`.

- [ ] **Step 3: Point the root POM at the local snapshot (uncommitted)**

In the root `pom.xml`, set `<ndopify-dto.version>0.0.90-SNAPSHOT</ndopify-dto.version>` (it is `0.0.89`). Do not commit this line task's commit.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/PresignListingImageRequest.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/PresignListingImageResponse.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/CreateListingImageRequest.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/CreateListingImageResponse.kt
git commit -m "feat(dto): add the direct listing image upload DTOs" -m "Refs #127"
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

### Task 3: Presign and register on ndopify-server

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/dao/ListingImageRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingImageService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/endpoint/ListingEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (under `ndopify.listing`)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingImageServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/PresignListingImageEndpointTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/endpoint/RegisterListingImageEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (listing domain paragraph)

**Interfaces:**
- Consumes: Task 1 DTOs; Task 2 `StorageService.presignedUploadUrl` / `metadata` / `readPrefix` / `delete`.
- Produces:
  - `ListingImageService.presign(listingId: Long, contentType: String?): PresignListingImageResponse`
  - `ListingImageService.register(listingId: Long, path: String): ListingImageEntity`
  - `ListingImageRepository.findByPath(path: String): ListingImageEntity?`
  - `POST /v1/listings/{id}/images/presign` → `PresignListingImageResponse`
  - `POST /v1/listings/{id}/images` → `CreateListingImageResponse`

- [ ] **Step 1: Write the failing service tests**

Extend `ListingImageServiceTest`; construct the service with the 6-argument form (`dao, listingService, accessService, storageProvider, 600, Clock.systemUTC()` — put `uploadUrlTTL` before `clock`). Tests:

```kotlin
@Test
fun `presign - s3`() {
    doReturn(listing).whenever(listingService).findById(101L)
    doReturn(storage).whenever(storageProvider).get()
    doReturn(URL("https://s3.example.com/put")).whenever(storage)
        .presignedUploadUrl(any(), eq("image/jpeg"), eq(600))

    val response = service.presign(101L, "image/jpeg")

    assertEquals("https://s3.example.com/put", response.url)
    assertTrue(response.path!!.matches(Regex("listings/101/images/[0-9a-f-]{36}\\.jpg")))
}

@Test
fun `presign - local storage returns no url`() {
    doReturn(listing).whenever(listingService).findById(101L)
    doReturn(storage).whenever(storageProvider).get()
    doReturn(null).whenever(storage).presignedUploadUrl(any(), any(), any())

    assertNull(service.presign(101L, "image/png").url)
    assertNull(service.presign(101L, "image/png").path)
}

@Test
fun `presign - not an image`() {
    doReturn(listing).whenever(listingService).findById(101L)

    assertFailsWith<BadRequestException> { service.presign(101L, "application/pdf") }
}

@Test
fun `register - records a pending image`() {
    val path = "listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    doReturn(listing).whenever(listingService).findById(101L)
    doReturn(storage).whenever(storageProvider).get()
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png()).whenever(storage).readPrefix(path, 4096)
    doReturn(URL("https://cdn.example.com/$path")).whenever(storage).publicUrl(path)
    doAnswer { it.arguments[0] }.whenever(dao).save(any())

    val image = service.register(101L, path)

    assertEquals(path, image.path)
    assertEquals("image/png", image.mimeType)
    assertEquals(ImageStatus.PENDING_VERIFICATION, image.status)
    assertEquals("https://cdn.example.com/$path", image.url)
}

@Test
fun `register - another listing path`() { ... assertFailsWith<BadRequestException> }
@Test
fun `register - object missing`() { ... metadata null → BadRequestException, dao.save never }
@Test
fun `register - too large`() { ... length 5MB+1 → BadRequestException, verify(storage).delete(path) }
@Test
fun `register - bytes are not an image`() { ... readPrefix "not an image" → BadRequestException, verify(storage).delete(path) }
@Test
fun `register - extension does not match the bytes`() { ... path .png + jpeg bytes → BadRequestException, verify(storage).delete(path) }
@Test
fun `register - idempotent`() { ... existing image by path → returned, dao.save never, metadata never }
```

`png()` is a small helper writing a 1×1 `BufferedImage` to PNG bytes.

- [ ] **Step 2: Run the service tests to verify they fail**

```bash
cd modules/ndopify-server
mvn test -Dtest=ListingImageServiceTest
```

Expected: FAIL — `presign` / `register` unresolved, constructor arity.

- [ ] **Step 3: Implement the repository, service and endpoints**

`ListingImageRepository`: `fun findByPath(path: String): ListingImageEntity?`

`ListingImageService`:

```kotlin
companion object {
    val IMAGE_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")
    const val MAX_IMAGE_BYTES = 5L * 1024 * 1024
    const val PREFIX_BYTES = 4096
    private val TIKA = Tika()
}

@param:Value("\${ndopify.listing.image.upload-url-ttl-seconds}") private val uploadUrlTTL: Int
```

`presign(listingId, contentType)`:
1. `listingService.findById(listingId)` → 404 `LISTING_NOT_FOUND`;
2. `accessService.ensureCanUpdate(listing)` → 403;
3. normalize the type (`split(";").first().trim().lowercase()`), reject outside `IMAGE_MIME_TYPES` with 400 `HTTP_INVALID_PARAMETER` (message: `"$contentType is not a JPEG, PNG or WEBP image."`, parameter `contentType`, `PARAMETER_TYPE_PAYLOAD`);
4. build `listings/${listing.id}/images/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(mimeType)}`;
5. `storageProvider.get().presignedUploadUrl(path, mimeType, uploadUrlTTL)`; null → `PresignListingImageResponse()`; else `PresignListingImageResponse(url.toString(), path)`.

`register(listingId, path)`:
1. `listingService.findById(listingId)` → 404; `ensureCanUpdate` → 403;
2. pattern `^listings/$listingId/images/[0-9a-f-]{36}\\.(jpg|png|webp)$` else 400 `HTTP_INVALID_PARAMETER` (parameter `path`, payload);
3. `dao.findByPath(path)` → return it;
4. `storage.metadata(path)` null → 400 (parameter `path`);
5. `contentLength > MAX_IMAGE_BYTES` → `storage.delete(path)` then 400;
6. `TIKA.detect(storage.readPrefix(path, PREFIX_BYTES))` outside `IMAGE_MIME_TYPES`, or `MimeUtils.getExtensionFromMimeType(detected) != path.substringAfterLast('.')` → `storage.delete(path)` then 400;
7. save and return a `ListingImageEntity` with `listingId`, `path`, `publicUrl(path).toString()`, `mimeType = detected`, `PENDING_VERIFICATION`, `createdAt`/`modifiedAt` from `clock` (same as `upload`).

KDoc each method with its error codes in check order.

`ListingEndpoints`:

```kotlin
/** A short-lived URL to upload a photo straight to the storage: no url with local storage. */
@PostMapping("/{id}/images/presign")
fun presign(@PathVariable id: Long, @RequestBody request: PresignListingImageRequest): PresignListingImageResponse =
    imageService.presign(id, request.contentType)

/** Records a photo uploaded straight to the storage. */
@PostMapping("/{id}/images")
fun registerImages(@PathVariable id: Long, @RequestBody request: CreateListingImageRequest): CreateListingImageResponse =
    CreateListingImageResponse(imageId = imageService.register(id, request.path).id ?: -1)
```

`application.yml`, under `ndopify.listing`:

```yaml
        image:
            upload-url-ttl-seconds: 600    # How long the browser has to PUT a photo straight to the storage
```

- [ ] **Step 4: Write the failing endpoint tests**

`PresignListingImageEndpointTest` (`@Sql(clean.sql, UploadListingImageEndpoint.sql)`, `roles = arrayOf(RoleCode.ADMIN)`, `@MockitoBean storageProvider`, `storage = mock()`, `publicUrl` answer as in `UploadListingImageEndpointTest`):

- `presign` — mock `storage.presignedUploadUrl(any(), any(), any())` → `URL("https://s3.example.com/put")`; POST `{"contentType":"image/jpeg"}`; assert 200, `url`, and `path` matches `listings/101/images/[0-9a-f-]{36}\.jpg`.
- `presign - local` — mock returns null; assert 200 with null `url`/`path`.
- `presign - not an image` — `application/pdf` → 400 `HTTP_INVALID_PARAMETER`.
- `presign - anonymous` — 401 `AUTH_UNAUTHORIZED`, storage never called.
- `presign - unknown listing` — `/v1/listings/999/images/presign` → 404 `LISTING_NOT_FOUND`.

`RegisterListingImageEndpointTest` (same setup):

- `register` — path `listings/101/images/0f8fad5b-d9cb-469f-a165-70867728950e.png`; mock `metadata` → `StoredObject("image/png", 3)`, `readPrefix` → PNG bytes; POST `{"path": ...}`; assert 200, `imageId`, one row `PENDING_VERIFICATION`, `url` from `publicUrl`.
- `register - idempotent` — same POST twice; one row.
- `register - another listing's path` — `listings/102/images/…png` → 400, no row.
- `register - missing object` — `metadata` null → 400, no row.
- `register - not an image` — `readPrefix` returns `"nope"` → 400, `verify(storage).delete(path)`, no row.
- `register - anonymous` — 401, no row.

- [ ] **Step 5: Run the endpoint tests to verify they pass**

Requires local MySQL (see the root `CLAUDE.md`).

```bash
mvn test -Dtest='PresignListingImageEndpointTest,RegisterListingImageEndpointTest,ListingImageServiceTest,UploadListingImageEndpointTest'
```

Expected: PASS.

- [ ] **Step 6: Update `modules/ndopify-server/CLAUDE.md`**

In the **listing** domain paragraph, after the existing `POST /{id}/images/upload` description, add: direct upload (`POST /{id}/images/presign` then `POST /{id}/images`), its checks order, the TTL setting, the S3-only capability with the null URL on local, and that the multipart endpoint stays.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing \
  modules/ndopify-server/src/main/resources/application.yml \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): presign and register listing photos uploaded directly to the storage"
```

---

### Task 4: Partner Central client calls

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClient.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdListingClientTest.kt`

**Interfaces:**
- Consumes: Task 3 endpoints and Task 1 DTOs.
- Produces: `NdListingClient.presignImage(id: Long, contentType: String): PresignListingImageResponse`, `NdListingClient.registerImage(id: Long, path: String): CreateListingImageResponse`.

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
fun registerImage() {
    server.expect(requestTo("http://localhost:8080/v1/listings/501/images"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"path":"listings/501/images/a.jpg"}"""))
        .andRespond(withSuccess("""{"imageId":7}""", MediaType.APPLICATION_JSON))

    assertEquals(7L, client.registerImage(501, "listings/501/images/a.jpg").imageId)
    server.verify()
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=NdListingClientTest
```

Expected: FAIL — unresolved `presignImage` / `registerImage`.

- [ ] **Step 3: Implement both methods**

```kotlin
/** A short-lived URL to upload a photo straight to the storage: no url with local storage. */
fun presignImage(id: Long, contentType: String): PresignListingImageResponse =
    rest.post()
        .uri("/v1/listings/{id}/images/presign", id)
        .body(PresignListingImageRequest(contentType))
        .retrieve()
        .body<PresignListingImageResponse>()
        ?: PresignListingImageResponse()

/** Records a photo that was uploaded straight to the storage. */
fun registerImage(id: Long, path: String): CreateListingImageResponse =
    rest.post()
        .uri("/v1/listings/{id}/images", id)
        .body(CreateListingImageRequest(path))
        .retrieve()
        .body<CreateListingImageResponse>()
        ?: CreateListingImageResponse()
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
git commit -m "feat(npc): add the presign and register listing image calls"
```

---

### Task 5: Partner Central proxy endpoints

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoControllerTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the photos paragraph)

**Interfaces:**
- Consumes: Task 4 client methods.
- Produces:
  - `POST /listings/{id}/photos/presign` — JSON in `PresignListingImageRequest`, out `PresignListingImageResponse` (200) or the upstream status (403/404/409/500 as an empty body).
  - `POST /listings/{id}/photos/register` — JSON in `CreateListingImageRequest`, out `CreateListingImageResponse` (200) or the upstream status with the server's `ErrorResponse` body.

- [ ] **Step 1: Write the failing controller tests**

Add to `ListingPhotoControllerTest` (MockMvc standalone, mocks as in the existing tests):

```kotlin
@Test
fun `presign - returns the upload url`() {
    // agent signed in, listing 501 is the agent's DRAFT
    doReturn(PresignListingImageResponse("https://s3.example.com/put", "listings/501/images/a.jpg"))
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
fun `register - returns the image id`() {
    doReturn(CreateListingImageResponse(7L)).whenever(listingClient).registerImage(501L, path)

    mockMvc.perform(post("/listings/501/photos/register").contentType(APPLICATION_JSON).content("""{"path":"$path"}"""))
        .andExpect(status().isOk)
}
@Test
fun `register - invalid object`() { ... doThrow(HttpClientErrorException.create(400, ..., """{"error":{...}}""", ...)) → 400 with the body }
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
    @RequestBody request: PresignListingImageRequest,
): ResponseEntity<PresignListingImageResponse> {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    val id = value.toLongOrNull() ?: return ResponseEntity.notFound().build()
    if (draftOrNull(id, agent) == null) return ResponseEntity.status(HttpStatus.CONFLICT).build()
    return try {
        ResponseEntity.ok(listingClient.presignImage(id, request.contentType.orEmpty()))
    } catch (ex: HttpClientErrorException) {
        ResponseEntity.status(ex.statusCode).build()
    }
}

@PostMapping("/register", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun register(
    @PathVariable("id") value: String,
    @RequestBody request: CreateListingImageRequest,
): ResponseEntity<*> {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    val id = value.toLongOrNull() ?: return ResponseEntity.notFound().build()
    if (draftOrNull(id, agent) == null) return ResponseEntity.status(HttpStatus.CONFLICT).build()
    return try {
        ResponseEntity.ok(listingClient.registerImage(id, request.path))
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

- [ ] **Step 5: Update `modules/ndopify-partner-central/CLAUDE.md`**

In the photos description, state that `listing.js` first uploads each shrunk photo directly to the storage through `POST /listings/{id}/photos/presign` + PUT + `POST /listings/{id}/photos/register`, and falls back to the multipart post when presign has no URL or any step fails; the multipart path is the no-JS path.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoControllerTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): proxy the direct listing photo upload to the browser"
```

---

### Task 6: Direct upload in `listing.js`, with fallback

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js`

**Interfaces:**
- Consumes: Task 5 routes, derived from the photo form's `action` (`/listings/{id}/photos` → `…/presign`, `…/register`).

- [ ] **Step 1: Add the direct-upload functions**

After `shrink` and before `initPhotos`:

```js
// Uploads one photo straight to the storage: presign, PUT, register. Resolves true once registered,
// false when the direct path can't run (local storage, no bucket CORS, expired URL, any error):
// the caller then posts the file to the form as usual.
function uploadDirect(form, photo) {
    var action = form.getAttribute("action");
    return fetch(action + "/presign", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ contentType: photo.type })
    }).then(function (response) {
        return response.ok ? response.json() : null;
    }).then(function (presigned) {
        if (!presigned || !presigned.url) return false;
        return fetch(presigned.url, {
            method: "PUT",
            headers: { "Content-Type": photo.type },
            body: photo
        }).then(function (put) {
            if (!put.ok) return false;
            return fetch(action + "/register", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ path: presigned.path })
            }).then(function (registered) {
                return registered.ok;
            });
        });
    }).catch(function () {
        return false;
    });
}
```

In `initPhotos`, replace the `send(input, shrunk)` call with a per-photo direct loop:

```js
function sendAll(input, files) {
    var i = 0;
    (function next() {
        if (i >= files.length) {
            // Everything is registered: reload to show the photos (as the form post does)
            location.reload();
            return;
        }
        var photo = files[i++];
        uploadDirect(form, photo).then(function (uploaded) {
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

- [ ] **Step 2: Check the fallback manually**

```bash
cd modules/ndopify-partner-central
mvn spring-boot:run
```

With local ndopify-server (local storage), presign answers `{url:null}`: choose a photo and confirm the multipart post still uploads it and the photo appears. With JS disabled the form still posts.

- [ ] **Step 3: Lint and test the module**

```bash
mvn verify
```

Expected: GREEN (no JS test runner in this repo; the controller/template tests cover the server side).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js
git commit -m "feat(npc): upload listing photos straight to the storage when possible"
```

---

## Verification

- `mvn verify` in `modules/ndopify-server` (needs local MySQL) and `modules/ndopify-partner-central`.
- The module CLAUDE.md files describe the new flow.
- The root `pom.xml` `<ndopify-dto.version>` is left at the local `-SNAPSHOT` only in the worktree, never committed (`push` reverts it and writes the released-version `fix dependencies` commit).

## Out of scope (do not build)

- Direct upload for KYC images, the profile photo, contracts or mandates.
- Writing to `local` storage from the browser.
- S3 multipart uploads, lifecycle rules or orphan cleanup.
- Any visual change to the photos page, or changes to the 5 MB / 20-photo limits.
