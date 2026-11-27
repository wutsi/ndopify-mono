# Mandate document preview page — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A mandate document's *Voir* opens a page of Partner Central, in the same tab, that shows one page's watermarked JPEG with a bar whose left side links back to the document page — working in the installed PWA and without JavaScript.

**Architecture:** A new GET route in `MandateDocumentController` renders `templates/mandate/document-preview.html` from a small view model built by `MandateDocumentViews.preview`; the existing authenticated `/preview` JPEG route becomes the page's image source. The document page's *Voir* rows point at the new route with no `target`/`rel`. The approved static prototype (`src/main/html/v2`, committed with this plan) is the visual baseline: Task 3 ports it into the Thymeleaf template and `document.css`; it is not redesigned.

**Tech Stack:** Kotlin, Spring Boot MVC, Thymeleaf, Jsoup-based template tests, `kotlin.test` + `mockito-kotlin`, esbuild asset bundle.

**Spec:** story #372 — https://github.com/wutsi/ndopify-mono/issues/372 — with its approved prototype under `modules/ndopify-partner-central/src/main/html/v2` (`mandate/document-preview.html`, `mandate/document.html`, `assets/css/documents.css` #372 block).

## Global Constraints

- Only `modules/ndopify-partner-central` changes: no `ndopify-dto`, no `ndopify-server`, no release.
- UI copy in French; code, comments and docs in English.
- The viewer works without JavaScript; the shared `app.min.js` bundle may load, but nothing on the page depends on it.
- No storage URL, page path, presigned URL nor original bytes in any HTML; the viewer's only image source is this app's authenticated `/mandates/{id}/documents/{documentId}/pages/{pageId}/preview` route.
- No site header, account menu, footer nor nav on the viewer; it is not a destination.
- CSS uses the tier-2 tokens of `partner-central.css` (`--color-overlay`, `--color-on-overlay`, `--space-*`, `--step-*`, `--target`, `--font-body`); no new colours.
- The approved prototype in `src/main/html/v2` is not served and must not be edited by the implementation.
- Assertions use `kotlin.test` (`assertEquals`, `assertTrue`, `assertFalse`, `assertNull`), mocks `mockito-kotlin`.
- `mvn verify` in `modules/ndopify-partner-central` (ktlint, tests, coverage gate, page-script tests) passes.

## Files

- Create: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document-preview.html`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html` (*Voir* rows)
- Modify: `modules/ndopify-partner-central/src/main/assets/css/document.css`
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (documents paragraph)
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt`
- Test: `.../src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt`

## Review Focus

- A page id that is not the document's (or not numeric): 404, never a 500, another document's image, or an empty `<img>` — Task 2.
- A 403/404 from ndopify-server on the mandate (another agent's, deleted): the viewer answers 404, never the document page's `changed` redirect — Task 2.
- A document with a blank title: its title reads as the type's French name in the bar and in the `alt` — Tasks 1 and 3.
- A long title at 320 px: the bar stays on one line, the title ellipsizes, the rank stays visible — Task 3 (the approved CSS is ported verbatim; check the rendered page at 320 px).
- A JS-off browser / failed image (502, signed-out): the bar and its back link are server-rendered and stay usable — Task 3 (server-rendered markup; no page script).

---

### Task 1: Preview view model and the document page's *Voir* href

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt:75-103`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt`

**Interfaces:**
- Consumes: `Mandate`, `MandateDocument`, `MandateDocumentPage`, `MandateDocumentLabels.type(type)`.
- Produces:
  - `data class DocumentPreviewView(val title: String, val number: Int, val count: Int, val imageHref: String, val backHref: String, val alt: String)`
  - `fun MandateDocumentViews.preview(mandate: Mandate, document: MandateDocument, page: MandateDocumentPage): DocumentPreviewView`
  - `DocumentPageView.href` now holds the viewer route (`/mandates/{id}/documents/{documentId}/pages/{pageId}`, no `/preview`).

- [ ] **Step 1: Write the failing tests**

In `MandateDocumentViewsTest`, change the expected hrefs of `document()` to the viewer route:

```kotlin
        assertEquals(
            listOf(
                "/mandates/301/documents/3103/pages/1",
                "/mandates/301/documents/3103/pages/2",
            ),
            view.pages.map { it.href },
        )
```

Add:

```kotlin
    @Test
    fun `preview - the page's rank, count and routes`() {
        val pages = listOf(
            MandateDocumentPage(id = 1, index = 0),
            MandateDocumentPage(id = 2, index = 1),
            MandateDocumentPage(id = 3, index = 2),
        )

        val view = MandateDocumentViews.preview(mandate, doc(pages = pages), pages[1])

        assertEquals("Titre foncier n° 4521", view.title)
        assertEquals(2, view.number)
        assertEquals(3, view.count)
        assertEquals("/mandates/301/documents/3103/pages/2/preview", view.imageHref)
        assertEquals("/mandates/301/documents/3103#pages-title", view.backHref)
        assertEquals("Page 2 du document « Titre foncier n° 4521 »", view.alt)
    }

    @Test
    fun `preview - a blank title reads as the type's name`() {
        val page = MandateDocumentPage(id = 1, index = 0)

        val view = MandateDocumentViews.preview(mandate, doc(title = "  ", pages = listOf(page)), page)

        assertEquals("Titre foncier", view.title)
        assertEquals("Page 1 du document « Titre foncier »", view.alt)
    }

    @Test
    fun `preview - the rank follows the page index, not the list order`() {
        val pages = listOf(
            MandateDocumentPage(id = 1, index = 2),
            MandateDocumentPage(id = 2, index = 0),
            MandateDocumentPage(id = 3, index = 1),
        )

        val view = MandateDocumentViews.preview(mandate, doc(pages = pages), pages.first { it.id == 3L })

        assertEquals(2, view.number)
        assertEquals(3, view.count)
        assertEquals("/mandates/301/documents/3103/pages/3/preview", view.imageHref)
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentViewsTest`
Expected: compilation fails — `preview` is unresolved; the `document()` href assertion fails.

- [ ] **Step 3: Add the view model and the builder**

In `MandateDocumentView.kt`, after `DocumentPageView`, add:

```kotlin
/**
 * The viewer of one page of a document: its title, its rank and the document's page count, this app's authenticated
 * JPEG route, and the way back to the document page's *Pages* heading. Never a storage path nor a storage URL.
 */
data class DocumentPreviewView(
    val title: String,
    /** "Page [number] sur [count]". */
    val number: Int,
    val count: Int,
    /** This app's `/preview` route: the watermarked JPEG, behind the agent's session. */
    val imageHref: String,
    /** The document page's *Pages* heading. */
    val backHref: String,
    val alt: String,
)
```

Update `DocumentPageView`'s KDoc to say `href` is the viewer page route (same tab), not the JPEG route.

In `MandateDocumentViews.kt`, change the page href inside `document()`:

```kotlin
                        href = "/mandates/${mandate.id}/documents/${document.id}/pages/${page.id}",
```

and add:

```kotlin
    /** The viewer of one page: its rank in the document's index order, the title (the type's name when blank). */
    fun preview(mandate: Mandate, document: MandateDocument, page: MandateDocumentPage): DocumentPreviewView {
        val title = document.title.trim().ifEmpty { MandateDocumentLabels.type(document.type) }
        val number = document.pages.sortedBy { it.index }.indexOfFirst { it.id == page.id } + 1
        return DocumentPreviewView(
            title = title,
            number = number,
            count = document.pages.size,
            imageHref = "/mandates/${mandate.id}/documents/${document.id}/pages/${page.id}/preview",
            backHref = "/mandates/${mandate.id}/documents/${document.id}#pages-title",
            alt = "Page $number du document « $title »",
        )
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentViewsTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentView.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViews.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentViewsTest.kt
git commit -m "feat(npc): build the mandate document preview view" -m "Refs #372"
```

---

### Task 2: The preview page route

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt` (constants near line 84; new mapping after `document()`, before `preview()`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt`

**Interfaces:**
- Consumes: `MandateDocumentViews.preview(...)` and `DocumentPreviewView` from Task 1; the existing `currentAgent`, `shownMandate`, `load`, `notFoundResponse`, `formatter` helpers.
- Produces: `MandateDocumentController.VIEW_PREVIEW = "mandate/document-preview"`; `GET /mandates/{id}/documents/{documentId}/pages/{pageId}` rendering it with the model attribute `preview: DocumentPreviewView`.

- [ ] **Step 1: Write the failing tests**

In `MandateDocumentControllerTest`, update `document page`'s expected hrefs:

```kotlin
        assertEquals(
            listOf(
                "/mandates/301/documents/3103/pages/2001",
                "/mandates/301/documents/3103/pages/2002",
            ),
            view.pages.map { it.href },
        )
```

Add, next to the existing preview tests:

```kotlin
    @Test
    fun `preview page`() {
        val result = mvc.perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}")).andReturn()

        assertEquals(MandateDocumentController.VIEW_PREVIEW, result.modelAndView?.viewName)
        assertEquals(
            DocumentPreviewView(
                title = "Titre foncier n° 4521",
                number = 2,
                count = 2,
                imageHref = "/mandates/301/documents/3103/pages/2002/preview",
                backHref = "/mandates/301/documents/3103#pages-title",
                alt = "Page 2 du document « Titre foncier n° 4521 »",
            ),
            model(result)["preview"],
        )
    }

    @Test
    fun `preview page - not signed in - signed out`() {
        doReturn(null).whenever(currentAgent).get()

        val result = mvc
            .perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page1.id}"))
            .andReturn()

        assertEquals("redirect:/login", result.modelAndView?.viewName)
        verify(mandateClient, never()).get(any())
    }

    @Test
    fun `preview page - an unknown page - 404`() {
        val result = mvc
            .perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/9999"))
            .andReturn()

        assertEquals(404, result.response.status)
    }

    @Test
    fun `preview page - a non-numeric page id - 404`() {
        val result = mvc
            .perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/abc"))
            .andReturn()

        assertEquals(404, result.response.status)
    }

    @Test
    fun `preview page - a page of another document - 404`() {
        doReturn(document.copy(pages = listOf(page1))).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val result = mvc
            .perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page2.id}"))
            .andReturn()

        assertEquals(404, result.response.status)
    }

    @Test
    fun `preview page - an unknown mandate - 404`() {
        doThrow(serverError(HttpStatus.NOT_FOUND, ErrorCode.MANDATE_NOT_FOUND)).whenever(mandateClient).get(MANDATE_ID)

        val result = mvc
            .perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page1.id}"))
            .andReturn()

        assertEquals(404, result.response.status)
    }

    @Test
    fun `preview page - another agent's mandate - 404`() {
        doThrow(serverError(HttpStatus.FORBIDDEN)).whenever(mandateClient).get(MANDATE_ID)

        val result = mvc
            .perform(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/${page1.id}"))
            .andReturn()

        assertEquals(404, result.response.status)
    }

    @Test
    fun `preview page - a cancelled mandate still renders`() {
        doReturn(document).whenever(documentClient).get(311, DOCUMENT_ID)

        val result = mvc.perform(get("/mandates/311/documents/$DOCUMENT_ID/pages/${page1.id}")).andReturn()

        assertEquals(MandateDocumentController.VIEW_PREVIEW, result.modelAndView?.viewName)
    }
```

`serverError(status, code)` and the fixtures already exist in the test class; `ErrorCode.MANDATE_NOT_FOUND` is already imported by the module's other tests if not by this one — add the import only if the compiler asks.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentControllerTest`
Expected: the new requests answer 404 (no mapping) and `VIEW_PREVIEW` does not compile.

- [ ] **Step 3: Add the route**

Add to the constants:

```kotlin
        const val VIEW_PREVIEW = "mandate/document-preview"
```

Add after `document()` (and before `preview()`):

```kotlin
    /**
     * The viewer of one page of a document (the approved prototype, #372): a bar whose left side links back to the
     * document page's *Pages* heading, the page's rank, and the page's image from this app's authenticated [preview]
     * route. A viewer, not a destination: no header nor account menu, and no JavaScript.
     *
     * Same access as the document page, except that anything ndopify-server can't return (an unknown mandate, a
     * document or a page of another one, another agent's mandate) is a 404 here rather than the mandate page's
     * `changed` redirect: this page has no way back to a document that isn't there.
     */
    @GetMapping("/{documentId}/pages/{pageId}")
    fun previewPage(
        @PathVariable id: String,
        @PathVariable documentId: String,
        @PathVariable pageId: String,
        model: Model,
    ): String {
        currentAgent.get() ?: throw SignInRequiredException()
        val mandate = shownMandate(id) ?: notFoundResponse()
        val document = load(mandate.id, documentId) ?: notFoundResponse()
        val page = document.pages.sortedBy { it.index }.firstOrNull { it.id == pageId.toLongOrNull() }
            ?: notFoundResponse()
        model.addAttribute("preview", MandateDocumentViews.preview(mandate, document, page))
        return VIEW_PREVIEW
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentController.kt \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentControllerTest.kt
git commit -m "feat(npc): add the mandate document preview page route" -m "Refs #372"
```

---

### Task 3: Port the approved prototype (template, document page, CSS)

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document-preview.html`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html:129-135`
- Modify: `modules/ndopify-partner-central/src/main/assets/css/document.css` (header comment, `.doc-page__view` comment, new block at the end)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt`

**Interfaces:**
- Consumes: `DocumentPreviewView` (`title`, `number`, `count`, `imageHref`, `backHref`, `alt`) from Task 1 and the route from Task 2.
- Produces: the rendered viewer with hooks `header.doc-preview__bar`, `a.doc-preview__back` (`data-preview-back`), `[data-preview-title]`, `[data-preview-count]`, `img.doc-preview__image` (`data-preview-image`), body `data-page="mandate-document-preview"`.

- [ ] **Step 1: Write the failing template tests**

In `MandateDocumentTemplateTest.document()`, replace the `_blank`/`noopener` assertions with:

```kotlin
        assertNull(links[0]?.attr("target"))
        assertNull(links[0]?.attr("rel"))
        assertEquals(listOf("la page 1", "la page 2"), links.map { it?.selectFirst(".visually-hidden")?.text() })
        assertEquals(listOf("Voir la page 1", "Voir la page 2"), links.map { it?.text() })
        assertFalse(page.html().contains("nouvel onglet"))
```

Add:

```kotlin
    @Test
    fun `preview page`() {
        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/2002"))

        assertEquals("Page 2 — Titre foncier n° 4521 — Ndopify Partner Central", page.title())
        assertEquals("mandate-document-preview", page.body().attr("data-page"))
        assertNull(page.selectFirst("header.site-header"))
        assertNull(page.selectFirst("nav.user-menu"))

        val bar = page.selectFirst("header.doc-preview__bar")!!
        val back = bar.selectFirst("a.doc-preview__back")!!
        assertEquals("/mandates/301/documents/3103#pages-title", back.attr("href"))
        assertEquals("Titre foncier n° 4521", back.selectFirst("[data-preview-title]")?.text())
        assertTrue(back.text().endsWith("— retour au document"), back.text())
        assertEquals("Page 2 sur 2", bar.selectFirst("[data-preview-count]")?.text())

        val image = page.selectFirst("img.doc-preview__image")!!
        assertEquals("/mandates/301/documents/3103/pages/2002/preview", image.attr("src"))
        assertEquals("Page 2 du document « Titre foncier n° 4521 »", image.attr("alt"))
        assertEquals("width=device-width, initial-scale=1", page.selectFirst("meta[name=viewport]")?.attr("content"))
        assertFalse(page.html().contains("https://s3"))
        assertFalse(page.html().contains("signature"))
        assertFalse(page.html().contains("presigned"))
    }

    @Test
    fun `preview page - a blank title reads as the type's name`() {
        doReturn(document.copy(title = " ")).whenever(documentClient).get(MANDATE_ID, DOCUMENT_ID)

        val page = render(get("/mandates/$MANDATE_ID/documents/$DOCUMENT_ID/pages/2001"))

        assertEquals("Page 1 — Titre foncier — Ndopify Partner Central", page.title())
        assertEquals("Titre foncier", page.selectFirst("[data-preview-title]")?.text())
        assertEquals("Page 1 du document « Titre foncier »", page.selectFirst("img.doc-preview__image")?.attr("alt"))
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: the preview route renders `mandate/document-preview` and the view resolver fails (template missing); the `document()` assertions fail on `target`/`rel`.

- [ ] **Step 3: Port the template**

Create `templates/mandate/document-preview.html` from the approved `src/main/html/v2/mandate/document-preview.html`, with the Thymeleaf model instead of the prototype's sample markup:

```html
<!doctype html>
<html lang="fr" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/head :: head(|Page ${preview.number} — ${preview.title} — Ndopify Partner Central|, |Une page d'un document du mandat, en plein écran, avec le retour au document.|, false)}"></head>
<!--/*
  #372: a page of a mandate's document, full screen (GET /mandates/{id}/documents/{documentId}/pages/{pageId}),
  opened by the document page's *Voir* in the same tab. A viewer, not a destination: no site header, no account menu,
  no footer nor nav. One compact bar whose whole left side is a single link back to the document page's *Pages*
  heading — the decorative arrow and the document's title, a visually hidden "— retour au document" completing its
  accessible name — and the page's rank on its right, on ONE line at every width (the title ellipsizes, the rank
  never). Then the page's image, this app's authenticated .../preview route, centred on the overlay: never a storage
  URL, a page path nor a presigned URL. No JavaScript is needed; the viewport keeps its default scaling, so
  pinch-zoom still works. "preview" is the DocumentPreviewView.
*/-->
<body class="doc-preview" data-page="mandate-document-preview">
    <header class="doc-preview__bar">
        <a class="doc-preview__back" th:href="${preview.backHref}" data-preview-back>
            <span class="doc-preview__arrow" aria-hidden="true">←</span>
            <h1 class="doc-preview__title"><span th:text="${preview.title}" data-preview-title>Titre foncier n° 4521</span></h1>
            <span class="visually-hidden"> — retour au document</span>
        </a>
        <p class="doc-preview__count" th:text="|Page ${preview.number} sur ${preview.count}|" data-preview-count>Page 2 sur 2</p>
    </header>
    <main id="main" class="doc-preview__stage">
        <img class="doc-preview__image" th:src="${preview.imageHref}" th:alt="${preview.alt}" data-preview-image>
    </main>
</body>
</html>
```

- [ ] **Step 4: Point *Voir* at the page route**

In `templates/mandate/document.html`, replace the *Voir* anchor:

```html
                                <a class="doc-page__view" th:href="${page.href}" data-page-link>Voir<span
                                        class="visually-hidden" th:text="| la page ${page.number}|"> la page 1</span></a>
```

- [ ] **Step 5: Port the CSS**

In `src/main/assets/css/document.css`:

- Update the header comment: "#372 adds the viewer of a document's page … the document page shows no image inline: *Voir* opens the page's viewer in the same tab".
- Update the `.doc-page__view` comment to "opening the page's viewer in the same tab (#372)".
- Append the approved prototype's `mandate/document-preview.html (#372)` block from
  `src/main/html/v2/assets/css/documents.css` verbatim (`body.doc-preview` through the `@media (min-width: 640px)`
  rule). Its tokens (`--color-overlay`, `--color-on-overlay`, `--space-*`, `--step-body`, `--step-hint`, `--target`,
  `--font-body`) already exist in `src/main/assets/css/partner-central.css`; do not add colours.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=MandateDocumentTemplateTest`
Expected: PASS.

- [ ] **Step 7: Check the rendered bar against the approved prototype**

Start the app (`mvn spring-boot:run`) with `ndopify-server` running and a signed-in agent, open a mandate document with
at least two pages and follow *Voir*. Expected at 320 and 390 px: one bar line, the title ellipsized, *Page N sur M*
whole at the right, the image centred, no site header; the back link lands on `…/documents/{documentId}#pages-title`
in the same tab, also with JavaScript disabled.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/mandate/document-preview.html \
  modules/ndopify-partner-central/src/main/resources/templates/mandate/document.html \
  modules/ndopify-partner-central/src/main/assets/css/document.css \
  modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/document/MandateDocumentTemplateTest.kt
git commit -m "feat(npc): port the mandate document preview page" -m "Refs #372"
```

---

### Task 4: Module docs and full verification

**Files:**
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the *documents* bullet, around lines 349-356)

**Interfaces:**
- Consumes: every task above.
- Produces: the module documentation and a green build.

- [ ] **Step 1: Update the module docs**

In the documents bullet, replace the sentence that describes *Voir* as "a *Voir* link to this app's preview route
(`/mandates/{id}/documents/{documentId}/pages/{pageId}/preview`, new tab)" with: each page's *Voir* opens the page's
viewer (`/mandates/{id}/documents/{documentId}/pages/{pageId}`, same tab, `templates/mandate/document-preview.html`):
a one-line bar whose whole left side links back to the document page at `#pages-title` — the arrow and the document's
title, a visually hidden *— retour au document* completing the link's name — and the rank *Page N sur M* at its
right, the image below; no header, no account menu, no JavaScript. Keep the following description of the `/preview`
route's headers, but make it the viewer's image source.

- [ ] **Step 2: Run the full module build**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint, unit and template tests, coverage gate, page-script tests).

- [ ] **Step 3: Check the change set**

Run: `git status --porcelain` and `git diff --stat origin/main`
Expected: only the files listed in *Files*; no `src/main/html/v2` change.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the mandate document preview page" -m "Refs #372"
```
