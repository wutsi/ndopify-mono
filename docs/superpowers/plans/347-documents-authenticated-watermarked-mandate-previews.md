# Authenticated, watermarked mandate document previews — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A mandate document page is never read through a storage URL from a browser: Partner Central serves an
authenticated, resized, watermarked JPEG preview, generated and stored by ndopify-server, while the original stays
private and byte-for-byte unchanged.

**Architecture:** ndopify-server gains a reusable "display copy" image component (`platform/image`) and a mandate
preview component (`mandate/server/service`) that derives `<page.path>.preview-v1.jpg` in the page's own storage,
generates it at write time and lazily for legacy pages under a per-path in-process lock, and exposes
`GET /v1/mandates/{id}/documents/{documentId}/pages/{pageId}/preview`. Partner Central exposes its own session-protected
route, calls the server with the agent's bearer token (web-core's REST interceptor), and streams the bytes. The page
DTO loses `url`; the template builds the Partner Central route from the ids.

**Tech Stack:** Kotlin, Spring Boot, Spring `RestClient`, Thymeleaf, TwelveMonkeys `imageio-jpeg` / `imageio-webp`,
`metadata-extractor` (EXIF orientation), ImageIO + AWT, the bundled DejaVu Sans font, JUnit 5 / `kotlin.test` /
mockito-kotlin, `MockMvc` + real Thymeleaf templates, `TestRestTemplate` against MySQL.

**Spec:** GitHub issue #347 (enriched body: *Decision*, *Scope*, *Acceptance criteria*, *Decisions and assumptions*);
design record `docs/superpowers/specs/2026-11-26-mandate-document-preview-design.md`.

## Global Constraints

- `UI: no` — the *Voir* link keeps its label and still opens a new tab; no layout, wording or prototype change.
  `src/main/html/` is untouched.
- `Shared code: ndopify-dto` — removing `MandateDocumentPage.url` is a breaking DTO change, released before
  `ndopify-server` / `ndopify-partner-central` deploy (`modules/ndopify-dto/CLAUDE.md`, *Versioning and Release*).
- No storage URL, presigned or public, and no `path` ever reaches a browser or a JSON document read.
- The preview is always JPEG: longest side 2000 px (never upscale), quality 0.82, opaque white background, EXIF
  orientation applied, tiled diagonal watermark `<tenant name> - Mandat n° <mandateId>`.
- A hard pixel cap (40 MP) is checked from the image header **before** any full decode. Failure is fail-closed:
  `400 HTTP_INVALID_PARAMETER` at write, 5xx at read, never the original bytes.
- Server endpoint errors, in this order: 404 `MANDATE_NOT_FOUND` → 403 `MANDATE_ACCESS_DENIED` → 404
  `MANDATE_DOCUMENT_NOT_FOUND` → 404 `MANDATE_DOCUMENT_PAGE_NOT_FOUND`; CLOSED / CANCELLED mandates stay readable.
- Partner Central preview response: `Content-Type: image/jpeg`, `Content-Disposition: inline;
  filename="mandat-<id>-page-<n>.jpg"`, `Cache-Control: private, max-age=300`, `X-Content-Type-Options: nosniff`;
  401 signs out, 403/404 keep their status, server 5xx or unreachable → 502.
- Preview path is deterministic: `<page.path>.preview-v1.jpg`, in the page's storage (default storage for legacy
  `UNKNOWN`). **No DB column, no migration, no backfill job.**
- No new log line names a page path or a storage URL: the preview failure log and the error message name the
  mandate id and the exception class only; the touched delete warning drops the existing path.
- French for user-facing text; English for code, comments, logs and docs.
- Tests: `kotlin.test` assertions, mockito-kotlin mocks, one endpoint test class per operation with its own SQL
  fixture under `db/test/mandate/`, template tests with a real Thymeleaf engine. `mvn verify` green (ktlint, tests,
  JaCoCo gate); ndopify-server needs a local MySQL.

## Review Focus

1. **A page created before this story** (or whose preview object is missing): the first view generates and stores
   `<path>.preview-v1.jpg`, the next view reads it without regenerating, and a local storage reader never sees a
   half-written file — Task 3.
2. **A corrupt, undecodable or over-cap legacy page**: the endpoint answers 5xx, logs no path/bytes, and never
   returns the original — Tasks 2 and 5.
3. **A copied preview URL opened in a private window / another browser**: sign-in redirect, zero image bytes —
   Task 6.
4. **A replaced or deleted page/document**: the old preview is deleted too; a replaced page's new preview reflects
   the new file; no stale preview is served — Task 4.
5. **A phone photo with EXIF orientation, a WEBP, a transparent PNG and a CMYK JPEG**: upright, white where
   transparent, JPEG, inside the cap, watermark visible on both white paper and dark photos — Task 2.

---

### Task 1: Remove `url` from the page DTO (`ndopify-dto`)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/MandateDocumentPage.kt`
- Modify: root `pom.xml` `<ndopify-dto.version>`

**Interfaces — Produces:**
- `data class MandateDocumentPage(val id: Long = -1, val index: Int = 0, val contentType: String? = null,
  val createdAt: Date = Date(), val modifiedAt: Date = Date())` — KDoc: the page's file is private; the caller builds
  its authenticated preview route from the mandate/document/page ids. No `url`, no `path`.

- [ ] **Step 1:** Remove `url` and rewrite the KDoc of `MandateDocumentPage` (it currently documents the presigned
  URL). No other DTO changes.
- [ ] **Step 2:** Set `<ndopify-dto.version>` in the root `pom.xml` to `0.0.108-SNAPSHOT` while developing.
- [ ] **Step 3:** Run `cd modules/ndopify-dto && mvn install` — Expected: BUILD SUCCESS (ktlint clean; the DTO has
  no tests).
- [ ] **Step 4:** Commit `feat(dto): drop the presigned url from a mandate document page` with `Refs #347`.
- [ ] **Step 5:** Release the DTO (`ndopify-dto-main.yml`) before any module that consumes the new contract can be
  built in CI; then set `<ndopify-dto.version>` to the released `0.0.108` in a "fix dependencies" commit. Do not
  deploy `ndopify-server` or Partner Central against the old `0.0.107`.

Partner Central's existing document tests still construct `MandateDocumentPage(url = …)`: they do not compile until
Tasks 6–7 are done, so build the modules in the task order (DTO → server → Partner Central), not all at once.

### Task 2: Reusable display-copy image component (`ndopify-server`)

**Files:**
- Modify: root `pom.xml` properties — `<twelvemonkeys.version>3.12.0</twelvemonkeys.version>`,
  `<metadata-extractor.version>2.19.0</metadata-extractor.version>`
- Modify: `modules/ndopify-server/pom.xml` dependencies —
  `com.twelvemonkeys.imageio:imageio-jpeg:${twelvemonkeys.version}`,
  `com.twelvemonkeys.imageio:imageio-webp:${twelvemonkeys.version}`,
  `com.drewnoakes:metadata-extractor:${metadata-extractor.version}`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/ImagePreviewService.kt`,
  `.../platform/image/UnprocessableImageException.kt`
- Create fixtures: `modules/ndopify-server/src/test/resources/files/preview/exif-orientation-6.jpg`,
  `.../preview/cmyk.jpg`, `.../preview/sample.webp`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/ImagePreviewServiceTest.kt`

**Interfaces — Produces:**
- `class UnprocessableImageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)`
- `@Service class ImagePreviewService { fun preview(original: ByteArray, mimeType: String?, watermark: String):
  ByteArray }` with `companion object { const val MAX_SIDE = 2000; const val QUALITY = 0.82f;
  const val MAX_PIXELS = 40_000_000 }`.
- Reads the format with `ImageIO.getImageReaders(ImageIO.createImageInputStream(...))`; rejects when
  `width * height > MAX_PIXELS` (throws `UnprocessableImageException`, no full decode); decodes with ImageIO (JDK
  JPEG/PNG readers plus TwelveMonkeys); applies EXIF orientation; scales down so the longest side is `MAX_SIDE` and
  never upscales; draws onto a white `TYPE_INT_RGB`; tiles the watermark diagonally; writes JPEG at `QUALITY`.
- Watermark font: `contract/fonts/DejaVuSans.ttf` (already in server resources, used by the signed contract), loaded
  once and derived to a size relative to the image. Semi-transparent mid-grey (`#808080`, alpha ≈ 0.40), rotated
  ≈ −30°, tiled across the whole image.
- Uses `mimeType` only as a hint; the reader is chosen from the bytes, so a legacy page with no MIME type still
  works. Any decode/metadata failure becomes `UnprocessableImageException`; nothing partial is returned.

- [ ] **Step 1: Write the failing test** `ImagePreviewServiceTest`:
  - a 3000×1000 JPEG → output starts with `FF D8`, decodes as 2000×667;
  - a 100×50 PNG → stays 100×50 (never upscaled), output JPEG;
  - a transparent PNG → sampled transparent pixels are near-white (all channels ≥ 250);
  - `sample.webp` → a JPEG is returned;
  - `cmyk.jpg` → a JPEG is returned (no exception);
  - `exif-orientation-6.jpg` (40×20 landscape pixels, orientation 6) → decoded preview is 20×40;
  - a solid-white 400×400 JPEG with watermark `Ndopify - Mandat n° 301` → at least one sampled pixel differs from
    white (watermark drawn);
  - a solid-black 400×400 JPEG → at least one sampled pixel differs from black (the watermark is readable on a dark
    photo too);
  - the output of a JPEG carrying EXIF/GPS carries neither (metadata-extractor finds no `ExifIFD0` / `Gps` directory
    in the re-encoded bytes);
  - a PNG whose IHDR declares 10000×10000 (a tiny header-only byte array with a valid IHDR CRC, built in the test)
    → `UnprocessableImageException`, before decode;
  - random bytes → `UnprocessableImageException`.
- [ ] **Step 2:** Run `cd modules/ndopify-server && mvn test -Dtest=ImagePreviewServiceTest` — Expected: FAIL
  (class missing / dependencies missing).
- [ ] **Step 3:** Add the dependency versions and dependencies, `UnprocessableImageException`, and
  `ImagePreviewService` as specified. Generate the three binary fixtures with ImageMagick
  (`magick -size 40x20 xc:red -orient RightTop exif-orientation-6.jpg`, etc.) and verify their dimensions/orientation
  in the test, not by eye.
- [ ] **Step 4:** Run the test — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): add the watermarked display-copy image component` + `Refs #347`.

### Task 3: Preview storage, lazy generation and lock (`ndopify-server`)

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentPreviewService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentPreviewServiceTest.kt`

**Interfaces:**
- Consumes: Task 2's `ImagePreviewService.preview(original, mimeType, watermark)` /
  `UnprocessableImageException`; `StorageServiceProvider`; `TenantService.findById(id)`; the server's
  `TenantContext.get()`; `StorageService.get/store/metadata/delete`.
- Produces:
  - `MandateDocumentPreviewService` companion: `const val PREVIEW_SUFFIX = ".preview-v1.jpg"`;
    `fun previewPath(path: String): String = path + PREVIEW_SUFFIX`.
  - `fun generate(mandateId: Long, storage: StorageService, path: String, mimeType: String?,
    original: ByteArray? = null): ByteArray` — reads the stored original when `original` is null (full
    `ByteArrayOutputStream` + `storage.get`), composes `<tenant name> - Mandat n° <mandateId>`, calls
    `imagePreview.preview`, stores the result at `previewPath(path)` with content type `image/jpeg`, returns it.
    Propagates `UnprocessableImageException`; storage failures stay runtime exceptions.
  - `fun read(mandateId: Long, path: String, storageType: StorageType, mimeType: String?): ByteArray` — resolves
    the storage (default when `UNKNOWN`), and under the per-path lock (whole check/read/generate sequence, so a
    local reader never sees a half-written file) returns the stored preview when `metadata(previewPath)` exists,
    else generates once and stores it. A generation/storage failure is caught here, logged with the mandate id and
    the exception class only (never the message/path), and rethrown as
    `InternalErrorException(Error(ErrorCode.HTTP_INTERNAL))` **without** the original cause, so no path reaches the
    request log through a stack trace.
  - `fun deleteQuietly(mandateId: Long, path: String, storageType: StorageType)` — deletes `previewPath(path)`,
    logging a warning naming the mandate id only (never the path) when it fails.
  - Per-path lock: a fixed array of lock objects indexed by `path.hashCode()` (striped, no unbounded map).

- [ ] **Step 1: Write the failing unit test** with mocked `ImagePreviewService`, `StorageServiceProvider`,
  `StorageService` and `TenantService` (the tenant name `Ndopify`; `TenantContext.set(1)` in `@BeforeEach`,
  `remove()` in `@AfterEach`):
  - `generate` without `original` reads `originalPath` and stores `originalPath.preview-v1.jpg` with
    `image/jpeg`; the watermark argument is `Ndopify - Mandat n° 301`; the returned bytes are the image service's;
  - `generate` with explicit `original` does not call `storage.get`;
  - `read` with `metadata(previewPath)` non-null reads the preview and never calls `imagePreview.preview`;
  - `read` with a missing preview generates, stores and returns it; the next `read` (metadata now non-null) reads it;
  - `read` of a page with `StorageType.UNKNOWN` uses `storageProvider.get()` (default storage);
  - two concurrent `read`s of the same missing preview call `imagePreview.preview` once (a `CountDownLatch` in the
    mocked image service, two threads, one generation count);
  - `deleteQuietly` calls `storage.delete(previewPath)`; a throwing delete is swallowed (no exception out);
  - `generate` propagates `UnprocessableImageException`.
- [ ] **Step 2:** Run `mvn test -Dtest=MandateDocumentPreviewServiceTest` — Expected: FAIL.
- [ ] **Step 3:** Implement `MandateDocumentPreviewService` as specified.
- [ ] **Step 4:** Run the test — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): store mandate page previews and generate them lazily` + `Refs #347`.

### Task 4: Generate at write time and clean up (`ndopify-server`)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt`
- Test (update): `.../mandate/server/service/MandateDocumentServiceTest.kt`
- Test (update, SQL fixtures unchanged unless a preview assertion needs one):
  `.../mandate/server/endpoint/CreateMandateDocumentEndpointTest.kt`,
  `UploadMandateDocumentEndpointTest.kt`, `AddMandateDocumentPageEndpointTest.kt`,
  `ReplaceMandateDocumentPageEndpointTest.kt`, `DeleteMandateDocumentPageEndpointTest.kt`,
  `DeleteMandateDocumentEndpointTest.kt`

**Interfaces:**
- Consumes: Task 3's `MandateDocumentPreviewService.generate/read/deleteQuietly/previewPath`.
- `MandateDocumentService`:
  - constructor drops `urlTTL` and adds `previewService: MandateDocumentPreviewService`;
  - `create`, `upload`, `addPage`, `uploadPage`, `replacePage`, `uploadReplacementPage` generate the preview
    **before** the page row is saved: `create`/`addPage`/`replacePage` (presigned paths) read the stored original;
    `upload`/`uploadPage`/`uploadReplacementPage` pass the file bytes. Wrap that phase so **any** failure deletes every
    new object (no page row references it yet) before rethrowing: an `UnprocessableImageException` becomes
    `BadRequestException(HTTP_INVALID_PARAMETER)` naming `path` (or `file`) with **no path value**, so nothing leaks in
    the error body or logs; a storage failure keeps its 500;
  - after a committed `replacePage` / `uploadReplacementPage`, delete the old page's preview next to the old object;
  - after a committed `deletePage` / `delete`, delete each removed page's preview next to its object;
  - a failed save deletes the new original and its preview when no page references it;
  - `pageUrl` and the `url-ttl-seconds` constructor parameter are removed; the class KDoc no longer mentions
    presigned reads;
  - the existing delete-failure warning drops the path (mandate id only), so no page path is logged.
- Errors keep the existing order: 404 → 403 → 404 document/page → 409 `MANDATE_ALREADY_CLOSED` → 400; an
  unprocessable image is a 400 at that point, before the save.

- [ ] **Step 1: Update the unit test** `MandateDocumentServiceTest` for the new constructor (`previewService` mock,
  no `urlTTL`) and add: `create` with a mocked `imagePreview` that throws `UnprocessableImageException` deletes every
  path and throws 400 `HTTP_INVALID_PARAMETER`; `replacePage` deletes `newPath.preview-v1.jpg` on a failed save and
  the old page's preview after a successful replace; `deletePage`/`delete` request the deleted pages' previews.
- [ ] **Step 2: Update the endpoint tests** to account for the preview writes (mock `ImagePreviewService` with
  `@MockitoBean` returning a small JPEG; or let a real fixture generate it):
  - `CreateMandateDocumentEndpointTest`: N pages → N preview stores at `<path>.preview-v1.jpg`; a refused page → 400
    and every new object/preview deleted;
  - `UploadMandateDocumentEndpointTest`: two files → two original stores **and** two preview stores; adjust the
    `times(2)` / `never()` verifications;
  - `AddMandateDocumentPageEndpointTest`, `ReplaceMandateDocumentPageEndpointTest`: preview stored; the replaced
    page's old preview deleted; the `store` verification matchers no longer assume a single call; an image service
    that throws `UnprocessableImageException` → 400 `HTTP_INVALID_PARAMETER` and the new object/preview deleted
    (for both a presigned `path` and a multipart `file`);
  - `DeleteMandateDocumentPageEndpointTest`, `DeleteMandateDocumentEndpointTest`: the preview path is deleted with
    each page object; an unprocessable legacy page is out of scope for these delete tests.
- [ ] **Step 3:** Run `mvn test -Dtest='*MandateDocument*Test'` — Expected: the new assertions FAIL.
- [ ] **Step 4:** Implement the service changes as specified.
- [ ] **Step 5:** Run the same tests — Expected: PASS.
- [ ] **Step 6:** Commit `feat(server): generate and clean up mandate page previews` + `Refs #347`.

### Task 5: Server preview endpoint and the end of presigned page reads (`ndopify-server`)

**Files:**
- Modify: `.../mandate/server/endpoint/MandateDocumentEndpoints.kt`
- Modify: `.../mandate/server/mapper/MandateDocumentMapper.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (remove
  `ndopify.mandate.document.url-ttl-seconds`) and `src/test/resources/application-qa.yml` (same key)
- Modify: `modules/ndopify-server/CLAUDE.md` (mandate documents paragraph)
- Test (new): `.../mandate/server/endpoint/MandateDocumentPreviewEndpointTest.kt` (+ reuse
  `db/test/mandate/GetMandateDocumentEndpoint.sql`, or add a dedicated fixture)
- Test (update): `GetMandateDocumentEndpointTest.kt`, `SearchMandateDocumentEndpointTest.kt`,
  `MandateDocumentCustomerAccessEndpointTest.kt`

**Interfaces — Produces:**
- `GET /v1/mandates/{id}/documents/{documentId}/pages/{pageId}/preview` on `MandateDocumentEndpoints`:
  `mandateService.findById(id)` → `access.ensureCanUpdate(mandate)` → `service.findDocument(mandate, documentId)` →
  `service.findPage(document, pageId)` → `service.preview(mandate, page)`; `200` with
  `Content-Type: image/jpeg` and the preview bytes. A generation/storage failure is `500` `HTTP_INTERNAL`
  (`InternalErrorException`), never the original.
- `MandateDocumentService.preview(mandate: MandateEntity, page: MandateDocumentPageEntity): ByteArray` delegates to
  `MandateDocumentPreviewService.read(mandate.id, page.path, page.storageType, page.mimeType)`, which already maps
  a generation/storage failure to 500 `HTTP_INTERNAL` (Task 3) without leaking a path.
- `MandateDocumentMapper.toMandateDocument(entity, pages)` — no `urlOf` callback; `MandateDocumentPage` without `url`.
- The search and get endpoints no longer presign anything; the `url-ttl-seconds` property disappears from
  `application.yml` and `application-qa.yml`.

- [ ] **Step 1: Write the failing endpoint test** `MandateDocumentPreviewEndpointTest`
  (`@Sql` on `GetMandateDocumentEndpoint.sql`; `@MockitoBean StorageServiceProvider` backed by a
  `StorageServiceLocal` over a JUnit `@TempDir`, seeded with a small JPEG at each fixture page path;
  `@MockitoBean ImagePreviewService` with an `AtomicInteger` answer returning a fixed JPEG, so the pipeline itself is
  covered by Task 2):
  - `metadata(previewPath)` absent → 200 `image/jpeg`, `store` at `<path>.preview-v1.jpg`, the original bytes
    unchanged (the storage still holds them), and the image service was called once;
  - the second call → 200, the stored preview is read, and the image service was still called once;
  - a legacy page with `storage_type NULL` uses the default storage;
  - the image service throwing `UnprocessableImageException` → 500 `HTTP_INTERNAL`, no `image/jpeg` body, and the
    original bytes untouched;
  - CLOSED mandate 300 → 200; admin (`roles = arrayOf(RoleCode.ADMIN)`) on another agent's mandate → 200;
  - anonymous → 401 `AUTH_UNAUTHORIZED`; another agent's mandate → 403 `MANDATE_ACCESS_DENIED`; unknown mandate →
    404 `MANDATE_NOT_FOUND`; unknown document → 404 `MANDATE_DOCUMENT_NOT_FOUND`; a page of another document → 404
    `MANDATE_DOCUMENT_PAGE_NOT_FOUND`;
  - the JSON search/get responses and the preview response body contain no `path`, no storage URL, no `url`;
  - the customer's token on the preview route → 403 `MANDATE_ACCESS_DENIED` (extend
    `MandateDocumentCustomerAccessEndpointTest`).
- [ ] **Step 2:** Remove `url` from `GetMandateDocumentEndpointTest` / `SearchMandateDocumentEndpointTest` assertions;
  replace them with "the JSON body has no `url`" and drop the presigned-URL mocks.
- [ ] **Step 3:** Run `mvn test -Dtest='*MandateDocument*Test'` — Expected: FAIL (endpoint and mapper missing).
- [ ] **Step 4:** Implement the endpoint, the `preview` service method, the mapper change, the config removal and the
  module documentation; keep the error order above.
- [ ] **Step 5:** Run the same tests — Expected: PASS.
- [ ] **Step 6:** Commit `feat(server): serve a mandate page preview instead of a presigned url` + `Refs #347`.

### Task 6: Partner Central authenticated preview route (`ndopify-partner-central`)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateDocumentClient.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt`
- Test (update): `.../npc/client/ndopify/NdMandateDocumentClientTest.kt`
- Test (update): `.../npc/ui/document/MandateDocumentControllerTest.kt`

**Interfaces — Produces:**
- `NdMandateDocumentClient.preview(mandateId: Long, documentId: Long, pageId: Long): ByteArray` — `GET`
  `/v1/mandates/{id}/documents/{documentId}/pages/{pageId}/preview`, `.retrieve().body<ByteArray>()` (the web-core
  `Authorization` interceptor adds the agent's bearer token and `X-Tenant-ID`).
- `MandateDocumentController.preview(id, documentId, pageId)` on
  `GET /mandates/{id}/documents/{documentId}/pages/{pageId}/preview`:
  - 401 without a signed-in agent (`CurrentAgent` / `SignInRequiredException`);
  - mandate read with `mandateClient.get` so 403/404 keep their status; a status outside `MandateRules.SHOWN` is 404;
  - document with `documentClient.get`; the page must belong to it (else `ResponseStatusException(NOT_FOUND)`);
  - bytes from `documentClient.preview`; headers `image/jpeg`, `inline; filename="mandat-<id>-page-<n>.jpg"`,
    `Cache-Control: private, max-age=300` (set the literal header, not a `CacheControl` builder whose order varies),
    `X-Content-Type-Options: nosniff`;
  - `HttpClientErrorException.Unauthorized` propagates to `HttpClientErrorHandler` (sign out + `/login`); 403/404
    propagate to the same advice (same status); `HttpServerErrorException` and `ResourceAccessException` →
    `502 Bad Gateway`, logged with the mandate/document/page ids only (never a path).

- [ ] **Step 1: Write the failing client test** in `NdMandateDocumentClientTest`: `preview(100, 10, 1000)` requests
  `GET http://localhost:8080/v1/mandates/100/documents/10/pages/1000/preview` and returns the raw bytes.
- [ ] **Step 2: Write the failing controller tests** in `MandateDocumentControllerTest` (and drop `url` from its
  `page1`/`page2` fixtures, which no longer exists on the DTO):
  - `GET /mandates/301/documents/3103/pages/2001/preview` → 200, `image/jpeg`, `inline;
    filename="mandat-301-page-1.jpg"`, `Cache-Control: private, max-age=300`, `X-Content-Type-Options: nosniff`, the
    client's bytes; verify the client was called with `(301, 3103, 2001)`;
  - no `CurrentAgent` → redirect `/login`;
  - a CANCELLED mandate (`311`) → 200;
  - a page id not in the document → 404;
  - server 403 → 403; server 404 → 404; server 500 → 502; `ResourceAccessException` → 502.
- [ ] **Step 3:** Run `cd modules/ndopify-partner-central && mvn test -Dtest='NdMandateDocumentClientTest,MandateDocumentControllerTest'`
  — Expected: FAIL.
- [ ] **Step 4:** Implement the client method and the controller route as specified.
- [ ] **Step 5:** Run the tests — Expected: PASS.
- [ ] **Step 6:** Commit `feat(npc): serve a mandate page preview behind the agent session` + `Refs #347`.

### Task 7: Point *Voir* at the Partner Central route (`ndopify-partner-central`)

**Files:**
- Modify: `.../npc/ui/document/MandateDocumentView.kt` — `DocumentPageView(id, number, href)`
- Modify: `.../npc/ui/document/MandateDocumentViews.kt` — build
  `href = "/mandates/${mandate.id}/documents/${document.id}/pages/${page.id}/preview"`
- Modify: `src/main/resources/templates/mandate/document.html` — `th:href="${page.href}"`; update the comment that
  describes the presigned URL
- Modify: `modules/ndopify-partner-central/CLAUDE.md` — the documents paragraph (no presigned URL; preview route)
- Test (update): `MandateDocumentViewsTest.kt`, `MandateDocumentTemplateTest.kt`,
  `MandateDocumentControllerTest.kt` (page fixtures lose `url`)

**Interfaces — Produces:**
- `DocumentPageView(val id: Long, val number: Int, val href: String)`; the template keeps
  `target="_blank" rel="noopener"` and the accessible label `Voir la page <n> (nouvel onglet)`.

- [ ] **Step 1: Update the failing tests**:
  - `MandateDocumentViewsTest` page fixtures lose `url`; assert
    `view.pages.map { it.href } == listOf("/mandates/301/documents/3103/pages/1/preview", …)`;
  - `MandateDocumentTemplateTest` `document` fixture loses `url`; assert each `[data-page-link]` href is the
    Partner Central preview route, and that the rendered HTML contains no `https://s3`, no `signature`, no
    `mandate/301/documents/` and no `presigned`;
  - `MandateDocumentControllerTest` page fixtures lose `url`.
- [ ] **Step 2:** Run `mvn test -Dtest='MandateDocumentViewsTest,MandateDocumentTemplateTest,MandateDocumentControllerTest'`
  — Expected: FAIL (no `href`, `url` removed from the DTO).
- [ ] **Step 3:** Implement the view, view builder, template and module documentation changes.
- [ ] **Step 4:** Run the tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(npc): link a document page to its authenticated preview` + `Refs #347`.

### Task 8: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` — mandate documents paragraph: the preview endpoint, the derived
  `-v1` path, the generated-at-write/lazy rule, the size/watermark/fail-closed rules, the removed `url-ttl-seconds`.
- Modify: `modules/ndopify-partner-central/CLAUDE.md` — documents paragraph: the preview route, the headers, the
  copied-URL behaviour.
- Modify: `modules/ndopify-dto/CLAUDE.md` only if the DTO change introduced a new convention (none expected).

- [ ] **Step 1:** Update both module CLAUDE.md paragraphs; remove every remaining reference to the presigned page
  URL / `url-ttl-seconds` in the touched modules
  (`grep -rn "url-ttl-seconds\|presigned url" modules/ndopify-server modules/ndopify-partner-central modules/ndopify-dto`).
- [ ] **Step 2:** Run `mvn install -Dheadless=true -pl modules/ndopify-dto` then
  `cd modules/ndopify-server && mvn verify` (MySQL running) — Expected: BUILD SUCCESS (ktlint, tests, JaCoCo).
- [ ] **Step 3:** Run `cd modules/ndopify-partner-central && mvn verify` — Expected: BUILD SUCCESS.
- [ ] **Step 4:** Commit `docs(documents): document the authenticated page previews` + `Refs #347`.

## Release and rollout

1. Release `ndopify-dto` first (`ndopify-dto-main.yml`); bump `<ndopify-dto.version>` to the released `0.0.108`.
2. Deploy `ndopify-server` (preview endpoint + generated previews) then `ndopify-partner-central` (route + template)
   in the same window; a server-first window only renders old links empty for a moment.
3. Existing presigned URLs already handed out remain valid for their remaining TTL (≤ 1 h); nothing to revoke.
4. No Flyway migration, no backfill: a pre-story page's preview is generated on its first view.
5. Out of scope: KYC images, signed contracts, PDFs, inline galleries on the mandate page, an audit log, the admin
   console.
