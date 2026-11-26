# Send a resized listing photo to the vision LLM — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `ListingVerifierImage` hands the vision model an ImageKit rendition (max 1600×1600, JPEG q85) of each listing image instead of the original upload, while the stored `width`/`height` stay the original image's.

**Architecture:** `ListingVerifierImage` injects the existing `ImageService`. Per image it downloads the original once (for the dimensions), then downloads the transformed URL when it differs and hands that file to the unchanged `ListingImageInfoExtractor`; a failed rendition falls back to the already-downloaded original, and every temp file is deleted in a `finally`. `ImageServiceLocal`, or a URL outside the ImageKit origin, returns the URL unchanged, so local dev downloads once, as today.

**Tech Stack:** Kotlin, Spring Boot, Spring AI (`ChatClients.VISION`), ImageKit URL transformation, JUnit 5 + mockito-kotlin + `kotlin.test`, the existing `com.sun.net.httpserver.HttpServer` harness of `ListingVerifierImageTest`.

**Spec:** issue #340 (the enriched story body). `UI: no` — no prototype.

## Global Constraints

- Transformation, verbatim: `Transformation(dimension = Dimension(1600, 1600), format = Format.JPG, crop = Crop.AT_MAX, quality = 85)`, i.e. `tr:w-1600,h-1600,f-jpg,c-at_max,q-85`.
- The constant is `ListingVerifierImage.VISION_TRANSFORMATION`, public in the companion object (like `ListingTelegramSender.PHOTO_TRANSFORMATION`).
- The saved `width`/`height` are always the original image's, never the rendition's.
- Fallbacks: transform returns the same URL → one download, original used; rendition download fails → the original is extracted; original download fails → today's behavior (the attempt throws and is retried).
- Every per-image temp file is deleted whatever the outcome; the extraction file stays readable until the extractor returns.
- `ListingImageInfoExtractor`, its prompt, the verdict rules and the DTOs don't change. No new dependency, no configuration key, no Flyway migration, no `ndopify-dto` / `ndopify-web-core` change.
- KYC images are out of scope.
- Assertions use `kotlin.test`; mocks use `mockito-kotlin`; ktlint must pass (`mvn validate`).
- `mvn verify` in `modules/ndopify-server` needs a local MySQL (see the root `CLAUDE.md`).

## Review Focus

- A WEBP or PNG upload whose rendition is served as `image/jpeg`: the extractor must receive a file typed `image/jpeg` (the extension comes from the response's `Content-Type`, not from the original URL) — Task 1.
- A local image service, or a URL outside the ImageKit origin: exactly one download, the original used, nothing else changes — Task 1.
- A rendition URL that 404s: the original is still extracted and the image still gets a verdict, never a rejection or `INTERNAL_ERROR` caused by the resize — Task 1.
- A large or portrait original: the stored dimensions are the original's (ex: 4000×3000), not the rendition's (1600×1200) — Task 1.
- An extractor (or save) failure: the temp files are deleted anyway, so a verification job run doesn't leak them — Task 2.

---

### Task 1: Download the rendition and keep the original dimensions

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImage.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImageTest.kt`

**Interfaces:**
- Produces: `ListingVerifierImage.VISION_TRANSFORMATION: Transformation` (public companion `val`).
- Produces: the constructor becomes `ListingVerifierImage(extractor, dao, tx, clock, imageService: ImageService, @param:Value("\${ndopify.listing.min-images}") minImages: Int)`.
- Consumes: `ImageService.transform(url: String, transformation: Transformation): String`, `UrlUtils.download(url: URL): File`.

- [ ] **Step 1: Write the failing tests**

In `ListingVerifierImageTest`, add the mock and fix the construction:

```kotlin
import com.wutsi.ndopify.platform.image.ImageService
import java.io.File

private val imageService = mock<ImageService>()
private val verifier = ListingVerifierImage(extractor, dao, tx, clock, imageService, 2)
```

In `setUp`, before starting the server, default the transform to a no-op (the `ImageServiceLocal` behavior) — the existing tests then keep their behavior:

```kotlin
doAnswer { it.getArgument<String>(0) }.whenever(imageService).transform(any(), any())
```

Add the server context `serve("/rendition.jpg", "image/jpeg", byteArrayOf(9, 9, 9))`, then these tests:

```kotlin
@Test
fun `the rendition is sent to the extractor`() {
    mockImages(pending = listOf(image(11)), verified = emptyList())
    doReturn(url("/rendition.jpg")).whenever(imageService).transform(any(), any())
    doReturn(ListingImageInfo(title = "Salon", valid = true, quality = 4))
        .whenever(extractor).extract(any(), any())

    verifier.verify(listing)

    val file = argumentCaptor<File>()
    verify(extractor).extract(file.capture(), eq(listing))
    assertEquals("jpg", file.firstValue.extension)
    assertEquals(3L, file.firstValue.length())
    verify(imageService).transform(image(11).url, ListingVerifierImage.VISION_TRANSFORMATION)

    val saved = argumentCaptor<ListingImageEntity>()
    verify(dao).save(saved.capture())
    assertEquals(640, saved.firstValue.width)
    assertEquals(480, saved.firstValue.height)
}

@Test
fun `the original is used when the image service leaves the URL unchanged`() {
    mockImages(pending = listOf(image(11)), verified = emptyList())
    doReturn(ListingImageInfo(valid = true, quality = 3)).whenever(extractor).extract(any(), any())

    verifier.verify(listing)

    val file = argumentCaptor<File>()
    verify(extractor).extract(file.capture(), eq(listing))
    assertEquals("png", file.firstValue.extension)
    verify(imageService).transform(image(11).url, ListingVerifierImage.VISION_TRANSFORMATION)
}

@Test
fun `a failed rendition falls back to the original`() {
    mockImages(pending = listOf(image(11)), verified = emptyList())
    doReturn(url("/missing.jpg")).whenever(imageService).transform(any(), any())
    doReturn(ListingImageInfo(valid = true, quality = 3)).whenever(extractor).extract(any(), any())

    val result = verifier.verify(listing)

    assertTrue(result.success)
    val file = argumentCaptor<File>()
    verify(extractor).extract(file.capture(), eq(listing))
    assertEquals("png", file.firstValue.extension)

    val saved = argumentCaptor<ListingImageEntity>()
    verify(dao).save(saved.capture())
    assertEquals(ImageStatus.VERIFIED, saved.firstValue.status)
    assertEquals(640, saved.firstValue.width)
    assertEquals(480, saved.firstValue.height)
}

@Test
fun `a webp upload gets a jpeg rendition`() {
    mockImages(pending = listOf(image(11, path = "/image.webp")), verified = emptyList())
    doReturn(url("/rendition.jpg")).whenever(imageService).transform(any(), any())
    doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any(), any())

    verifier.verify(listing)

    val file = argumentCaptor<File>()
    verify(extractor).extract(file.capture(), eq(listing))
    assertEquals("jpg", file.firstValue.extension)
}
```

`File` here is `java.io.File`; the other imports stay as they are.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingVerifierImageTest`
Expected: compilation FAIL (`ListingVerifierImage` has no `imageService` parameter, no `VISION_TRANSFORMATION`).

- [ ] **Step 3: Implement**

In `ListingVerifierImage`:

- Add `private val imageService: ImageService` before the `@param:Value` parameter, and the import
  `com.wutsi.ndopify.platform.image.{Format, Crop, ImageService, Transformation}` plus `java.awt.Dimension`.
- Add to the companion object:

```kotlin
/** The vision model's input: at most 1600×1600, never upscaled, JPEG at a quality invisible on a photo and readable on a plan. */
val VISION_TRANSFORMATION = Transformation(
    dimension = Dimension(1600, 1600),
    format = Format.JPG,
    crop = Crop.AT_MAX,
    quality = 85,
)
```

- In `verify(listing, image)`, replace the single download with: download the original, read its dimensions, then
  pick the extraction file:

```kotlin
val original = UrlUtils.download(URL(image.url))
val dimension = getDimensions(original)
val file = extractFile(original, image)
val info = extractor.extract(file, listing)
```

- Add the private helper. It leaves the URL unchanged for a no-op image service, and falls back to the original on
  a failed rendition; the warning names the listing and the image, never the URL:

```kotlin
private fun extractFile(original: File, image: ListingImageEntity): File {
    val url = imageService.transform(image.url, VISION_TRANSFORMATION)
    if (url == image.url) {
        return original
    }
    return try {
        UrlUtils.download(URL(url))
    } catch (ex: IOException) {
        LOGGER.warn(">>> Listing#${image.listingId} - Image ${image.id}: rendition download failed, using the original", ex)
        original
    }
}
```

Nothing else in the class changes: `getDimensions(original)` still feeds `width`/`height`, and the verdict and
save logic stay as they are. This task intentionally leaves the temp files behind; Task 2 deletes them.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingVerifierImageTest`
Expected: PASS (the existing tests and the four new ones).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImage.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImageTest.kt
git commit -m "feat(server): verify listings from a resized rendition of each image" -m "Refs #340"
```

### Task 2: Delete the verification temp files

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImage.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImageTest.kt`

**Interfaces:**
- Consumes: `ListingVerifierImage.extractFile(original, image)` from Task 1.

- [ ] **Step 1: Write the failing tests**

Add two tests. The first asserts the original and the rendition are both gone; the second asserts the cleanup also
happens when the extractor throws:

```kotlin
@Test
fun `the temporary files are deleted`() {
    mockImages(pending = listOf(image(11)), verified = emptyList())
    doReturn(url("/rendition.jpg")).whenever(imageService).transform(any(), any())
    doReturn(ListingImageInfo(valid = true)).whenever(extractor).extract(any(), any())
    val tempDir = File(System.getProperty("java.io.tmpdir"))
    val before = tempDir.listFiles()!!.toSet()

    verifier.verify(listing)

    val file = argumentCaptor<File>()
    verify(extractor).extract(file.capture(), eq(listing))
    assertFalse(file.firstValue.exists())
    val leaked = tempDir.listFiles()!!.toSet() - before
    assertTrue(leaked.none { it.extension == "png" || it.extension == "jpg" }, "leaked: $leaked")
}

@Test
fun `the temporary files are deleted when the extractor fails`() {
    mockImages(pending = listOf(image(11)), verified = emptyList())
    doThrow(IllegalStateException("boom")).whenever(extractor).extract(any(), any())

    assertFailsWith<IllegalStateException> { verifier.verify(listing) }

    val file = argumentCaptor<File>()
    verify(extractor).extract(file.capture(), eq(listing))
    assertFalse(file.firstValue.exists())
}
```

Add the `assertFalse` and `doThrow` imports.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingVerifierImageTest`
Expected: FAIL on both new tests (`file.firstValue.exists()` is true).

- [ ] **Step 3: Implement**

In `verify(listing, image)`, wrap the dimension read, the extraction-file selection, the extractor call and the
`tx.execute { dao.save(...) }` in a `try`/`finally`. The original is deleted, and the rendition only when Task 1
downloaded one; the file is not touched before the save completes:

```kotlin
val original = UrlUtils.download(URL(image.url))
var rendition: File? = null
try {
    val dimension = getDimensions(original)
    val file = extractFile(original, image).also { if (it !== original) rendition = it }
    val info = extractor.extract(file, listing)
    // ... the existing kind / verified / tx.execute block, unchanged ...
} finally {
    original.delete()
    rendition?.delete()
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingVerifierImageTest`
Expected: PASS (all tests, including the existing `download error`).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImage.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/verifier/ListingVerifierImageTest.kt
git commit -m "fix(server): delete the listing verification temp files" -m "Refs #340"
```

### Task 3: Document the verification input

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md` (the **listing** bullet, the *Send* paragraph that describes
  `ListingVerifierImage`).

**Interfaces:**
- Consumes: nothing; it documents Task 1 and Task 2.

- [ ] **Step 1: Add the paragraph**

After the sentence describing how each image is classified `PHOTO` / `FLOORPLAN` / `MAP`, state: each image is sent
to the vision model as an ImageKit rendition (max 1600×1600, JPEG q85, `VISION_TRANSFORMATION`) downloaded from the
transformed URL; `ImageServiceLocal` or a URL outside the origin sends the original, and so does a failed rendition;
`width`/`height` are still the original's, and the temp files are deleted.

- [ ] **Step 2: Verify the file**

Run: `git diff --check modules/ndopify-server/CLAUDE.md && grep -n "VISION_TRANSFORMATION" modules/ndopify-server/CLAUDE.md`
Expected: no whitespace error, one match.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document the resized listing verification input" -m "Refs #340"
```

### Task 4: Validate on real plans and maps, and measure — pre-merge gate

No production code changes: this task produces the evidence criteria 8 and 9 require and appends it to this plan.
It needs the human and real images (TEST listings, or a local listing with real files) and a working vision provider
key; the codes go in the plan file, committed with the implementation.

**Files:**
- Modify: `docs/superpowers/plans/340-server-send-a-resized-listing-photo-to-the-vision-llm.md`

- [ ] **Step 1: Choose the fixtures**

Pick at least 5 real floor plans and parcel maps whose verification passed before this change (their `kind` is
`FLOORPLAN` / `MAP` and their `valid` true). Record each image's URL and its original pixel size.

- [ ] **Step 2: Compare original and rendition**

For each fixture, obtain the original bytes and the `VISION_TRANSFORMATION` rendition (the ImageKit URL, or the
transformed URL by `ImageService`), and run them through `ListingImageInfoExtractor.extract` with the same
`ListingEntity`. Record, side by side: `valid`, `kind`, `quality`, `title`, `description`. A temporary logging
statement in the extractor (not committed) or a scratch test is fine.

Expected: the same verdict and kind, an equivalent quality (at most one point lower), and a description that keeps
the plan's rooms, areas or the parcel's boundaries. If a plan or a map loses legibility at 1600, reduce the dimension
in `VISION_TRANSFORMATION` (ex: 2048), re-run, and record the change and the reason.

- [ ] **Step 3: Measure one real call before and after**

On one real listing photo, record: the original file's bytes, the rendition file's bytes, the request payload bytes
(the image is base64-encoded into the vision request), and the provider's usage (`promptTokens` /
`completionTokens`) when it reports them (`ChatResponse.metadata.usage` — read it in a temporary log, not committed).
Record the two rows in the plan.

- [ ] **Step 4: Append the results to this plan**

Add a `## Validation` section with the 5-fixture table, the before/after measurement table, and any bound change. If
a bound changed, commit it with the code in Task 1's file.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/340-server-send-a-resized-listing-photo-to-the-vision-llm.md
git commit -m "docs(server): record the resize validation and measurement" -m "Refs #340"
```

### Task 5: Run the module's verification

**Files:**
- None.

- [ ] **Step 1: Run the full module build**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, the unit and endpoint tests, and the coverage gate.

- [ ] **Step 2: Check the unrelated tests**

`ListingImageInfoExtractorTest` is unchanged and green; `VerifyListingsJobTest` stays green (the verifier still
passes a valid listing and sends back an invalid one).

## Validation

_Criteria 8 and 9, run before opening the PR._

**Method.** No ndopify listing data was reachable from the implementation machine (no TEST server, no S3 bucket
credentials). The fixtures are real floor plans, cadastral/parcel maps and interior photos downloaded from Wikimedia
Commons, each sent through the extractor's prompt (`ListingImageInfoExtractor.PROMPT`, with the listing context shown
per fixture) to the vision client's model (`deepseek-flash`, OpenAI-compatible endpoint, image as a base64
`data:` URL), once with the original bytes and once with the rendition. Renditions were produced with macOS
`sips -Z 1600 -s format jpeg -s formatOptions 85` — ImageKit's `w-1600,h-1600,c-at_max,f-jpg,q-85` equivalent
(`-Z` resamples the longest side to 1600, never upscaling smaller images). "Payload" is the byte length of the
request body, the image included.

### Criterion 8 — plans and maps keep their verdict

| Fixture (original pixels) | Original | Rendition (1600) | Verdict |
|---|---|---|---|
| [3d-design-floor-plan-coloured.jpg](https://upload.wikimedia.org/wikipedia/commons/7/70/3d-design-floor-plan-coloured.jpg) (2000×1500) | valid, FLOORPLAN, 4 — séjour, chambres, salles d'eau | valid, FLOORPLAN, 4 — séjour circulaire, cuisine, chambres, salle de bain | same |
| [Floor_plan_of_Wanstead_House.png](https://upload.wikimedia.org/wikipedia/commons/a/a6/Floor_plan_of_Wanstead_House.png) (2321×1538) | invalid, FLOORPLAN, 5 — chapelle, cours, bibliothèque, dimensions en pieds | invalid, FLOORPLAN, 4 — mêmes pièces, colonnade, échelle en pieds | same |
| [Heathcote_Ground-floor_Plan…1913_Page239.jpg](https://upload.wikimedia.org/wikipedia/commons/3/30/Heathcote_Ground-floor_Plan_Lutyens_Houses_and_Gardens_1913_Page239.jpg) (2288×1582) | valid, FLOORPLAN, 4 — hall, salle à manger, salon, billard, cuisine, office, WC | 2 runs of 3 valid, FLOORPLAN, 4 — mêmes pièces | see below |
| [Parcelle_Cadastrale.png](https://upload.wikimedia.org/wikipedia/commons/7/7a/Parcelle_Cadastrale.png) (2360×2365) | invalid, MAP, 4 — parcelles numérotées, rue, aucune parcelle mise en évidence | invalid, MAP, 4 — mêmes parcelles, mêmes limites | same |
| [Mont-sur-Marchienne — cadastre primitif (extrait).jpg](https://upload.wikimedia.org/wikipedia/commons/b/bd/Mont-sur-Marchienne_-_cadastre_primitif_-_section_A_%28extrait%29.jpg) (1912×1859) | invalid, MAP, 3 — village, Champ de Foret, parcelles numérotées | invalid, MAP, 4 — mêmes parcelles, même lieu-dit | same |

The rendition keeps every detail the verdict and the description are built on: room names and dimensions ("hall,
salle à manger, salon, billard", "dimensions et échelle en pieds"), parcel outlines and numbers ("parcelles
numérotées", "Champ de Foret"), and the quality score never drops by more than one point (it rose on one map, where
the rendition's cleaner JPEG helped).

The one verdict that moved is the Heathcote plan: valid on the original, invalid on the first rendition run. It is
sampling variance, not legibility loss — the rejected run quotes the plan's own caption ("labelled 'Ground Floor
Plan of Heathcote'"), so the label was read from the rendition; repeated calls gave 3/3 valid on the original and 2/3
valid on the rendition, and a 2048-px rendition of the same plan scored 1/3 valid — raising the bound does not
remove the flip, the caption naming another property does. The bound stays **1600×1600**.

### Criterion 9 — one real call, before and after

| | Original | Rendition (1600×1200) |
|---|---|---|
| Fixture | [Chambre à Oinville-sous-Auneau.jpg](https://upload.wikimedia.org/wikipedia/commons/d/df/Chambre_%C3%A0_Oinville-sous-Auneau.jpg) (5472×3648) | same photo |
| Image bytes | 2,810,768 | 261,862 (**−90.7 %**) |
| Request payload | 3,753,261 | 354,721 (**−90.5 %**) |
| `promptTokens` | 2,261 | 2,261 (**−0 %**) |
| `completionTokens` | 338 | 286 |
| Verdict | valid, PHOTO, 4 — lit, tables de chevet, lampes rouges | valid, PHOTO, 4 — same objects |

**The token saving is zero**: the provider already caps the image tokens, so both requests billed 2,261 prompt
tokens. What the change buys is payload size (−90 %), upload latency, and format compatibility — exactly the risk the
story flagged ("the provider may already downscale below 1600"). The rendition also costs the server one extra small
download (261 KB against 2.8 MB).

**Bonus finding:** an 8256×4492 listing photo (9.7 MB) is **rejected today** — `The image is too large to process: its
width and height must each be at most 8192 pixels` (HTTP 400) — which fails the attempt and, after `MAX_RETRIES`,
sends the listing back with `INTERNAL_ERROR`. Its 283 KB rendition gets a valid `PHOTO` verdict. The change fixes
oversized uploads, not only their cost.
