# Partner Central PWA Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Partner Central's shared Ndopify artwork with the dedicated house/N icon set, rebuild the five PWA/apple PNGs from it, and remove the browser-tab favicon from the BFF and the prototype.

**Architecture:** The 500×500 attachment becomes the master `public/assets/images/icon.png`; a one-off, **uncommitted** Python/Pillow script derives the two `any` icons (the artwork resized) and the maskable pair plus the 180 `apple-touch-icon` (a full-bleed `#1d7edf` tile with the artwork at 90%, its white margin clipped away). `templates/fragments/head.html` and `public/offline.html` lose their `<link rel="icon">` lines while keeping the manifest and apple-touch links; the three favicon files are deleted, and `/favicon.ico` stays a public, never-remembered path that now answers 404. The manifest's icon list, the service worker and the cache headers do not change.

**Tech Stack:** Kotlin, Spring Boot 4.1.2 (Thymeleaf, Security), Maven, JUnit 5 + `kotlin.test` + jsoup, `java.awt.ImageIO` for the PNG assertions, Python 3 + Pillow for the one-off asset generation (not a project dependency).

**Spec:** https://github.com/wutsi/ndopify-mono/issues/249 (enriched story) and its approved prototype, `modules/ndopify-partner-central/src/main/html/pwa-icons.html`.

## Global Constraints

- `modules/ndopify-partner-central` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server`, `ndopify-web` or `ndopify-esignature` change; no database, Flyway, `application*.yml` or new project dependency.
- **The approved prototype is the baseline.** This plan ports it; it does not design anything. The head loses the favicon link, and the icons are exactly the sheet's composition. Do not reopen the design; do not edit `src/main/html/` (it is the approved prototype, already committed with this plan).
- The master is the story's attachment, byte for byte:
  `https://github.com/user-attachments/assets/b7f9d547-a614-46de-b318-dca4aef459ab` — 500×500 PNG, opaque white square, `#1d7edf` circle with a ~7 px white margin, black house/N and its white window. SHA-256 of that file:
  `0a1638ee9bd9092f89305a93de5c6d923727517c57446d541eb2b2f20fe15855`.
- Icon values, exactly: brand blue `#1d7edf` (`BLUE = (29, 126, 223)`); artwork scale `MARK_SCALE = 0.9` of the tile; the circle that removes the white margin is drawn at a **94 %** diameter of the scaled artwork (`inset = round(side * 0.03)`), because the artwork's blue circle leaves a white ring inside its square.
- Delivered sizes: `icon-192.png` 192×192, `icon-512.png` 512×512 (`any`, the artwork as supplied), `icon-maskable-192.png` 192×192, `icon-maskable-512.png` 512×512 (`maskable`, full-bleed blue), `apple-touch-icon.png` 180×180 (full-bleed blue). No 48/32/16 files: the sheet's are legibility previews.
- The manifest keeps its four icons, the white theme color and its other fields; the service worker's precache (`/offline.html`, `logo.png`) and every `Cache-Control` rule are untouched.
- Deleted: `public/favicon.ico`, `public/assets/images/favicon-16.png`, `public/assets/images/favicon-32.png`. **Kept** in `config/SecurityConfiguration.kt` and `ui/login/SignInTarget.EXACT_EXCLUDED`: `/favicon.ico` — a browser's automatic request must answer 404 (through `/error`), never a 302 to `/login`, and must never become the post-sign-in target.
- `apple-touch-icon` stays: it is the iOS install icon, not a favicon. In `head.html` it already points at `/assets/images/apple-touch-icon.png`; `public/offline.html`'s points at the master `icon.png` and must be repointed at the rebuilt `apple-touch-icon.png`.
- Icons stay committed binaries. The generator lives in `/tmp`, is not committed, and is not wired into Maven.
- UI copy, if touched, is French; code, comments, KDoc and commit messages are English.
- `mvn test` (and the final `mvn verify`) in `modules/ndopify-partner-central` must be green; its tests need neither MySQL nor a running ndopify-server.
- Commits are conventional, scoped `npc`, with `Refs #249` in the body.

## Review Focus

- **A master that is not the attachment.** Replacing `icon.png` with a re-export or a resized copy would silently ship different artwork. Task 2 downloads the attachment and checks its SHA-256; `PwaAssetsTest.masterIconIsTheSuppliedArtwork` pins the size, the opaque white corner, the blue circle, the black centre and the white window.
- **A white ring or white corners in the maskable/apple tile.** The artwork's circle leaves a ~7 px white margin inside its square, so clipping at the artwork's own edge keeps a light ring on the blue tile. Task 2 clips at 94 %; `rebuiltIconsHaveTheirSizeAndComposition` pins the four corners of each tile to `#1d7edf`, and `maskableContentStaysInsideTheSafeCircle` fails if any non-blue pixel sits outside the 80 % safe circle.
- **A mark cropped by a launcher mask.** Content must stay inside the inner 80 %-diameter circle (Android's maskable guarantee); 90 % of the artwork puts the roof tips at its edge. Task 2's safe-circle test allows the 2 px antialiasing fringe and nothing more.
- **A favicon left behind.** One missed page, one missed file, or a browser falling back to `/favicon.ico` and the confusion returns. Task 1's template and offline-page tests, Task 3's absence test and Task 4's grep over `src/main` (prototype included) cover the markup, the files and the docs.
- **`/favicon.ico` becoming a sign-in redirect.** With the file gone, the automatic request must still be permitted and excluded from the sign-in target. No code change is needed — but removing either entry would turn it into a 302 to `/login`. Task 3 keeps both, `SignInTargetTest.rejectsLoginAndPublicPaths` already lists it, and Task 5's `curl` proves the 404.

---

### Task 1: Port the approved prototype — drop the favicon link from the BFF heads

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/fragments/head.html:10-12`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/offline.html:10-11`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt` (`profile - the page's chrome`)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt` (new test)

**Interfaces:**
- Consumes: nothing.
- Produces: every rendered page's head has no `link[rel=icon]`; it keeps `link[rel=manifest]`, `link[rel=apple-touch-icon]` → `/assets/images/apple-touch-icon.png` and the `apple-mobile-web-app-*` metas. `/offline.html` has no `link[rel=icon]` and its apple-touch icon points at `/assets/images/apple-touch-icon.png`.

- [ ] **Step 1: Write the failing tests**

In `AgentProfileTemplateTest`, replace the favicon assertion (around line 152) with, and add the `assertNull` import:

```kotlin
assertNull(page.selectFirst("link[rel=icon]"))
assertEquals(
    "/assets/images/apple-touch-icon.png",
    page.selectFirst("link[rel=apple-touch-icon]")?.attr("href"),
)
```

In `PwaAssetsTest`, add:

```kotlin
@Test
fun offlinePageHasNoFaviconButKeepsTheAppleIcon() {
    val html = resource("public/offline.html")
    assertFalse(html.contains("rel=\"icon\""), "no favicon link")
    assertTrue(html.contains("rel=\"apple-touch-icon\" href=\"/assets/images/apple-touch-icon.png\""))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='AgentProfileTemplateTest,PwaAssetsTest'`
Expected: FAIL — the rendered head still has `link[rel=icon]` (href `/favicon.ico`), and `/offline.html` still has both links, the apple-touch one on `icon.png`.

- [ ] **Step 3: Remove the favicon links**

In `templates/fragments/head.html`, delete these three lines (the rest of the head is unchanged):

```html
<link rel="icon" th:href="@{/favicon.ico}" sizes="any">
<link rel="icon" type="image/png" sizes="32x32" th:href="@{/assets/images/favicon-32.png}">
<link rel="icon" type="image/png" sizes="16x16" th:href="@{/assets/images/favicon-16.png}">
```

In `public/offline.html`, delete the `rel="icon"` line and repoint the apple-touch icon:

```html
<link rel="apple-touch-icon" href="/assets/images/apple-touch-icon.png">
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='AgentProfileTemplateTest,PwaAssetsTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/fragments/head.html \
        modules/ndopify-partner-central/src/main/resources/public/offline.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt
git commit -m "feat(npc): drop the favicon from the page heads" -m "Refs #249"
```

---

### Task 2: Rebuild the icon set from the supplied master

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/images/icon.png` (replace)
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/images/icon-192.png`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/images/icon-512.png`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/images/icon-maskable-192.png`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/images/icon-maskable-512.png`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/images/apple-touch-icon.png`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt`

**Interfaces:**
- Consumes: the story's attachment.
- Produces: classpath resources `public/assets/images/icon.png` (500×500 master) and the five derivatives at 192/192/512/512/180, in the composition the approved prototype shows.
- The one-off generator used in Step 4 (not committed):

```python
from PIL import Image, ImageDraw

SRC = "src/main/resources/public/assets/images/icon.png"
OUT = "src/main/resources/public/assets/images"
BLUE = (29, 126, 223)
MARK_SCALE = 0.9

src = Image.open(SRC).convert("RGB")
assert src.size == (500, 500)

def art(size):
    return src.resize((size, size), Image.LANCZOS)

# "any": the artwork as supplied (opaque white corners, blue circle)
art(192).save(f"{OUT}/icon-192.png")
art(512).save(f"{OUT}/icon-512.png")

def full_bleed(size):
    side = round(size * MARK_SCALE)
    scaled = art(side)
    # The artwork's blue circle sits ~7 px inside its square: cut at 94 % of the diameter
    # so no white ring survives on the blue tile; outside the cut is the tile's blue.
    mask = Image.new("L", (side, side), 0)
    inset = round(side * 0.03)
    ImageDraw.Draw(mask).ellipse((inset, inset, side - 1 - inset, side - 1 - inset), fill=255)
    tile = Image.new("RGB", (side, side), BLUE)
    tile.paste(scaled, (0, 0), mask)
    canvas = Image.new("RGB", (size, size), BLUE)
    canvas.paste(tile, ((size - side) // 2, (size - side) // 2))
    return canvas

full_bleed(192).save(f"{OUT}/icon-maskable-192.png")
full_bleed(512).save(f"{OUT}/icon-maskable-512.png")
full_bleed(180).save(f"{OUT}/apple-touch-icon.png")
```

- [ ] **Step 1: Write the failing tests**

In `PwaAssetsTest`, add the imports `java.awt.image.BufferedImage`, `javax.imageio.ImageIO`, `kotlin.math.hypot`, a companion with the two colours, a `BufferedImage` reader and the three tests:

```kotlin
private companion object {
    const val BLUE = 0x1d7edf
    const val WHITE = 0xffffff
}

private fun image(path: String): BufferedImage =
    ImageIO.read(javaClass.classLoader.getResourceAsStream(path) ?: fail("missing classpath resource $path"))

private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

private fun differsFromBlue(argb: Int): Boolean =
    listOf(16, 8, 0).any { abs(((argb shr it) and 0xFF) - ((BLUE shr it) and 0xFF)) > 8 }

@Test
fun masterIconIsTheSuppliedArtwork() {
    val master = image("public/assets/images/icon.png")
    assertEquals(500 to 500, master.width to master.height)
    assertEquals(WHITE, rgb(master, 0, 0))          // opaque white square (not the old transparent corner)
    assertEquals(BLUE, rgb(master, 250, 30))        // the blue circle
    assertEquals(0x000000, rgb(master, 250, 250))   // the black house/N
    assertEquals(WHITE, rgb(master, 231, 130))      // the white window
}

@Test
fun rebuiltIconsHaveTheirSizeAndComposition() {
    assertEquals(192 to 192, pngSize(resourceBytes("public/assets/images/icon-192.png")))
    assertEquals(512 to 512, pngSize(resourceBytes("public/assets/images/icon-512.png")))
    assertEquals(192 to 192, pngSize(resourceBytes("public/assets/images/icon-maskable-192.png")))
    assertEquals(512 to 512, pngSize(resourceBytes("public/assets/images/icon-maskable-512.png")))
    assertEquals(180 to 180, pngSize(resourceBytes("public/assets/images/apple-touch-icon.png")))

    val any = image("public/assets/images/icon-512.png")
    assertEquals(WHITE, rgb(any, 0, 0))            // "any" keeps the white corner
    assertEquals(BLUE, rgb(any, 256, 20))
    assertEquals(0x000000, rgb(any, 256, 256))

    listOf(
        "public/assets/images/icon-maskable-192.png",
        "public/assets/images/icon-maskable-512.png",
        "public/assets/images/apple-touch-icon.png",
    ).forEach { path ->
        val tile = image(path)
        val last = tile.width - 1
        listOf(0 to 0, last to 0, 0 to last, last to last).forEach { (x, y) ->
            assertEquals(BLUE, rgb(tile, x, y), "$path is not full bleed at ($x,$y)")
        }
    }
}

@Test
fun maskableContentStaysInsideTheSafeCircle() {
    listOf(
        "public/assets/images/icon-maskable-192.png",
        "public/assets/images/icon-maskable-512.png",
        "public/assets/images/apple-touch-icon.png",
    ).forEach { path ->
        val tile = image(path)
        val side = tile.width
        val radius = side * 0.4
        for (y in 0 until side) {
            for (x in 0 until side) {
                if (differsFromBlue(tile.getRGB(x, y))) {
                    val r = hypot(x + 0.5 - side / 2.0, y + 0.5 - side / 2.0)
                    assertTrue(r <= radius + 2, "$path has content at ($x,$y), r=$r > $radius")
                }
            }
        }
    }
}
```

Add `import kotlin.math.abs` too. Keep the existing `pngSize` helper.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PwaAssetsTest`
Expected: FAIL on `masterIconIsTheSuppliedArtwork` — the old `icon.png` has a transparent corner (`getRGB(0,0)` is `0x00000000`, so `rgb(...)` is `0x000000`, not `white`), and its pixel at (231,130) is not the window.

- [ ] **Step 3: Download the master and verify it**

From the module directory:

```bash
curl -sL -o src/main/resources/public/assets/images/icon.png \
  "https://github.com/user-attachments/assets/b7f9d547-a614-46de-b318-dca4aef459ab"
file src/main/resources/public/assets/images/icon.png
shasum -a 256 src/main/resources/public/assets/images/icon.png
```

Expected: `PNG image data, 500 x 500, 8-bit/color RGBA, non-interlaced` and
`0a1638ee9bd9092f89305a93de5c6d923727517c57446d541eb2b2f20fe15855`. A different digest means the wrong
file: stop and re-check the attachment URL.

- [ ] **Step 4: Generate the five derivatives**

Create `/tmp/build-pwa-icons.py` with the script from this task's *Interfaces* block, install Pillow once in a
throwaway venv, and run it from the module directory:

```bash
python3 -m venv /tmp/pwa-icons
/tmp/pwa-icons/bin/pip install --quiet pillow
/tmp/pwa-icons/bin/python /tmp/build-pwa-icons.py
```

Then inspect the five files (open `icon-maskable-512.png` and `apple-touch-icon.png`): a full-bleed blue tile, the
black house/N with its white window, no white ring at the circle's edge; `icon-512.png` keeps the white corners.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PwaAssetsTest`
Expected: PASS (all tests, including `declaredIconsExistWithTheirDeclaredDimensions` and the service-worker hash).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/public/assets/images
git commit -m "feat(npc): rebuild the pwa icons from the npc artwork" -m "Refs #249"
```

---

### Task 3: Remove the favicon files, keep `/favicon.ico` harmless

**Files:**
- Delete: `modules/ndopify-partner-central/src/main/resources/public/favicon.ico`
- Delete: `modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-16.png`
- Delete: `modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-32.png`
- Keep unchanged: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt:25` and
  `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/SignInTarget.kt:25` (`/favicon.ico` entries)
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt`

**Interfaces:**
- Consumes: Task 1 (no page references a favicon any more).
- Produces: no `public/favicon.ico`, `public/assets/images/favicon-16.png` or `public/assets/images/favicon-32.png`
  on the classpath. `/favicon.ico` is still permitted and still excluded from the sign-in target, so it answers 404
  instead of redirecting to `/login`.

- [ ] **Step 1: Write the failing test**

In `PwaAssetsTest`, add the `kotlin.test.assertNull` import and:

```kotlin
@Test
fun faviconFilesAreGone() {
    assertNull(javaClass.classLoader.getResource("public/favicon.ico"))
    assertNull(javaClass.classLoader.getResource("public/assets/images/favicon-16.png"))
    assertNull(javaClass.classLoader.getResource("public/assets/images/favicon-32.png"))
}
```

Delete the old `assertTrue(resourceBytes("public/favicon.ico").size > 0)` line from
`declaredIconsExistWithTheirDeclaredDimensions`.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=PwaAssetsTest`
Expected: FAIL — the three files are still on the classpath.

- [ ] **Step 3: Delete the files**

```bash
git rm modules/ndopify-partner-central/src/main/resources/public/favicon.ico \
       modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-16.png \
       modules/ndopify-partner-central/src/main/resources/public/assets/images/favicon-32.png
```

- [ ] **Step 4: Verify the two `/favicon.ico` entries are still there**

```bash
grep -n '"/favicon.ico"' modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/config/SecurityConfiguration.kt \
  modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/login/SignInTarget.kt
```

Expected: one match in each file (inside the `permitAll()` matcher and `EXACT_EXCLUDED`). Do not remove them: a
browser's automatic request would become a 302 to `/login` and could be remembered as a sign-in target.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test`
Expected: PASS, `SignInTargetTest.rejectsLoginAndPublicPaths` included.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/PwaAssetsTest.kt
git commit -m "feat(npc): remove the favicon files" -m "Refs #249"
```

---

### Task 4: Update the module documentation

**Files:**
- Modify: `modules/ndopify-partner-central/README.md` (the *Installable app (PWA)* bullet, the *Authentication* paragraph)
- Modify: `modules/ndopify-partner-central/CLAUDE.md` (the *PWA* paragraph, the authentication list in *Pages*)

**Interfaces:**
- Consumes: Tasks 1–3.
- Produces: no stale favicon or icon description in either file.

- [ ] **Step 1: Rewrite the PWA descriptions**

In `README.md`, the PWA bullet currently ends with the sentence that lists `apple-touch-icon.png` (180 px),
`favicon-16/32.png` and `/favicon.ico`. Replace that sentence with: the manifest ships `any` and `maskable` icons at
192 and 512 px (`assets/images/icon-*.png`) cut from `assets/images/icon.png`, Partner Central's own house/N mark
(white corners for `any`, a full-bleed `#1d7edf` tile with the mark inside the maskable safe circle);
`assets/images/apple-touch-icon.png` (180 px) is the iOS install icon; the tab has **no favicon** — the head declares
no `<link rel="icon">` and `favicon.ico` / `favicon-16/32.png` are gone, so `/favicon.ico` answers 404 (it stays
public so that answer is not a sign-in redirect). Keep the sentence that HTML and API responses are never cached.

In `CLAUDE.md`, the **PWA** paragraph: add the master/derived icon set and the removal. It should read that
`/assets/images/icon.png` is the NPC master (the house/N artwork); `icon-192.png`/`icon-512.png` are it resized;
`icon-maskable-192.png`/`icon-maskable-512.png` and `apple-touch-icon.png` are the full-bleed `#1d7edf` tile with the
mark at 90 % of the side (inside the maskable 80 % safe circle); the heads declare no favicon, the favicon files are
deleted, and `/favicon.ico` stays a public path that now 404s. In the same file's authentication list (the one naming
`/assets/**`, `/sw.js`, `/offline.html`, `/manifest.json`), add `/favicon.ico` with that one-line reason.

- [ ] **Step 2: Check for stale references**

```bash
grep -rn 'rel="icon"' modules/ndopify-partner-central/src/main
grep -rn "favicon" modules/ndopify-partner-central/README.md modules/ndopify-partner-central/CLAUDE.md
```

Expected: the first command prints nothing (templates, `public/offline.html` and the prototype); the second only the
"no favicon" / "404" sentences — no claim that `favicon.ico` or `favicon-16/32.png` is served.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-partner-central/README.md modules/ndopify-partner-central/CLAUDE.md
git commit -m "docs(npc): document the npc pwa icons and the removed favicon" -m "Refs #249"
```

---

### Task 5: Full verification and the device check

**Files:**
- No source change. Fix anything the checks find in the task that owns it.

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces: the evidence that the story's acceptance criteria hold.

- [ ] **Step 1: Run the module's full verification**

```bash
cd modules/ndopify-partner-central && mvn verify
```

Expected: BUILD SUCCESS (ktlint, tests, JaCoCo gate). The template tests read the rebuilt PNGs from the classpath.

- [ ] **Step 2: Verify the signed-out answers**

With the app running against a local ndopify-server (`mvn spring-boot:run`, port 8081), and with no cookie:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/favicon.ico
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/assets/images/favicon-32.png
curl -s -D - -o /dev/null http://localhost:8081/login | grep -i -e 'rel=icon' -e 'apple-touch-icon'
curl -s -D - -o /dev/null http://localhost:8081/assets/images/apple-touch-icon.png | grep -i 'content-type'
```

Expected: `404` and `404` (not `302` to `/login`); the login page's head has no `rel=icon` and its
`apple-touch-icon` is `/assets/images/apple-touch-icon.png`; the apple-touch file is `image/png`.

- [ ] **Step 3: Check the installed apps**

After the deploy, on an Android phone (browser menu → *Install*) and an iPhone (*Share → Sur l'écran d'accueil*):
the launcher/home-screen icon is the new house/N mark (the blue maskable tile on Android, the apple-touch tile on
iOS), and the browser tab shows no favicon. An icon already installed keeps its old artwork until the OS refreshes it
or the app is reinstalled — that is expected (the icon URLs are not versioned).

- [ ] **Step 4: Report**

No commit if Steps 1–2 are green. If a check fails, fix it in its owning task's files, re-run `mvn verify`, and
commit with `fix(npc): …` and `Refs #249`.
