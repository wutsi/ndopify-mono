# Mandate documents accept PDF files — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An agent adds a PDF to a mandate's document — or replaces a page with one — and ndopify-server renders every
PDF page to a JPEG page, stored and read exactly like a photo page, while Partner Central offers PDF next to
JPEG/PNG/WEBP with the approved copy.

**Architecture:** A PDFBox 3 rasterizer in `platform/image` turns a PDF into one JPEG per page (200 DPI max, longest
side 2000 px). `MandateDocumentService` expands a PDF in its multipart add / add-page / replace-page paths before
storing; the presigned JSON paths and `DirectUploadService` stay images-only. Three `ndopify-dto` error codes let
Partner Central word each refusal in French. The approved prototype ports to the two Thymeleaf templates and
`mandate-document.js`.

**Tech Stack:** Kotlin, Spring Boot, PDFBox 3.0.8 (already a dependency), Apache Tika, Thymeleaf, vanilla JavaScript,
JUnit 5 / `kotlin.test` / mockito-kotlin, `MockMvc` + real Thymeleaf templates, `TestRestTemplate` + MySQL, Maven with
ktlint and JaCoCo.

**Spec:** GitHub issue #366 (enriched body and acceptance criteria); approved prototype under
`modules/ndopify-partner-central/src/main/html/v2` (approved 2026-11-26).

## Global Constraints

- `UI: yes`. The approved prototype is the baseline: its markup, states and French copy are ported to the production
  templates (`templates/mandate/document-new.html`, `templates/mandate/document.html`) and
  `src/main/assets/js/mandate-document.js`. Do not design again.
- `Shared code: ndopify-dto` — three `ErrorCode` constants, released as `0.0.109` before `ndopify-server` and
  `ndopify-partner-central` deploy. No `ndopify-web-core` change, no DTO request/response field.
- A PDF is accepted only in the multipart endpoints: `POST /v1/mandates/{id}/documents/upload`,
  `POST /v1/mandates/{id}/documents/{documentId}/pages/upload`,
  `POST /v1/mandates/{id}/documents/{documentId}/pages/{pageId}/upload`. `presign`, `POST /documents` (paths),
  `POST /documents/{documentId}/pages` and `POST /documents/{documentId}/pages/{pageId}` stay images-only;
  `DirectUploadService` is unchanged (images-only, 5 MB).
- One PDF page becomes one stored page: a JPEG at `mandate/{id}/documents/{uuid}.jpg`, `mimeType = image/jpeg`, in the
  PDF's order, at most 2000 px on its longest side and rendered at most at 200 DPI. The uploaded PDF is never stored.
- Caps: 5 MB per uploaded file; one PDF in an add renders at most 10 pages; a `upload` with a PDF refuses more than 10
  pages once expanded (images count one page); `pages/upload` refuses more than 10 pages from one PDF;
  `pages/{pageId}/upload` requires exactly one page from a PDF.
- Refusals: 400 `MANDATE_DOCUMENT_PDF_ENCRYPTED`, `MANDATE_DOCUMENT_PDF_UNREADABLE` or
  `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`; `HTTP_INVALID_PARAMETER` for a bad type, a file over 5 MB, a path outside the
  folder. Parameter is `file` (multipart) or `path` (presigned), `PARAMETER_TYPE_PAYLOAD`, and no value, filename, path
  or message that names the PDF.
- Every refused add leaves no document row, page row, stored original or preview.
- No PDF bytes, filename, path or extracted value in any log or error body. A rendered page is visible only through
  the authenticated, watermarked preview of #347.
- French user-facing copy, exactly as approved in the prototype; English code, comments, logs and docs.
- Tests: `kotlin.test` assertions (`assertEquals`, `assertTrue`, `assertFailsWith`…), mockito-kotlin mocks, one
  endpoint test class per operation, template tests with a real Thymeleaf engine. `mvn verify` green in each module
  (ktlint, tests, JaCoCo); `ndopify-server` needs a local MySQL.
- No `.pdf` binary fixture in the repository: tests generate small PDFs with PDFBox in memory.

## Review Focus

1. **A page rendered from a PDF is an ordinary page** — the preview endpoint returns its watermarked JPEG, the
   extraction re-encodes it with `ImageResizer`, #331's fields display, and no `.pdf` object exists in the storage —
   Tasks 2–4.
2. **A refused add leaves nothing** — a corrupt/encrypted/oversized PDF after a valid image must delete the image's
   object and preview, store no page row and no document; the endpoint test asserts `storage.delete` and empty
   repositories — Tasks 3–4.
3. **A replace refusal does not touch the page** — a multi-page PDF leaves the page's path, preview, `pageIndex` and
   the document's extracted data unchanged; a one-page PDF keeps the rank and resets the extraction — Tasks 3–4.
4. **The caps are enforced without a 500** — 11 pages after expansion, a PDF over 5 MB, an encrypted PDF, a corrupt
   or empty PDF, and a multi-page replace each answer their 400 code; no half-written object is served — Tasks 2–4.
5. **Order and privacy** — a mixed add keeps the files' order, a PDF expanded in place, page indices contiguous from
   0; no log or error names the PDF — Tasks 2–4.

---

### Task 1: The three PDF error codes (`ndopify-dto`)

**Files:**
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt`
- Modify: root `pom.xml` (`<ndopify-dto.version>`)

**Interfaces:**
- Produces: `ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED`, `ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE`,
  `ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`, each a `urn:wutsi:ndopify:error:mandate-document:<reason>` string,
  in the `MANDATE_DOCUMENT_*` group in alphabetical order.

- [ ] **Step 1: Add the three constants** after `MANDATE_DOCUMENT_PAGE_NOT_FOUND` and before
  `MANDATE_DOCUMENT_VERSION_CONFLICT`, with a KDoc line on the group: a PDF is refused when it needs a password, cannot
  be read or has no page, or renders more pages than the endpoint allows.

```kotlin
val MANDATE_DOCUMENT_PDF_ENCRYPTED = "$PREFIX:mandate-document:pdf-encrypted"
val MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES = "$PREFIX:mandate-document:pdf-too-many-pages"
val MANDATE_DOCUMENT_PDF_UNREADABLE = "$PREFIX:mandate-document:pdf-unreadable"
```

- [ ] **Step 2: Set the root `pom.xml` to the DTO's current snapshot**
  `<ndopify-dto.version>0.0.109-SNAPSHOT</ndopify-dto.version>` (the DTO's `pom.xml` is already `0.0.109-SNAPSHOT`).

- [ ] **Step 3: Build the DTO locally**

Run: `cd modules/ndopify-dto && mvn install`
Expected: BUILD SUCCESS (ktlint clean; the DTO has no tests).

- [ ] **Step 4: Commit and ship the DTO first**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/error/dto/ErrorCode.kt pom.xml
git commit -m "feat(dto): add the mandate document PDF error codes" -m "Refs #366"
```

Merge that change to `main` and wait for `ndopify-dto-main.yml` to release `0.0.109` (tag `ndopify-dto-0.0.109`).
Then set `<ndopify-dto.version>0.0.109</ndopify-dto.version>` and commit `fix(dependencies): use ndopify-dto 0.0.109`.
The server and Partner Central cannot build in CI against a `-SNAPSHOT`.

---

### Task 2: PDF rasterizer (`ndopify-server`, `platform/image`)

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/PdfRasterizationException.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/PdfRasterizer.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/PdfRasterizerTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the `platform/image` line — add the rasterizer)

**Interfaces:**
- Produces:

```kotlin
class PdfRasterizationException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    enum class Reason { ENCRYPTED, UNREADABLE, EMPTY, TOO_MANY_PAGES }
}

@Component
class PdfRasterizer {
    companion object {
        const val MAX_SIDE = 2000
        const val MAX_DPI = 200f
        const val QUALITY = 0.82f
    }

    /** One JPEG per page, in the PDF's order. */
    fun rasterize(pdf: ByteArray, maxPages: Int): List<ByteArray>
}
```

- `Loader.loadPDF(pdf)`; `org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException` → `ENCRYPTED`; a read failure →
  `UNREADABLE`; `numberOfPages == 0` → `EMPTY`; `numberOfPages > maxPages` → `TOO_MANY_PAGES` (before rendering).
- For each page: `scale = min(MAX_DPI / 72f, MAX_SIDE / max(cropBox.width, cropBox.height))`; 
  `PDFRenderer(document).renderImage(index, scale, ImageType.RGB)`; encode JPEG at `QUALITY` with ImageIO. The 2000-px
  side keeps every page under 4 MP, below the preview's 40 MP cap, so no separate pixel check is needed.
- Close the `PDDocument` in `use`.

- [ ] **Step 1: Write the failing tests**

Generate the fixtures in the test, no binary file:

```kotlin
private fun pdf(pages: Int, color: java.awt.Color = java.awt.Color.WHITE): ByteArray =
    PDDocument().use { document ->
        repeat(pages) {
            val page = PDPage(PDRectangle(200f, 100f))
            document.addPage(page)
            PDPageContentStream(document, page).use { content ->
                content.setNonStrokingColor(color)
                content.addRect(0f, 0f, 200f, 100f)
                content.fill()
            }
        }
        ByteArrayOutputStream().also { document.save(it) }.toByteArray()
    }
```

Tests: `one page -> one JPEG` (starts `FF D8`, decodes, about 556×278 at 200 DPI for a 200×100 pt page); `three pages
keep their order` (red, green, blue pages; decode each JPEG and assert the centre pixel); `a page over 2000 px on a
side is scaled to 2000` (a 4000×2000 pt page → 2000×1000); `more than maxPages is TOO_MANY_PAGES` (3 pages,
`maxPages = 2`); `an encrypted PDF is ENCRYPTED` (`StandardProtectionPolicy("owner", "user", AccessPermission())`,
128-bit, `protect` then save); `a corrupt PDF is UNREADABLE` (`"not a pdf".toByteArray()`); `an empty PDF is EMPTY` (a
`PDDocument` with no page, saved).

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=PdfRasterizerTest`
Expected: FAIL — `PdfRasterizer` / `PdfRasterizationException` undefined.

- [ ] **Step 3: Implement the exception and the rasterizer** as above. The JPEG encoder mirrors
  `ImagePreviewService.encode`: `ImageIO.getImageWritersByFormatName("jpeg")`, `MODE_EXPLICIT`, `quality = QUALITY`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=PdfRasterizerTest`
Expected: PASS.

- [ ] **Step 5: Document the component and commit**

Add `PdfRasterizer` to the `platform/image` line of `modules/ndopify-server/CLAUDE.md`: a PDF becomes one JPEG per
page, 200 DPI / 2000 px max, and the reasons it refuses.

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/PdfRasterizer.kt \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/PdfRasterizationException.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/PdfRasterizerTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): rasterize a PDF's pages to JPEGs" -m "Refs #366"
```

---

### Task 3: Multipart add / add page / replace accept PDFs (`ndopify-server`)

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (the **Documents** paragraph)

**Interfaces:**
- Consumes: `PdfRasterizer.rasterize(pdf, maxPages)` and `PdfRasterizationException.reason` from Task 2.
- Produces: `MandateDocumentService` constructor gains `private val pdfRasterizer: PdfRasterizer`; companion gains
  `const val MAX_PAGES = 10`.
- `private data class PageSource(val bytes: ByteArray, val mimeType: String)`.
- `private fun pageSources(file: MultipartFile, field: String, maxPages: Int = MAX_PAGES): List<PageSource>`:
  `Tika().detect(file.bytes)`; an image → `directUpload.check(file)` and one `PageSource(file.bytes, mime)`; a PDF →
  `file.size > DirectUploadService.MAX_BYTES` is `invalidFile(field)`, else `pdfRasterizer.rasterize(file.bytes,
  maxPages).map { PageSource(it, "image/jpeg") }`; anything else → `invalidFile(field)`.
- `private fun pdfError(reason: PdfRasterizationException.Reason, field: String)`:
  `BadRequestException(Error(code = when (reason) { ENCRYPTED -> ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED;
  UNREADABLE, EMPTY -> ErrorCode.MANDATE_DOCUMENT_PDF_UNREADABLE; TOO_MANY_PAGES ->
  ErrorCode.MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES }, parameter = Parameter(name = field,
  type = ParameterType.PARAMETER_TYPE_PAYLOAD)))` — no message, no value.
- `private fun invalidFile(field: String)`:
  `BadRequestException(Error(code = ErrorCode.HTTP_INVALID_PARAMETER, parameter = Parameter(name = field,
  type = ParameterType.PARAMETER_TYPE_PAYLOAD)))`.

Behavior:
- `upload`: each file expands to 1..n `PageSource`s; the page index is the running page count (not the file index),
  so a PDF's pages are contiguous and the next file follows. Track `hasPdf`; if `hasPdf` and the running total would
  exceed `MAX_PAGES`, throw `pdfError(TOO_MANY_PAGES, "files")`. Store each source at
  `"${prefix}/${UUID.randomUUID()}.${MimeUtils.getExtensionFromMimeType(source.mimeType)}"`, `storage.store(path,
  source.bytes.inputStream(), source.mimeType)`, `previewService.generate(..., original = source.bytes)`. On
  `PdfRasterizationException` clean up the stored paths and throw `pdfError(reason, "file")`; on
  `UnprocessableImageException` keep the existing `invalidImage("file")`; on any other exception clean up and rethrow.
  The uploaded PDF bytes are never stored.
- `uploadPage`: same, appending after `findMaxPageIndex`; a PDF's `maxPages` is `MAX_PAGES`, and a PDF over that is
  `pdfError(TOO_MANY_PAGES, "file")`; no per-document lifetime cap.
- `uploadReplacementPage`: `pageSources(file, "file", maxPages = 1)`; a PDF of more than one page is
  `pdfError(TOO_MANY_PAGES, "file")` and the old page, its object and its preview are untouched; a one-page PDF is
  stored as `.jpg`, replaces the page (same `pageIndex`), resets the extraction and deletes the old object + preview
  after the commit, like an image.
- `create`, `addPage`, `replacePage`, `presign`: unchanged (images-only; `verify` still rejects a `.pdf` path).

- [ ] **Step 1: Write the failing service tests**

Add `private val pdfRasterizer = mock<PdfRasterizer>()` and pass it to the constructor. A PDF file is
`MockMultipartFile("files", "scan.pdf", "application/pdf", byteArrayOf(1, 2, 3))`; a rendered page is
`byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())`. Tests:

- `upload - a PDF becomes its rendered pages in the PDF's order`: `doReturn(listOf(jpeg1, jpeg2)).whenever(pdfRasterizer)
  .rasterize(any(), eq(10))`; one document saved with 2 pages, indices 0 and 1, both paths ending `.jpg`, mime type
  `image/jpeg`, `previewService.generate` twice, `storage.store` twice for originals and twice for previews.
- `upload - images and PDFs keep the order`: an image (`directUpload.check` → `image/png`) + a 2-page PDF + an image;
  4 pages with indices 0..3; the page entities' paths are in the order the sources were stored.
- `upload - a PDF add over 10 pages is refused, and the pages already stored are deleted`: an image then a PDF that
  renders 10 pages; 11 > 10 → 400 `MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES` parameter `files`; `storage.delete` called on
  the image's path and preview; no document saved.
- `upload - an encrypted PDF is refused as file and nothing is stored`: `doThrow(PdfRasterizationException(ENCRYPTED,
  ...)).whenever(pdfRasterizer).rasterize(any(), any())`; 400 `MANDATE_DOCUMENT_PDF_ENCRYPTED` parameter `file`;
  `verify(storage, never()).store(...)`; no document.
- `uploadPage - a PDF appends after the last page index`: the document's max index is 1; a 2-page PDF → indices 2 and 3,
  document reset.
- `uploadReplacementPage - a one-page PDF replaces the page and resets the extraction`: the new path ends `.jpg`, the
  page keeps its id and index, the old path and preview are deleted.
- `uploadReplacementPage - a multi-page PDF is refused and the page is unchanged`: `doThrow(TOO_MANY_PAGES)`; 400
  parameter `file`; the page's path and storage type unchanged, no delete, no save.

- [ ] **Step 2: Run to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: FAIL — constructor/`PdfRasterizer` undefined and the new cases fail.

- [ ] **Step 3: Implement the service changes** as specified. Keep the existing storage-I/O-outside-transaction and
  `saveLocked` shape; the cleanup on failure deletes only objects no page references.

- [ ] **Step 4: Run to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=MandateDocumentServiceTest`
Expected: PASS.

- [ ] **Step 5: Update the server docs and commit**

In `modules/ndopify-server/CLAUDE.md`'s **Documents** paragraph: the three multipart endpoints also accept
`application/pdf`; each page is rendered to a JPEG (200 DPI / 2000 px), stored in the PDF's order, the PDF is not
kept; the caps (10 pages on a PDF add, one page on replace); the three codes; the presigned paths stay images-only.

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentService.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/MandateDocumentServiceTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): expand a PDF into mandate document pages" -m "Refs #366"
```

---

### Task 4: Endpoint coverage (`ndopify-server`)

**Files:**
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UploadMandateDocumentEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AddMandateDocumentPageEndpointTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/ReplaceMandateDocumentPageEndpointTest.kt`
- Reuse the SQL fixtures `db/test/clean.sql`, `db/test/mandate/CreateMandateDocumentEndpoint.sql`,
  `db/test/mandate/MandateDocumentMutationEndpoint.sql`.

**Interfaces:**
- Consumes: the three endpoints and the service from Task 3; the codes from Task 1.
- Produces: no production interface; the tests pin the multipart contract.

- [ ] **Step 1: Add the PDF helpers to the two test classes**

```kotlin
private fun pdf(pages: Int): ByteArray = PDDocument().use { document ->
    repeat(pages) {
        val page = PDPage(PDRectangle(LETTER))
        document.addPage(page)
        PDPageContentStream(document, page).use { content ->
            content.beginText(); content.newLineAtOffset(72f, 720f); content.showText("Page $it"); content.endText()
        }
    }
    ByteArrayOutputStream().also { document.save(it) }.toByteArray()
}

private fun encryptedPdf(): ByteArray = PDDocument().use { document ->
    document.addPage(PDPage(PDRectangle(LETTER)))
    document.protect(StandardProtectionPolicy("owner", "user", AccessPermission()))
    ByteArrayOutputStream().also { document.save(it) }.toByteArray()
}
```

`UploadMandateDocumentEndpointTest.request` gains a per-file content type (default `IMAGE_PNG`), or a
`pdfRequest(type, vararg files: Pair<String, ByteArray>)` with `MediaType.APPLICATION_PDF`.

- [ ] **Step 2: Write the failing endpoint tests**

- `upload - a one-page PDF becomes one JPEG page`: 200; one page, path ends `.jpg`, `mimeType` `image/jpeg`;
  `verify(storage).store(argThat { endsWith(".jpg") }, any(), eq("image/jpeg"))`; the preview is stored; no call stores
  a `.pdf`.
- `upload - an image and a two-page PDF keep their order`: 200; 4 pages with indices 0..3; the `store` calls for the
  originals carry the MIME sequence `image/png, image/jpeg, image/jpeg, image/png` (filter out `.preview-v1.jpg`
  calls).
- `upload - an encrypted PDF is 400 MANDATE_DOCUMENT_PDF_ENCRYPTED and nothing is stored`: no document, no page,
  `verify(storage, never()).store(any(), any(), any())`.
- `upload - a corrupt PDF is 400 MANDATE_DOCUMENT_PDF_UNREADABLE`.
- `upload - an 11-page PDF is 400 MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES`.
- `upload - a PDF over 5 MB is 400 HTTP_INVALID_PARAMETER on file`: `pdf(1) + ByteArray(5 * 1024 * 1024)`.
- `pages/upload - a PDF appends its rendered pages`: the document's max index is 1; POST a 2-page PDF to
  `/pages/upload` → 2 new pages at indices 2 and 3, all `.jpg`.
- `replace - a one-page PDF keeps the page's id and index, path `.jpg`, and deletes the old object + preview`.
- `replace - a multi-page PDF is 400 MANDATE_DOCUMENT_PDF_TOO_MANY_PAGES, and the page and its object are unchanged`.

- [ ] **Step 3: Run to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest='UploadMandateDocumentEndpointTest,AddMandateDocumentPageEndpointTest,ReplaceMandateDocumentPageEndpointTest'`
Expected: FAIL — the PDF upload is refused as a non-image (HTTP 400) where a 200 is expected.

- [ ] **Step 4: Fix the tests to the implemented contract and run again**

Run the same command.
Expected: PASS. These tests need the local MySQL (see the root `CLAUDE.md`).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/UploadMandateDocumentEndpointTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/AddMandateDocumentPageEndpointTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/endpoint/ReplaceMandateDocumentPageEndpointTest.kt
git commit -m "test(server): cover PDF mandate document uploads and replacements" -m "Refs #366"
```

---

### Task 5: Partner Central rules and controller (`ndopify-partner-central`, Kotlin)

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRulesTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt`

**Interfaces:**
- Consumes: `ErrorCode.MANDATE_DOCUMENT_PDF_ENCRYPTED|UNREADABLE|TOO_MANY_PAGES` from Task 1.
- Produces: `MandateDocumentRules.PDF_MIME_TYPE = "application/pdf"`, `IMAGE_MIME_TYPES`, `MIME_TYPES =
  IMAGE_MIME_TYPES + PDF_MIME_TYPE`, `fun isPdf(page: SniffedPage): Boolean`.
- Produces: `MandateDocumentRules.validatePage` accepts a PDF; the replacement messages
  `FILE_REQUIRED = "Choisissez la nouvelle page : une photo ou un PDF."`,
  `NOT_A_PAGE = "Ce fichier n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou WEBP, ou un PDF."`,
  `PDF_TOO_LARGE = "Ce PDF dépasse 5 Mo : choisissez un PDF plus léger, ou envoyez une photo."`.
- Produces: `MandateDocumentController.pageServerErrors(ex, action, fallback)` where
  `enum class PageAction { ADD, REPLACE }`; it maps the server's code to French:

```kotlin
ENCRYPTED, ADD    -> "Un PDF est protégé par un mot de passe : enregistrez-le sans protection, ou envoyez ses pages en photos. Choisissez de nouveau les pages."
ENCRYPTED, REPLACE -> "Ce PDF est protégé par un mot de passe : enregistrez-le sans protection, ou envoyez une photo."
UNREADABLE, ADD   -> "Un PDF est vide ou endommagé : envoyez ses pages en photos. Choisissez de nouveau les pages."
UNREADABLE, REPLACE -> "Ce PDF est vide ou endommagé : envoyez une photo."
TOO_MANY, ADD     -> "Un PDF fait plus de 10 pages : envoyez ses pages en photos, 10 au plus, ou un PDF plus court. Choisissez de nouveau les pages."
TOO_MANY, REPLACE -> "Ce PDF fait plusieurs pages : envoyez un PDF d'une page, ou une photo."
HTTP_INVALID_PARAMETER (file/files), ADD -> "Un fichier n'a pas pu être ajouté : vérifiez qu'il s'agit d'une photo lisible ou d'un PDF non protégé de 10 pages au plus. Choisissez de nouveau les pages."
HTTP_INVALID_PARAMETER (file), REPLACE -> "Cette page n'a pas pu être remplacée : vérifiez qu'il s'agit d'une photo lisible ou d'un PDF d'une page, non protégé."
```

The `field` is `error.parameter.name` when it is `type` or `title`; a `file` or `files` parameter always answers on the
form's own field (`fallback`: `files` for an add, `file` for a replace); anything else also uses `fallback`.

- [ ] **Step 1: Write the failing rules tests**

Update `a replacement page`: a PDF is accepted (`assertNull(validatePage(page(mimeType = "application/pdf")))`), a PDF
over 5 Mo is `PDF_TOO_LARGE`, a GIF is `NOT_A_PAGE`, a missing file is `FILE_REQUIRED`. Add `an add accepts a PDF` and
`an add's PDF over 5 Mo is refused with the PDF message`. The image messages stay as they are.

- [ ] **Step 2: Implement the rules; run the rules test**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentRulesTest`
Expected: PASS.

- [ ] **Step 3: Write the failing controller tests**

Add `private val PDF = "%PDF-1.7\n%...".toByteArray()` (Tika detects it) and a `pdfFile(name = "scan.pdf", size = …)`
helper. Tests:
- `add - a PDF is sent with its MIME type`: `documentClient.upload` captor's `UploadedPage.mimeType` is
  `application/pdf`; redirect to the mandate.
- `add - a PDF over 5 Mo shows the field error and calls nothing`: `errors["files"]` is the PDF size message.
- `add - the server's PDF codes map to the French pages error`: for each of the three codes, a
  `HttpClientErrorException` 400 with that `ErrorResponse`; `errors["files"]` is the matching message; the type and
  title are kept in the model.
- `replacePage - a one-page PDF is accepted`: `uploadReplacementPage` captor's MIME is `application/pdf`; redirect.
- `replacePage - the server's multi-page PDF code opens the row`: `replaceErrors[page.id]` is the multi-page message.
Update the existing PDF-is-refused test (line ~282) to the accepted/PDF case, and `Choisissez une image.` to
`FILE_REQUIRED`.

- [ ] **Step 4: Implement the controller mapping and run**

`add` catches 400 → `addPage(..., pageServerErrors(ex, ADD, "files"), ...)`; `replacePage` catches 400 →
`mapOf(page.id to (pageServerErrors(ex, REPLACE, "file")["file"] ?: REPLACE_FAILED))`. `sniff`'s fallback filename
becomes `"Ce fichier"`. The multipart-limit `TOO_LARGE` becomes
`"Ce fichier dépasse 5 Mo : choisissez un fichier plus léger."` (a >10 MB file is refused before the rules know its
type); update its tests.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRules.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentRulesTest.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt
git commit -m "feat(npc): accept PDFs in the mandate document forms" -m "Refs #366"
```

---

### Task 6: Partner Central UI — port the approved prototype

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document-new.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html`
- Modify: `modules/ndopify-partner-central/src/main/assets/js/mandate-document.js`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the documents paragraph)

**Interfaces:**
- Consumes: the approved prototype (`modules/ndopify-partner-central/src/main/html/v2/mandate/document-new.html`,
  `document.html`, `assets/js/mandate-documents.js`) — copy its markup and copy, not its simulation.
- Produces: production templates and `mandate-document.js` matching the prototype:
  - Add hint: *Une photo par page, dans l'ordre (recto, puis verso). JPEG, PNG, WEBP ou PDF, 5 Mo par fichier, 10 pages
    au plus. Les pages d'un PDF sont ajoutées à sa place, dans son ordre.*
  - Native input `accept="image/jpeg,image/png,image/webp,application/pdf"`; the JS image input stays images-only;
    the second JS button is *Choisir des fichiers* with the four types.
  - Add list: an image row *Page N · name · size*; a PDF row *PDF · name · size*; the count line *Aucun fichier
    choisi.* / *N fichier(s) choisi(s) · 10 pages au plus. Les pages d'un PDF comptent à l'envoi.*
  - Add submit with a PDF: shrink each image, sync the chosen files to the native input, then `form.submit()` (the
    server posts and renders; *Envoi du document…*); images alone keep the per-page direct upload.
  - Replace: label *Nouveau fichier de la page N*, accept the four types, hint *Photo JPEG, PNG ou WEBP, ou PDF d'une
    page, 5 Mo au plus. Le fichier prend la place de cette page.*; a PDF calls `form.submit()` (no presign); an image
    keeps shrinking and direct-uploading.
  - Local messages mirror Task 5's rules; the JS mirror is
    `fileError`: an unsupported type → *« name » n'est ni une photo ni un PDF : choisissez une photo JPEG, PNG ou
    WEBP, ou un PDF.*; an image over 5 Mo → the existing message; a PDF over 5 Mo → *« name » dépasse 5 Mo : choisissez
    un PDF plus léger, ou envoyez ses pages en photos.*

- [ ] **Step 1: Port the two templates** from the approved prototype, keeping the server's `name="files"` /
  `name="file"`, the Thymeleaf `errors` / `replaceErrors` bindings, and the existing `data-*` hooks. No layout change.

- [ ] **Step 2: Write the failing template tests**

In `MandateDocumentTemplateTest`:
- the add page's native input `accept` contains `application/pdf`; the hint contains `PDF` and `5 Mo par fichier`;
  the JS picker contains *Choisir des fichiers* and an input whose `accept` contains `application/pdf`;
- the document page's replace input `accept` contains `application/pdf`, its label reads *Nouveau fichier de la page 1*
  and its hint contains *PDF d'une page*;
- the existing `TOO_LARGE` assertion uses the neutral message.

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: FAIL — the old accept/hints.

- [ ] **Step 3: Port the JavaScript** to `src/main/assets/js/mandate-document.js`:
  - constants `IMAGE_TYPES`, `PDF_TYPE`, `ACCEPTED_TYPES`; `isPdf(file)`;
  - `fileError` as above; `render()` labels a PDF *PDF* and counts image rows as *Page N*; the count line and the
    announcement follow the prototype;
  - `initNew`: on submit, if any chosen file is a PDF, run `shrink` over each image, replace it in the chosen list,
    `sync()`, then `form.submit()`; otherwise keep the existing direct-upload loop. Keep `DataTransfer` /
    native-input fallbacks.
  - `initDocument`: if the chosen file is a PDF, `form.submit()` (the controller renders the error); otherwise keep
    the shrink + `NdDirectUpload.upload` path.
  - Do not presign a PDF anywhere.

- [ ] **Step 4: Run the template and page-script tests**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS (the assets bundle builds; `page-scripts.test.mjs` loads the rendered pages without a script error).

- [ ] **Step 5: Update the NPC docs and commit**

In `modules/ndopify-partner-central/CLAUDE.md`'s documents paragraph: the add and replace accept JPEG/PNG/WEBP/PDF,
the *Choisir des fichiers* picker, the PDF row/count, the multipart form for a PDF (no presign), the French errors and
the 5 Mo/one-page rules.

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/document-new.html \
  modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html \
  modules/ndopify-partner-central/src/main/assets/js/mandate-document.js \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt \
  modules/ndopify-partner-central/CLAUDE.md
git commit -m "feat(npc): offer PDFs in the mandate document forms" -m "Refs #366"
```

---

## Final Verification

- `cd modules/ndopify-dto && mvn install` — BUILD SUCCESS, `0.0.109` released and used in the root POM.
- `cd modules/ndopify-server && mvn verify` — ktlint, the rasterizer/service/endpoint tests and the JaCoCo gate pass
  (local MySQL running).
- `cd modules/ndopify-partner-central && mvn verify` — ktlint, the rules/controller/template tests, the built JS bundle
  and `page-scripts.test.mjs` pass.
- Manual, with `ndopify-server` and Partner Central running: add a one-page PDF and a three-page PDF (with photos
  mixed in), check *Page 1*…*Page N* and *Voir*, then add a protected PDF, a corrupt PDF, an 11-page PDF and replace a
  page with a two-page PDF — each shows the approved French field error and leaves nothing changed.
- Confirm no `.pdf` object is in the storage and no log names a PDF, a path or a filename.
