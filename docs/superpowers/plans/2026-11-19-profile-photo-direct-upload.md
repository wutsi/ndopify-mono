# Profile Photo Direct Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The browser uploads the agent's profile photo straight to the storage (presign → PUT → attach), keeping the multipart flow as the no-JS / local / failure fallback, and checking on ndopify-server that a party only changes its own photo.

**Architecture:** Reuse #127's platform code on ndopify-server (`platform/storage/upload/DirectUploadService`, `StorageService.presignedUploadUrl` / `metadata` / `readPrefix` / `delete`) and Actor Partner Central's `direct-upload.js`. The party domain adds `POST /v1/parties/{id}/photo/presign` and `/attach`, both (and the multipart upload) guarded by a new `PartyAccessService`. Partner Central proxies them under `/profile/photo/presign` and `/attach`, and a small `GET /profile/photo/saved` hands the agent back to `/` with the existing saved flash.

**Tech Stack:** Kotlin, Spring Boot, AWS SDK v1 (through the existing `StorageService`), Apache Tika (through `DirectUploadService`), Thymeleaf, vanilla JS.

**Spec:** GitHub issue #206 (`gh issue view 206`) — the story holds the design; there is no separate spec file.

## Global Constraints

- Code, comments, logs and docs in English; UI text in French.
- `kotlin.test` assertions; `mockito-kotlin` (`com.nhaarman.mockitokotlin2`) mocks.
- ktlint runs on `validate`; `mvn verify` (tests + JaCoCo gate) must be green in `modules/ndopify-server` (needs local MySQL) and `modules/ndopify-partner-central`.
- Conventional commits. This story touches no library: `modules/ndopify-dto/` and `modules/ndopify-web-core/` are unchanged, and the root `pom.xml` versions stay `ndopify-dto 0.0.99` / `ndopify-web-core 0.0.8`.
- Photo types: `image/jpeg`, `image/png`, `image/webp`; max 5 MB — `DirectUploadService`'s limits, not new ones.
- `POST /v1/parties/{id}/photo` (multipart) and `POST /profile/photo` stay, and are the no-JS / local / failure fallback.
- Naming: the second call is *attach*, never *register* / *create*.
- The party's photo path stays `party/{id}/photo/{uuid}.{ext}`; the previous object is not deleted.
- Never serve or modify `src/main/html/`.
- No visual change on the profile photo page.

## Review Focus

- **Another party's id** on the multipart upload, presign or attach → 403 `HTTP_ACCESS_DENIED`, and nothing written. Task 1 (multipart) and Task 2 (presign / attach) tests.
- **The attach payload is wrong** — path of another party, missing object, over 5 MB, not an image — → 400 `HTTP_INVALID_PARAMETER`, the stored object deleted, `photoUrl` unchanged. Task 2 tests.
- **The direct path can't run** (local storage `{url:null}`, failed PUT, missing `direct-upload.js`) → the untouched multipart post still uploads the photo. Task 5 keeps the fallback; Task 1's controller tests cover the multipart path it falls back to.
- **A successful direct attach ends elsewhere** — the browser never posts the form — so the agent must land on `/` with the saved message. Task 4's `GET /profile/photo/saved` test.
- **An upload with no new photo** still keeps the current one and shows no message. Task 1 keeps the multipart controller's behaviour and its existing test.

---

### Task 1: Party ownership on the multipart upload

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/PartyAccessService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/PartyService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/PartyServiceTest.kt`
- Modify: `modules/ndopify-server/src/test/resources/db/test/party/UpdatePartyPhotoEndpoint.sql`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/UpdatePartyPhotoEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (party domain bullet)

**Interfaces:**
- Produces: `PartyAccessService.ensureIsParty(party: PartyEntity)` — 401 `AUTH_UNAUTHORIZED` without a token, 403 `HTTP_ACCESS_DENIED` when the caller's party (JWT user → membership → party, `CallerService.findPartyIdOrNull`) isn't `party`. No admin bypass.
- Produces: `PartyService(dao, accessService, storageProvider, clock)` — `accessService` is new, after `dao`.

- [ ] **Step 1: Extend `PartyServiceTest` with the ownership case**

Make the storage mocks fields (`storageProvider`, `storage`), add `accessService = mock<PartyAccessService>()`, and build the service with `PartyService(dao, accessService, storageProvider, clock)`.

```kotlin
@Test
fun `upload photo - another party's photo`() {
    doReturn(party).whenever(dao).findById(1L)
    doThrow(ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED)))
        .whenever(accessService).ensureIsParty(party)

    assertFailsWith<ForbiddenException> { service.uploadPhoto(1L, mock()) }

    verify(accessService).ensureIsParty(party)
    verify(storage, never()).store(any(), any(), any())
    verify(dao, never()).save(any())
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd modules/ndopify-server
mvn test -Dtest=PartyServiceTest
```

Expected: FAIL — `PartyAccessService` unresolved / `PartyService` constructor arity.

- [ ] **Step 3: Implement `PartyAccessService` and wire it into `uploadPhoto`**

```kotlin
/** Who may change a party: only the caller whose party it is. */
@Service
class PartyAccessService(private val callerService: CallerService) {
    /** 401 AUTH_UNAUTHORIZED without a token, 403 HTTP_ACCESS_DENIED when the caller's party isn't [party]. */
    fun ensureIsParty(party: PartyEntity) {
        val principal = callerService.getPrincipal()
        if (callerService.findPartyIdOrNull(principal) != party.id) {
            throw ForbiddenException(error = Error(code = ErrorCode.HTTP_ACCESS_DENIED))
        }
    }
}
```

In `PartyService.uploadPhoto`, after `findById(id)` and before `ensurePhoto(file)`, call `accessService.ensureIsParty(party)`. Update the KDoc's error order to: 404 `PARTY_NOT_FOUND`, 403 `HTTP_ACCESS_DENIED` but for the party itself, then 400.

- [ ] **Step 4: Run the service test to verify it passes**

```bash
mvn test -Dtest=PartyServiceTest
```

Expected: PASS.

- [ ] **Step 5: Fixture the caller's membership and extend the endpoint test**

In `UpdatePartyPhotoEndpoint.sql`, add a second party, then the users/memberships the token uses (the integration token is user `11`):

```sql
INSERT INTO T_PARTY(id, email, first_name, last_name, created_at, modified_at) VALUES
    (101, 'john.smith@gmail.com', 'John', 'Smith', '2024-06-10 00:00:00', '2024-06-10 00:00:00');

INSERT INTO T_USER (id, email, deleted, created_at) VALUES
    (11, 'ray.sponsible@gmail.com', false, '2024-05-20 00:00:00'),
    (12, 'john.smith@gmail.com', false, '2024-05-20 00:00:00');
INSERT INTO T_MEMBERSHIP (id, tenant_id, user_id, party_id, deleted) VALUES
    (11, 1, 11, 100, false),
    (12, 1, 12, 101, false);
```

Add to `UpdatePartyPhotoEndpointTest`:

```kotlin
@Test
fun `photo - another party's photo`() {
    val response = rest.postForEntity(
        "/v1/parties/101/photo",
        request("me.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
        ErrorResponse::class.java,
    )

    assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
    assertEquals(ErrorCode.HTTP_ACCESS_DENIED, response.body?.error?.code)
    assertNull(dao.findById(101L).get().photoUrl)
    verify(storage, never()).store(any(), any(), any())
}

@Test
fun `photo - anonymous`() {
    anonymousUser = true

    val response = rest.postForEntity(
        "/v1/parties/100/photo",
        request("me.jpg", MediaType.IMAGE_JPEG, byteArrayOf(1, 2, 3)),
        ErrorResponse::class.java,
    )

    assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    assertEquals(ErrorCode.AUTH_UNAUTHORIZED, response.body?.error?.code)
}
```

- [ ] **Step 6: Run the endpoint tests to verify they pass**

Requires local MySQL.

```bash
mvn test -Dtest='UpdatePartyPhotoEndpointTest,PartyServiceTest'
```

Expected: PASS (the existing `party not found` / `different tenant` tests still pass).

- [ ] **Step 7: Update `modules/ndopify-server/CLAUDE.md`**

In the **party** domain bullet, state that the profile photo's upload (multipart or direct) is only for the party itself (`PartyAccessService`, 403 otherwise), no admin bypass.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party \
  modules/ndopify-server/src/test/resources/db/test/party \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(party): only the party itself changes its profile photo"
```

---

### Task 2: Presign and attach the profile photo on ndopify-server

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/service/PartyService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party/server/endpoint/PartyEndpoints.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/server/service/PartyServiceTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/PresignPartyPhotoEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party/endpoint/AttachPartyPhotoEndpointTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (party domain bullet)

**Interfaces:**
- Consumes: Task 1 `PartyAccessService.ensureIsParty`; #127 `DirectUploadService.presign(prefix, contentType)` / `verify(path, prefix)`; `common.dto` `PresignUploadRequest` / `PresignUploadResponse` / `AttachUploadRequest`.
- Produces:
  - `PartyService.prefix(partyId: Long): String` = `"party/$partyId/photo"`.
  - `PartyService.presign(id: Long, contentType: String?): PresignUploadResponse`.
  - `PartyService.attach(id: Long, path: String): PartyEntity`.
  - `POST /v1/parties/{id}/photo/presign` (`PresignUploadRequest`) → `PresignUploadResponse`.
  - `POST /v1/parties/{id}/photo/attach` (`AttachUploadRequest`) → empty 200.
- `PartyService` gains `directUpload: DirectUploadService` between `storageProvider` and `clock`.

- [ ] **Step 1: Write the failing service tests**

Add to `PartyServiceTest` (a `directUpload = mock<DirectUploadService>()` field; service built with `PartyService(dao, accessService, storageProvider, directUpload, clock)`):

```kotlin
@Test
fun `presign - under the party's prefix`() {
    val party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray@example.com")
    doReturn(party).whenever(dao).findById(100L)
    val expected = PresignUploadResponse("https://s3.example.com/put", "party/100/photo/x.jpg")
    doReturn(expected).whenever(directUpload).presign("party/100/photo", "image/jpeg")

    assertEquals(expected, service.presign(100L, "image/jpeg"))
    verify(accessService).ensureIsParty(party)
}

@Test
fun `attach - records the new photo url`() {
    val party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray@example.com")
    val path = "party/100/photo/0f8fad5b-d9cb-469f-a165-70867728950e.png"
    doReturn(party).whenever(dao).findById(100L)
    doReturn("image/png").whenever(directUpload).verify(path, "party/100/photo")
    doReturn(storage).whenever(storageProvider).get()
    doReturn(URL("https://cdn.example.com/$path")).whenever(storage).publicUrl(path)
    doAnswer { it.arguments[0] }.whenever(dao).save(any())

    val attached = service.attach(100L, path)

    assertEquals("https://cdn.example.com/$path", attached.photoUrl)
    verify(accessService).ensureIsParty(party)
}

@Test
fun `attach - rejected upload leaves the photo unchanged`() {
    val party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible", email = "ray@example.com")
    doReturn(party).whenever(dao).findById(100L)
    doThrow(BadRequestException(error = Error(ErrorCode.HTTP_INVALID_PARAMETER)))
        .whenever(directUpload).verify(any(), any())

    assertFailsWith<BadRequestException> { service.attach(100L, "party/100/photo/x.png") }
    verify(dao, never()).save(any())
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
mvn test -Dtest=PartyServiceTest
```

Expected: FAIL — `presign` / `attach` / `prefix` unresolved, constructor arity.

- [ ] **Step 3: Implement the service methods and endpoints**

`PartyService` — add `directUpload` to the constructor, put `prefix` in the companion, and reuse it in `uploadPhoto`:

```kotlin
companion object {
    val PHOTO_MIME_TYPES = listOf("image/jpeg", "image/png", "image/webp")

    /** The folder of a party's profile photo: the multipart upload and the direct upload share it. */
    fun prefix(partyId: Long) = "party/$partyId/photo"
}

/**
 * A short-lived URL to upload the profile photo straight to the storage, for the party itself. 404 PARTY_NOT_FOUND,
 * 403 HTTP_ACCESS_DENIED but for the party itself, then 400 HTTP_INVALID_PARAMETER (by [DirectUploadService.presign])
 * for a type that isn't JPEG, PNG or WEBP. No URL when the storage can't presign (local): the caller falls back to
 * [uploadPhoto].
 */
fun presign(id: Long, contentType: String?): PresignUploadResponse {
    val party = findById(id)
    accessService.ensureIsParty(party)
    return directUpload.presign(prefix(party.id!!), contentType)
}

/**
 * Attaches the photo uploaded straight to the storage, once [DirectUploadService.verify] has checked it. 404
 * PARTY_NOT_FOUND, 403 HTTP_ACCESS_DENIED but for the party itself, then the upload's 400s. The previous object stays
 * in the storage: e-mails already sent keep showing it.
 */
@Transactional
fun attach(id: Long, path: String): PartyEntity {
    val party = findById(id)
    accessService.ensureIsParty(party)
    directUpload.verify(path, prefix(party.id!!))

    val storage = storageProvider.get()
    val now = Date(clock.millis())
    return dao.save(party.copy(photoUrl = storage.publicUrl(path).toString(), modifiedAt = now))
}
```

`PartyEndpoints`:

```kotlin
/** A short-lived URL to upload the party's profile photo straight to the storage; no url with local storage. */
@PostMapping("/{id}/photo/presign")
fun presignPhoto(@PathVariable id: Long, @RequestBody request: PresignUploadRequest): PresignUploadResponse =
    service.presign(id, request.contentType)

/** Attaches the profile photo uploaded straight to the storage, at the path `/photo/presign` returned. */
@PostMapping("/{id}/photo/attach")
fun attachPhoto(@PathVariable id: Long, @RequestBody request: AttachUploadRequest) {
    service.attach(id, request.path)
}
```

- [ ] **Step 4: Write the failing presign endpoint test**

`PresignPartyPhotoEndpointTest` (mirror `PresignListingImageEndpointTest`: `@Sql("/db/test/clean.sql", "/db/test/party/UpdatePartyPhotoEndpoint.sql")`, `@MockitoBean storageProvider`, `storage = mock()`; the real `DirectUploadService` runs over the mocked storage — it needs no `roles` override, the party membership is what grants access):

```kotlin
@Test
fun presign() {
    doReturn(URL("https://s3.example.com/put")).whenever(storage).presignedUploadUrl(any(), any(), any())

    val response = rest.postForEntity(
        "/v1/parties/100/photo/presign",
        PresignUploadRequest("image/jpeg"),
        PresignUploadResponse::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("https://s3.example.com/put", response.body?.url)
    assertTrue(response.body!!.path!!.matches(Regex("party/100/photo/[0-9a-f-]{36}\\.jpg")), response.body?.path)
}
```

Also write: `presign - local` (null URL → 200 with null `url`/`path`), `presign - not an image` (`application/pdf` → 400 `HTTP_INVALID_PARAMETER`, storage never), `presign - another party` (`/v1/parties/101/photo/presign` → 403 `HTTP_ACCESS_DENIED`, storage never), `presign - anonymous` (401 `AUTH_UNAUTHORIZED`), `presign - unknown party` (`/v1/parties/999/photo/presign` → 404 `PARTY_NOT_FOUND`).

- [ ] **Step 5: Write the failing attach endpoint test**

`AttachPartyPhotoEndpointTest` (same setup, plus `whenever(storage.publicUrl(any())).thenAnswer { URL("https://cdn.example.com/${it.arguments[0]}") }`; `@Autowired PartyRepository dao`; a `png(w, h)` helper writing a 1×1 `BufferedImage` through `ImageIO`):

```kotlin
@Test
fun attach() {
    doReturn(StoredObject("image/png", 3)).whenever(storage).metadata(path)
    doReturn(png(1, 1)).whenever(storage).readPrefix(eq(path), any())

    val response = rest.postForEntity(
        "/v1/parties/100/photo/attach",
        AttachUploadRequest(path),
        Void::class.java,
    )

    assertEquals(HttpStatus.OK, response.statusCode)
    assertEquals("https://cdn.example.com/$path", dao.findById(100L).get().photoUrl)
}
```

with `path = "party/100/photo/0f8fad5b-d9cb-469f-a165-70867728950e.png"`. Also write: `attach - another party's path` (`party/101/photo/<uuid>.png` → 400, `photoUrl` null), `attach - missing object` (metadata null → 400, unchanged), `attach - not an image` (`readPrefix` returns `"nope"` → 400, `verify(storage).delete(path)`, unchanged), `attach - another party` (party 101 → 403, unchanged), `attach - anonymous` (401).

- [ ] **Step 6: Run the endpoint tests to verify they pass**

Requires local MySQL.

```bash
mvn test -Dtest='PresignPartyPhotoEndpointTest,AttachPartyPhotoEndpointTest,PartyServiceTest,UpdatePartyPhotoEndpointTest'
```

Expected: PASS.

- [ ] **Step 7: Update `modules/ndopify-server/CLAUDE.md`**

In the **party** domain bullet, after the KYC sentence, add: the profile photo goes direct (`POST /v1/parties/{id}/photo/presign` then `/attach` through `DirectUploadService`), its check order (404, 403, then the upload's 400s), S3-only with `{url:null}` on local, the previous object kept, and the multipart endpoint staying as the fallback.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/party \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/party \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(party): presign and attach profile photos uploaded directly to the storage"
```

---

### Task 3: Partner Central client calls

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdPartyClient.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdPartyClientTest.kt`

**Interfaces:**
- Consumes: Task 2 endpoints and the `common.dto` presign/attach DTOs.
- Produces: `NdPartyClient.presignPhoto(partyId: Long, contentType: String): PresignUploadResponse`, `NdPartyClient.attachPhoto(partyId: Long, path: String)`.

- [ ] **Step 1: Write the failing client tests**

```kotlin
@Test
fun presignPhoto() {
    server.expect(requestTo("http://localhost:8080/v1/parties/100/photo/presign"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"contentType":"image/jpeg"}"""))
        .andRespond(
            withSuccess(
                """{"url":"https://s3.example.com/put","path":"party/100/photo/a.jpg"}""",
                MediaType.APPLICATION_JSON,
            )
        )

    val response = client.presignPhoto(100, "image/jpeg")
    assertEquals("https://s3.example.com/put", response.url)
    assertEquals("party/100/photo/a.jpg", response.path)
    server.verify()
}

@Test
fun attachPhoto() {
    server.expect(requestTo("http://localhost:8080/v1/parties/100/photo/attach"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().json("""{"path":"party/100/photo/a.jpg"}"""))
        .andRespond(withSuccess())

    client.attachPhoto(100, "party/100/photo/a.jpg")
    server.verify()
}
```

- [ ] **Step 2: Run them to verify they fail**

```bash
cd modules/ndopify-partner-central
mvn test -Dtest=NdPartyClientTest
```

Expected: FAIL — unresolved `presignPhoto` / `attachPhoto`.

- [ ] **Step 3: Implement both methods**

Mirror `NdListingClient.presignImage` / `attachImage`; `attachPhoto` uses `.toBodilessEntity()` (the endpoint answers an empty 200).

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=NdPartyClientTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdPartyClient.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdPartyClientTest.kt
git commit -m "feat(npc): add the presign and attach profile photo calls"
```

---

### Task 4: Partner Central proxies and the saved route

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileController.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileControllerTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (profile pages paragraph)

**Interfaces:**
- Consumes: Task 3 `NdPartyClient.presignPhoto` / `attachPhoto`.
- Produces (the contract `direct-upload.js` expects):
  - `POST /profile/photo/presign` — JSON `PresignUploadRequest` in, `PresignUploadResponse` out (200), ndopify-server's status (without a body) otherwise.
  - `POST /profile/photo/attach` — JSON `AttachUploadRequest` in, empty 200 out, ndopify-server's status and `ErrorResponse` body otherwise.
  - `GET /profile/photo/saved` — redirect to `/` with `ProfileRules.SAVED` = `ProfileRules.SAVED_PHOTO`.
- Both proxies take the party from `CurrentAgent`, never from the request; the body carries only the content type or the path.

- [ ] **Step 1: Write the failing controller tests**

Add to `ProfileControllerTest` (standalone MockMvc already set up; `partyClient` is a mock):

```kotlin
@Test
fun `presign - returns the upload url`() {
    doReturn(PresignUploadResponse("https://s3.example.com/put", "party/100/photo/a.jpg"))
        .whenever(partyClient).presignPhoto(100L, "image/jpeg")

    mvc.perform(
        post("/profile/photo/presign")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"contentType":"image/jpeg"}""")
    ).andExpect(status().isOk)
        .andExpect(content().json("""{"url":"https://s3.example.com/put","path":"party/100/photo/a.jpg"}"""))
}

@Test
fun `attach - attaches the path for the signed-in party`() {
    mvc.perform(
        post("/profile/photo/attach")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"path":"party/100/photo/a.jpg"}""")
    ).andExpect(status().isOk)

    verify(partyClient).attachPhoto(100L, "party/100/photo/a.jpg")
}

@Test
fun `attach - rejected keeps the server error body`() {
    val ex = HttpClientErrorException.create(
        HttpStatus.BAD_REQUEST,
        "Bad Request",
        HttpHeaders.EMPTY,
        """{"error":{"code":"http:invalid-parameter"}}""".toByteArray(),
        Charsets.UTF_8,
    )
    doThrow(ex).whenever(partyClient).attachPhoto(eq(100L), any())

    val result = mvc.perform(
        post("/profile/photo/attach")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""{"path":"party/100/photo/a.jpg"}""")
    ).andReturn()

    assertEquals(HttpStatus.BAD_REQUEST.value(), result.response.status)
    assertTrue(result.response.contentAsString.contains("http:invalid-parameter"))
}

@Test
fun `saved - back to the Espace agent with the message`() {
    val result = mvc.perform(get("/profile/photo/saved")).andReturn()

    assertEquals("redirect:/", result.modelAndView?.viewName)
    assertEquals("photo", result.flashMap[ProfileRules.SAVED])
}
```

Also write: `presign - refused keeps the status` (403 from the client → 403, no body), `presign - not an agent` (no `CurrentAgent` → `redirect:/login`), `saved - not an agent` (`redirect:/login`).

- [ ] **Step 2: Run them to verify they fail**

```bash
mvn test -Dtest=ProfileControllerTest
```

Expected: FAIL — 404 on `/profile/photo/presign`, `/attach`, `/saved`.

- [ ] **Step 3: Implement the three handlers**

In `ProfileController`, next to `upload`:

```kotlin
@PostMapping("/photo/presign", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun presignPhoto(@RequestBody request: PresignUploadRequest): ResponseEntity<PresignUploadResponse> {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    return try {
        ResponseEntity.ok(partyClient.presignPhoto(agent.party.id, request.contentType.orEmpty()))
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("Presign of party ${agent.party.id} refused: ${ex.statusCode}")
        ResponseEntity.status(ex.statusCode).build()
    }
}

@PostMapping("/photo/attach", produces = [MediaType.APPLICATION_JSON_VALUE])
@ResponseBody
fun attachPhoto(@RequestBody request: AttachUploadRequest): ResponseEntity<*> {
    val agent = currentAgent.get() ?: throw SignInRequiredException()
    return try {
        partyClient.attachPhoto(agent.party.id, request.path)
        ResponseEntity.ok().build<Void>()
    } catch (ex: HttpClientErrorException) {
        LOGGER.warn("Attach of party ${agent.party.id} refused: ${ex.statusCode}")
        ResponseEntity.status(ex.statusCode)
            .contentType(MediaType.APPLICATION_JSON)
            .body<String>(ex.responseBodyAsString)
    }
}

/** Where the direct upload ends: the browser never posts the form, so this hands back the usual saved outcome. */
@GetMapping("/photo/saved")
fun saved(redirect: RedirectAttributes): String {
    currentAgent.get() ?: throw SignInRequiredException()
    redirect.addFlashAttribute(ProfileRules.SAVED, ProfileRules.SAVED_PHOTO)
    return ProfileRules.HOME
}
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn test -Dtest=ProfileControllerTest
```

Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-partner-central/CLAUDE.md`**

In the profile pages paragraph, add the direct path: `profile/photo.html` loads `direct-upload.js`, `kyc.js` sends the shrunk photo straight to the storage and comes back through `GET /profile/photo/saved`, and the multipart `POST /profile/photo` stays the no-JS / local / failure path.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/profile \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): proxy the direct profile photo upload to the browser"
```

---

### Task 5: `direct-upload.js` on the profile photo page

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/profile/photo.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (JS assets list / profile paragraph)

**Interfaces:**
- Consumes: Task 4 routes, derived from the form's `action` (`/profile/photo` → `/profile/photo/presign`, `/attach`, `/saved`); #127 `window.NdDirectUpload.upload(action, file)` → `Promise<boolean>`.
- Produces: no new JS API; the profile photo page's submit handler is the only change.

- [ ] **Step 1: Load the shared script on the page**

In `templates/profile/photo.html`, before the `kyc.js` tag:

```html
<script th:src="@{/assets/js/direct-upload.js}" defer></script>
```

Both are deferred, so they run in order.

- [ ] **Step 2: Write the failing template assertion**

In `ProfileTemplateTest.photo` (or its own test):

```kotlin
assertEquals("/assets/js/direct-upload.js", page.selectFirst("script[src$=direct-upload.js]")?.attr("src"))
```

- [ ] **Step 3: Run it to verify it fails**

```bash
mvn test -Dtest=ProfileTemplateTest
```

Expected: FAIL — no such `<script>` yet.

- [ ] **Step 4: Try the direct path on submit, only on the profile photo page**

In `kyc.js`, add (near `initSubmit`):

```js
// The profile photo page uploads straight to the storage when it can (direct-upload.js). "Enregistrer" still starts
// it, so the agent can change their mind before; any failure posts the form as usual.
function initDirectUpload(form) {
    if (!window.NdDirectUpload) return;
    var action = form.getAttribute("action");
    form.addEventListener("submit", function (event) {
        var input = $(".upload__input", form);
        var file = input && input.files && input.files[0];
        if (!file) return; // no new photo: the form post keeps the current one
        event.preventDefault();
        window.NdDirectUpload.upload(action, file).then(function (uploaded) {
            if (uploaded) {
                location.assign(action + "/saved");
            } else {
                // Local storage or any failure: the multipart post, the no-JS path
                form.submit();
            }
        });
    });
}
```

In `DOMContentLoaded`, after `initSubmit(form)`:

```js
if (page === "profile-photo") initDirectUpload(form);
```

Do **not** call it on the KYC wizard (`page === "kyc"`): #205 owns that path.

- [ ] **Step 5: Run the template test to verify it passes**

Requires local MySQL? No — partner-central tests need neither MySQL nor a server.

```bash
mvn test -Dtest=ProfileTemplateTest
```

Expected: PASS.

- [ ] **Step 6: Check the fallbacks by inspection**

No JS test runner exists in this repo. Confirm: without `window.NdDirectUpload` nothing is intercepted (form posts); `upload()` resolving `false` calls `form.submit()` with the file still in the input; no file posts the form unchanged; local storage returns `{url:null}` from presign → `false` → fallback. The multipart path itself is covered by `ProfileControllerTest` / `UpdatePartyPhotoEndpointTest`.

- [ ] **Step 7: Update `modules/ndopify-partner-central/CLAUDE.md`**

Describe `kyc.js`'s profile-photo submit handler: it tries `direct-upload.js` (presign → PUT → attach) and falls back to the form post on any failure or without JS; the KYC wizard is unchanged.

- [ ] **Step 8: Run `mvn verify` in both modules**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: GREEN.

```bash
cd modules/ndopify-server && mvn verify
```

Expected: GREEN (needs local MySQL).

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/profile/photo.html \
  modules/ndopify-partner-central/src/main/resources/public/assets/js/kyc.js \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/profile/ProfileTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): upload the profile photo straight to the storage when possible"
```

---

## Verification

- `mvn verify` in `modules/ndopify-server` (needs local MySQL) and `modules/ndopify-partner-central`.
- The two module `CLAUDE.md` files describe the direct upload, the ownership rule and the fallback.
- No `ndopify-dto` / `ndopify-web-core` change; the root `pom.xml` versions are untouched.

## Out of scope (do not build)

- Direct upload for KYC images (#205), listing photos (#127, already done), contracts or mandates.
- Deleting the previous photo object, S3 lifecycle rules, orphan cleanup.
- An admin-console bypass on the party photo.
- Any visual change to the profile photo page, or changes to the 5 MB limit.
- Changes to the KYC wizard's upload (`page === "kyc"` stays on the form post).
