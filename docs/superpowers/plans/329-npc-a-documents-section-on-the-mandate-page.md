# Partner Central — a "Documents" section on the mandate page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a mandate's page in Partner Central, the agent lists its documents and adds (type, title, pages),
renames, replaces a page of, and deletes them, with and without JavaScript.

**Architecture:** A new `ui/document/` feature in `ndopify-partner-central` (controller, rules, views, forms) on a new
`NdMandateDocumentClient` for #328's `/v1/mandates/{id}/documents` endpoints. The mandate page reads the documents
alongside its other reads and renders a *Documents* section; a document has its own page and the add form its own
page. Forms are multipart (no JS); the page script uploads straight to the storage through `direct-upload.js` and
falls back to the form post.

**Tech Stack:** Kotlin, Spring Boot MVC + Thymeleaf, `RestClient`, Tika, esbuild bundle, `node:test` + jsdom.

**Spec:** issue #329 (enriched body, acceptance criteria 1–14, decisions, prototype feedback); approved prototype
`modules/ndopify-partner-central/src/main/html/v2/mandate.html` (*Documents* section),
`v2/mandate/document-new.html`, `v2/mandate/document.html`, `v2/assets/js/mandate-documents.js`,
`v2/assets/css/documents.css`, and the #329 rows of `v2/README.md`. Server contract: #328's plan
`docs/superpowers/plans/328-server-mandate-documents-record-private-storage-upload-read.md` (Task 1 DTOs, Task 3–4
endpoints).

## Global Constraints

- Blocked by #328: its `ndopify-dto` release must exist; this story changes no DTO, no server, no web-core.
- Module: `modules/ndopify-partner-central` only (package `com.wutsi.ndopify.npc`). Never touch `src/main/html/`.
- French UI; dates through `TenantFormatter` with the tenant `dateFormat` (year included), never a hard-coded pattern.
- Every page works without JavaScript; the script only enhances and its rules are re-checked by `MandateDocumentRules`.
- No image of a page is shown anywhere: a page is a *Page N* row with a *Voir* link (`target="_blank"
  rel="noopener"`, href = the page's presigned `url`) and *Remplacer*. Never render a page `path`.
- Document page info: title, status pill, the type only when the title differs from the type's name, one *Ajouté le
  …* (the document's `createdAt`). No size, no page count in the header.
- Limits: JPEG, PNG or WEBP (Tika-sniffed), 5 Mo per page, 10 pages per add, title ≤ 100 after trim.
- Writes only while the mandate is in `MandateRules.ACTIVE`; on `CLOSED` / `CANCELLED` the section and the document
  page are read-only (*Voir* only).
- *Ajouter un document* is `btn btn--secondary btn--compact` (the WhatsApp button's style) in every state.
- Status labels: `UPLOADED` *Reçu*, `EXTRACTING` *En cours d'analyse*, `EXTRACTED` *Analysé*, `FAILED` *À corriger*
  (pill styles as the prototype). No expiry status (#327).
- Type names (as #328's `MandateDocumentLabels`): `NATIONAL_ID` *Carte nationale d'identité*, `PASSPORT`
  *Passeport*, `LAND_TITLE` *Titre foncier*, `CERTIFICATE_OF_OWNERSHIP` *Certificat de propriété*, `SURVEY_PLAN`
  *Plan de bornage*, `POWER_OF_ATTORNEY` *Procuration*, `LEASE_CONTRACT` *Contrat de bail*, `UTILITY_BILL` *Facture
  d'eau ou d'électricité*, `TAX_RECEIPT` *Quittance d'impôt*, `PAY_SLIP` *Bulletin de paie*, `BANK_PRE_APPROVAL`
  *Accord de principe bancaire*, `PROOF_OF_FUNDS` *Justificatif de fonds*, `OTHER` *Document*.
- Suggested types — LISTING: `NATIONAL_ID`, `PASSPORT`, `LAND_TITLE`, `CERTIFICATE_OF_OWNERSHIP`, `SURVEY_PLAN`,
  `POWER_OF_ATTORNEY`; SEARCH: `NATIONAL_ID`, `PASSPORT`, `PAY_SLIP`, `BANK_PRE_APPROVAL`, `PROOF_OF_FUNDS`. The
  picker groups *Conseillés pour ce mandat* then *Autres types* (the rest, `OTHER` last).
- Tests: `kotlin.test` assertions, `mockito-kotlin` mocks, standalone `MockMvc`, real-Thymeleaf template tests.

## Review Focus

1. **A document id of another mandate** in the URL: the server answers 404 `MANDATE_DOCUMENT_NOT_FOUND`; the page must
   redirect to the mandate page with `documentOutcome=changed`, never a 500 (Task 3 test).
2. **Multipart over the limits** (`MaxUploadSizeExceededException` before the controller) on the add and replace
   posts: back to the form with the size error, never the error page (Task 4 test).
3. **Direct-upload fallback after partial success** (2 of 3 pages PUT, then a failure): the script posts the whole
   form, so the server gets one create, never two documents (Task 5 script test: create JSON not called when a
   store fails).
4. **Expired session on a JSON proxy**: answers 401, never the sign-in redirect `fetch` would follow (Task 4 test).
5. **Closed mandate with a stale form**: a write the server answers 409 `MANDATE_ALREADY_CLOSED` redirects to the
   mandate page with `documentOutcome=changed` (Task 3 test).

---

### Task 1: DTO bump and `NdMandateDocumentClient`

**Files:**
- Modify: root `pom.xml` `<ndopify-dto.version>` → the release carrying #328's `MandateDocument` (find it with
  `gh release list --limit 20 | grep ndopify-dto`; stop if #328's DTO isn't released yet).
- Create: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateDocumentClient.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/client/ndopify/NdMandateDocumentClientTest.kt`

**Interfaces — Produces** (`@Service`, built like `NdKycClient` on `RestClient.Builder` with
`ndopify.server.api.base-url`; multipart parts as `NdKycClient.upload` does, with a file name):
- `fun search(mandateId: Long): List<MandateDocument>` — `GET /v1/mandates/{id}/documents`
- `fun get(mandateId: Long, documentId: Long): MandateDocument` — `GET /v1/mandates/{id}/documents/{documentId}`
- `fun presign(mandateId: Long, contentType: String): PresignUploadResponse` — `POST .../documents/presign`
- `fun create(mandateId: Long, request: CreateMandateDocumentRequest): Long` — `POST .../documents`, the `documentId`
- `fun upload(mandateId: Long, type: DocumentType, title: String?, files: List<UploadedPage>): Long` — `POST
  .../documents/upload` (parts `type`, `title` when not blank, `files`)
- `fun rename(mandateId: Long, documentId: Long, request: UpdateMandateDocumentRequest)` — `POST .../{documentId}`
- `fun replacePage(mandateId: Long, documentId: Long, pageId: Long, path: String): Long` — `POST
  .../{documentId}/pages/{pageId}` (`AttachUploadRequest`)
- `fun uploadReplacementPage(mandateId: Long, documentId: Long, pageId: Long, page: UploadedPage): Long` — `POST
  .../{documentId}/pages/{pageId}/upload`
- `fun delete(mandateId: Long, documentId: Long)` — `DELETE .../{documentId}`
- `data class UploadedPage(val content: ByteArray, val mimeType: String, val filename: String)` in the same file.

- [ ] **Step 1:** Bump `<ndopify-dto.version>`; run `cd modules/ndopify-partner-central && mvn -q compile` — Expected:
  BUILD SUCCESS.
- [ ] **Step 2: Write the failing test** — `NdMandateDocumentClientTest` with
  `MockRestServiceServer.bindTo(RestClient.builder())`: one test per method asserting URL, method, JSON body
  (`create` sends `{"type":"LAND_TITLE","title":null,"paths":["a","b"]}`; `rename` sends `title` and `version`) or
  multipart content type, and the parsed result (`search` returns two documents in the server's order; `create`
  returns `42` from `{"documentId":42}`).
- [ ] **Step 3:** Run `mvn test -Dtest=NdMandateDocumentClientTest` — Expected: FAIL (class missing).
- [ ] **Step 4:** Implement `NdMandateDocumentClient`.
- [ ] **Step 5:** Run the test — Expected: PASS.
- [ ] **Step 6:** Commit `feat(npc): add the mandate documents client` with `Refs #329`.

### Task 2: Labels, rules and views

**Files:**
- Create in `.../npc/ui/document/`: `MandateDocumentLabels.kt`, `MandateDocumentRules.kt`, `MandateDocumentViews.kt`
  (+ `MandateDocumentView.kt` for the view classes), `MandateDocumentForms.kt`
- Test: `MandateDocumentLabelsTest.kt`, `MandateDocumentRulesTest.kt`, `MandateDocumentViewsTest.kt` (same package)

**Interfaces — Produces:**
- `object MandateDocumentLabels { fun type(type: DocumentType): String; fun status(status: DocumentStatus):
  MandateRules.Tag; fun suggested(mandateType: MandateType): List<DocumentType>; fun others(mandateType: MandateType):
  List<DocumentType> }` — values from *Global Constraints*; `others` = every type but `UNKNOWN` and the suggested,
  in enum order with `OTHER` last.
- `object MandateDocumentRules`:
  - `const val MAX_PAGES = 10`, `const val MAX_BYTES = 5 * 1024 * 1024L`, `const val MAX_TITLE = 100`,
    `val MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")`
  - `fun writable(mandate: Mandate): Boolean` — `mandate.status in MandateRules.ACTIVE`
  - `fun validateAdd(form: AddDocumentForm, pages: List<SniffedPage>): Map<String, String>` — keys `type`, `title`,
    `files`; messages: no type *Choisissez le type de document.*; no page *Ajoutez au moins une page.*; > 10 *10
    pages au plus.*; title > 100 *100 caractères au plus.*; a non-image *« {name} » n'est pas une image JPEG, PNG ou
    WEBP.*; > 5 Mo *« {name} » dépasse 5 Mo : reprenez la photo, ou choisissez une image plus légère.* (first page
    error wins).
  - `fun validateTitle(title: String): Map<String, String>` — blank *Indiquez un titre.*, > 100 as above.
  - `fun validatePage(page: SniffedPage?): String?` — none *Choisissez une image.*, type / size as `validateAdd`
    (*Cette image dépasse 5 Mo : …* wording of the prototype).
- `data class SniffedPage(val filename: String, val size: Long, val mimeType: String?, val content: ByteArray)`
- Forms: `data class AddDocumentForm(val type: String = "", val title: String = "")`,
  `data class RenameDocumentForm(val title: String = "", val version: Long? = null)`.
- Views: `DocumentSectionView(rows: List<DocumentRowView>, unavailable: Boolean, writable: Boolean, emptyHint: String)`,
  `DocumentRowView(id, typeLabel, title: String?, status: MandateRules.Tag, addedOn: String, pageCount: Int, href)`
  (`title` null when equal to the type's name), `DocumentPageView(id, number: Int, url: String)`,
  `DocumentView(id, mandateId, typeLabel: String?, title, status, addedOn, version, pages, writable)`;
  `object MandateDocumentViews { fun section(mandate: Mandate, documents: List<MandateDocument>?, formatter:
  TenantFormatter): DocumentSectionView; fun document(mandate: Mandate, document: MandateDocument, formatter:
  TenantFormatter): DocumentView }`. `null` documents → `unavailable = true`. Rows newest first (`createdAt` desc).
  `emptyHint` per mandate type: the prototype's text for LISTING / SEARCH.

- [ ] **Step 1: Write the failing tests** — Labels: all 13 names; LISTING suggested = the six, SEARCH = the five;
  `others(SEARCH)` contains `LAND_TITLE` and ends with `OTHER`. Rules: each message above; 10 pages OK, 11 refused;
  exactly 5 MiB OK, +1 byte refused; `image/gif` refused; `writable` true for ASSIGNED, PENDING_APPROVAL, APPROVED,
  false for CLOSED, CANCELLED. Views: dates formatted *25/11/2026* with a tenant `dd/MM/yyyy`; a title equal to the
  type name gives `title = null` / `typeLabel = null`; pages numbered 1..n in `index` order; `unavailable` on null.
- [ ] **Step 2:** Run `mvn test -Dtest='MandateDocument*Test'` — Expected: FAIL.
- [ ] **Step 3:** Implement the four files.
- [ ] **Step 4:** Run the tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(npc): add the mandate document rules and views` with `Refs #329`.

### Task 3: Port the prototype: pages, controller, mandate section (no JavaScript path)

First step of the UI work: **port the approved prototype** (`v2/mandate.html` *Documents* section,
`v2/mandate/document-new.html`, `v2/mandate/document.html`, `v2/assets/css/documents.css`) into Thymeleaf — markup and
CSS as approved, no redesign.

**Files:**
- Create: `.../npc/ui/document/MandateDocumentController.kt`
- Create: `src/main/resources/templates/mandate/document-new.html`, `templates/mandate/document.html`,
  `templates/fragments/mandate-documents.html` (the section)
- Create: `src/main/assets/css/document.css` (from `v2/assets/css/documents.css`); modify `build-assets.mjs` to add
  `"document"` after `"telegram"` in the CSS order
- Modify: `templates/mandate.html` (include the fragment after *Biens correspondants*, before *Sa recherche*, as the
  prototype), `ui/mandate/MandateController.kt` (`render`: start `parallel.async { lookup("documents of mandate
  ${mandate.id}") { documentClient.search(mandate.id) } }` with the other reads, join it, add model `documentSection`
  = `MandateDocumentViews.section(...)`; read the `documentOutcome` / `documentTitle` flash attributes into
  `documentOutcomeText` / `documentOutcomeStyle`: `added` *Document « {title} » ajouté.*, `deleted` *Document
  supprimé.*, `changed` *Ce document a changé ou n'existe plus.*)
- Test: `MandateDocumentControllerTest.kt`, `MandateDocumentTemplateTest.kt` (`ui/document/`); extend
  `ui/mandate/MandateControllerTest.kt`, `MandateTemplateTest.kt`

**Interfaces — Produces** (`MandateDocumentController`, `@RequestMapping("/mandates/{id}/documents")`; agent from
`CurrentAgent` or `SignInRequiredException`; mandate via `NdMandateClient.get` limited to `MandateRules.SHOWN`, else
the locked view; flash names `documentOutcome`, `documentTitle`, `outcome`):
- `GET /new` → `mandate/document-new` (model `form`, `errors`, `suggested`, `others`, `mandate`); not writable →
  redirect `/mandates/{id}` with `documentOutcome=changed`.
- `POST` (multipart `type`, `title`, `files`) → sniff each file (Tika), `validateAdd`; errors → the form again (type
  and title kept); else `upload` → redirect `/mandates/{id}#documents-title`, flash `added` + the saved title.
- `GET /{documentId}` (`?delete=1` shows the confirmation panel) → `mandate/document`, model `document`,
  `renameForm`, `renameErrors`, `replaceErrors` (pageId → message), `conflict`.
- `POST /{documentId}/rename` (`RenameDocumentForm`) → `validateTitle`; `rename`; flash `outcome=renamed`, redirect to
  the document; 409 `MANDATE_DOCUMENT_VERSION_CONFLICT` → the page again with the current document's title and
  `conflict = true`.
- `POST /{documentId}/pages/{pageId}` (multipart `file`) → `validatePage`; `uploadReplacementPage`; flash
  `outcome=replaced`, `page={number}`; error → the document page with that page's error.
- `POST /{documentId}/delete` → `delete`; flash `deleted`, redirect `/mandates/{id}#documents-title`.
- Every action: 404 (`MANDATE_NOT_FOUND`, `MANDATE_DOCUMENT_NOT_FOUND`, `MANDATE_DOCUMENT_PAGE_NOT_FOUND`), 403, 409
  `MANDATE_ALREADY_CLOSED` → redirect `/mandates/{id}` with `documentOutcome=changed`; a 400 from the server → the
  form with the error on its field (`error.data.parameter` → field, else `files`); any other failure → flash `error`.

- [ ] **Step 1: Write the failing tests** — Controller: add posts the parts (captor) and redirects with `added`; each
  `validateAdd` error re-renders with type/title kept; a closed mandate's `GET /new` redirects `changed`; rename OK,
  blank, 409 conflict (current title shown); replace OK (flash `page=2`) and a 6 MB file; delete; the 404/403/409
  list above on every action, including a 404 `MANDATE_DOCUMENT_NOT_FOUND` on `GET /{documentId}` (Review Focus 1)
  and a 409 `MANDATE_ALREADY_CLOSED` on rename (Review Focus 5). MandateController: the documents section present,
  `unavailable` when `search` throws while the page still renders, flash texts. Templates: the section lists rows
  (type, title only when different, pill, *Ajouté le 25/11/2026 · 2 pages*), empty state with its hint, unavailable
  text, no *Ajouter un document* on a CLOSED mandate and `btn btn--secondary btn--compact` otherwise; the add form's
  two `optgroup`s in the order above, hint *JPEG, PNG ou WEBP, 5 Mo par page, 10 pages au plus.*, `multiple` and
  `accept="image/jpeg,image/png,image/webp"`; the document page has no `<img>`, no `path`, a *Voir* link per page with
  the presigned URL and `target="_blank"`, a *Remplacer* form per page with *JPEG, PNG ou WEBP, 5 Mo au plus.*, the
  `?delete=1` panel, read-only rows (*Voir* only) on a CANCELLED mandate. Each template test saves its pages with
  `ScriptFixtures.write`.
- [ ] **Step 2:** Run `mvn test -Dtest='MandateDocument*Test,MandateControllerTest,MandateTemplateTest'` — Expected:
  FAIL.
- [ ] **Step 3:** Implement the controller, templates, fragment, CSS and the `MandateController` change.
- [ ] **Step 4:** Run the tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(npc): a Documents section on the mandate page` with `Refs #329`.

### Task 4: Upload limits and JSON proxies for the direct upload

**Files:**
- Modify: `src/main/resources/application.yml` — `spring.servlet.multipart.max-request-size: 55MB` (10 pages of 5 Mo
  plus the form); `max-file-size` stays `10MB` (the 5 Mo check is `MandateDocumentRules`'s, as KYC's). Update its
  comment.
- Modify: `ui/kyc/KycUploadErrorHandler.kt` — `^/mandates/(\d+)/documents$` → `redirect:/mandates/{id}/documents/new?
  error=too-large`; `^/mandates/(\d+)/documents/(\d+)/pages/(\d+)$` → `redirect:/mandates/{id}/documents/{documentId}?
  replaceError=size&page={pageId}`; the controller's `GET`s read these and show the size messages.
- Modify: `MandateDocumentController.kt` — JSON endpoints (`produces = application/json`, 401 without a signed-in
  agent — never the sign-out redirect; 409 when the mandate isn't writable; ndopify-server's status and error body
  when it refuses):
  - `POST /presign` (`PresignUploadRequest`) → `PresignUploadResponse`
  - `POST /create` (`CreateMandateDocumentRequest`, ≤ 10 paths, title ≤ 100) → `CreateMandateDocumentResponse`
  - `GET /saved?documentId=` → flash `added` with the document's title, redirect `/mandates/{id}#documents-title`
  - `POST /{documentId}/pages/{pageId}/presign` → the mandate's presign; `POST /{documentId}/pages/{pageId}/attach`
    (`AttachUploadRequest`) → `replacePage`; `GET /{documentId}/pages/{pageId}/saved` → flash `replaced`, redirect to
    the document.
- Test: extend `MandateDocumentControllerTest.kt`, `ui/kyc/KycUploadErrorHandlerTest.kt`

- [ ] **Step 1: Write the failing tests** — each JSON endpoint OK; 401 without agent (Review Focus 4); 409 on a CLOSED
  mandate; a server 400 relayed with its body; `create` with 11 paths → 400 and no client call; the two new
  `KycUploadErrorHandler` routes (Review Focus 2) and the forms showing *« … » dépasse 5 Mo* / *Cette image dépasse
  5 Mo* after them.
- [ ] **Step 2:** Run `mvn test -Dtest='MandateDocumentControllerTest,KycUploadErrorHandlerTest'` — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run the tests — Expected: PASS.
- [ ] **Step 5:** Commit `feat(npc): direct upload of mandate documents` with `Refs #329`.

### Task 5: Page script

**Files:**
- Modify: `src/main/assets/js/direct-upload.js` — add `NdDirectUpload.store(action, file)`: presign → PUT, resolves
  the stored `path` or `null` on any failure (same timeouts); `upload` reuses it, unchanged behavior.
- Create: `src/main/assets/js/mandate-document.js` (from `v2/assets/js/mandate-documents.js`, minus its sample data),
  gated on `data-page` `mandate-document-new` / `mandate-document`; modify `build-assets.mjs` to add it after
  `"mandate"` in the JS order.
- Modify: `src/test/js/page-scripts.test.mjs`
- Behavior (as the prototype): add form — *Prendre une photo* / *Choisir des images*, each chosen file a numbered
  removable row *Page 1 · IMG_2041.jpg · 1,8 Mo* (Ko under 1 Mo; no thumbnail), client checks (type, 5 Mo, 10 pages)
  mirroring `MandateDocumentRules`, pages shrunk to 2000 px (KYC's `MAX_SIDE` approach), then `store` each page to
  `/mandates/{id}/documents` with the *Envoi de la page N sur M…* progress bar, `POST /create` with the paths, then
  `location = /mandates/{id}/documents/saved?documentId=`; if any `store` or `create` fails, the form posts with its
  files (no `create` after a failed store — Review Focus 3). Replace — `NdDirectUpload.upload(".../pages/{pageId}",
  file, {attachTimeout: true})`, then `.../saved`, else the form post. Delete — the confirmation in place.
- [ ] **Step 1: Write the failing test** — in `page-scripts.test.mjs`: both pages load without error; choosing 2 files
  renders two rows with name and size and no `<img>`; a 6 MB file shows the size error and keeps *Ajouter* disabled;
  with `fetch` stubbed so the second PUT fails, `/create` is never called and the form is submitted.
- [ ] **Step 2:** Run `mvn test` (builds the bundle, then runs the script tests) — Expected: FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run `mvn test` — Expected: PASS.
- [ ] **Step 5:** Commit `feat(npc): enhance the mandate document pages` with `Refs #329`.

### Task 6: Docs and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` — a *Pages* entry for the documents (routes, outcomes, limits,
  no image, *Voir*, read-only rule, prototype `src/main/html/v2/mandate/document*.html` approved 2026-11-25); add
  `document` to the `ui/<feature>/` list and the CSS/JS bundle orders.
- [ ] **Step 1:** Edit the docs.
- [ ] **Step 2:** Run `cd modules/ndopify-partner-central && mvn verify` — Expected: BUILD SUCCESS (ktlint, tests,
  script tests, JaCoCo gate).
- [ ] **Step 3:** Commit `docs(npc): document the mandate documents pages` with `Refs #329`.
