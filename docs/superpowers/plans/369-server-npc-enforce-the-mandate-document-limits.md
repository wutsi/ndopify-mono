# Mandate document limits (5 documents, 10 pages, one kind) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce a mandate's document caps — 5 documents per mandate, 10 pages per document, and one kind per document (photos or one PDF) — on ndopify-server, and show them in Partner Central as the approved prototype does, with the French refusals.

**Architecture:** The document records the kind it was created with (`MandateDocument.source`, stored on `T_MANDATE_DOCUMENT`), and `MandateDocumentService` checks the document count, the page count after PDF expansion, and the kind on every create/add page/replace page path before storing anything. The kind travels to Partner Central on `MandateDocument`, so the document page accepts the matching replacement file and refuses the other kind; the mandate page shows the count and hides the add action at 5. New `ErrorCode`s let Partner Central word each refusal; the page cap reuses #366's code.

**Tech Stack:** Kotlin, Spring Boot, Spring Data JPA + Flyway, Thymeleaf, vanilla JavaScript, JUnit 5 / `kotlin.test` / mockito-kotlin, `MockMvc` + real Thymeleaf templates, `TestRestTemplate` + MySQL, Maven with ktlint and JaCoCo.

**Spec:** GitHub issue #369 (enriched body, rules and acceptance criteria) and the approved prototype under
`modules/ndopify-partner-central/src/main/html/v2` (approved 2026-11-26).

## Global Constraints

- **#366 must be merged first.** This plan builds on its `PdfRasterizer`, `MandateDocumentService.MAX_PAGES = 10`,
  `pageSources(...)`, `pdfError(...)` and its `MANDATE_DOCUMENT_PDF_*` codes. Confirm `main` has
  `platform/image/PdfRasterizer.kt` and the PDF branch of `MandateDocumentService` before Task 2; stop if not.
- `UI: yes`. The approved prototype is the baseline: its markup, states and French copy are ported to
  `templates/mandate/document-new.html`, `templates/mandate/document.html`, `fragments/mandate-documents.html`,
  `src/main/assets/js/mandate-document.js` and `src/main/assets/css/document.css`. **Do not design again** and never
  edit the prototype.
- `Shared code: ndopify-dto` — `DocumentSource`, `MandateDocument.source`, `MANDATE_DOCUMENT_LIMIT_REACHED` and
  `MANDATE_DOCUMENT_MIXED_SOURCES`, released before `ndopify-server` and `ndopify-partner-central` deploy. The page cap
  is #366's `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` (already released in 0.0.109). No `ndopify-web-core` change.
- Caps: **5 documents per mandate** (all types, LISTING and SEARCH alike, counted on the stored rows); **10 pages per
  document** after PDF expansion (images count one page each); **one kind per document** (1-10 images, or exactly one
  PDF of 1-10 pages). A page cap or a mixed request is a 400; nothing is stored.
- **Only the create paths are capped by the 5**: `POST /v1/mandates/{id}/documents` and `.../documents/upload`.
  Page-level writes are kind and page-cap checks only. The 5 count is checked under the mandate's write lock.
- **Grandfathered data**: a `T_MANDATE_DOCUMENT` row with `source` NULL reads `DocumentSource.UNKNOWN`; it keeps its
  documents/pages, stays readable, and accepts either kind on a page-level write. No migration of the rows, no delete,
  no truncation. Over-limit data (6+ documents, 11+ pages) can't grow (no new document/page) but is still read,
  previewed, extracted, replaced and deleted.
- French user-facing copy exactly as approved in the prototype; English code, comments, logs and docs.
- Privacy: no PDF bytes, file name, path or extracted value in a log or an error body; an error carries a code and a
  parameter name, never a value. No page path, storage URL or image in the HTML.
- Tests: `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertFailsWith`…), mockito-kotlin mocks
  (`com.nhaarman.mockitokotlin2`), `MockMvc` + a real Thymeleaf engine in the web app, `TestRestTemplate` + MySQL in
  the server. No `.pdf` binary fixture: tests generate small PDFs with PDFBox in memory (#366 already does).
- `mvn verify` green in `ndopify-dto`, `ndopify-server` (local MySQL) and `ndopify-partner-central` (ktlint, tests,
  JaCoCo, and the `page-scripts.test.mjs` run after `mvn test`).
- `ndopify-dto` is released from its own commits before the branch is rebased (the developer's release step); never
  commit a `-SNAPSHOT` in the root `pom.xml`.

## Review Focus

1. **A mixed create/add leaves nothing** — a valid image before the refused PDF must leave no document row, page row,
   stored object or preview; the endpoint test asserts `storage.delete` on the stored object and empty repositories.
2. **The 5 cap under concurrency** — the count is re-checked inside `saveLocked`, so two simultaneous creates at 4
   can't both take the last slot; a test drives the service with the repository count changing between the pre-check
   and the locked block.
3. **A wrong-kind page write touches nothing** — no new object/preview, the page's path and rank unchanged, the
   document's status and extracted fields unchanged (no `resetExtraction`).
4. **Grandfathered rows** — `source` NULL reads `UNKNOWN`, a 6-document mandate and an 11-page document still read,
   preview, replace and delete; no new document/page; nothing deleted.
5. **The page cap is per document, not per request** — an image-only add that takes a document past 10 pages is
   refused, and a PDF's expansion counts against the same 10; nothing is stored on the refusal.
6. **Partner Central with JavaScript off** — the count, the cap notice and the hint are server-rendered; the capped
   add page posts nothing; each server code shows the approved French copy (add vs replace, photo vs PDF document).

---

### Task 1: The document kind and the two refusal codes (`ndopify-dto`)

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/DocumentSource.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/MandateDocument.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`
- Test: none (the DTO has no tests; the server endpoint tests cover it)

**Interfaces:**
- Produces: `enum class DocumentSource { UNKNOWN, IMAGE, PDF }`; `MandateDocument.source: DocumentSource = DocumentSource.UNKNOWN`;
  `ErrorCode.MANDATE_DOCUMENT_LIMIT_REACHED = "urn:wutsi:ndopify:error:mandate-document:limit-reached"`;
  `ErrorCode.MANDATE_DOCUMENT_MIXED_SOURCES = "urn:wutsi:ndopify:error:mandate-document:mixed-sources"`.

- [ ] **Step 1: Add `DocumentSource`**

One file per enum under `mandate/dto`, values starting with `UNKNOWN` (the repo's enum rule), with a KDoc line: the
files a document was created from — images, or one PDF rendered to image pages; `UNKNOWN` for rows written before the
kind was recorded (they accept either on a page-level write).

- [ ] **Step 2: Add `source` to `MandateDocument`**

After `status`, as `val source: DocumentSource = DocumentSource.UNKNOWN`, so Jackson builds it and a client may omit
it. No other field changes; `MandateDocumentPage` is untouched.

- [ ] **Step 3: Add the two error codes**

In the `MANDATE_DOCUMENT_*` group, alphabetical: `MANDATE_DOCUMENT_LIMIT_REACHED` after `MANDATE_DOCUMENT_LAST_PAGE`,
then `MANDATE_DOCUMENT_MIXED_SOURCES`, before `MANDATE_DOCUMENT_NOT_FOUND`. KDoc: a mandate holds 5 documents at most;
a document is photos or one PDF, never both nor two PDFs.

- [ ] **Step 4: Build the DTO**

Run: `cd modules/ndopify-dto && mvn install`
Expected: BUILD SUCCESS (ktlint clean). The rest of the branch builds against this local install until the release.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/DocumentSource.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/mandate/dto/MandateDocument.kt \
  modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt
git commit -m "feat(dto): add the mandate document kind and limit error codes" -m "Refs #369"
```

The developer's release step releases `ndopify-dto` from these commits; later tasks run against the released version.

---

### Task 2: The server stores the document's kind

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateDocumentEntity.kt`
- Create: `modules/ndopify-server/src/main/resources/db/migration/common/V20_0__mandate-document-source.sql`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateDocumentRepository.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateDocumentMapper.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateDocumentEndpointTest.kt`
  and its `src/test/resources/db/test/mandate/GetMandateDocumentEndpoint.sql`

**Interfaces:**
- Consumes: `DocumentSource` from Task 1.
- Produces: `MandateDocumentEntity.source: DocumentSource = DocumentSource.UNKNOWN`;
  `MandateDocumentRepository.countByMandateId(mandateId: Long): Long`;
  `MandateDocument.source` on every document read.

- [ ] **Step 1: Write the failing endpoint test**

In `GetMandateDocumentEndpointTest`, add two cases (and their rows in the SQL fixture, inserted with the new column):
a document whose `source` is `'PDF'` returns `document.source == DocumentSource.PDF`; a document whose `source` is
`NULL` returns `DocumentSource.UNKNOWN`. Assert with `assertEquals`.

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateDocumentEndpointTest`
Expected: FAIL — `source` is `UNKNOWN` even for the PDF row (the mapper doesn't read it yet).

- [ ] **Step 2: Add the entity field, the converter and the migration**

- Entity: `val source: DocumentSource = DocumentSource.UNKNOWN` after `status`.
- `EnumConverters.kt`: `@Converter(autoApply = true) class DocumentSourceConverter : EnumConverter<DocumentSource>(DocumentSource::class.java, DocumentSource.UNKNOWN)` in the mandate group (a NULL column or an unknown name reads `UNKNOWN`).
- Migration `V20_0__mandate-document-source.sql`, after V19_1: `ALTER TABLE T_MANDATE_DOCUMENT ADD COLUMN source VARCHAR(30) DEFAULT NULL;` — nullable, so every existing row reads `UNKNOWN`. Never modify V19_0.

- [ ] **Step 3: Map it and add the count query**

`MandateDocumentMapper.toMandateDocument`: `source = entity.source`. `MandateDocumentRepository`:
`fun countByMandateId(mandateId: Long): Long` (the tenant filter applies, as with the other derived queries).

- [ ] **Step 4: Run the test**

Run: `cd modules/ndopify-server && mvn test -Dtest=GetMandateDocumentEndpointTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/domain/MandateDocumentEntity.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/util/jpa/EnumConverters.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/dao/MandateDocumentRepository.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/mapper/MandateDocumentMapper.kt \
  modules/ndopify-server/src/main/resources/db/migration/common/V20_0__mandate-document-source.sql \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/GetMandateDocumentEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/mandate/GetMandateDocumentEndpoint.sql
git commit -m "feat(server): record the kind of a mandate document" -m "Refs #369"
```

---

### Task 3: The 5-document cap on the create paths

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt`
  (`create`, `upload`, companion, helpers, KDoc)
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateDocumentEndpointTest.kt` and `UploadMandateDocumentEndpointTest.kt`, with their SQL fixtures

**Interfaces:**
- Consumes: `MandateDocumentRepository.countByMandateId`.
- Produces: `MandateDocumentService.MAX_DOCUMENTS = 5`;
  `private fun limitReached(): BadRequestException` (400 `MANDATE_DOCUMENT_LIMIT_REACHED`, no parameter, no value);
  `private fun ensureDocumentSlot(mandateId: Long)`.

- [ ] **Step 1: Write the failing service tests**

In `MandateDocumentServiceTest` (mockito-kotlin; `countByMandateId` returns 5):

- `create - a 6th document is refused and nothing is stored`: `assertFailsWith<BadRequestException>` → code
  `MANDATE_DOCUMENT_LIMIT_REACHED`, `parameter == null`; `verify(documentDao, never()).save(...)`,
  `verify(storage, never()).store(...)`, `verify(previewService, never()).generate(...)`.
- `create - the 5th document is created`: count 4 → one document saved, one page per path.
- `upload - a 6th document is refused before a file is stored`: `verifyNoInteractions(pdfRasterizer)` and no store.
- `create - the count is re-checked under the mandate lock`: `countByMandateId` returns 4 on the pre-check then 5
  inside `saveLocked` (use `doReturn(4L, 5L).whenever(documentDao).countByMandateId(any())`) → refused, no save.

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: FAIL — no `MAX_DOCUMENTS`/limit code yet.

- [ ] **Step 2: Implement the cap**

In `create` and `upload`, right after `ensureWritable(mandateId)`, call `ensureDocumentSlot(mandateId)`; inside each
`saveLocked` block, before saving the document, call it again (authoritative, under the mandate's lock). Add
`MAX_DOCUMENTS = 5` to the companion and `limitReached()`; extend each method's KDoc error order with the 400
`MANDATE_DOCUMENT_LIMIT_REACHED` after the 409 `MANDATE_ALREADY_CLOSED`. Do not touch `rename`, the page writes or
`delete`.

- [ ] **Step 3: Run the service tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: PASS.

- [ ] **Step 4: Write the failing endpoint tests**

- `CreateMandateDocumentEndpointTest`: with 5 stored documents, `POST /v1/mandates/{id}/documents` → 400
  `MANDATE_DOCUMENT_LIMIT_REACHED`; the count and the pages are unchanged; with 4, it creates the 5th.
- `UploadMandateDocumentEndpointTest`: the same for `POST .../documents/upload`.

Run: `cd modules/ndopify-server && mvn test -Dtest='CreateMandateDocumentEndpointTest,UploadMandateDocumentEndpointTest'`
Expected: FAIL.

- [ ] **Step 5: Run them green, then commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateDocumentEndpointTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UploadMandateDocumentEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/mandate/CreateMandateDocumentEndpoint.sql \
  modules/ndopify-server/src/test/resources/db/test/mandate/UploadMandateDocumentEndpoint.sql
git commit -m "feat(server): cap a mandate at 5 documents" -m "Refs #369"
```

---

### Task 4: The kind on create, the mixed refusal, the create page cap

**Files:**
- Modify: `MandateDocumentService.kt` (`create`, `upload`, helpers, KDoc)
- Test: `MandateDocumentServiceTest.kt`, `CreateMandateDocumentEndpointTest.kt`, `UploadMandateDocumentEndpointTest.kt`

**Interfaces:**
- Produces: `private fun mixedSources(field: String): BadRequestException` (400 `MANDATE_DOCUMENT_MIXED_SOURCES`, parameter `field`);
  `private fun pageCap(field: String): BadRequestException` (400 `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`, parameter `field`);
  the saved `MandateDocumentEntity.source` is `IMAGE` on `create`, `IMAGE` or `PDF` on `upload`.

- [ ] **Step 1: Write the failing service tests**

- `create - the document is an image document`: the saved entity has `source == DocumentSource.IMAGE`.
- `create - more than 10 paths is the page cap`: 11 paths → `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` on `paths`, no
  store.
- `upload - images only is an image document` (`source == IMAGE`), `upload - one PDF is a PDF document` (`source ==
  DocumentSource.PDF`, pages are the rendered ones).
- `upload - an image and a PDF is refused`: `MANDATE_DOCUMENT_MIXED_SOURCES` parameter `files`; the image already
  stored is deleted (`verify(storage).delete(...)`), no document, no page.
- `upload - two PDFs is refused`: same code, `verifyNoInteractions(pdfRasterizer)` (the kind is detected before any
  rendering).
- `upload - 11 images is the page cap`: `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` parameter `files`, no document, the
  stored images deleted.

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: FAIL.

- [ ] **Step 2: Classify the files before storing**

In `upload`, after the limit checks and before the storage loop: read each file's Tika MIME once into a list; an
unknown type is `invalidFile("file")`; count PDFs and images; `pdfs > 1 || (pdfs == 1 && images > 0)` →
`mixedSources("files")`; `source = if (pdfs == 1) DocumentSource.PDF else DocumentSource.IMAGE`. Remove the old
`hasPdf`/"image-only add has no page cap" rule: the running page total (`pages.size + fileSources.size`) is now
compared to `MAX_PAGES` for every add, and exceeding it is `pageCap("files")`. Save the document with `source`.
In `create`, refuse `paths.size > MAX_PAGES` with `pageCap("paths")` and save `source = DocumentSource.IMAGE`.

- [ ] **Step 3: Run the service tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: PASS.

- [ ] **Step 4: Extend the endpoint tests**

- `CreateMandateDocumentEndpointTest`: the created document's `source` is `IMAGE`; 11 paths → 400
  `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`.
- `UploadMandateDocumentEndpointTest`: images → `IMAGE`; one PDF → `PDF`; image + PDF → 400
  `MANDATE_DOCUMENT_MIXED_SOURCES`; two PDFs → the same; 11 images → 400 `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`; each
  refusal leaves the store and the repositories as they were.

Run: `cd modules/ndopify-server && mvn test -Dtest='CreateMandateDocumentEndpointTest,UploadMandateDocumentEndpointTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/CreateMandateDocumentEndpointTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UploadMandateDocumentEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/mandate/CreateMandateDocumentEndpoint.sql \
  modules/ndopify-server/src/test/resources/db/test/mandate/UploadMandateDocumentEndpoint.sql
git commit -m "feat(server): give a document its kind and refuse a mixed create" -m "Refs #369"
```

---

### Task 5: Page-level writes keep the kind and the 10-page cap

**Files:**
- Modify: `MandateDocumentService.kt` (`addPage`, `uploadPage`, `replacePage`, `uploadReplacementPage`, helpers, KDoc)
- Test: `MandateDocumentServiceTest.kt`, `AddMandateDocumentPageEndpointTest.kt`, `ReplaceMandateDocumentPageEndpointTest.kt`
  and their SQL fixtures

**Interfaces:**
- Produces: `private fun ensureKind(document: MandateDocumentEntity, mimeType: String?, field: String)` — an `IMAGE`
  document accepts `image/*`, a `PDF` document `application/pdf`, `UNKNOWN` either, else `mixedSources(field)`;
  `private fun ensureCanAddPages(document: MandateDocumentEntity, count: Int, field: String)` — the document's existing
  pages + `count` > `MAX_PAGES` → `pageCap(field)`.

- [ ] **Step 1: Write the failing add-page tests**

In `MandateDocumentServiceTest`:

- `uploadPage - an image document refuses a PDF` (`MANDATE_DOCUMENT_MIXED_SOURCES` on `file`; no store, no rasterizer)
  and `- a PDF document refuses an image`.
- `uploadPage - a document with 9 pages refuses a PDF of 2 pages` (`MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` on `file`) and
  `- accepts the page that brings it to exactly 10`.
- `addPage - a PDF document refuses an image path` (`MANDATE_DOCUMENT_MIXED_SOURCES` on `path`, no `resetExtraction`).
- `addPage - an 11th page is refused` and nothing saved.

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: FAIL.

- [ ] **Step 2: Implement add/add-page**

`addPage`: after `ensureNotClosed`, `ensureKind(document, mimeType, "path")` (the path is verified as an image by
`directUpload.verify`), `ensureCanAddPages(document, 1, "path")`; re-check inside `saveLocked` with the current page
count. `uploadPage`: detect the MIME once, `ensureKind(document, mimeType, "file")`, expand, `ensureCanAddPages(document,
sources.size, "file")`, and re-check inside `saveLocked` before saving. A PDF document's PDF is expanded with
`maxPages = MAX_PAGES`; an `UNKNOWN` document also accepts a PDF (as #366).

- [ ] **Step 3: Write the failing replacement tests**

- `uploadReplacementPage - an image document refuses a PDF` and `- a PDF document refuses an image`
  (`MANDATE_DOCUMENT_MIXED_SOURCES` on `file`; the page, its path and the document's extraction are unchanged).
- `uploadReplacementPage - a PDF document refuses a 2-page PDF` (`MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`, page
  unchanged) and `- accepts a one-page PDF`.
- `replacePage - a PDF document refuses an image path` (`MANDATE_DOCUMENT_MIXED_SOURCES` on `path`).
- `uploadReplacementPage - an UNKNOWN document accepts a photo and a one-page PDF`.
- `uploadReplacementPage - a page of an 11-page document is replaced` (no page-cap check).

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: FAIL.

- [ ] **Step 4: Implement replacement**

`replacePage`: `ensureKind(document, mimeType, "path")` after `ensureNotClosed`, no page-count check.
`uploadReplacementPage`: detect the MIME once, `ensureKind(document, mimeType, "file")`, then
`pageSources(..., maxPages = 1)` (a multi-page PDF is `pageCap("file")`), no page-count check. Extend the KDoc of the
four methods with the new codes in their checked order.

- [ ] **Step 5: Run the service tests, then the endpoint tests**

Add the matching endpoint cases to `AddMandateDocumentPageEndpointTest` and `ReplaceMandateDocumentPageEndpointTest`
(kind refusals, the 11th page, an 11-page document's replacement, an UNKNOWN document accepting both), with rows in
their SQL fixtures (`source` `'IMAGE'`, `'PDF'` and `NULL`).

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateDocumentServiceTest,AddMandateDocumentPageEndpointTest,ReplaceMandateDocumentPageEndpointTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AddMandateDocumentPageEndpointTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/ReplaceMandateDocumentPageEndpointTest.kt \
  modules/ndopify-server/src/test/resources/db/test/mandate/AddMandateDocumentPageEndpoint.sql \
  modules/ndopify-server/src/test/resources/db/test/mandate/ReplaceMandateDocumentPageEndpoint.sql
git commit -m "feat(server): keep a document's kind and page cap on every page write" -m "Refs #369"
```

---

### Task 6: Grandfathered data (tests)

**Files:**
- Test: `MandateDocumentServiceTest.kt`, `SearchMandateDocumentEndpointTest.kt`,
  `AddMandateDocumentPageEndpointTest.kt`, `UploadMandateDocumentEndpointTest.kt` and their SQL fixtures

**Interfaces:**
- Consumes: the behaviour of Tasks 2–5. No production change is expected; fix the code only if a test fails.

- [ ] **Step 1: Write the grandfathered tests**

- `mandate with 6 documents`: the search returns all 6; `create`/`upload` refuse the 7th with
  `MANDATE_DOCUMENT_LIMIT_REACHED`; a delete succeeds and the 5 remaining allow a new one.
- `document with 11 pages` (`source` NULL): `GET` returns its 11 pages; a page replacement succeeds; `addPage` and
  `uploadPage` refuse with `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`; a page delete succeeds.
- `document with source NULL`: accepts a photo and a one-page PDF on a replacement (both succeed), and an image page
  add.

- [ ] **Step 2: Run them**

Run: `cd modules/ndopify-server && mvn test -Dtest='MandateDocumentServiceTest,SearchMandateDocumentEndpointTest,AddMandateDocumentPageEndpointTest,UploadMandateDocumentEndpointTest'`
Expected: PASS (no production change). If a test fails, fix only the rule it exposes, then rerun.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint \
  modules/ndopify-server/src/test/resources/db/test/mandate
git commit -m "test(server): cover the grandfathered mandate documents" -m "Refs #369"
```

---

### Task 7: Partner Central rules and views

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentLabels.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt`
- Test: `MandateDocumentRulesTest.kt`, `MandateDocumentLabelsTest.kt`, `MandateDocumentViewsTest.kt`

**Interfaces:**
- Produces:
  - `MandateDocumentRules.MAX_DOCUMENTS = 5`, `fun atLimit(count: Int): Boolean`, `fun mixed(pages: List<SniffedPage>): Boolean`,
    `fun source(pages: List<SniffedPage>): DocumentSource`, `fun validatePage(page: SniffedPage?, source: DocumentSource): String?`,
    `fun accept(source: DocumentSource): String`, `fun replaceHint(source: DocumentSource): String`,
    `fun replaceRequired(source: DocumentSource): String`, `fun mixedReplace(source: DocumentSource): String`,
    and `MIXED_SOURCES`, `DOCUMENT_LIMIT` copy constants.
  - `MandateDocumentLabels.source(source: DocumentSource): String` → `"PDF"`, `"Photos"`, `"Photos ou PDF"`.
  - `DocumentSectionView` gains `count: Int` and `atLimit: Boolean`; `DocumentView` gains `source: DocumentSource`,
    `sourceLabel: String`, `replaceAccept: String`, `replaceHint: String`, `replaceRequired: String`.

- [ ] **Step 1: Write the failing rules tests**

`MandateDocumentRulesTest`:
- `atLimit` is true at 5 and false at 4.
- `mixed` is true for a photo + a PDF and two PDFs, false for photos only and one PDF.
- `source` returns `IMAGE` for photos only, `PDF` for one PDF, and never throws on a mixed list (the caller refuses it).
- `validateAdd` keeps its existing errors and adds the mixed list's error on `files`; a mixed list is refused before
  the file-count rule.
- `validatePage`: a PDF of one page is accepted for a `PDF` document and refused for an `IMAGE` one; a photo is the
  reverse; an `UNKNOWN` document accepts both.
- `accept(PDF) == "application/pdf"`, `accept(IMAGE)` is the three image types, `accept(UNKNOWN)` both.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentRulesTest`
Expected: FAIL — no such members.

- [ ] **Step 2: Implement the rules and labels**

Mirror the approved prototype's copy exactly:
- `MIXED_SOURCES = "Un document contient soit des photos, soit un PDF, jamais les deux : choisissez de nouveau les pages."`
- `DOCUMENT_LIMIT = "Un mandat peut avoir 5 documents au plus."`
- `replaceHint(PDF) = "PDF d'une page, 5\u00a0Mo au plus. Ce document a été créé à partir d'un PDF\u00a0: seul un PDF d'une page remplace cette page."`
- `replaceHint(IMAGE) = "Photo JPEG, PNG ou WEBP, 5\u00a0Mo au plus. Ce document a été créé à partir de photos\u00a0: une photo remplace cette page."`
- `replaceHint(UNKNOWN) = "Photo JPEG, PNG ou WEBP, ou PDF d'une page, 5\u00a0Mo au plus. Le fichier prend la place de cette page."`
- `replaceRequired(PDF) = "Choisissez un PDF d'une page pour cette page."`, `replaceRequired(IMAGE) = "Choisissez une photo pour cette page."`, `replaceRequired(UNKNOWN) = "Choisissez la nouvelle page : une photo ou un PDF."`
- `mixedReplace(PDF) = "Ce document a été créé à partir d'un PDF : remplacez cette page par un PDF d'une page, pas par une photo."`
- `mixedReplace(IMAGE) = "Ce document a été créé à partir de photos : remplacez cette page par une photo JPEG, PNG ou WEBP, pas par un PDF."`
- `mixedReplace(UNKNOWN) = "Ce document accepte une photo ou un PDF d'une page : vérifiez le fichier."`

- [ ] **Step 3: Write the failing view tests**

`MandateDocumentViewsTest`: `section` with 3 documents → `count == 3`, `atLimit == false`; with 5 → `atLimit == true`;
with a null (unreadable) list → `count == 0`, `atLimit == false`. `document` carries `source`, the pill label and the
replacement accept/hint/required of the document's source.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentViewsTest`
Expected: FAIL.

- [ ] **Step 4: Implement the views**

`section`: `count = documents?.size ?: 0`, `atLimit = documents != null && MandateDocumentRules.atLimit(count)`.
`document`: `source = document.source`, `sourceLabel = MandateDocumentLabels.source(source)`,
`replaceAccept = MandateDocumentRules.accept(source)`, `replaceHint = ...`, `replaceRequired = ...`.

- [ ] **Step 5: Run the three test classes, then commit**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='MandateDocumentRulesTest,MandateDocumentLabelsTest,MandateDocumentViewsTest'`
Expected: PASS.

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRules.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentLabels.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRulesTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentLabelsTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt
git commit -m "feat(npc): model a mandate's document caps and kind" -m "Refs #369"
```

---

### Task 8: Partner Central controller

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt`
- Test: `MandateDocumentControllerTest.kt`

**Interfaces:**
- Consumes: `MandateDocumentRules.*` and the views of Task 7.
- Produces (model attributes): the add page gains `atLimit: Boolean` (and `documentsCount: Int`); the add refusal adds
  `limitError: Boolean`. `DocumentView` carries the source fields for the document page.

- [ ] **Step 1: Write the failing controller tests**

`MandateDocumentControllerTest`:
- `GET /mandates/{id}/documents/new` with 5 documents → `VIEW_NEW`, model `atLimit == true`, `documentsCount == 5`.
- the same with 2 documents → `atLimit == false`, `documentsCount == 2`.
- the documents read failing (5xx) → the form is rendered with `atLimit == false` (the server still enforces).
- `POST /mandates/{id}/documents` refused by ndopify-server with 400 `MANDATE_DOCUMENT_LIMIT_REACHED` → `VIEW_NEW`,
  model `limitError == true`, the type/title kept, no redirect.
- refused with `MANDATE_DOCUMENT_MIXED_SOURCES` → `errors["files"]` is `MandateDocumentRules.MIXED_SOURCES`.
- refused with `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` → `errors["files"]` is the page-cap copy.
- a replacement refused with `MANDATE_DOCUMENT_MIXED_SOURCES` on a `PDF` document → `replaceErrors[pageId]` is
  `mixedReplace(PDF)`.
- the document page model's `document.source` is the DTO's source.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentControllerTest`
Expected: FAIL.

- [ ] **Step 2: Implement the controller**

- `addForm`: read the mandate's documents with `documentClient.search(mandate.id)` inside the existing try/catch
  pattern; a failure leaves the count unknown (`atLimit = false`); add `atLimit` and `documentsCount` to the model.
- `add`: check `atLimit(documentsCount)` before sending (render the notice, nothing posted); catch 400
  `MANDATE_DOCUMENT_LIMIT_REACHED` → render the form with `limitError = true`, the type/title kept; map
  `MANDATE_DOCUMENT_MIXED_SOURCES` and `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` through `pageServerErrors` to `files`.
- `document`: pass `document.source` (the view builds the rest).
- `replacePage`: `pageServerErrors` also words `MANDATE_DOCUMENT_MIXED_SOURCES` by the document's source; a
  `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` copy for a PDF document doesn't offer a photo.
- Keep the existing 403/404/409 → `changed` and 5xx behaviour untouched.

- [ ] **Step 3: Run the controller tests, then commit**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentControllerTest`
Expected: PASS.

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt
git commit -m "feat(npc): word the document caps and kind refusals" -m "Refs #369"
```

---

### Task 9: Port the approved prototype to the templates and the stylesheet

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-documents.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document-new.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/document.css`
- Test: `MandateDocumentTemplateTest.kt`

**Interfaces:**
- Consumes: `DocumentSectionView.count` / `.atLimit`, `DocumentView.sourceLabel` / `.replaceAccept` / `.replaceHint` /
  `.replaceRequired`, and the controller's `atLimit` / `limitError` / `documentsCount` model attributes.

- [ ] **Step 1: Write the failing template tests**

`MandateDocumentTemplateTest`:
- the mandate page at 5 renders `5/5` and the cap notice, and no `Ajouter un document`; at 3 it renders `3/5` and the
  action.
- the add page with `atLimit` renders the cap notice, the way back, and no `<form>`; without it renders the form and
  the hint *5 documents par mandat, 10 pages au plus, soit des photos soit un PDF*.
- the add page with `limitError` renders the form again with the alert and the kept type/title.
- the document page renders the *PDF* pill and `accept="application/pdf"` for a PDF document, the *Photos* pill and the
  image accept for an image document, and the *Photos ou PDF* pill with both for an UNKNOWN one.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: FAIL.

- [ ] **Step 2: Port the section and the add page**

Copy the approved prototype's markup and copy into the Thymeleaf templates, replacing the static sample values with
the model:
- `fragments/mandate-documents.html`: `.documents__head` gets `.documents__title` with the heading and
  `<p class="documents__count"><span class="visually-hidden" th:text="|${section.count} documents sur 5|">3 documents sur 5</span><span aria-hidden="true" th:text="|${section.count}/5|">3/5</span></p>`;
  the action renders only while `section.writable and !section.atLimit`; at the limit,
  `<p class="documents__limit">Un mandat peut avoir 5 documents au plus.</p>`.
- `document-new.html`: the cap notice (`atLimit`) instead of the form, with *Supprimez un document du mandat pour en
  ajouter un autre.* and the back link; the form's hint as approved; a form-level alert for `limitError`; the form
  carries `th:attr="data-documents-count=${documentsCount}"` for the script.
- `document.html`: the source pill `<span class="status-tag" data-document-source th:text="${document.sourceLabel}">PDF</span>`
  in `.doc-intro__head`; the replace input's `th:accept="${document.replaceAccept}"`; the hint
  `th:text="${document.replaceHint}"`; the error `th:text="${replaceErrors[page.id]}"`.
- `document.css`: port `.documents__title`, `.documents__count`, `.documents__limit` and the add page's alert spacing
  from the prototype, using the existing tokens.

- [ ] **Step 3: Run the template tests, then commit**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: PASS.

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/mandate-documents.html \
  modules/ndopify-partner-central/src/main/resources/templates/mandate/document-new.html \
  modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html \
  modules/ndopify-partner-central/src/main/assets/css/document.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt
git commit -m "feat(npc): show the document caps and the kind" -m "Refs #369"
```

---

### Task 10: The page script blocks a mixed add and checks the replacement's kind

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/assets/js/mandate-document.js`
- Test: `modules/ndopify-partner-central/src/test/js/page-scripts.test.mjs` (and the fixture writes in
  `MandateDocumentTemplateTest`)

**Interfaces:**
- Consumes: `data-documents-count` on the add form, `data-document-source` on the document page, the server-rendered
  accept/hint.
- Produces: `MAX_DOCUMENTS = 5`; `mixedProblem(chosen)`; a submit refusal at the cap; a per-source replacement check.

- [ ] **Step 1: Write the failing script tests**

In `page-scripts.test.mjs`, on the rendered fixtures:
- an add page below the cap, choosing a photo then a PDF (and two PDFs) → the pages field's error is the mixed copy and
  the form is not submitted (the test's `submit` event does not post);
- an add page whose form carries `data-documents-count="5"` → submitting nothing at the cap is refused with the limit
  copy;
- a `PDF` document's replace form with a photo → the field's error is `mixedReplace(PDF)`, the form not submitted; with
  a one-page PDF it proceeds;
- an `IMAGE` document's replace form with a PDF → `mixedReplace(IMAGE)`.

Run: `cd modules/ndopify-partner-central && mvn test` (the Maven `test` phase runs `page-scripts.test.mjs`)
Expected: FAIL.

- [ ] **Step 2: Implement the script checks**

Mirror the prototype's `mandate-documents.js`:
- read `MAX_DOCUMENTS` and the form's `data-documents-count`; on submit, refuse when `count >= MAX_DOCUMENTS`;
- `mixedProblem(chosen)` (a PDF with any other file, or two PDFs) on add, shown on the pages field and blocking the
  submit;
- on the document page, read `data-document-source`; the replace form refuses a photo for `PDF` and a PDF for `IMAGE`
  with `mixedReplace(...)`; the PDF refusal copies stop offering a photo for a PDF document.
Keep the direct upload, the multipart fallback and the no-JS path unchanged.

- [ ] **Step 3: Run the tests, then commit**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS (surefire, the Thymeleaf tests and the page-script tests).

```bash
git add modules/ndopify-partner-central/src/main/assets/js/mandate-document.js \
  modules/ndopify-partner-central/src/test/js/page-scripts.test.mjs \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt
git commit -m "feat(npc): block a mixed add and a wrong-kind replacement in the browser" -m "Refs #369"
```

---

### Task 11: Document the behaviour and verify the modules

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **Documents** paragraph of the *mandate* domain)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the mandate's **documents** paragraph)
- Verify: the three touched modules

**Interfaces:** none; documentation only.

- [ ] **Step 1: Update the server's Documents paragraph**

State: the 5-document cap on the two create paths (checked under the mandate lock, `MANDATE_DOCUMENT_LIMIT_REACHED`);
the 10-page cap after PDF expansion (`MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`) on create and page writes; one kind per
document (`source`: `IMAGE`/`PDF`, `UNKNOWN` for the rows written before `V20_0`, which accept either); a mixed
request (`MANDATE_DOCUMENT_MIXED_SOURCES`); and that nothing is stored on a refusal. Keep the existing access,
preview, extraction and privacy text.

- [ ] **Step 2: Update Partner Central's documents paragraph**

State: the compact `n/5` count, the hidden add action at 5 with *Un mandat peut avoir 5 documents au plus.*, the add
page's notice at the cap, the kind pill and the kind-aware *Remplacer*, the French copies of the new codes, and the
script's checks (mixed selection, count, kind) with the no-JS server errors.

- [ ] **Step 3: Verify each module**

```bash
cd modules/ndopify-dto && mvn verify
cd ../ndopify-server && mvn verify        # needs the local MySQL
cd ../ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS in each (ktlint, tests, JaCoCo; the server's endpoint tests use MySQL, and the web app runs
its page scripts after surefire).

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(server,npc): document the mandate document caps" -m "Refs #369"
```

---

## Self-Review Notes

- Every acceptance criterion is covered: AC 1 (Tasks 3, 8, 9), AC 2 (Tasks 4, 5), AC 3 (Task 5), AC 4 (Tasks 4, 2),
  AC 5 (Task 4), AC 6 (Task 6), AC 7 (Tasks 7–10), AC 8 (unchanged paths, asserted by the existing access/privacy
  tests, and no new endpoint).
- Types reused consistently: `DocumentSource` (DTO, entity, rules, views), `MAX_DOCUMENTS`/`MAX_PAGES`, the two new
  codes plus #366's page-cap code, `ensureDocumentSlot`/`ensureKind`/`ensureCanAddPages`.
- The plan's first UI step (Task 9) ports the approved prototype; no task redesigns it.
