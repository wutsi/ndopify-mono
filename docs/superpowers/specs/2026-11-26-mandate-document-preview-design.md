# Mandate document preview: authenticated, resized and watermarked

Date: 2026-11-26

## Intent and why

A mandate's documents are sensitive: ID cards, payslips, land titles, powers of attorney. Today Partner
Central renders a page's *Voir* link with the page's **presigned storage URL**. That URL lives one hour, needs
no session, and returns the **untouched original**. If an agent copies it (or the browser history / a proxy log
keeps it), anybody can open the document and download the full-resolution file; a screenshot or a saved file can
be reused elsewhere with no trace of where it came from.

We want two things:

1. a leaked URL opens nothing without a signed-in agent who may read that mandate;
2. what an agent can save is a **resized, watermarked** copy, attributable to the tenant and the mandate
   (`<tenant name> - <mandate number>`), never the original.

The original must stay untouched: it is the evidence, and the pending extraction (#330) needs its full
resolution and its metadata.

## Goals

- No storage URL, presigned or public, is ever sent to a browser for a mandate document page.
- The browser displays (and can save) only a derived JPEG: longest side capped, re-encoded, tiled watermark.
- The preview is served through Partner Central, behind the agent session, so copying the URL outside that
  session yields no document.
- The original object is never modified, never served to the browser, and is still what the future extraction
  reads.
- Existing pages work without a migration or a backfill job.

## Non-goals

- Stopping an authenticated agent from photographing the screen, or a stolen session. Nothing can.
- KYC images (`KycService` has the same presigned-URL exposure today) and signed contracts. The server-side
  preview component is built to be reusable for them, but they are a follow-up.
- PDFs: mandate document pages are images only (JPEG, PNG, WEBP).
- Inline previews on the mandate page. The document page keeps one *Voir* link per page, in a new tab.

## Constraints

- Partner Central must not read the storage nor process images: it has no storage credentials, no image
  library, and the web apps must not depend on `ndopify-server` at build time. It only calls the server over
  HTTP.
- The original stays in the page's own storage (S3 in TEST/PROD, local locally). Previews go in the same
  storage.
- Tenant-visible text is French; code, comments and docs are English.
- Access rules must stay exactly as strict as the document reads today: `MandateAccessService.ensureCanUpdate`
  (assigned agent or admin), not the wider `ensureCanRead` (which also allows the agent a PENDING offer).
- `ndopify-dto` is a released dependency: any change is released before the modules using it are deployed.

## Current state (for the builder)

- `MandateDocumentService.pageUrl()` presigns `page.path` for `ndopify.mandate.document.url-ttl-seconds` (1 h).
- `MandateDocumentMapper` maps the pages and takes a `urlOf` callback; the DTO `MandateDocumentPage` carries
  only `url`, never `path`.
- `MandateDocumentEndpoints` calls `ensureCanUpdate` on both reads (`GET /v1/mandates/{id}/documents` and
  `/{documentId}`), and the preview endpoint must do the same.
- `mandate/document.html` renders the *Voir* link from `DocumentPageView.url`; the page shows no image.
- Partner Central's `MandateDocumentController` loads the mandate with `shownMandate` (closed mandates stay
  readable), the document with `load`, and renders errors through `HttpClientErrorHandler` (403/404 as status
  pages, 401 signs the agent out and redirects to `/login`).
- `NdMandateDocumentClient` wraps the server's document endpoints with the agent's bearer token.
- `DirectUploadService` verifies JPEG/PNG/WEBP, ≤ 5 MB, by MIME from bytes. It does not decode dimensions.
- Tests mock `StorageServiceProvider`; no MySQL file storage is used in endpoint tests.

## Approaches considered

**A. Watermarked preview, still presigned.** Generate and store the preview, presign *it* and keep the
current flow. Cheapest (no new delivery path, no DTO change), but a copied URL still opens the document for up
to an hour: it does not close the leak.

**B. Watermarked preview, authenticated delivery (chosen).** The server generates/stores the preview and serves
it from an authenticated endpoint; Partner Central exposes its own session-protected route and streams it. A
leaked URL opens nothing without an agent session. The page DTO no longer carries a URL. More moving parts, but
this is the only option that meets goal 1.

**C. Authenticated proxy of the original, no watermark.** Closes the leak but not reuse: the agent can still
save the full-resolution original. Not enough.

ImageKit text overlays were considered and rejected: the documents are private (ImageKit would need to fetch
them), it would ship sensitive documents to a third party, and our `ImageKit` client only supports
size/format/crop/quality today.

## Design

### 1. Server: preview generation

A new service (exact name left to the builder) owns "make the display copy of a page". It is deliberately
free of mandate rules so KYC can reuse it later:

```
fun preview(original: ByteArray, mimeType: String?, watermark: String): ByteArray // JPEG
```

Pipeline:

1. Read the dimensions from the image header first. Reject (unprocessable, logged) above a hard cap
   (≈ 40 MP), before any full decode.
2. Decode with ImageIO. JPEG and PNG use the JDK readers; add TwelveMonkeys `imageio-jpeg` and
   `imageio-webp` (the JDK cannot read WEBP) so CMYK and phone JPEGs and WEBP all decode.
3. Apply EXIF orientation (phone photos). The builder chooses the small metadata reader; if no reliable
   reader handles all three formats, normalize by handling the orientations it can and testing the rest.
4. Scale down preserving aspect ratio so the **longest side is 2000 px**. Never upscale.
5. Draw every page on an opaque background (white), so a transparent PNG/WEBP does not become black.
6. Watermark: the text `<tenant name> - Mandat n° <mandateId>` (the mandate "number" is the id shown as
   `Mandat n° 301` on the page today), tiled diagonally across the whole image, semi-transparent, with
   enough contrast on both white paper and dark photos. A single line is trivial to crop out; tiling is not.
   Use a bundled font (e.g. DejaVu/Noto, Apache-2.0) so accents render on the deployed JRE, which may carry
   no usable system fonts.
7. Encode JPEG, quality ≈ 82, `TYPE_INT_RGB`.

Re-encoding also drops EXIF/GPS/device metadata from what the agent can save, which is a privacy win.

Failure is **fail closed**: the endpoint returns an error and never falls back to the original bytes.

### 2. Server: storage and lifecycle

- Preview path is deterministic from the original path:
  `<page.path>.preview-v1.jpg` (e.g. `mandate/301/documents/<uuid>.webp.preview-v1.jpg`), so **no DB
  column and no migration** are needed.
- The preview lives in the same storage as the page (`page.storageType`, default when `UNKNOWN`, which is
  what `pageUrl()` already does).
- On request: if `metadata(previewPath)` exists, read it; else read the original, generate, store, return.
  Concurrent first views may both generate; both write identical bytes, which is harmless.
- Page replace/delete and document delete delete the preview next to the original (derive the path from the
  old page path, like the existing `deleteQuietly`).
- `-v1` allows regenerating later (watermark style, size) by bumping the version. Old previews then become
  orphans; a cleanup job is out of scope.
- A replaced page gets a new path, hence a new preview; nothing stale can be served.
- Known limitation: a tenant rename leaves old previews with the old name until regenerated (version bump
  or lazy rewrite). Accepted for now.

### 3. Server: endpoint

```
GET /v1/mandates/{id}/documents/{documentId}/pages/{pageId}/preview
```

- Bearer JWT, same checks and order as the document reads: 404 `MANDATE_NOT_FOUND`, 403
  `MANDATE_ACCESS_DENIED`, 404 `MANDATE_DOCUMENT_NOT_FOUND`, 404 `MANDATE_DOCUMENT_PAGE_NOT_FOUND`.
- Closed/cancelled mandates: allowed (documents stay readable).
- Response: `200 image/jpeg`, the preview bytes. `Content-Type` is always `image/jpeg`, whatever the original
  was.
- A corrupt/unreadable image or a generation/storage failure is logged and answered with an error status
  (5xx); never the original.
- No presigned URL, no path, no storage type in the response.

### 4. Partner Central: authenticated route

```
GET /mandates/{id}/documents/{documentId}/pages/{pageId}/preview
```

- Requires a signed-in agent (`CurrentAgent`), like every other page.
- The mandate is read with `shownMandate` (closed mandates included), the document with `load`, and the page
  must belong to it; anything else is the usual 404.
- Calls the server endpoint with the agent's bearer token and relays the bytes.
- Response headers:
  - `Content-Type: image/jpeg`
  - `Content-Disposition: inline; filename="mandat-<id>-page-<number>.jpg"`
  - `Cache-Control: private, max-age=300` (no shared cache; a copied URL still needs the session).
    `no-store` is the stricter alternative if the browser's disk cache is considered a copy too far.
  - `X-Content-Type-Options: nosniff`
- Errors: 401 → existing sign-out + `/login`; 403/404 → same status (error page); server 5xx or unreachable →
  502, logged. Do not redirect to `/login` for an `<img>` request; today the route is only opened as a tab,
  so the existing advice is acceptable and should be revisited if previews become inline.
- `Voir` links point here. The route is guessable but useless without the agent's session, and the server
  re-checks access on every request.
- New client method on `NdMandateDocumentClient`: `preview(mandateId, documentId, pageId): ByteArray`.

### 5. DTO and HTML changes

- Remove `url` from `MandateDocumentPage` in `ndopify-dto`; drop the `urlOf` callback from
  `MandateDocumentMapper`, `pageUrl()` and the `url-ttl-seconds` property from `MandateDocumentService`.
  The search and get endpoints stop presigning entirely, which also removes one URL per page per request.
- `DocumentPageView` drops `url`; the template builds the Partner Central route from
  `document.mandateId`, `document.id` and `page.id`.
- Update the comments in `mandate/document.html` and `MandateDocumentController` that describe the old
  presigned-URL behaviour.
- No change to the static prototype in `src/main/html/` (never served, never deleted).
- This is a breaking DTO change: no in-repo consumer other than Partner Central uses `url` today, and no
  admin console exists in this repo. If an external consumer relies on it, say so before the release and we
  keep the field deprecated for one release instead.

### 6. Configuration

- Remove `ndopify.mandate.document.url-ttl-seconds`.
- Preview tuning (max side, JPEG quality, watermark opacity/spacing, preview version) stays as constants in
  the service unless the builder sees a real need to make it configurable. Nothing environment-specific is
  expected.

## Edge cases

- **Phone photos**: EXIF orientation applied; otherwise pages display sideways.
- **CMYK JPEG**: TwelveMonkeys JPEG reader.
- **WEBP**: TwelveMonkeys webp reader (read-only is enough; we always write JPEG).
- **Transparent PNG/WEBP**: white background.
- **Huge image / decompression bomb**: dimension cap before decode; unprocessable fails closed.
- **Corrupt bytes**: Tika accepted the upload but ImageIO cannot decode; fail closed, log, let the agent
  replace the page.
- **Preview store fails after generation**: return 5xx; do not serve the original; a retry regenerates.
- **Page replaced while its preview is generated**: the original may be gone; fail closed, and the next
  request reads the new page. A single re-read-retry is acceptable.
- **Tenant rename**: stale watermark until regeneration (documented limitation).
- **URL copied into another browser**: redirect to sign-in / 401; no document bytes.
- **URL copied after the session ends**: same.
- **Signed-in agent saving the image**: allowed by design; gets the resized, watermarked JPEG only.

## Testing

- **Image pipeline (server, unit)**: output is JPEG; longest side capped and aspect ratio kept; no upscale;
  EXIF orientations (portrait/landscape); transparent PNG composited on white; WEBP input; CMYK JPEG; a
  solid test image shows watermark pixels (sampled region differs from the base colour); oversized image
  rejected; corrupt bytes rejected. Use small committed fixtures.
- **Server endpoint (integration, existing pattern)**: 200 `image/jpeg` with the store/read mocked, first
  call stores `<path>.preview-v1.jpg`, second call reads it without re-generating; original bytes unchanged;
  no path/URL in the response; 401 anonymous; 403 another agent's mandate; 404 unknown/foreign
  document/page; closed mandate still 200.
- **Partner Central**: `MockMvc` route returns the client's bytes with the headers above; anonymous → login;
  server 403/404 → same status; server 5xx → 502; template test asserts *Voir* points at the Partner Central
  route and the rendered document page contains no storage URL.
- **DTO consumers**: update the existing server tests that assert `page.url` (they will not compile after the
  field is removed) and `MandateDocumentViewsTest` / `MandateDocumentTemplateTest`.
- **Manual**: sign in, open *Voir*, save the image (resized + watermark, mandate and tenant readable); copy
  the URL into a private window (no image, sign-in); replace a page and confirm the new preview is
  watermarked and the old one is gone; open a document of a CLOSED mandate.

## Rollout

1. Release `ndopify-dto` (field removal).
2. Deploy `ndopify-server` (preview endpoint; documents no longer presign), then `ndopify-partner-central`
   (new route, updated template) in the same window. A server-first window only makes old links render
   empty; do not leave it for long.
3. Existing presigned URLs already handed out stay valid until their TTL (≤ 1 h); nothing to revoke.
4. No DB migration, no backfill: previews are generated on first view.

## Open decisions for the human

1. **Breaking DTO**: remove `url` (recommended, no in-repo consumer) or keep it deprecated for one release?
2. **2000 px longest side**: is that legible enough for ID numbers, stamps and land-title text? The cap is
   easy to raise, at the cost of a bigger saveable file.
3. **Browser cache**: `private, max-age=300` (recommended) or `no-store`?

## Left to the builder

- Exact class names/packages; whether the image component sits in `platform/image` with mandate-specific
  composition in `mandate/server/service`.
- Metadata/EXIF library and bundled font choice.
- Streaming vs buffering the bytes between server and Partner Central (images are small; buffering is fine).
- Whether to add a pixel-dimension check to the upload path so an unprocessable image is refused before it
  becomes a page (nice-to-have; the preview endpoint already fails closed).
- Cleanup of orphaned `-v1` previews on a future version bump.
