# Share a Listing from Partner Central — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Partager" button to Partner Central's listing page that shares the listing's public URL, shown only for `PUBLISHED`, `RENTED` and `SOLD` listings, and mirror it in the prototype.

**Architecture:** Server-side, the existing `ListingView.publicUrl` already gates the public link to the three public statuses. The template renders a share button next to that link, carrying the URL and title as data attributes; `listing.js` reveals it when the browser can share or copy and performs either action on click. The static prototype gets the same markup and behavior with a stub public URL.

**Tech Stack:** Kotlin, Spring Boot + Thymeleaf, vanilla JS, CSS custom-property tokens, Jsoup + MockMvc template tests.

**Spec:** `docs/superpowers/specs/2026-11-09-npc-listing-share-design.md`

## Global Constraints

- UI text is French; code, comments and docs are English.
- No new dependency, no DTO change, no ndopify-server change.
- Every page works without JavaScript: the share button stays `hidden` without it; the public link remains.
- `src/main/html/` is the prototype: never served, never deleted.
- Tests use `kotlin.test` assertions; ktlint runs on `mvn validate`.

## Review Focus

- A public listing whose tenant has no `publicPortalUrl`: no share button, no public link (Task 1 test).
- The shared URL is the public page, never `/listings/{id}` (Task 1 test asserts the data attribute).
- A browser with neither `navigator.share` nor `navigator.clipboard`: the button stays hidden (Task 1 template/JS, manual check).
- A dismissed share sheet: no uncaught rejection and the button label is unchanged (Task 1 JS `catch`, Task 2 manual check).
- A `RENTED`/`SOLD` listing in the prototype: its tag renders and the share button appears instead of a JS error (Task 2 manual check).

---

### Task 1: Share button in the BFF

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html:110-111`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css:25-26`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js:104-118`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt:179-269`

**Interfaces:**
- Consumes: `ListingView.publicUrl: String?` and `ListingView.title: String` (already built by `ListingViews.of`).
- Produces: markup hooks `[data-listing-share]`, `[data-share-url]`, `[data-share-title]`, `[data-share-label]`, `[data-share-status]`, and a `initShare()` function local to `listing.js`.

- [ ] **Step 1: Write the failing template tests**

In `ListingTemplateTest`, add `assertNull(page.selectFirst("[data-listing-share]"))` to `published()` (the tenant there has no `publicPortalUrl`), then add:

```kotlin
@Test
fun `published - share button`() {
    doReturn(TENANT.copy(publicPortalUrl = "https://ndopify.com")).whenever(tenantContext).getTenant()

    val page = render(
        COMPLETE.copy(
            status = ListingStatus.PUBLISHED,
            publishedAt = SENT,
            url = "/douala/bonapriso/listing/501/appartement",
        ),
    )

    val button = page.selectFirst("[data-listing-share]")!!
    assertEquals("https://ndopify.com/douala/bonapriso/listing/501/appartement", button.attr("data-share-url"))
    assertEquals("Annonce · Appartement, Bonapriso", button.attr("data-share-title"))
    assertEquals("Partager", button.text())
    assertTrue(button.hasAttr("hidden"))
    assertNotNull(page.selectFirst("[data-share-status]"))
}
```

In `closed - public url`, inside the `RENTED`/`SOLD` loop, add:

```kotlin
assertEquals(
    "https://ndopify.com/douala/bonapriso/listing/501/appartement",
    page.selectFirst("[data-listing-share]")?.attr("data-share-url"),
)
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingTemplateTest`
Expected: FAIL — `[data-listing-share]` is null in the new assertions.

- [ ] **Step 3: Render the button next to the public link**

Replace the public-link paragraph in `templates/listing.html` with:

```html
<p class="listing-public" th:if="${l.publicUrl}" data-listing-public>
    <a th:href="${l.publicUrl}" target="_blank" rel="noopener" data-listing-public-url>Voir l'annonce en ligne</a>
    <button type="button" class="btn btn--secondary btn--compact listing-share" data-listing-share hidden
            th:attr="data-share-url=${l.publicUrl},data-share-title=${l.title}">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M12 15V4m0 0L8 8m4-4 4 4M5 13v6h14v-6"/></svg>
        <span data-share-label>Partager</span>
    </button>
    <span class="visually-hidden" role="status" data-share-status></span>
</p>
```

- [ ] **Step 4: Add the styles**

After the `.listing-hero` rule in `public/assets/css/listing.css`, add:

```css
/* The public page's link and its share button, once the listing is published or closed. */
.listing-public {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--space-3);
}

/* The share button borrows the mandate page's WhatsApp button style (btn--secondary btn--compact). */
.listing-share[hidden] {
    display: none;
}
```

- [ ] **Step 5: Add the share behavior**

In `public/assets/js/listing.js`, add `initShare()` above `initListing()` and call it first:

```js
/* ---------- The checklist ---------- */
// The public page's URL: the system share sheet where there is one, the clipboard otherwise.
function initShare() {
    var button = $("[data-listing-share]");
    if (!button) return;
    var url = button.getAttribute("data-share-url");
    var title = button.getAttribute("data-share-title") || document.title;
    var label = $("[data-share-label]", button);
    var status = $("[data-share-status]");
    var canShare = typeof navigator.share === "function";
    var canCopy = !!(navigator.clipboard && navigator.clipboard.writeText);
    if (!url || (!canShare && !canCopy)) return;
    button.hidden = false;
    button.addEventListener("click", function () {
        if (canShare) {
            navigator.share({ title: title, url: url }).catch(function () { /* dismissed */ });
            return;
        }
        navigator.clipboard.writeText(url).then(function () {
            label.textContent = "Lien copié";
            if (status) status.textContent = "Lien copié.";
            window.setTimeout(function () {
                label.textContent = "Partager";
                if (status) status.textContent = "";
            }, 2000);
        }).catch(function () { /* nothing */ });
    });
}

function initListing() {
    initShare();
    focusFlash("[data-listing-flash]");
    // ...existing body unchanged
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=ListingTemplateTest`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Confirm the whole module still passes**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/listing.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/listing.css \
        modules/ndopify-partner-central/src/main/resources/public/assets/js/listing.js \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt
git commit -m "feat(npc): share a published or closed listing's public page"
```

---

### Task 2: Same behavior in the prototype

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/html/listing.html:79-80`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/css/listing.css:14-16`
- Modify: `modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js:3170-3177` (`LISTING_TAGS`), `3334-3338` (near `listingTitle`), `3391-3537` (`initListing`)

**Interfaces:**
- Consumes: the prototype's in-browser `listing` object (`id`, `status`, `fields`) and `listingTitle(listing)`.
- Produces: the same `[data-listing-share]` / `[data-share-url]` / `[data-share-title]` / `[data-share-label]` / `[data-share-status]` hooks, plus `publicUrlOf(listing)`.

- [ ] **Step 1: Add the prototype markup**

Replace the public-link paragraph in `src/main/html/listing.html` with:

```html
<p class="listing-public" hidden data-listing-public>
    <a href="#" target="_blank" rel="noopener" data-listing-public-url>Voir l'annonce en ligne</a>
    <button type="button" class="btn btn--outline listing-share" data-listing-share hidden
            data-share-url="" data-share-title="">
        <svg class="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
             stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path d="M12 15V4m0 0L8 8m4-4 4 4M5 13v6h14v-6"/></svg>
        <span data-share-label>Partager</span>
    </button>
    <span class="visually-hidden" role="status" data-share-status></span>
</p>
```

- [ ] **Step 2: Add the prototype styles**

After `.status-tag--neutral` in `src/main/html/assets/css/listing.css`, add the same two rules as Task 1 Step 4 (`.listing-public` and `.listing-share[hidden]`).

- [ ] **Step 3: Add the closed tags**

In `LISTING_TAGS`, after `PUBLISHED`, add:

```js
RENTED: ["Loué", "warning"],
SOLD: ["Vendu", "warning"]
```

- [ ] **Step 4: Add the stub public URL (the prototype has no public portal)**

After `listingTitle(listing)`, add:

```js
// PROTOTYPE: no public portal here, the shared link is a stub.
function publicUrlOf(listing) {
    return "https://ndopify.com/listing/" + encodeURIComponent(listing.id);
}
```

- [ ] **Step 5: Wire the link and button in `initListing`**

At the top of `initListing`, after the listing is found and `mandateLink` is built, define the share button, its label, its status element and the click handler:

```js
var share = $("[data-listing-share]");
var shareLabel = $("[data-share-label]", share);
var shareStatus = $("[data-share-status]");
share.addEventListener("click", function () {
    var url = share.getAttribute("data-share-url");
    if (!url) return;
    var title = share.getAttribute("data-share-title") || document.title;
    if (typeof navigator.share === "function") {
        navigator.share({ title: title, url: url }).catch(function () { /* dismissed */ });
        return;
    }
    if (!navigator.clipboard || !navigator.clipboard.writeText) return;
    navigator.clipboard.writeText(url).then(function () {
        shareLabel.textContent = "Lien copié";
        shareStatus.textContent = "Lien copié.";
        window.setTimeout(function () {
            shareLabel.textContent = "Partager";
            shareStatus.textContent = "";
        }, 2000);
    }).catch(function () { /* nothing */ });
});
```

In `render()`, replace the `// PROTOTYPE: no public portal here...` block with:

```js
var publicUrl = ["PUBLISHED", "RENTED", "SOLD"].indexOf(listing.status) !== -1 ? publicUrlOf(listing) : null;
$("[data-listing-public]").hidden = !publicUrl;
if (publicUrl) {
    $("[data-listing-public-url]").href = publicUrl;
    share.setAttribute("data-share-url", publicUrl);
    share.setAttribute("data-share-title", "Annonce · " + listingTitle(listing));
    share.hidden = !(typeof navigator.share === "function" ||
        (navigator.clipboard && navigator.clipboard.writeText));
}
```

- [ ] **Step 6: Check the script parses and the hooks are present**

Run: `node --check modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
Expected: no output (exit 0).

Run: `grep -c 'data-listing-share\|data-share-url\|data-share-title' modules/ndopify-partner-central/src/main/html/listing.html modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js`
Expected: each file reports at least 2 matches.

- [ ] **Step 7: Manual prototype check**

Open `modules/ndopify-partner-central/src/main/html/listing.html?id=<a listing id>` after creating/publishing a listing, then:
- PUBLISHED: "Voir l'annonce en ligne" and "Partager" both visible; clicking shares/copies `https://ndopify.com/listing/{id}`.
- Close its mandate to make it RENTED: the tag says "Loué" and "Partager" stays.
- DRAFT: neither appears.
- A browser with JS disabled: "Partager" is absent. The whole prototype page is JS-driven, so its public link is absent too (the BFF is the no-JS reference: the link stays, the share button does not render).

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/html/listing.html \
        modules/ndopify-partner-central/src/main/html/assets/css/listing.css \
        modules/ndopify-partner-central/src/main/html/assets/js/partner-central.js
git commit -m "feat(npc): prototype share button for published and closed listings"
```
