# Partner Central Tab Favicon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Partner Central a transparent browser-tab favicon — the supplied blue disc with its black chevron, cut out of the white square of the attachment — and declare it in every served head, while the app's install icons stay the house/N of #249.

**Architecture:** The 500×500 attachment is cut to the disc (alpha 0 outside it) and committed as `public/assets/images/favicon.png`; `favicon-16.png`, `favicon-32.png` and `public/favicon.ico` (16/32/48) are derived from it by a one-off, **uncommitted** Python/Pillow script (the same recipe #249 used for the icon set). `templates/fragments/head.html` and `public/offline.html` gain the three `<link rel="icon">` lines the approved prototype shows; `/favicon.ico` stays public and a sign-in-target exclusion, and now answers 200. Nothing else moves: the manifest, the install PNGs, the service worker's precache and the cache headers are untouched (the worker's `VERSION` re-hashes because `offline.html` changed, which is the point).

**Tech Stack:** Kotlin, Spring Boot 4.1.2 (Thymeleaf, Security), Maven, JUnit 5 + `kotlin.test` + jsoup, `java.awt.ImageIO` for the PNG assertions, Python 3 + Pillow for the one-off asset generation (not a project dependency).

**Spec:** https://github.com/wutsi/ndopify-mono/issues/286 (enriched story, decision and acceptance criteria) and its approved prototype, `modules/ndopify-partner-central/src/main/html/favicon.html`.

## Global Constraints

- `modules/ndopify-partner-central` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server`, `ndopify-web` or `ndopify-esignature` change; no database, `application*.yml`, new dependency or new Maven plugin.
- **The approved prototype is the baseline.** This plan ports it; it does not design anything. The three head lines are exactly the sheet's `<pre>` ([`favicon.html`](../../../modules/ndopify-partner-central/src/main/html/favicon.html), *Les fichiers livrés et les lignes du head*). Do not edit `src/main/html/`: it is the approved prototype, already committed with this plan.
- The supplied source is the story attachment, 500×500 PNG: opaque white square, `#1d7edf` disc with a ~7 px white margin (visual radius 243 px), black chevron with bounds x 71–428, y 121–279. Its SHA-256:
  `97b45e25e493ef84beb3291fcf4024795870cc1dccff8e7ccb2a9ac11a5b3994`.
- **Transparent, no background.** Every delivered file keeps the disc and the chevron and has alpha 0 outside the disc — no opaque white square, no full-bleed tile, no halo. The cut is at radius **236 px** of the 500 px source (~97 % of the disc's 243 px visual radius), which falls inside the disc's antialiased edge so no white fringe survives on a dark tab. The disc is not resized, recoloured or redrawn.
- Delivered files, exactly: `public/assets/images/favicon.png` 500×500 (the transparent master), `favicon-16.png` 16×16, `favicon-32.png` 32×32, `public/favicon.ico` with 16/32/48 layers. The brand blue is `#1d7edf` (`BLUE = 0x1d7edf`), the chevron black (`0x000000`).
- `templates/fragments/head.html` and `public/offline.html` gain the same three lines, in this order: `rel="icon"` `sizes="any"` → `/favicon.ico`; `type="image/png" sizes="32x32"` → `/assets/images/favicon-32.png`; `type="image/png" sizes="16x16"` → `/assets/images/favicon-16.png`. In `head.html` the `href`s go through `@{…}` (like the manifest and the apple-touch icon); `offline.html` is plain HTML and carries absolute paths.
- **Unchanged:** `public/manifest.json`, `public/assets/images/icon.png`, `icon-192.png`, `icon-512.png`, `icon-maskable-192.png`, `icon-maskable-512.png`, `apple-touch-icon.png` (the house/N install identity), the service worker's precache list (`/offline.html`, `/assets/images/logo.png`) and `config/AssetCacheFilter.kt`'s rules. `/favicon.ico` stays in `config/SecurityConfiguration.kt`'s `permitAll()` and in `ui/login/SignInTarget.EXACT_EXCLUDED`.
- The assets stay committed binaries. The Pillow generator lives in `/tmp`, is not committed and is not wired into Maven.
- UI copy is French; code, comments, KDoc and commit messages are English.
- `mvn test` (and the final `mvn verify`) in `modules/ndopify-partner-central` must be green; they need neither MySQL nor a running ndopify-server.
- Commits are conventional and scoped `npc`, with `Refs #286` in the body.

## File map

| File | Responsibility |
|---|---|
| `src/main/resources/public/assets/images/favicon.png` | The transparent 500×500 master, cut from the attachment |
| `src/main/resources/public/assets/images/favicon-16.png` | 16 px, the tab size |
| `src/main/resources/public/assets/images/favicon-32.png` | 32 px, dense tabs |
| `src/main/resources/public/favicon.ico` | The browser's automatic request, 16/32/48 |
| `src/main/resources/templates/fragments/head.html` | The one head of every served page: declares the three icons |
| `src/main/resources/public/offline.html` | Self-contained offline page: declares the same three, absolute |
| `src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt` | Alpha/size/composition of the four files, the ICO header, the offline head |
| `src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt` | The rendered head's three icon links |
| `README.md`, `CLAUDE.md` | The module's PWA and authentication descriptions |

## Review Focus

- **An opaque background slipping back in.** A plain resize of the attachment, a copied source file, or an ICO writer that flattens alpha onto white would ship the white square again. Task 1 pins alpha 0 at the corners and edges of every PNG and the ICO's 32-bit layers.
- **A white fringe around the disc on a dark tab.** Cutting at the disc's visual edge keeps the source's blue-on-white antialiasing, which reads as a halo. Task 1 pins alpha 0 just outside radius 236 and full alpha just inside it; Task 4 checks the tab on a dark browser.
- **`/favicon.ico` answering 404 or 302 again.** Removing its security matcher or its `SignInTarget` exclusion, or writing the file at the wrong path, turns the browser's automatic request into an error or a sign-in redirect. Task 2 runs `SignInTargetTest.rejectsLoginAndPublicPaths`, and Task 4 curls it signed out.
- **An offline page whose new head never reaches the browser.** `sw.js`'s `VERSION` is the hash of `offline.html` + `logo.png`; if the build does not regenerate it, the cached page keeps the old head. Task 2 runs `PwaAssetsTest.serviceWorkerVersionMatchesTheOfflinePage`, which recomputes that hash.
- **A test or document still saying "no favicon".** #249 pinned the absence in three places; one missed leaves a green build contradicting the shipped app. Task 1 replaces the asset assertions, Task 2 the head and offline ones, and Task 3 greps `favicon` across the module and its two documents.

---

### Task 1: Port the approved asset set — the transparent master and its derivatives

**Files:**
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon.png`
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-16.png`
- Create: `modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-32.png`
- Create: `modules/ndopify-partner-central/src/main/resources/public/favicon.ico`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt`

**Interfaces:**
- Consumes: the story's attachment (downloaded in Step 3); the existing `PwaAssetsTest` helpers `image(path)`, `rgb(image, x, y)`, `pngSize(bytes)`, `resourceBytes(path)` and the companion's `BLUE`.
- Produces: classpath resources `public/assets/images/favicon.png`, `public/assets/images/favicon-16.png`, `public/assets/images/favicon-32.png`, `public/favicon.ico`, all transparent around the disc.

- [ ] **Step 1: Write the failing tests**

In `PwaAssetsTest`, delete `faviconFilesAreGone` and add these four tests (the existing helpers `image`, `rgb`,
`pngSize`, `resourceBytes` and the companion's `BLUE`/`WHITE` are enough):

```kotlin
@Test
fun faviconsAreTransparentAroundTheDisc() {
    listOf(
        "public/assets/images/favicon.png",
        "public/assets/images/favicon-16.png",
        "public/assets/images/favicon-32.png",
    ).forEach { path ->
        val icon = image(path)
        val last = icon.width - 1
        listOf(0 to 0, last to 0, 0 to last, last to last).forEach { (x, y) ->
            assertEquals(0, icon.getRGB(x, y) ushr 24, "$path is not transparent at ($x,$y)")
        }
        assertEquals(0xFF, icon.getRGB(last / 2, last / 2) ushr 24, "$path has no opaque centre")
    }
}

@Test
fun faviconMasterIsTheSuppliedArtworkWithoutItsBackground() {
    val master = image("public/assets/images/favicon.png")
    assertEquals(500 to 500, master.width to master.height)
    assertEquals(BLUE, rgb(master, 250, 250)) // the disc
    assertEquals(0x000000, rgb(master, 250, 130)) // the roof
    assertEquals(BLUE, rgb(master, 250, 60))
    assertEquals(0, master.getRGB(0, 0) ushr 24) // the source's white square is gone
    assertEquals(0, master.getRGB(250, 13) ushr 24) // outside the cut (radius 236: the disc starts at y = 14)
    assertEquals(0xFF, master.getRGB(250, 15) ushr 24) // just inside it: opaque, no white fringe
}

@Test
fun faviconFilesHaveTheirSizes() {
    assertEquals(500 to 500, pngSize(resourceBytes("public/assets/images/favicon.png")))
    assertEquals(16 to 16, pngSize(resourceBytes("public/assets/images/favicon-16.png")))
    assertEquals(32 to 32, pngSize(resourceBytes("public/assets/images/favicon-32.png")))
}

@Test
fun faviconIcoCarriesThreeTransparentLayers() {
    val bytes = resourceBytes("public/favicon.ico")
    assertEquals(listOf(0, 0, 1, 0), bytes.take(4).map { it.toInt() and 0xff }, "not an ICO header")
    assertEquals(3, bytes[4].toInt() and 0xff, "the ICO must carry 16, 32 and 48")
    val entries = (0 until 3).map { i ->
        val at = 6 + 16 * i
        val size = (bytes[at].toInt() and 0xff) to (bytes[at + 1].toInt() and 0xff)
        val bitCount = (bytes[at + 6].toInt() and 0xff) or ((bytes[at + 7].toInt() and 0xff) shl 8)
        size to bitCount
    }
    assertEquals(listOf(16 to 16, 32 to 32, 48 to 48), entries.map { it.first })
    assertEquals(listOf(32, 32, 32), entries.map { it.second }, "a layer without alpha (32 bpp) loses the transparency")
}
```

`rgb(...)` masks the alpha out, so the colour assertions still read the stored RGB; do not assert RGB on a pixel whose
alpha the tests assert is 0.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PwaAssetsTest`
Expected: FAIL — `favicon.png`, `favicon-16.png`, `favicon-32.png` and `favicon.ico` do not exist on the classpath yet.

- [ ] **Step 3: Download the attachment and verify it**

```bash
cd modules/ndopify-partner-central
curl -sL -o /tmp/npc-favicon-source.png \
  "https://github.com/user-attachments/assets/0cc99495-217a-4a6e-b572-c453751551f6"
file /tmp/npc-favicon-source.png
shasum -a 256 /tmp/npc-favicon-source.png
```

Expected: `PNG image data, 500 x 500, 8-bit/color RGBA, non-interlaced` and
`97b45e25e493ef84beb3291fcf4024795870cc1dccff8e7ccb2a9ac11a5b3994`. A different digest means the wrong file: stop and
re-check the attachment URL.

- [ ] **Step 4: Generate the transparent master and its derivatives**

Create `/tmp/build-npc-favicon.py` with exactly this, install Pillow once in a throwaway venv, and run it from the
module directory:

```python
from PIL import Image, ImageDraw

SRC = "/tmp/npc-favicon-source.png"
OUT = "src/main/resources/public"
IMAGES = f"{OUT}/assets/images"
SIDE = 500
CUT = 236  # 97 % of the disc's 243 px visual radius: inside its antialiased edge, so no white fringe survives
SS = 8  # supersampling factor for the alpha mask

src = Image.open(SRC).convert("RGB")
assert src.size == (SIDE, SIDE), src.size

mask = Image.new("L", (SIDE * SS, SIDE * SS), 0)
centre = SIDE * SS / 2
radius = CUT * SS
ImageDraw.Draw(mask).ellipse(
    (centre - radius, centre - radius, centre + radius, centre + radius),
    fill=255,
)
mask = mask.resize((SIDE, SIDE), Image.LANCZOS)

master = src.convert("RGBA")
master.putalpha(mask)
master.save(f"{IMAGES}/favicon.png")

for size in (16, 32):
    master.resize((size, size), Image.LANCZOS).save(f"{IMAGES}/favicon-{size}.png")

master.resize((48, 48), Image.LANCZOS).save(f"{OUT}/favicon.ico", sizes=[(16, 16), (32, 32), (48, 48)])
```

```bash
python3 -m venv /tmp/npc-favicon-venv
/tmp/npc-favicon-venv/bin/pip install --quiet pillow
/tmp/npc-favicon-venv/bin/python /tmp/build-npc-favicon.py
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PwaAssetsTest`
Expected: PASS.

If `faviconIcoCarriesThreeTransparentLayers` fails on the bit count, the ICO was flattened: regenerate it with
Pillow from the RGBA master (as above) rather than from an RGB image.

- [ ] **Step 6: Look at the ICO in a browser**

Open the module's `src/main/resources/public/favicon.ico` in a browser tab with a dark theme (or on the prototype's
dark strip, `src/main/html/favicon.html`): the disc must sit on the browser's own background, with no white box and no
light ring. A white box means the ICO writer dropped alpha — fix it in Step 4, not by loosening the test.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon.png \
        modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-16.png \
        modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-32.png \
        modules/ndopify-partner-central/src/main/resources/public/favicon.ico \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt
git commit -m "feat(npc): add the transparent tab favicon" -m "Refs #286"
```

---

### Task 2: Port the approved prototype's head lines

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/head.html:9-11`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/offline.html:10-11`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt:151-156`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt` (`offlinePageHasNoFaviconButKeepsTheAppleIcon`)

**Interfaces:**
- Consumes: Task 1's four files.
- Produces: every page rendered from `fragments/head.html` declares three `link[rel=icon]` — hrefs `/favicon.ico`, `/assets/images/favicon-32.png`, `/assets/images/favicon-16.png`, sizes `any`, `32x32`, `16x16` in that order — and keeps `link[rel=manifest]`, `link[rel=apple-touch-icon]` → `/assets/images/apple-touch-icon.png` and the `apple-mobile-web-app-*` metas. `public/offline.html` declares the same three with absolute paths and keeps its apple-touch link.

- [ ] **Step 1: Write the failing tests**

In `AgentProfileTemplateTest`'s `profile - the page's chrome`, replace the single `assertNull(page.selectFirst("link[rel=icon]"))` line with:

```kotlin
assertEquals(
    listOf("/favicon.ico", "/assets/images/favicon-32.png", "/assets/images/favicon-16.png"),
    page.select("link[rel=icon]").map { it.attr("href") },
)
assertEquals(listOf("any", "32x32", "16x16"), page.select("link[rel=icon]").map { it.attr("sizes") })
```

(Keep the `assertNull` import: other tests in the file still use it.)

In `PwaAssetsTest`, replace `offlinePageHasNoFaviconButKeepsTheAppleIcon` with:

```kotlin
@Test
fun offlinePageDeclaresTheFaviconAndKeepsTheAppleIcon() {
    val html = resource("public/offline.html")
    assertTrue(html.contains("<link rel=\"icon\" href=\"/favicon.ico\" sizes=\"any\">"))
    assertTrue(html.contains("rel=\"icon\" type=\"image/png\" sizes=\"32x32\" href=\"/assets/images/favicon-32.png\""))
    assertTrue(html.contains("rel=\"icon\" type=\"image/png\" sizes=\"16x16\" href=\"/assets/images/favicon-16.png\""))
    assertTrue(html.contains("rel=\"apple-touch-icon\" href=\"/assets/images/apple-touch-icon.png\""))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='AgentProfileTemplateTest,PwaAssetsTest'`
Expected: FAIL — the rendered head has no `link[rel=icon]` (two empty lists instead of three hrefs) and `/offline.html` has no favicon line.

- [ ] **Step 3: Add the three lines to `head.html`**

In `templates/fragments/head.html`, directly after the `theme-color` meta and before the `apple-touch-icon` link:

```html
    <link rel="icon" th:href="@{/favicon.ico}" sizes="any">
    <link rel="icon" type="image/png" sizes="32x32" th:href="@{/assets/images/favicon-32.png}">
    <link rel="icon" type="image/png" sizes="16x16" th:href="@{/assets/images/favicon-16.png}">
```

- [ ] **Step 4: Add the same three to `public/offline.html`**

After its `theme-color` meta and before its `apple-touch-icon` link, with absolute paths (no Thymeleaf on that page):

```html
    <link rel="icon" href="/favicon.ico" sizes="any">
    <link rel="icon" type="image/png" sizes="32x32" href="/assets/images/favicon-32.png">
    <link rel="icon" type="image/png" sizes="16x16" href="/assets/images/favicon-16.png">
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS — including `PwaAssetsTest.serviceWorkerVersionMatchesTheOfflinePage` (the worker is regenerated from the new `offline.html` by `build-assets.mjs`) and `SignInTargetTest.rejectsLoginAndPublicPaths` (its `/favicon.ico` entry is unchanged).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/head.html \
        modules/ndopify-partner-central/src/main/resources/public/offline.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt
git commit -m "feat(npc): declare the tab favicon in the page heads" -m "Refs #286"
```

---

### Task 3: Update the module documentation

**Files:**
- Modify: `modules/ndopify-partner-central/README.md` (the *Installable app (PWA)* bullet, the *Authentication* paragraph)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the **PWA** paragraph, the authentication list in *Pages*)

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: no stale "the tab has no favicon" / "`/favicon.ico` answers 404" claim in either file.

- [ ] **Step 1: Rewrite the PWA descriptions**

In `README.md`, the *Installable app (PWA)* bullet ends with the sentence saying the tab has **no favicon**, that
`favicon.ico` and `favicon-16/32.png` are gone, and that `/favicon.ico` stays public so its automatic 404 is not a
sign-in redirect. Replace that sentence with: the tab carries its own **transparent** favicon — the blue disc with the
black chevron, the white square of the supplied illustration cut away — delivered as `favicon.ico` (16/32/48) plus
`favicon-16.png` / `favicon-32.png` cut from the 500 px master `assets/images/favicon.png`; it is deliberately not the
house/N install identity of `icon*.png` / `apple-touch-icon.png`, which do not change; `/favicon.ico` stays public (and
excluded from the sign-in target) and now answers 200. Keep the sentence that HTML and API responses are never cached.
Keep the badge block verbatim (CI-managed, first lines) — a targeted edit, never a full rewrite.

In `CLAUDE.md`, the **PWA** paragraph: add the tab favicon — transparent, the disc/chevron mark, the four files, the
three heads' links, `/favicon.ico` public and answering 200 — and keep the install-icon description as it is. In the
same file's authentication list in *Pages* (the one naming `/assets/**`, `/sw.js`, `/offline.html`, `/manifest.json`),
the `/favicon.ico` entry keeps its place but its reason changes: it is public because the browser's automatic request
must answer the icon, signed out or not, and it stays out of `SignInTarget` so it can never become the sign-in target
(the "though no file is served" clause goes).

- [ ] **Step 2: Check for stale references**

```bash
cd modules/ndopify-partner-central
grep -rn 'rel="icon"' src/main
grep -rn "favicon" README.md CLAUDE.md
grep -rn "no favicon\|sans favicon\|answers 404" README.md CLAUDE.md
```

Expected: the first command lists the two production heads (`templates/fragments/head.html`, `public/offline.html`) and
nothing else — no served page left without the link; the second lists only the new sentences; the third prints nothing
(the prototype's historical *before #249* prose lives in `src/main/html/`, outside this grep, and is not to be
rewritten).

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/README.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the tab favicon" -m "Refs #286"
```

---

### Task 4: Full verification and the device check

**Files:**
- No source change. Fix anything the checks find in the task that owns it.

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: the evidence that the story's acceptance criteria hold.

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gate).

- [ ] **Step 2: Verify the signed-out answers**

With the app running (`mvn spring-boot:run`, port 8081) and no cookie:

```bash
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' http://localhost:8081/favicon.ico
curl -s -o /dev/null -w '%{http_code} %{content_type}\n' http://localhost:8081/assets/images/favicon-32.png
curl -s -D - -o /dev/null http://localhost:8081/login | grep -i 'rel=icon'
curl -s -D - -o /dev/null http://localhost:8081/offline.html | grep -i 'rel=icon'
```

Expected: `200 image/x-icon` for `/favicon.ico` and `200 image/png` for the 32 px file — never `302` to `/login`; the
login page's head lists the three icons; `/offline.html` lists the same three with absolute paths. Repeat the
`/favicon.ico` request with no cookie at all (`curl -s -o /dev/null -w '%{http_code}' -b ''`): still `200`.

- [ ] **Step 3: Check the tab**

In a browser signed in to a local tenant, open `/login` (signed out) and `/` (signed in): the tab shows the blue disc
with the black chevron, on a light theme and with the browser's dark theme — no white box behind it. Sign out, reload,
sign in again: the `next` target after sign-in is the page you asked for, never `/favicon.ico`.

- [ ] **Step 4: Report**

No commit if Steps 1–3 are green. If a check fails, fix it in its owning task's files, re-run `mvn verify`, and commit
with `fix(npc): …` and `Refs #286`.
