# Mandate documents — record, private storage, upload, read — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An agent (or an admin) can add, rename, change the pages of, delete and read back the documents of a
LISTING or SEARCH mandate, stored privately and read only through presigned URLs. No AI.

**Architecture:** A new sub-domain of `mandate` in `ndopify-server` (`MandateDocumentEntity` + ordered
`MandateDocumentPageEntity`, `MandateDocumentService`, `MandateDocumentEndpoints` under
`/v1/mandates/{id}/documents`), on the KYC direct-upload pattern: storage I/O outside any transaction, the row saved
in a transaction holding the mandate's lock, replaced/removed objects deleted after the commit. Contracts in
`ndopify-dto` (`com.wutsi.ndopify.mandate.dto`), released before the server.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA / Hibernate (`@TenantId`, `@Version`), Flyway (MySQL 8),
`DirectUploadService`, `StorageServiceProvider`, JUnit + `kotlin.test` + mockito-kotlin, `TestRestTemplate`.

**Spec:** GitHub issue #328 (enriched body: *Scope*, *Acceptance criteria*, *Decisions and assumptions*); epic #327.

## Global Constraints

- `UI: no`. `Shared code: ndopify-dto` — DTO release first (`modules/ndopify-dto/CLAUDE.md`, *Versioning and Release*).
- Images only: JPEG, PNG, WEBP, at most 5 MB per page (`DirectUploadService.IMAGE_MIME_TYPES` / `MAX_BYTES`).
- Storage path: `mandate/{mandateId}/documents/{uuid}.{ext}`; private; read only by `generatePresignedUrl`, never
  `publicUrl`, never `path` in a response.
- Access: `MandateAccessService.ensureCanUpdate` for reads **and** writes (assigned agent or admin; not the offered
  agent, not the customer).
- Error order: 401 → 404 `MANDATE_NOT_FOUND` → 403 `MANDATE_ACCESS_DENIED` → 404 `MANDATE_DOCUMENT_NOT_FOUND` /
  `MANDATE_DOCUMENT_PAGE_NOT_FOUND` → 409 `MANDATE_ALREADY_CLOSED` (writes, CLOSED or CANCELLED) → 400s.
- Any page change (create, add, replace, delete) leaves the document `UPLOADED` with `problemCodes`, `data`,
  `dataVersion`, `issuedAt`, `expiresAt` cleared. Rename changes only `title`.
- New migration only: `V19_0__mandate-document.sql` (renumber after the highest if one lands first; never edit an
  applied script).
- Tests: `kotlin.test` assertions, mockito-kotlin mocks, one endpoint test class per operation with its own
  `db/test/mandate/<Name>Endpoint.sql`; new tables in `clean.sql`, children first.
- French for user-facing text (default titles), English for code, logs, KDoc. Never log a path with document data
  (paths are fine; there is no extracted data yet).

## Review Focus

1. **A customer with a valid token** calling any document endpoint, or `GET /v1/mandates/{id}`: a 403 on the
   documents, and no `documents` / `path` / `url` anywhere in the mandate read — Task 5.
2. **A document id of another mandate** (same agent) on `/v1/mandates/{id}/documents/{documentId}`: 404
   `MANDATE_DOCUMENT_NOT_FOUND`, never the other mandate's document — Tasks 3 and 4.
3. **A mandate closed during the storage I/O** (passes the 409 check, closed before the save): the save is refused under
   the lock and the new object deleted — Task 3 (`MandateDocumentServiceTest`).
4. **Deleting the last page** of a document: 400 `MANDATE_DOCUMENT_LAST_PAGE`, the page and object kept — Task 4.
5. **A retried attach** (same path twice) on add-page or replace: the same page returned, no duplicate row, no object
   deleted — Task 4.

---

### Task 1: DTOs and error codes (`ndopify-dto`)

**Files:**
- Create in `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/`: `DocumentType.kt`,
  `DocumentStatus.kt`, `DocumentField.kt`, `MandateDocument.kt`, `MandateDocumentPage.kt`,
  `CreateMandateDocumentRequest.kt`, `CreateMandateDocumentResponse.kt`, `UpdateMandateDocumentRequest.kt`,
  `GetMandateDocumentResponse.kt`, `SearchMandateDocumentResponse.kt`, `AttachMandateDocumentPageResponse.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt` (mandate group, alphabetical)
- Modify: root `pom.xml` `<ndopify-dto.version>` → the DTO's current `-SNAPSHOT` while developing

**Interfaces — Produces:**
- `enum class DocumentType { UNKNOWN, NATIONAL_ID, PASSPORT, LAND_TITLE, CERTIFICATE_OF_OWNERSHIP, SURVEY_PLAN,
  POWER_OF_ATTORNEY, LEASE_CONTRACT, UTILITY_BILL, TAX_RECEIPT, PAY_SLIP, BANK_PRE_APPROVAL, PROOF_OF_FUNDS, OTHER }`
  — KDoc: `LAND_TITLE`, `SURVEY_PLAN`, `CERTIFICATE_OF_OWNERSHIP` reuse `TitleDocumentType`'s names on purpose (#294).
- `enum class DocumentStatus { UNKNOWN, UPLOADED, EXTRACTING, EXTRACTED, FAILED }` — no `EXPIRED`: validity is
  `expiresAt` against the consumer's clock, not a status (#327)
- `data class DocumentField(val key: String = "", val value: String? = null, val confidence: Double? = null)`
- `data class MandateDocumentPage(val id: Long = -1, val index: Int = 0, val contentType: String? = null,
  val url: String = "", val createdAt: Date = Date(), val modifiedAt: Date = Date())` — `url` is presigned, short-lived.
- `data class MandateDocument(val id: Long = -1, val mandateId: Long = -1, val type: DocumentType = UNKNOWN,
  val title: String = "", val status: DocumentStatus = UNKNOWN, val problemCodes: List<String> = emptyList(),
  val fields: List<DocumentField> = emptyList(), val issuedAt: LocalDate? = null, val expiresAt: LocalDate? = null,
  val pages: List<MandateDocumentPage> = emptyList(), val version: Long = 0, val createdAt: Date = Date(),
  val modifiedAt: Date = Date())`
- `CreateMandateDocumentRequest(val type: DocumentType = UNKNOWN, val title: String? = null,
  val paths: List<String> = emptyList())`; `CreateMandateDocumentResponse(val documentId: Long = -1)`
- `UpdateMandateDocumentRequest(val title: String = "", val version: Long? = null)`
- `GetMandateDocumentResponse(val document: MandateDocument = MandateDocument())`;
  `SearchMandateDocumentResponse(val documents: List<MandateDocument> = emptyList())`;
  `AttachMandateDocumentPageResponse(val pageId: Long = -1)`
- `ErrorCode.MANDATE_DOCUMENT_LAST_PAGE = "$PREFIX:mandate-document:last-page"`,
  `MANDATE_DOCUMENT_NOT_FOUND = "$PREFIX:mandate-document:not-found"`,
  `MANDATE_DOCUMENT_PAGE_NOT_FOUND = "$PREFIX:mandate-document-page:not-found"`,
  `MANDATE_DOCUMENT_VERSION_CONFLICT = "$PREFIX:mandate-document:version-conflict"` (own `mandate-document` group,
  placed alphabetically after `mandate`).

Presign and attach reuse `common.dto.PresignUploadRequest` / `PresignUploadResponse` / `AttachUploadRequest`.

- [ ] **Step 1:** Add the files above (one enum per file, every field defaulted, KDoc on each class saying what it's
  for; `MandateDocument` KDoc: never part of `Mandate` / `MandateSummary`, only of the document endpoints).
- [ ] **Step 2:** Run `cd modules/ndopify-dto && mvn install` — Expected: BUILD SUCCESS (ktlint clean).
- [ ] **Step 3:** Commit `feat(dto): add the mandate document contracts` with `Refs #328`.

### Task 2: Schema, entities and repositories (`ndopify-server`)

**Files:**
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V19_0__mandate-document.sql`
- Create: `.../mandate/server/domain/MandateDocumentEntity.kt`, `.../mandate/server/domain/MandateDocumentPageEntity.kt`
- Create: `.../mandate/server/dao/MandateDocumentRepository.kt`, `.../mandate/server/dao/MandateDocumentPageRepository.kt`
- Modify: `.../util/jpa/EnumConverters.kt` (converters for `DocumentType`, `DocumentStatus`)
- Modify: `.../error/server/endpoint/ControllerErrorHandler.kt` (the entity → code `when`, ~line 83) — map
  `MandateDocumentEntity` to 409 `MANDATE_DOCUMENT_VERSION_CONFLICT`
- Modify: `modules/ndopify-server/src/test/resources/db/test/clean.sql` — `DELETE FROM T_MANDATE_DOCUMENT_PAGE;`
  then `DELETE FROM T_MANDATE_DOCUMENT;` before `DELETE FROM T_MANDATE_NEIGHBORHOOD;`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateDocumentRepositoryTest.kt`

(`...` = `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify`.)

**Schema** (`V19_0`):
- `T_MANDATE_DOCUMENT`: `id BIGINT AUTO_INCREMENT PK`, `tenant_id BIGINT NOT NULL`, `mandate_id BIGINT NOT NULL`
  (FK `T_MANDATE`), `type VARCHAR(30)`, `title VARCHAR(100) NOT NULL`, `status VARCHAR(30)`, `problem_codes TEXT NULL`,
  `data JSON NULL`, `data_version INT NULL`, `issued_at DATE NULL`, `expires_at DATE NULL`,
  `version BIGINT NOT NULL DEFAULT 0`, `created_at DATETIME NOT NULL`, `modified_at DATETIME NOT NULL`;
  index `(tenant_id, mandate_id)`, index `(status)`.
- `T_MANDATE_DOCUMENT_PAGE`: `id BIGINT AUTO_INCREMENT PK`, `tenant_id BIGINT NOT NULL`, `document_id BIGINT NOT NULL`
  (FK `T_MANDATE_DOCUMENT`), `page_index INT NOT NULL`, `path VARCHAR(255) NOT NULL`, `storage_type VARCHAR(30)`,
  `mime_type VARCHAR(50)`, `created_at`, `modified_at`; `UNIQUE (document_id, page_index)`, `UNIQUE (path)`.
  (`rank` is reserved in MySQL 8: hence `page_index`.)

**Interfaces — Produces:**
- `MandateDocumentEntity(id: Long? = null, @TenantId tenantId: Long? = null, mandateId: Long = -1,
  type: DocumentType = UNKNOWN, title: String = "", status: DocumentStatus = UNKNOWN,
  @Convert(StringListConverter) problemCodes: List<String> = emptyList(),
  @JdbcTypeCode(SqlTypes.JSON) data: String? = null, dataVersion: Int? = null, issuedAt: LocalDate? = null,
  expiresAt: LocalDate? = null, @Version version: Long? = null, createdAt: Date, modifiedAt: Date)` — `data` stays a
  raw JSON string here; #330 adds the typed helper.
- `MandateDocumentPageEntity(id: Long? = null, @TenantId tenantId: Long? = null, documentId: Long = -1,
  pageIndex: Int = 0, path: String = "", storageType: StorageType = UNKNOWN, mimeType: String? = null, createdAt,
  modifiedAt)` — not `@Version`-ed: always written under the mandate's lock.
- `MandateDocumentRepository : CrudRepository<MandateDocumentEntity, Long>` with
  `findByMandateIdOrderByIdDesc(mandateId: Long): List<MandateDocumentEntity>`.
- `MandateDocumentPageRepository : CrudRepository<MandateDocumentPageEntity, Long>` with
  `findByDocumentIdOrderByPageIndex(documentId: Long): List<…>`,
  `findByDocumentIdInOrderByPageIndex(documentIds: Collection<Long>): List<…>`, `findByPath(path: String): …?`,
  `@Query("SELECT MAX(p.pageIndex) …") findMaxPageIndex(documentId: Long): Int?`.

- [ ] **Step 1: Write the failing test** `MandateDocumentRepositoryTest` (`@SpringBootTest`, `qa`, `@Sql` clean + a
  fixture with one mandate in tenant 1 and one in tenant 2, each with a document and two pages): with `TenantContext`
  set to 1, `findByMandateIdOrderByIdDesc(tenant2MandateId)` is empty; `findByDocumentIdOrderByPageIndex` returns
  indexes `[0, 1]`; saving a page with an existing `path` throws `DataIntegrityViolationException`.
- [ ] **Step 2:** Run `mvn test -Dtest=MandateDocumentRepositoryTest` — Expected: FAIL (classes missing).
- [ ] **Step 3:** Add the migration, entities, converters, repositories, the 409 mapping and `clean.sql` lines.
- [ ] **Step 4:** Run the test — Expected: PASS.
- [ ] **Step 5:** Add both entities to the `@TenantId` list and `MandateDocumentEntity` to the *Optimistic locking*
  list of `modules/ndopify-server/CLAUDE.md`. Commit `feat(server): add the mandate document tables` + `Refs #328`.

### Task 3: Create, presign, read (`MandateDocumentService` + endpoints)

**Files:**
- Create: `.../mandate/server/service/MandateDocumentService.kt`, `.../mandate/server/service/MandateDocumentLabels.kt`,
  `.../mandate/server/mapper/MandateDocumentMapper.kt`, `.../mandate/server/endpoint/MandateDocumentEndpoints.kt`
- Modify: `.../platform/storage/upload/DirectUploadService.kt` — add `check(file: MultipartFile): String`
- Modify: `modules/ndopify-server/src/main/resources/application.yml` (the `ndopify.mandate` block) —
  `document.url-ttl-seconds: 3600`; the same key in `src/test/resources/application-qa.yml`'s `mandate` block
- Test: `.../mandate/server/endpoint/CreateMandateDocumentEndpointTest.kt`, `UploadMandateDocumentEndpointTest.kt`,
  `PresignMandateDocumentEndpointTest.kt`, `GetMandateDocumentEndpointTest.kt`, `SearchMandateDocumentEndpointTest.kt`
  with fixtures `db/test/mandate/<Name>Endpoint.sql`; `.../mandate/server/service/MandateDocumentServiceTest.kt`;
  `.../platform/storage/upload/DirectUploadServiceTest.kt` (extend)

**Interfaces:**
- Consumes: Task 1 DTOs; Task 2 entities/repositories; `MandateService.findById(id)`,
  `MandateRepository.findByIdForUpdate(id)`, `MandateAccessService.ensureCanUpdate(mandate)`,
  `DirectUploadService.presign(prefix, contentType)` / `verify(path, prefix)`, `StorageServiceProvider`,
  `TransactionTemplate`, `Clock`.
- Produces:
  - `MandateDocumentService.prefix(mandateId: Long) = "mandate/$mandateId/documents"` (companion)
  - `fun findDocument(mandate: MandateEntity, documentId: Long): MandateDocumentEntity` — 404
    `MANDATE_DOCUMENT_NOT_FOUND` when unknown or `documentId`'s `mandateId != mandate.id`
  - `fun findPages(documentIds: Collection<Long>): Map<Long, List<MandateDocumentPageEntity>>`
  - `fun pageUrl(page: MandateDocumentPageEntity): String` — `storageProvider.get(page.storageType)` (default storage
    when `UNKNOWN`) `.generatePresignedUrl(page.path, ttl)`
  - `fun presign(mandateId: Long, contentType: String?): PresignUploadResponse`
  - `fun create(mandateId: Long, request: CreateMandateDocumentRequest): MandateDocumentEntity`
  - `fun upload(mandateId: Long, type: DocumentType, title: String?, files: List<MultipartFile>): MandateDocumentEntity`
  - private `ensureWritable(mandateId): MandateEntity` (find → `ensureCanUpdate` → 409 on CLOSED/CANCELLED) and
    private `saveLocked(mandateId, block)`: `tx.execute { mandateDao.findByIdForUpdate(id)`, re-check not
    CLOSED/CANCELLED (409), run `block` }` — shared with Task 4.
  - private `deleteQuietly(path, storageType)` — failure logged `>>> Mandate#{id} - Could not delete {path}`.
  - `MandateDocumentLabels.title(type: DocumentType): String` — `NATIONAL_ID` *Carte nationale d'identité*,
    `PASSPORT` *Passeport*, `LAND_TITLE` *Titre foncier*, `CERTIFICATE_OF_OWNERSHIP` *Certificat de propriété*,
    `SURVEY_PLAN` *Plan de bornage*, `POWER_OF_ATTORNEY` *Procuration*, `LEASE_CONTRACT` *Contrat de bail*,
    `UTILITY_BILL` *Facture d'eau ou d'électricité*, `TAX_RECEIPT` *Quittance d'impôt*, `PAY_SLIP` *Bulletin de paie*,
    `BANK_PRE_APPROVAL` *Accord de principe bancaire*, `PROOF_OF_FUNDS` *Justificatif de fonds*, `OTHER` *Document*.
  - `MandateDocumentMapper.toMandateDocument(entity, pages, urlOf: (MandateDocumentPageEntity) -> String):
    MandateDocument` — `fields = emptyList()`, `index = pageIndex`, `contentType = mimeType`; never `path`.
  - `DirectUploadService.check(file: MultipartFile): String` — the sniffed MIME type; 400 `HTTP_INVALID_PARAMETER`
    (`file`) when larger than `MAX_BYTES` or not JPEG/PNG/WEBP.
  - Endpoints (`@RequestMapping("/v1/mandates/{id}/documents")`):
    `POST /presign` (`PresignUploadRequest` → `PresignUploadResponse`); `POST` (`CreateMandateDocumentRequest` →
    `CreateMandateDocumentResponse`); `POST /upload` (multipart `type`, `title?`, `files`) →
    `CreateMandateDocumentResponse`; `GET` → `SearchMandateDocumentResponse` (newest first); `GET /{documentId}` →
    `GetMandateDocumentResponse`. Reads run `mandateService.findById` + `accessService.ensureCanUpdate` in the endpoint.

**Create rules** (KDoc on `create`, errors in this order): 404 `MANDATE_NOT_FOUND` → 403 `MANDATE_ACCESS_DENIED` → 409
`MANDATE_ALREADY_CLOSED` → 400 `HTTP_INVALID_PARAMETER`: `type` `UNKNOWN`; `title` longer than 100 after trim; `paths`
empty or with duplicates; a path any page already has; then each `verify(path, prefix(mandateId))`. Saved in
`saveLocked`: document `UPLOADED`, `title` = trimmed title or `MandateDocumentLabels.title(type)` when blank, pages
indexed `0..n-1` in the given order with the default storage's type. A failed save deletes every new object
(`deleteQuietly`) not referenced by a page (`findByPath`). `upload` is the same with `check(file)` then
`store(path, …)` for each file (outside the transaction), `files` empty → 400 (`files`).

- [ ] **Step 1: Write the failing tests**
  - `CreateMandateDocumentEndpointTest` (fixture: tenant 1, agent party of user 11 assigned to an ASSIGNED LISTING
    mandate `100`, an ASSIGNED SEARCH mandate `101`, another agent's mandate `200`, a CLOSED `300`, a CANCELLED `301`,
    mandate `400` with a PENDING offer to user 11's agent, mandate `500` of tenant 2; `@MockitoBean
    StorageServiceProvider` as in `AttachKycImageEndpointTest`): `create` on `100` with `type=LAND_TITLE`, no title,
    two uploaded paths → 200, reloaded document has `status == UPLOADED`, `title == "Titre foncier"`, pages' paths in
    order with `pageIndex` `[0, 1]`, `storageType == S3`; same on SEARCH `101` with `PAY_SLIP` and title
    `"  Bulletin mars  "` → `"Bulletin mars"`; errors: `999` → 404 `MANDATE_NOT_FOUND`; `500` → 404; `200` → 403
    `MANDATE_ACCESS_DENIED`; `400` (offered only) → 403; admin (`roles = arrayOf(RoleCode.ADMIN)`) on `200` → 200;
    `300` → 409 `MANDATE_ALREADY_CLOSED`; `301` → 409; `UNKNOWN` type → 400; empty `paths` → 400; a path under
    `mandate/101/documents/` for mandate `100` → 400 and no row; a path already a page's → 400; `anonymousUser = true`
    → 401.
  - `UploadMandateDocumentEndpointTest`: multipart with two PNGs → 200, two pages, `store` called twice under
    `mandate/100/documents/`; a text file → 400 `HTTP_INVALID_PARAMETER`, nothing stored.
  - `PresignMandateDocumentEndpointTest`: S3 mock returning a URL → `url` and `path` starting with
    `mandate/100/documents/`; `presignedUploadUrl` returning null (local) → `url == null`; `application/pdf` → 400.
  - `GetMandateDocumentEndpointTest` / `SearchMandateDocumentEndpointTest` (fixture: two documents on `100`, one on
    `101`, pages with S3 and LOCAL storage types): get returns the pages ordered by index with `url` equal to the
    mocked presigned URL for its own storage type, `fields` empty, and the JSON body contains no `"path"` and no
    `publicUrl` value (`verify(storage, never()).publicUrl(any())`); get of `101`'s document via `/v1/mandates/100/…`
    → 404 `MANDATE_DOCUMENT_NOT_FOUND`; search on `100` returns its two documents newest first; 403/404 as above;
    reads on CLOSED `300` → 200.
  - `MandateDocumentServiceTest` (unit): when `findByIdForUpdate` returns a CLOSED mandate (closed during the I/O),
    `create` throws `ConflictException` with `MANDATE_ALREADY_CLOSED` and `storage.delete(path)` is called for each
    new path.
  - `DirectUploadServiceTest`: `check` returns `image/png` for a PNG, throws 400 (`file`) for 5 MB + 1 byte and for
    a text file.
- [ ] **Step 2:** Run `cd modules/ndopify-server && mvn test -Dtest='*MandateDocument*Test,DirectUploadServiceTest'`
  — Expected: FAIL.
- [ ] **Step 3:** Implement the service, labels, mapper, `check`, endpoints and config as specified.
- [ ] **Step 4:** Run the same tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): create and read a mandate's documents` + `Refs #328`.

### Task 4: Rename, add / replace / delete a page, delete a document

**Files:**
- Modify: `.../mandate/server/service/MandateDocumentService.kt`, `.../mandate/server/endpoint/MandateDocumentEndpoints.kt`
- Test: `UpdateMandateDocumentEndpointTest.kt`, `AddMandateDocumentPageEndpointTest.kt`,
  `ReplaceMandateDocumentPageEndpointTest.kt`, `DeleteMandateDocumentPageEndpointTest.kt`,
  `DeleteMandateDocumentEndpointTest.kt` (+ fixtures) under `.../mandate/server/endpoint/`

**Interfaces:**
- Consumes: Task 3's `ensureWritable`, `saveLocked`, `findDocument`, `deleteQuietly`, `prefix`, `DirectUploadService.check`.
- Produces (service):
  - `fun rename(mandateId: Long, documentId: Long, request: UpdateMandateDocumentRequest): MandateDocumentEntity`
  - `fun addPage(mandateId: Long, documentId: Long, path: String): MandateDocumentPageEntity`
  - `fun uploadPage(mandateId: Long, documentId: Long, file: MultipartFile): MandateDocumentPageEntity`
  - `fun replacePage(mandateId: Long, documentId: Long, pageId: Long, path: String): MandateDocumentPageEntity`
  - `fun uploadReplacementPage(mandateId: Long, documentId: Long, pageId: Long, file: MultipartFile): MandateDocumentPageEntity`
  - `fun deletePage(mandateId: Long, documentId: Long, pageId: Long)`
  - `fun delete(mandateId: Long, documentId: Long)`
  - private `resetExtraction(document): MandateDocumentEntity` — `copy(status = UPLOADED, problemCodes = emptyList(),
    data = null, dataVersion = null, issuedAt = null, expiresAt = null, modifiedAt = now)`
- Produces (endpoints, under `/v1/mandates/{id}/documents/{documentId}`): `POST` (`UpdateMandateDocumentRequest`);
  `POST /pages` (`AttachUploadRequest` → `AttachMandateDocumentPageResponse`); `POST /pages/upload` (multipart
  `file`); `POST /pages/{pageId}` (`AttachUploadRequest`, replace); `POST /pages/{pageId}/upload` (multipart, replace);
  `DELETE /pages/{pageId}`; `DELETE`.

**Rules** (each in its KDoc, after Task 3's 404 → 403 → document/page 404 → 409 order):
- `rename`: 400 `HTTP_INVALID_PARAMETER` (`title`) blank or > 100 after trim; `version` non-null and not the entity's
  → 409 `MANDATE_DOCUMENT_VERSION_CONFLICT`; only `title` and `modifiedAt` change (status kept).
- `addPage`: a page of **this** document already at `path` → returned as is (idempotent, nothing else changes); a
  path of another page → 400; `verify`; saved at `findMaxPageIndex + 1` (under the lock); document reset.
- `replacePage`: the page already at `path` → returned as is; a path of another page → 400; `verify`; the page keeps
  its `pageIndex`, gets `path` / `storageType` / `mimeType`; document reset; after the commit the old object is deleted
  with the old page's own storage type.
- `deletePage`: the document's only page → 400 `MANDATE_DOCUMENT_LAST_PAGE`; else row deleted, other indexes
  unchanged, document reset, object deleted after the commit.
- `delete`: pages then document deleted in `saveLocked`; every page's object deleted after the commit.
- A failed save deletes the new object unless a page references it (Task 3 rule). A failed deletion is logged only.

- [ ] **Step 1: Write the failing tests** (same fixture shape as Task 3: mandate `100` with document `1000` of 2
  pages, status `FAILED`, `problem_codes='BLURRY'`, `data='{"a":1}'`, `issued_at`, `expires_at` set; document `1001`
  with 1 page; `101`'s document `1100`; CLOSED `300` with document `3000`):
  - `UpdateMandateDocumentEndpointTest`: title `"Titre foncier lot 12"` → saved, status still `FAILED`, pages and data
    untouched, `version` bumped; blank → 400; stale `version` → 409 `MANDATE_DOCUMENT_VERSION_CONFLICT`; `1100` via
    `100` → 404 `MANDATE_DOCUMENT_NOT_FOUND`; `3000` → 409 `MANDATE_ALREADY_CLOSED`; other agent → 403.
  - `AddMandateDocumentPageEndpointTest`: new path → page index `2`, document `UPLOADED`, `problemCodes` empty, `data`,
    `issuedAt`, `expiresAt` null; same path again → same `pageId`, still 3 pages, `storage.delete` never called; path of
    `1001`'s page → 400; multipart `pages/upload` with a PNG → index `2`.
  - `ReplaceMandateDocumentPageEndpointTest`: page index `1` replaced → same id and index, new path, `UPLOADED`, data
    cleared, `local.delete(oldPath)` called (fixture page is LOCAL); same path twice → no delete on the second call;
    page `id` of `1001` under `1000` → 404 `MANDATE_DOCUMENT_PAGE_NOT_FOUND`.
  - `MandateDocumentServiceTest` (extend): `replacePage` whose page save throws → `storage.delete(newPath)` called,
    old object not deleted.
  - `DeleteMandateDocumentPageEndpointTest`: page index `0` of `1000` deleted → remaining page keeps index `1`,
    `UPLOADED`, object deleted; the only page of `1001` → 400 `MANDATE_DOCUMENT_LAST_PAGE`, page and object kept.
  - `DeleteMandateDocumentEndpointTest`: `1000` deleted → no document, no pages, both objects deleted (each with its own
    storage); `storage.delete` throwing → still 200, rows gone; `3000` → 409.
- [ ] **Step 2:** Run `mvn test -Dtest='*MandateDocument*Test'` — Expected: the new tests FAIL.
- [ ] **Step 3:** Implement the service methods and endpoints as specified.
- [ ] **Step 4:** Run the same tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(server): rename, re-page and delete a mandate's documents` + `Refs #328`.

### Task 5: No leak to the customer, docs, full verification

**Files:**
- Test: `.../mandate/server/endpoint/MandateDocumentCustomerAccessEndpointTest.kt` (+ fixture)
- Modify: `modules/ndopify-server/CLAUDE.md` — a *mandate documents* paragraph in the mandate domain section
- Modify: `modules/ndopify-dto/CLAUDE.md` only if a new convention appeared (none expected)

- [ ] **Step 1: Write the failing test** `MandateDocumentCustomerAccessEndpointTest` (fixture: mandate `100` with a
  document and a page at a known path; the token's user is the mandate's customer's party): `GET /v1/mandates/100`
  → 200 and the raw JSON body contains neither `"documents"`, the page path, nor `mandate/100/documents`; every
  document endpoint (`GET`, `GET /{documentId}`, `POST`, `POST /presign`, `POST /{documentId}`, `POST /pages`,
  `DELETE /pages/{pageId}`, `DELETE`) → 403 `MANDATE_ACCESS_DENIED`; `GET /v1/mandates?…` as the agent → body without
  `"documents"`.
- [ ] **Step 2:** Run it — Expected: PASS for the mandate read (nothing was added to `Mandate`) and for the document
  endpoints (Tasks 3–4); if any fails, fix the access check, not the test.
- [ ] **Step 3:** Document in `modules/ndopify-server/CLAUDE.md`: the endpoints, the access rule (assigned agent or
  admin, not the offered agent nor the customer), the error order, the storage path and presigned reads, the reset
  on a page change, the last-page rule, the mandate lock and the after-commit deletions, `V19_0`.
- [ ] **Step 4:** Run `cd /Users/htchepannou/Perso/ndopify-mono && mvn install -Dheadless=true -pl modules/ndopify-dto`
  then `cd modules/ndopify-server && mvn verify` — Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gate).
- [ ] **Step 5:** Commit `docs(server): document the mandate documents` + `Refs #328`.

## Release

1. Merge with `<ndopify-dto.version>` at the `-SNAPSHOT`: `ndopify-dto-main.yml` releases the DTO.
2. Set `<ndopify-dto.version>` to the released version (a "fix dependencies" commit) so the server's CI and deploy
   resolve it. No web-core change; no Partner Central change (#329).
