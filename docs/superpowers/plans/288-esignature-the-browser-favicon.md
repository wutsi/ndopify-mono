# E-Signature Browser Icons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate the pen-and-signature favicon set from the story's attachment, and give every e-signature page its own tab and iOS home-screen icon instead of the shared Ndopify N mark (#288).

**Architecture:** A one-off, **uncommitted** Python/Pillow script derives five committed binaries from the 500×500 attachment: a transparent 500×500 master `favicon.png` (the attachment's white backdrop cut away, leaving the blue disc and the pen), a 32 and a 16 PNG, a three-frame `favicon.ico` (16/32/48), and a 180×180 full-bleed `#1d7edf` `apple-touch-icon.png` (the transparent master composited on a blue tile, so iOS's rounded mask leaves no white ring). The four served heads (`sign`, `verify`, `403`, `404`) and the two prototype heads link that set with `th:href` / relative paths; tests pin the files' sizes, transparency and full-bleed composition, and the rendered heads.

**Tech Stack:** Kotlin, Spring Boot 4.1.2 (Thymeleaf), Maven, JUnit 5 + `kotlin.test` + jsoup, `java.awt.image` / `javax.imageio` for the PNG assertions, a hand-parsed ICO directory, Python 3 + Pillow for the one-off asset generation (not a project dependency).

**Spec:** https://github.com/wutsi/ndopify-mono/issues/288 (enriched story) and its approved prototype, `modules/ndopify-esignature/src/main/html/favicon.html`.

## Global Constraints

- `modules/ndopify-esignature` only: no `ndopify-dto`, `ndopify-web-core`, `ndopify-server`, `ndopify-web` or `ndopify-partner-central` change; no database, Flyway, `application*.yml`, new dependency, `SecurityConfiguration` or `manifest.json` change.
- **The approved prototype is the baseline.** This plan ports it; it does not design it. The prototype sheet and pages are already committed with this plan — do not reopen the design, and do not edit `src/main/html/favicon.html`, `sign.html` or `verify.html` in this plan.
- The attachment is the source of truth: `https://github.com/user-attachments/assets/4a1c07e3-c1b1-4539-8762-fb2f3f86a613` — 500×500 PNG, opaque white square, `#1d7edf` disc (non-white bbox 6..493, ≈ 6 px white margin), white pen with black details and a black signature line. SHA-256: `9fed84d1c53f14c45137dd4ffcf881694fef974f28a1eba36146e16bec32f5aa`.
- Delivered files, exactly, under `src/main/resources/public/assets/images/`: `favicon.png` 500×500 (transparent master), `favicon-16.png` 16×16, `favicon-32.png` 32×32, `favicon.ico` (16/32/48, 32-bit) and `apple-touch-icon.png` 180×180. The file names match the sibling story #286.
- **The tab files are transparent around the disc.** The cut sits at 47 % of the image (radius 235 px at 500, inside the disc's ≈ 244 px antialiased edge): alpha 0 outside, opaque inside, a 1 px feather, and nothing added behind it (no white square, no tile, no halo). The pen's white stays opaque. No recolouring, no redraw.
- **The apple-touch icon is the opposite composition:** a full-bleed, opaque `#1d7edf` 180×180 tile; the transparent master scaled to 90 % (162×162) and pasted centred, so its white margin is already gone and the disc becomes a ~152 px mark on blue. Four corners and all four edges are `#1d7edf`, no transparent pixel; the pen stays inside the inner 80 %-diameter safe circle.
- The head links, exactly, on every page: `rel="icon"` → `/assets/images/favicon.ico` (`sizes="any"`), `/assets/images/favicon-32.png` (`32x32`), `/assets/images/favicon-16.png` (`16x16`); `rel="apple-touch-icon"` → `/assets/images/apple-touch-icon.png` (`180x180`). No page links `/assets/images/icon.png` any more; `icon.png` itself stays in place.
- No `/favicon.ico` at the root and no security change: the module serves only `/assets/**`, `/sign/**`, `/verify/**` and `/error`, and every page links its icons explicitly.
- Icons stay committed binaries. The generator lives in `/tmp`, is not committed, and is not wired into Maven.
- UI copy is French; code, comments, KDoc and commit messages are English. Commits are conventional, scoped `esignature`, with `Refs #288`.
- `mvn verify` in `modules/ndopify-esignature` must be green; its tests need neither MySQL nor a running ndopify-server.

## Review Focus

- **A white background smuggled back in.** A plain resize, a copy of the attachment, or an ICO writer that flattens alpha onto white would ship the white square again. Task 1's tests pin the alpha 0 corners of the master and both tab PNGs and the 32-bit ICO directory; the approved sheet is the visual check.
- **A white ring or white corners on the iOS home screen.** The source has a ≈ 6 px white margin inside its square; pasting the artwork on white would leave a ring inside iOS's rounded mask. Task 1's apple-touch test pins every edge and corner to `#1d7edf`, every pixel opaque (no transparent hole), and the pen's white/black content inside the 80 % safe circle.
- **A page left on the shared N mark.** Four templates each carry their own head. Task 2 asserts the three icon links and the apple-touch link in the sign, verify, 403 and 404 renders, and asserts no `/assets/images/icon.png` link remains.
- **A 16 px mark that cannot be read.** The pen survives the resample in the approved sheet's actual-size preview; the plan must not redraw or simplify it. If the committed 16 px file looks different from the sheet, the generator is wrong, not the design.
- **Browsers caching favicons aggressively**, often outside `Cache-Control`. An existing visitor may keep the old N mark until the browser's icon cache expires; expect it, and do not "fix" it by renaming the paths.
- **The prototype pages point at files that only exist after Task 1.** Task 1 copies the four delivered icon files into `src/main/html/assets/images/` so the approved prototype's tab and apple-touch links resolve; without it, the prototype's heads 404 locally.

---

### Task 1: Generate and pin the icon set

**Files:**
- Create: `modules/ndopify-esignature/src/main/resources/public/assets/images/favicon.png`
- Create: `modules/ndopify-esignature/src/main/resources/public/assets/images/favicon-16.png`
- Create: `modules/ndopify-esignature/src/main/resources/public/assets/images/favicon-32.png`
- Create: `modules/ndopify-esignature/src/main/resources/public/assets/images/favicon.ico`
- Create: `modules/ndopify-esignature/src/main/resources/public/assets/images/apple-touch-icon.png`
- Create: `modules/ndopify-esignature/src/main/html/assets/images/favicon-16.png`
- Create: `modules/ndopify-esignature/src/main/html/assets/images/favicon-32.png`
- Create: `modules/ndopify-esignature/src/main/html/assets/images/favicon.ico`
- Create: `modules/ndopify-esignature/src/main/html/assets/images/apple-touch-icon.png`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/FaviconAssetsTest.kt` (new)

**Interfaces:**
- Consumes: the story's attachment and its SHA-256 above.
- Produces: the five classpath resources above under `public/assets/images/`, in the composition the approved sheet shows; the same four served tab/apple files copied into the prototype's `assets/images/` so `src/main/html/{sign,verify}.html` resolve their links.

- [ ] **Step 1: Write the failing asset test**

Create `FaviconAssetsTest.kt`:

```kotlin
package com.wutsi.ndopify.esignature

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Pins the generated browser icons of #288: sizes, transparency and the full-bleed apple-touch tile. */
class FaviconAssetsTest {
    private companion object {
        const val BLUE = 0x1d7edf
        const val WHITE = 0xffffff
        const val DIR = "public/assets/images"
    }

    private data class Frame(val width: Int, val height: Int, val bitCount: Int, val size: Int, val offset: Int)

    @Test
    fun `files have their sizes`() {
        assertEquals(500 to 500, pngSize(resourceBytes("$DIR/favicon.png")))
        assertEquals(16 to 16, pngSize(resourceBytes("$DIR/favicon-16.png")))
        assertEquals(32 to 32, pngSize(resourceBytes("$DIR/favicon-32.png")))
        assertEquals(180 to 180, pngSize(resourceBytes("$DIR/apple-touch-icon.png")))
    }

    @Test
    fun `master is the supplied artwork without its backdrop`() {
        val master = image("$DIR/favicon.png")
        assertEquals(500 to 500, master.width to master.height)

        listOf(0 to 0, 499 to 0, 0 to 499, 499 to 499).forEach { (x, y) ->
            assertEquals(0, alpha(master, x, y), "white backdrop at ($x,$y)")
        }
        assertEquals(0, alpha(master, 250, 5)) // above the disc
        assertEquals(BLUE, rgb(master, 250, 20)) // the blue disc
        assertEquals(WHITE, rgb(master, 250, 250)) // the white pen, opaque
        assertEquals(255, alpha(master, 250, 250))
    }

    @Test
    fun `tab icons are transparent around the disc`() {
        listOf("$DIR/favicon.png", "$DIR/favicon-16.png", "$DIR/favicon-32.png").forEach { path ->
            val icon = image(path)
            val last = icon.width - 1
            listOf(0 to 0, last to 0, 0 to last, last to last).forEach { (x, y) ->
                assertEquals(0, alpha(icon, x, y), "$path is not transparent at ($x,$y)")
            }
            assertEquals(255, alpha(icon, icon.width / 2, icon.height / 2), "$path's pen is not opaque")
        }
    }

    @Test
    fun `favicon ico embeds the sixteen thirty two and forty eight frames`() {
        val bytes = resourceBytes("$DIR/favicon.ico")
        assertEquals(0, bytes[0].toInt()) // reserved
        assertEquals(0, bytes[1].toInt())
        assertEquals(1, bytes[2].toInt()) // type: icon
        assertEquals(0, bytes[3].toInt())
        val count = u16(bytes, 4)
        assertEquals(3, count)

        val frames = (0 until count).map { index ->
            val at = 6 + index * 16
            Frame(
                width = bytes[at].toInt() and 0xff,
                height = bytes[at + 1].toInt() and 0xff,
                bitCount = u16(bytes, at + 6),
                size = u32(bytes, at + 8),
                offset = u32(bytes, at + 12),
            )
        }
        assertEquals(listOf(16, 32, 48), frames.map { it.width })
        assertEquals(listOf(16, 32, 48), frames.map { it.height })
        frames.forEach {
            assertEquals(32, it.bitCount, "frame ${it.width} is not 32-bit")
            assertTrue(it.offset + it.size <= bytes.size, "frame ${it.width} lies outside the file")
        }
    }

    @Test
    fun `apple touch icon is a full bleed blue tile`() {
        val tile = image("$DIR/apple-touch-icon.png")
        assertEquals(180 to 180, tile.width to tile.height)

        for (i in 0 until 180) {
            listOf(0 to i, 179 to i, i to 0, i to 179).forEach { (x, y) ->
                assertEquals(BLUE, rgb(tile, x, y), "not full bleed at ($x,$y)")
                assertEquals(255, alpha(tile, x, y), "not opaque at ($x,$y)")
            }
        }

        val side = tile.width
        val radius = side * 0.4
        for (y in 0 until side) {
            for (x in 0 until side) {
                val argb = tile.getRGB(x, y)
                assertEquals(255, alpha(tile, x, y), "transparent pixel at ($x,$y)")
                if (isPen(argb)) { // white pen or black line, never the disc's near-blue antialiased edge
                    val r = hypot(x + 0.5 - side / 2.0, y + 0.5 - side / 2.0)
                    assertTrue(r <= radius + 2, "pen at ($x,$y), r=$r > $radius")
                }
            }
        }
    }

    private fun image(path: String): BufferedImage =
        ImageIO.read(javaClass.classLoader.getResourceAsStream(path) ?: fail("missing classpath resource $path"))

    private fun rgb(image: BufferedImage, x: Int, y: Int): Int = image.getRGB(x, y) and 0xFFFFFF

    private fun alpha(image: BufferedImage, x: Int, y: Int): Int = (image.getRGB(x, y) ushr 24) and 0xff

    /** The pen's white and the signature's black — the only clearly non-blue content on the tile. */
    private fun isPen(argb: Int): Boolean {
        val r = (argb shr 16) and 0xff
        val g = (argb shr 8) and 0xff
        val b = argb and 0xff
        return (r > 200 && g > 200 && b > 200) || (r < 60 && g < 60 && b < 60)
    }

    private fun resourceBytes(path: String): ByteArray =
        javaClass.classLoader.getResourceAsStream(path)?.use { it.readBytes() }
            ?: fail("missing classpath resource $path")

    /** Reads `width x height` from the PNG's IHDR chunk. */
    private fun pngSize(bytes: ByteArray): Pair<Int, Int> {
        assertEquals("PNG", String(bytes, 1, 3, Charsets.US_ASCII), "not a PNG")
        fun int(at: Int) =
            ((bytes[at].toInt() and 0xff) shl 24) or
                ((bytes[at + 1].toInt() and 0xff) shl 16) or
                ((bytes[at + 2].toInt() and 0xff) shl 8) or
                (bytes[at + 3].toInt() and 0xff)
        return int(16) to int(20)
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xff) or
            ((bytes[at + 1].toInt() and 0xff) shl 8) or
            ((bytes[at + 2].toInt() and 0xff) shl 16) or
            ((bytes[at + 3].toInt() and 0xff) shl 24)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-esignature && mvn test -Dtest=FaviconAssetsTest`
Expected: FAIL — `missing classpath resource public/assets/images/favicon.png` (none of the files exists yet).

- [ ] **Step 3: Download the attachment and verify it**

```bash
curl -sL -o /tmp/esignature-favicon.png \
  https://github.com/user-attachments/assets/4a1c07e3-c1b1-4539-8762-fb2f3f86a613
shasum -a 256 /tmp/esignature-favicon.png
```

Expected: `9fed84d1c53f14c45137dd4ffcf881694fef974f28a1eba36146e16bec32f5aa`. Stop if it differs — the artwork changed, do not ship a different mark without reopening the story.

Install Pillow for the one-off generator (not a project dependency): `python3 -m pip install --user pillow` (or the project's Python environment's `pip install pillow`).

- [ ] **Step 4: Generate the five icons and copy the four prototype files**

Run this from the repository root (the script is not committed):

```python
from PIL import Image
import math

SRC = "/tmp/esignature-favicon.png"
OUT = "modules/ndopify-esignature/src/main/resources/public/assets/images"
PROTO = "modules/ndopify-esignature/src/main/html/assets/images"
BLUE = (29, 126, 223)
SIDE = 500
CUT = round(SIDE * 0.47)  # 235 px, inside the disc's antialiased edge (~244)

src = Image.open(SRC).convert("RGBA")
assert src.size == (500, 500)

master = Image.new("RGBA", (SIDE, SIDE), (0, 0, 0, 0))
source = src.load()
output = master.load()
cx = cy = SIDE / 2
for y in range(SIDE):
    for x in range(SIDE):
        r, g, b, a = source[x, y]
        distance = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
        coverage = min(1.0, max(0.0, CUT + 0.5 - distance))  # 1 px feather, no white fringe
        if coverage > 0:
            output[x, y] = (r, g, b, round(a * coverage))

master.save(f"{OUT}/favicon.png")
master.resize((32, 32), Image.LANCZOS).save(f"{OUT}/favicon-32.png")
master.resize((16, 16), Image.LANCZOS).save(f"{OUT}/favicon-16.png")
master.save(f"{OUT}/favicon.ico", format="ICO", sizes=[(16, 16), (32, 32), (48, 48)])

art = master.resize((162, 162), Image.LANCZOS)  # 90 % of the 180 tile
tile = Image.new("RGB", (180, 180), BLUE)
tile.paste(art, (9, 9), art)
tile.save(f"{OUT}/apple-touch-icon.png")
```

Then copy the four files the prototype heads link:

```bash
cp modules/ndopify-esignature/src/main/resources/public/assets/images/{favicon.ico,favicon-16.png,favicon-32.png,apple-touch-icon.png} \
   modules/ndopify-esignature/src/main/html/assets/images/
```

- [ ] **Step 5: Run the asset test to verify it passes**

Run: `cd modules/ndopify-esignature && mvn test -Dtest=FaviconAssetsTest`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-esignature/src/main/resources/public/assets/images/favicon.png \
        modules/ndopify-esignature/src/main/resources/public/assets/images/favicon-16.png \
        modules/ndopify-esignature/src/main/resources/public/assets/images/favicon-32.png \
        modules/ndopify-esignature/src/main/resources/public/assets/images/favicon.ico \
        modules/ndopify-esignature/src/main/resources/public/assets/images/apple-touch-icon.png \
        modules/ndopify-esignature/src/main/html/assets/images/favicon-16.png \
        modules/ndopify-esignature/src/main/html/assets/images/favicon-32.png \
        modules/ndopify-esignature/src/main/html/assets/images/favicon.ico \
        modules/ndopify-esignature/src/main/html/assets/images/apple-touch-icon.png \
        modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/FaviconAssetsTest.kt
git commit -m "feat(esignature): add the portal's favicon set" -m "Refs #288"
```

---

### Task 2: Port the approved prototype into the four served heads

**Files:**
- Modify: `modules/ndopify-esignature/src/main/resources/templates/sign.html:11-12`
- Modify: `modules/ndopify-esignature/src/main/resources/templates/verify.html:11-12`
- Modify: `modules/ndopify-esignature/src/main/resources/templates/error/403.html:9-10`
- Modify: `modules/ndopify-esignature/src/main/resources/templates/error/404.html:9-10`
- Modify: `modules/ndopify-esignature/CLAUDE.md` (Pages section, one bullet)
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/sign/SignTemplateTest.kt`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/verify/VerifyTemplateTest.kt`
- Test: `modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/layout/ErrorTemplateTest.kt`

**Interfaces:**
- Consumes: the approved prototype heads (`src/main/html/{sign,verify}.html`) and Task 1's five assets.
- Produces: every rendered e-signature page declares the three `rel="icon"` links and the `180x180` apple-touch link pointing at `/assets/images/…`, and no `/assets/images/icon.png` link.

- [ ] **Step 1: Write the failing head tests**

In `SignTemplateTest`, after the `details`/`blocked` fixtures, add:

```kotlin
@Test
fun `head declares the portal icons`() {
    val page = render(details)

    assertEquals(
        listOf("/assets/images/favicon.ico", "/assets/images/favicon-32.png", "/assets/images/favicon-16.png"),
        page.select("link[rel=icon]").map { it.attr("href") },
    )
    assertEquals(listOf("any", "32x32", "16x16"), page.select("link[rel=icon]").map { it.attr("sizes") })
    val apple = page.selectFirst("link[rel=apple-touch-icon]")
    assertEquals("/assets/images/apple-touch-icon.png", apple?.attr("href"))
    assertEquals("180x180", apple?.attr("sizes"))
    assertNull(page.selectFirst("link[href=/assets/images/icon.png]"))
}
```

In `VerifyTemplateTest`, after the `valid` fixture, add:

```kotlin
@Test
fun `head declares the portal icons`() {
    val page = render(valid)

    assertEquals(
        listOf("/assets/images/favicon.ico", "/assets/images/favicon-32.png", "/assets/images/favicon-16.png"),
        page.select("link[rel=icon]").map { it.attr("href") },
    )
    assertEquals(listOf("any", "32x32", "16x16"), page.select("link[rel=icon]").map { it.attr("sizes") })
    val apple = page.selectFirst("link[rel=apple-touch-icon]")
    assertEquals("/assets/images/apple-touch-icon.png", apple?.attr("href"))
    assertEquals("180x180", apple?.attr("sizes"))
    assertNull(page.selectFirst("link[href=/assets/images/icon.png]"))
}
```

In `ErrorTemplateTest`, add:

```kotlin
@Test
fun `head declares the portal icons`() {
    listOf(403, 404).forEach { status ->
        val page = render(status)

        assertEquals(
            listOf("/assets/images/favicon.ico", "/assets/images/favicon-32.png", "/assets/images/favicon-16.png"),
            page.select("link[rel=icon]").map { it.attr("href") },
            "$status",
        )
        assertEquals(listOf("any", "32x32", "16x16"), page.select("link[rel=icon]").map { it.attr("sizes") }, "$status")
        val apple = page.selectFirst("link[rel=apple-touch-icon]")
        assertEquals("/assets/images/apple-touch-icon.png", apple?.attr("href"), "$status")
        assertEquals("180x180", apple?.attr("sizes"), "$status")
        assertNull(page.selectFirst("link[href=/assets/images/icon.png]"), "$status")
    }
}
```

`SignTemplateTest` and `VerifyTemplateTest` already import `assertNull`; `ErrorTemplateTest` does too. Add `assertTrue` only if the file lacks it — none of the new tests needs it.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-esignature && mvn test -Dtest='SignTemplateTest,VerifyTemplateTest,ErrorTemplateTest'`
Expected: FAIL — each rendered head still has a single `link[rel=icon]` → `/assets/images/icon.png` and an apple-touch → `/assets/images/icon.png`.

- [ ] **Step 3: Replace the icon lines in the four templates**

In each of `templates/sign.html`, `templates/verify.html`, `templates/error/403.html` and `templates/error/404.html`, replace these two lines:

```html
<link rel="icon" type="image/png" th:href="@{/assets/images/icon.png}">
<link rel="apple-touch-icon" th:href="@{/assets/images/icon.png}">
```

with the four lines the approved prototype declares (Thymeleaf form):

```html
<link rel="icon" th:href="@{/assets/images/favicon.ico}" sizes="any">
<link rel="icon" type="image/png" sizes="32x32" th:href="@{/assets/images/favicon-32.png}">
<link rel="icon" type="image/png" sizes="16x16" th:href="@{/assets/images/favicon-16.png}">
<link rel="apple-touch-icon" sizes="180x180" th:href="@{/assets/images/apple-touch-icon.png}">
```

Nothing else in the heads changes: the `theme-color`, `noindex` and `no-referrer` metas stay, as does the rest of each page.

- [ ] **Step 4: Record the head rule in the module instructions**

In `modules/ndopify-esignature/CLAUDE.md`, under **Pages**, add after the first bullet (tenant resolution):

```markdown
- Every served page's head declares the portal's own icon set — `favicon.ico` / `favicon-32.png` / `favicon-16.png`
  (transparent around the `#1d7edf` disc) and a 180 `apple-touch-icon.png` (full-bleed `#1d7edf`), all under
  `/assets/images/`; `favicon.png` is the 500 × 500 master. The shared `icon.png` stays in the folder but is no page's
  icon, and no `/favicon.ico` is served at the root (the module only serves `/assets/**`, `/sign/**`, `/verify/**`
  and `/error`).
```

- [ ] **Step 5: Run the module's full verification**

Run: `cd modules/ndopify-esignature && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests (including the three template tests and `FaviconAssetsTest`) and the coverage gate pass, without MySQL and without a running ndopify-server.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-esignature/src/main/resources/templates/sign.html \
        modules/ndopify-esignature/src/main/resources/templates/verify.html \
        modules/ndopify-esignature/src/main/resources/templates/error/403.html \
        modules/ndopify-esignature/src/main/resources/templates/error/404.html \
        modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/sign/SignTemplateTest.kt \
        modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/verify/VerifyTemplateTest.kt \
        modules/ndopify-esignature/src/test/kotlin/com/wutsi/ndopify/esignature/ui/layout/ErrorTemplateTest.kt \
        modules/ndopify-esignature/CLAUDE.md
git commit -m "feat(esignature): give every page its own favicon and apple-touch icon" -m "Refs #288"
```

---

## Self-Review

- **Spec coverage:** the attachment/SHA (Task 1 Step 3), the five file names and sizes (Task 1 Steps 1/4), the transparent tab set (Task 1 test + generator), the full-bleed apple-touch safe circle (Task 1 test), the four heads on all four served pages (Task 2 Steps 1/3), the prototype mirror (already committed; Task 1 Step 4 makes the prototype links resolve), `icon.png` kept and unlinked (Task 2 test), no root `/favicon.ico` or security change (Global Constraints), the module instructions (Task 2 Step 4), green `mvn verify` (Task 2 Step 5).
- **Step scan:** every step has one checkable action; the generator script is the exact asset recipe (values and cut), not product code.
- **Type consistency:** the test's `Frame`/`u16`/`u32` helpers are local to `FaviconAssetsTest`; the file names in Task 2's head tests match Task 1's outputs and the prototype's links.
- **Review Focus:** each line names its pinning test in Task 1 or Task 2.
