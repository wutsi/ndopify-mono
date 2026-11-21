# Adaptive Listing Image Renditions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Serve listing photos as exact 3:2 ImageKit variants (240–2048 px, auto format) chosen by the browser with `srcset`/`sizes`, on the public web app and Partner Central, without either app knowing the CDN.

**Architecture:** `ndopify-server` builds the variants once in `ListingMapper.toListingImage` using a new `ListingImages` object and the existing `ImageService`. `ndopify-dto` carries them as `ImageVariant` values on `ListingImage`. The two web apps map them into their existing view models (`Photo`, `PhotoView`, `GalleryPhotoView`) and the templates emit `srcset`/`sizes`, `width`/`height`, `fetchpriority` and the social image. The raw `url` stays for e-mail/Telegram and for local storage.

**Tech Stack:** Kotlin, Spring Boot, Thymeleaf 3, ImageKit URL transformations, JUnit 5 + `kotlin.test` + `mockito-kotlin`, jsoup for template assertions, JaCoCo.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/234

## Global Constraints

- Kotlin, ktlint (`mvn validate` must pass); assertions use `kotlin.test` (`assertEquals`, `assertTrue`, …), never `org.junit.jupiter.api.Assertions.*`.
- UI text stays French; code, comments and KDoc stay English.
- No visual redesign: layouts, CSS and `src/main/html/` prototypes are untouched.
- `ndopify-web` and `ndopify-partner-central` must not depend on `ndopify-server`. No `ndopify-web-core` change in this story.
- `ndopify-dto` has no tests; the server and client tests cover it.
- While developing, set the root `pom.xml` `<ndopify-dto.version>` to `0.0.100-SNAPSHOT` and `mvn install` the DTO. **Never commit the `-SNAPSHOT`**: `push` releases the DTO and commits `fix dependencies: ndopify-dto <version>`.
- Conventional commits, one library module per commit or none; `Refs #234` in library commit bodies.
- ImageKit transformations: width `240 / 480 / 960 / 1440 / 2048`, exact 3:2 (`ar-3-2`, `c-maintain_ratio`, `cm-extract`), `f-auto`, `q-75`. Social: exactly `1200 × 630`, same format/quality. Never generate 3200.
- Ladder widths greater than the stored source width are omitted; when the source width is unknown, keep the whole ladder.

## Review Focus

- **Unknown or small source width:** a `ListingImage.width` that is null must still produce a full ladder; a width below 240 must produce one variant at the source width, never an upscaled ladder. Tests live in Task 2.
- **Non-ImageKit URL / local mode:** a URL outside `ndopify.image.image-kit.origin-url` must pass through unchanged and every variant must equal `url`; pages must still render. Tests live in Tasks 2 and 3.
- **Wrong exact-crop parameters:** if the URL does not return exactly the declared width, `srcset` picks the wrong candidate. The `ImageKitTest` pins the URL, and Task 2's step 6 verifies a real TEST URL.
- **Photo-less listing / blank URL:** no gallery, `og:image` falls back to the tenant logo, no empty `srcset`. Tests live in Task 5.
- **`sizes` mismatch:** a missing or wrong `sizes` silently downloads a larger candidate. Template tests assert the exact `sizes` strings in Tasks 5 and 6.

---

### Task 1: DTO carries the variants

**Files:**
- Create: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ImageVariant.kt`
- Modify: `modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ListingImage.kt`
- Modify (local only, never committed): `pom.xml` `<ndopify-dto.version>`

**Interfaces:**
- Produces: `ImageVariant(width: Int = 0, height: Int = 0, url: String = "")`
- Produces: `ListingImage.variants: List<ImageVariant> = emptyList()` and `ListingImage.socialUrl: String = ""`
- Produces: built artifact `com.wutsi.ndopify:ndopify-dto:0.0.100-SNAPSHOT`

- [ ] **Step 1: Create `ImageVariant`**

```kotlin
package com.wutsi.ndopify.listing.dto

/** One rendition of a listing photo: its exact pixel size and its URL. */
data class ImageVariant(
    val width: Int = 0,
    val height: Int = 0,
    val url: String = "",
)
```

- [ ] **Step 2: Add the two fields to `ListingImage`**

Append after `errorMessage`:

```kotlin
    /** The 3:2 renditions, ascending width; the browser picks one with `srcset`. */
    val variants: List<ImageVariant> = emptyList(),

    /** The 1200×630 rendition for `og:image`. */
    val socialUrl: String = "",
```

- [ ] **Step 3: Point the root POM at the local snapshot**

In the root `pom.xml`, set `<ndopify-dto.version>0.0.100-SNAPSHOT</ndopify-dto.version>`. This is a local, uncommitted change.

- [ ] **Step 4: Install the DTO**

Run: `cd modules/ndopify-dto && mvn install`
Expected: `BUILD SUCCESS`, `ndopify-dto-0.0.100-SNAPSHOT.jar` installed.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ImageVariant.kt \
        modules/ndopify-dto/src/main/kotlin/com/wutsi/ndopify/listing/dto/ListingImage.kt
git commit -m "feat(dto): add listing image variants

Refs #234"
```

Do not `git add pom.xml`.

---

### Task 2: Server builds the renditions

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/Format.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/image/ListingImages.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/ImageKitTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/image/ListingImagesTest.kt`

**Interfaces:**
- Consumes: `ImageVariant` (Task 1), `Transformation`, `Format`, `Crop`, `CropMode`, `ImageService`.
- Produces: `Format.AUTO`.
- Produces: `ListingImages.WIDTHS: List<Int>`, `ListingImages.transformation(width: Int): Transformation`, `ListingImages.SOCIAL: Transformation`, `ListingImages.variants(url: String, sourceWidth: Int?, imageService: ImageService): List<ImageVariant>`, `ListingImages.social(url: String, imageService: ImageService): String`.

- [ ] **Step 1: Write the failing `Format.AUTO` and exact-crop tests**

Append to `ImageKitTest`:

```kotlin
    @Test
    fun `automatic format`() {
        val result = service.transform(url, Transformation(format = Format.AUTO))
        assertEquals("https://ik.imagekit.io/cx8qxsgz4d/tr:f-auto/foo/bar/image.jpg", result)
    }

    @Test
    fun `exact 3 by 2 crop`() {
        val result = service.transform(
            url,
            Transformation(
                dimension = Dimension(960, 640),
                aspectRatio = Dimension(3, 2),
                format = Format.AUTO,
                crop = Crop.MAINTAIN_RATIO,
                cropMode = CropMode.EXTRACT,
                quality = 75,
            ),
        )
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-960,h-640,f-auto,ar-3-2,c-maintain_ratio,cm-extract,q-75/foo/bar/image.jpg",
            result,
        )
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ImageKitTest`
Expected: FAIL — `Format.AUTO` does not exist.

- [ ] **Step 3: Add `AUTO` to `Format`**

```kotlin
enum class Format {
    PNG,
    JPG,
    GIF,
    WEBP,
    AUTO
}
```

- [ ] **Step 4: Write the failing `ListingImagesTest`**

```kotlin
package com.wutsi.ndopify.listing.server.image

import com.wutsi.ndopify.listing.dto.ImageVariant
import com.wutsi.ndopify.platform.image.Crop
import com.wutsi.ndopify.platform.image.CropMode
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListingImagesTest {
    private val url = "https://bucket.s3.amazonaws.com/listings/1/images/a.jpg"
    private val service = object : ImageService {
        override fun transform(url: String, transformation: Transformation): String =
            "w-${transformation.dimension!!.width}"
    }

    @Test
    fun `the ladder is 240 to 2048`() {
        assertEquals(listOf(240, 480, 960, 1440, 2048), ListingImages.WIDTHS)
    }

    @Test
    fun `a variant is exact 3 by 2, auto format at quality 75`() {
        assertEquals(
            Transformation(
                dimension = Dimension(960, 640),
                aspectRatio = Dimension(3, 2),
                format = Format.AUTO,
                crop = Crop.MAINTAIN_RATIO,
                cropMode = CropMode.EXTRACT,
                quality = 75,
            ),
            ListingImages.transformation(960),
        )
    }

    @Test
    fun `variants are the ladder below the source width, ascending`() {
        val variants = ListingImages.variants(url, 700, service)

        assertEquals(listOf(240, 480), variants.map(ImageVariant::width))
        assertEquals(listOf(160, 320), variants.map(ImageVariant::height))
        assertEquals(listOf("w-240", "w-480"), variants.map(ImageVariant::url))
    }

    @Test
    fun `an unknown source width keeps the whole ladder`() {
        assertEquals(
            listOf(240, 480, 960, 1440, 2048),
            ListingImages.variants(url, null, service).map(ImageVariant::width),
        )
    }

    @Test
    fun `a source below the smallest step gets one variant at its own width`() {
        val variants = ListingImages.variants(url, 200, service)

        assertEquals(listOf(200), variants.map(ImageVariant::width))
    }

    @Test
    fun `a blank URL has no variant and no social image`() {
        assertTrue(ListingImages.variants(" ", 4000, service).isEmpty())
        assertEquals("", ListingImages.social(" ", service))
    }

    @Test
    fun `the social image is 1200 by 630`() {
        assertEquals(
            Transformation(
                dimension = Dimension(1200, 630),
                aspectRatio = Dimension(1200, 630),
                format = Format.AUTO,
                crop = Crop.MAINTAIN_RATIO,
                cropMode = CropMode.EXTRACT,
                quality = 75,
            ),
            ListingImages.SOCIAL,
        )
        assertEquals("w-1200", ListingImages.social(url, service))
    }

    @Test
    fun `the local service leaves every URL unchanged`() {
        val variants = ListingImages.variants(url, 4000, ImageServiceLocal())

        assertEquals(ListingImages.WIDTHS, variants.map(ImageVariant::width))
        assertTrue(variants.all { it.url == url })
        assertEquals(url, ListingImages.social(url, ImageServiceLocal()))
    }
}
```

- [ ] **Step 5: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingImagesTest`
Expected: FAIL — `ListingImages` is unresolved.

- [ ] **Step 6: Implement `ListingImages`**

```kotlin
package com.wutsi.ndopify.listing.server.image

import com.wutsi.ndopify.listing.dto.ImageVariant
import com.wutsi.ndopify.platform.image.Crop
import com.wutsi.ndopify.platform.image.CropMode
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension

/**
 * The listing-page renditions, exact 3:2 and generated on demand by [ImageService]. The widths are the ladder the
 * browser chooses from with `srcset`; a width the source can't fill is left out, so nothing is upscaled.
 */
object ListingImages {
    val WIDTHS = listOf(240, 480, 960, 1440, 2048)

    val SOCIAL = transformation(Dimension(1200, 630), 1200, 630)

    fun transformation(width: Int): Transformation = transformation(Dimension(width, width * 2 / 3), 3, 2)

    /** [url] in each ladder width that fits [sourceWidth]; the whole ladder when it is null, its own width when it's tiny. */
    fun variants(url: String, sourceWidth: Int?, imageService: ImageService): List<ImageVariant> {
        if (url.isBlank()) return emptyList()
        val widths = if (sourceWidth == null || sourceWidth <= 0) {
            WIDTHS
        } else {
            WIDTHS.filter { it <= sourceWidth }.ifEmpty { listOf(sourceWidth) }
        }
        return widths.map { width ->
            ImageVariant(width = width, height = width * 2 / 3, url = imageService.transform(url, transformation(width)))
        }
    }

    fun social(url: String, imageService: ImageService): String =
        if (url.isBlank()) "" else imageService.transform(url, SOCIAL)

    private fun transformation(dimension: Dimension, aspectWidth: Int, aspectHeight: Int) = Transformation(
        dimension = dimension,
        aspectRatio = Dimension(aspectWidth, aspectHeight),
        format = Format.AUTO,
        crop = Crop.MAINTAIN_RATIO,
        cropMode = CropMode.EXTRACT,
        quality = 75,
    )
}
```

- [ ] **Step 7: Run the tests**

Run: `cd modules/ndopify-server && mvn test -Dtest='ImageKitTest,ListingImagesTest'`
Expected: PASS.

- [ ] **Step 8: Verify a real TEST URL (manual, before rollout)**

Open a URL built like `{IMAGE_KIT_ENDPOINT_URL}/tr:w-960,h-640,f-auto,ar-3-2,c-maintain_ratio,cm-extract,q-75/<path>` against the TEST ImageKit account. Expected: an image exactly 960 px wide, served as AVIF/WebP to a browser. If either parameter is rejected, stop and report before proceeding.

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/Format.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/image/ListingImages.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/ImageKitTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/image/ListingImagesTest.kt
git commit -m "feat(server): add the listing image renditions"
```

---

### Task 3: The mapper exposes the renditions

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapper.kt:12-124`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapperTest.kt`

**Interfaces:**
- Consumes: `ListingImages.variants(...)`, `ListingImages.social(...)` (Task 2).
- Produces: `ListingMapper(imageService: ImageService)`; `toListingImage` fills `variants` and `socialUrl`.
- Consumed by: the existing `ListingEndpoints.images` endpoint, unchanged.

- [ ] **Step 1: Write the failing `ListingMapperTest`**

```kotlin
package com.wutsi.ndopify.listing.server.mapper

import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import com.wutsi.ndopify.platform.image.Transformation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListingMapperTest {
    private val url = "https://bucket.s3.amazonaws.com/listings/1/images/a.jpg"
    private val service = object : ImageService {
        override fun transform(url: String, transformation: Transformation): String =
            "w-${transformation.dimension!!.width}"
    }
    private val image = ListingImageEntity(id = 9, listingId = 1, url = url, width = 4000, height = 3000)

    @Test
    fun `toListingImage adds the ladder and the social image`() {
        val dto = ListingMapper(service).toListingImage(image)

        assertEquals(url, dto.url)
        assertEquals(listOf(240, 480, 960, 1440, 2048), dto.variants.map { it.width })
        assertEquals(listOf("w-240", "w-480", "w-960", "w-1440", "w-2048"), dto.variants.map { it.url })
        assertEquals("w-1200", dto.socialUrl)
    }

    @Test
    fun `toListingImage leaves a URL outside ImageKit unchanged`() {
        val dto = ListingMapper(ImageServiceLocal()).toListingImage(image)

        assertTrue(dto.variants.all { it.url == url })
        assertEquals(url, dto.socialUrl)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingMapperTest`
Expected: FAIL — `ListingMapper` takes no `ImageService`.

- [ ] **Step 3: Inject `ImageService` and fill the fields**

Change the class header to `class ListingMapper(private val imageService: ImageService)`, import `com.wutsi.ndopify.listing.server.image.ListingImages` and `com.wutsi.ndopify.platform.image.ImageService`, and in `toListingImage` add:

```kotlin
            variants = ListingImages.variants(entity.url, entity.width, imageService),
            socialUrl = ListingImages.social(entity.url, imageService),
```

- [ ] **Step 4: Run the mapper and image tests**

Run: `cd modules/ndopify-server && mvn test -Dtest='ListingMapperTest,ListingImagesTest,ImageKitTest'`
Expected: PASS.

- [ ] **Step 5: Run the endpoint tests that render images**

Run: `cd modules/ndopify-server && mvn test -Dtest='SearchListingImageEndpointTest,GetListingEndpointTest'`
Expected: PASS (the test profile uses `image-kit`? No — the default `ndopify.image.type=local`, so variants equal `url` and existing assertions still hold). Requires local MySQL.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapper.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mapper/ListingMapperTest.kt
git commit -m "feat(server): expose listing image variants"
```

---

### Task 4: Web view model carries the variants

**Files:**
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt:42`
- Modify: `modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt:184-189`
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt:232-258`

**Interfaces:**
- Consumes: `ListingImage.variants`, `ListingImage.socialUrl` (Task 1).
- Produces: `Photo(src, alt, srcSet, width, height, fullSrc, thumbSrc, socialUrl, variants)` where `src` is the 960 variant (or the largest available), `fullSrc` the largest, `thumbSrc` the 240 variant.
- Consumed by: `ListingPages`, `MatchPages`, `VisitPages` (all reuse `ListingPages.of`).

- [ ] **Step 1: Write the failing `ListingPagesTest`**

Add a test next to the existing photos tests:

```kotlin
    @Test
    fun `photos - the view carries the renditions`() {
        val images = listOf(
            ListingImage(
                id = 1,
                url = "https://cdn/1.jpg",
                variants = listOf(
                    ImageVariant(240, 160, "https://ik/1-240.jpg"),
                    ImageVariant(960, 640, "https://ik/1-960.jpg"),
                    ImageVariant(2048, 1365, "https://ik/1-2048.jpg"),
                ),
                socialUrl = "https://ik/1-og.jpg",
            ),
        )

        val photo = page(images = images).photos.single()

        assertEquals("https://ik/1-960.jpg", photo.src)
        assertEquals("https://ik/1-2048.jpg", photo.fullSrc)
        assertEquals("https://ik/1-240.jpg", photo.thumbSrc)
        assertEquals("https://ik/1-og.jpg", photo.socialUrl)
        assertEquals("https://ik/1-240.jpg 240w, https://ik/1-960.jpg 960w, https://ik/1-2048.jpg 2048w", photo.srcSet)
        assertEquals(960, photo.width)
        assertEquals(640, photo.height)
    }

    @Test
    fun `photos - a listing image without variants falls back to its raw URL`() {
        val photo = page(images = listOf(ListingImage(id = 1, url = "https://cdn/1.jpg"))).photos.single()

        assertEquals("https://cdn/1.jpg", photo.src)
        assertEquals("https://cdn/1.jpg", photo.fullSrc)
        assertEquals("https://cdn/1.jpg", photo.thumbSrc)
        assertEquals("", photo.srcSet)
    }
```

Adjust the existing `page(...)` helper call style to the test's local helper (it currently takes `images`); keep the existing assertions.

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-web && mvn test -Dtest=ListingPagesTest`
Expected: FAIL — `Photo` has no `srcSet`.

- [ ] **Step 3: Extend `Photo` and fill it in `ListingPages.photos`**

```kotlin
data class Photo(
    val src: String,
    val alt: String,
    val srcSet: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fullSrc: String = "",
    val thumbSrc: String = "",
    val socialUrl: String = "",
    val variants: List<ImageVariant> = emptyList(),
)
```

In `photos`, replace the `Photo(src = image.url, alt = …)` construction with:

```kotlin
            val base = image.variants.find { it.width == 960 } ?: image.variants.maxByOrNull { it.width }
            val full = image.variants.maxByOrNull { it.width }
            val thumb = image.variants.find { it.width == 240 } ?: base
            Photo(
                src = base?.url ?: image.url,
                alt = image.title?.ifBlank { null } ?: image.description?.ifBlank { null } ?: title,
                srcSet = image.variants.joinToString(", ") { variant -> "${variant.url} ${variant.width}w" },
                width = base?.width ?: image.width ?: 0,
                height = base?.height ?: image.height ?: 0,
                fullSrc = full?.url ?: image.url,
                thumbSrc = thumb?.url ?: image.url,
                socialUrl = image.socialUrl.ifBlank { base?.url ?: image.url },
                variants = image.variants,
            )
```

- [ ] **Step 4: Run the view tests**

Run: `cd modules/ndopify-web && mvn test -Dtest='ListingPagesTest,MatchPagesTest,VisitPagesTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPage.kt \
        modules/ndopify-web/src/main/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPages.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingPagesTest.kt
git commit -m "feat(web): carry listing image renditions in the view"
```

---

### Task 5: Web templates render them

**Files:**
- Modify: `modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html:34-43`
- Modify: `modules/ndopify-web/src/main/resources/templates/listing.html:15`
- Modify: `modules/ndopify-web/src/main/resources/templates/visit.html:84-88`
- Modify: `modules/ndopify-web/src/main/resources/public/assets/js/listing.js:324`
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt`
- Modify: `modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt`

**Interfaces:**
- Consumes: `Photo.srcSet`, `src`, `fullSrc`, `thumbSrc`, `socialUrl`, `width`, `height` (Task 4).
- Produces: gallery `<img>` with `srcset`/`sizes`/`width`/`height`, hero `fetchpriority="high"`, links to `fullSrc`, `og:image` = `socialUrl`, lightbox JSON full-size source, visit thumb = 240.

- [ ] **Step 1: Write the failing template assertions**

In `ListingTemplateTest`, add a **new** test that renders a page whose images carry variants (`ImageVariant(240, 160, "https://ik/1-240.jpg")`, `ImageVariant(960, 640, "https://ik/1-960.jpg")`, `ImageVariant(2048, 1365, "https://ik/1-2048.jpg")`, `socialUrl = "https://ik/1-og.jpg"`). Leave the existing raw-URL tests untouched: they pin the fallback. Assert:

```kotlin
        val hero = doc.selectFirst(".gallery__item--hero img")!!
        assertEquals("https://ik/1-960.jpg", hero.attr("src"))
        assertEquals("https://ik/1-240.jpg 240w, https://ik/1-960.jpg 960w, https://ik/1-2048.jpg 2048w", hero.attr("srcset"))
        assertEquals("(min-width: 768px) 62vw, 88vw", hero.attr("sizes"))
        assertEquals("960", hero.attr("width"))
        assertEquals("640", hero.attr("height"))
        assertEquals("high", hero.attr("fetchpriority"))
        assertEquals("", hero.attr("loading"))
        assertEquals("https://ik/1-2048.jpg", doc.selectFirst(".gallery__item--hero")!!.attr("href"))
        assertEquals("https://ik/1-og.jpg", doc.selectFirst("meta[property=og:image]")?.attr("content"))
        assertTrue(doc.selectFirst("#listing-photos")!!.data().replace("\\/", "/").contains("https://ik/1-2048.jpg"))
```

Add a tile assertion:

```kotlin
        val tile = doc.select(".gallery__item")[1].selectFirst("img")!!
        assertEquals("(min-width: 768px) 31vw, 88vw", tile.attr("sizes"))
        assertEquals("lazy", tile.attr("loading"))
        assertEquals("", tile.attr("fetchpriority"))
```

In `VisitTemplateTest`, add a **new** test whose summary listing photo carries variants, and assert:

```kotlin
        assertEquals("https://ik/1-240.jpg", doc.selectFirst(".visit-listing img")?.attr("src"))
```

Keep the existing logo fallback test; a photo-less listing still has `og:image` = the tenant logo.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-web && mvn test -Dtest='ListingTemplateTest,VisitTemplateTest'`
Expected: FAIL — no `srcset`/`sizes` in the HTML.

- [ ] **Step 3: Update the gallery fragment**

```html
    <a th:each="photo, it : ${listing.photos}" th:if="${it.index < 5}"
       class="gallery__item" th:classappend="${it.first} ? 'gallery__item--hero'"
       th:data-photo="${it.index}" th:href="${photo.fullSrc}">
        <img th:alt="${photo.alt}" th:src="${photo.src}"
             th:sizes="${it.first} ? '(min-width: 768px) 62vw, 88vw' : '(min-width: 768px) 31vw, 88vw'"
             th:width="${photo.width}" th:height="${photo.height}"
             th:attr="srcset=${photo.srcSet},fetchpriority=${it.first} ? 'high' : null,loading=${it.first} ? null : 'lazy'">
    </a>
    <a class="btn btn-outline gallery__all" data-photo="0" th:if="${#lists.size(listing.photos) > 5}"
       th:href="${listing.photos[0].fullSrc}" th:text="'Voir les ' + ${#lists.size(listing.photos)} + ' photos'">Voir les 8 photos</a>
```

Thymeleaf 3.1.7 has a `th:sizes` processor but no `th:srcset`: write `srcset` through `th:attr` (one assignment list, no commas outside the `${...}` expression). The template test proves both attributes land in the HTML.

- [ ] **Step 4: Update `og:image` and the lightbox source**

`listing.html` line 15:

```html
        <meta property="og:image" th:content="${#lists.isEmpty(page.photos)} ? ${logoUrl} : ${page.photos[0].socialUrl}">
```

`listing.js` line 324:

```javascript
      img.dataset.src = photo.fullSrc || photo.src;
```

- [ ] **Step 5: Update the visit thumbnail**

`visit.html`:

```html
                <img alt="" height="80" th:unless="${#lists.isEmpty(page.listing.photos)}"
                     th:src="${page.listing.photos[0].thumbSrc}" width="120">
```

- [ ] **Step 6: Run the web tests**

Run: `cd modules/ndopify-web && mvn test -Dtest='ListingTemplateTest,VisitTemplateTest,MatchTemplateTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-web/src/main/resources/templates/fragments/listing-body.html \
        modules/ndopify-web/src/main/resources/templates/listing.html \
        modules/ndopify-web/src/main/resources/templates/visit.html \
        modules/ndopify-web/src/main/resources/public/assets/js/listing.js \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/listing/ListingTemplateTest.kt \
        modules/ndopify-web/src/test/kotlin/com/wutsi/ndopify/web/ui/visit/VisitTemplateTest.kt
git commit -m "feat(web): render responsive listing images"
```

---

### Task 6: Partner Central renders them

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt:175-190`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt:209-235`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingView.kt:10-35`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt:50-67`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViews.kt:74-78`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing/photos.html:107-108`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/listing.html:128-137`
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/agent.html:96`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViewsTest.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViewsTest.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt`
- Modify: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt`

**Interfaces:**
- Consumes: `ListingImage.variants`, `ListingImage.socialUrl` (Task 1).
- Produces: `PhotoView(id, url, alt, note, rejected, deleteLabel, fullUrl)`, `GalleryPhotoView(url, alt, hero, extra, srcSet, fullUrl, width, height)`, `AgentListingCardView.photoUrl` = the 240 variant.
- Consumed by: the existing templates.

- [ ] **Step 1: Write the failing view tests**

Add to `ListingViewsTest`:

```kotlin
    @Test
    fun `photos - the grid is the 480 variant and the link the 2048 one`() {
        val image = ListingImage(
            id = 1,
            url = "https://cdn/1.jpg",
            variants = listOf(
                ImageVariant(240, 160, "https://ik/1-240.jpg"),
                ImageVariant(480, 320, "https://ik/1-480.jpg"),
                ImageVariant(2048, 1365, "https://ik/1-2048.jpg"),
            ),
        )

        val photo = ListingViews.photos(listOf(image), editable = false).photos.single()

        assertEquals("https://ik/1-480.jpg", photo.url)
        assertEquals("https://ik/1-2048.jpg", photo.fullUrl)
    }
```

Add to `PublishedListingViewsTest`:

```kotlin
    @Test
    fun `photos - the gallery is the 960 variant, the viewer the 2048 one`() {
        val image = ListingImage(
            id = 1,
            url = "https://cdn/1.jpg",
            status = ImageStatus.VERIFIED,
            variants = listOf(
                ImageVariant(240, 160, "https://ik/1-240.jpg"),
                ImageVariant(960, 640, "https://ik/1-960.jpg"),
                ImageVariant(2048, 1365, "https://ik/1-2048.jpg"),
            ),
        )

        val photo = PublishedListingViews.of(listing, listOf(image), locations, formatter, owner = true).photos.single()

        assertEquals("https://ik/1-960.jpg", photo.url)
        assertEquals("https://ik/1-2048.jpg", photo.fullUrl)
        assertEquals("https://ik/1-240.jpg 240w, https://ik/1-960.jpg 960w, https://ik/1-2048.jpg 2048w", photo.srcSet)
        assertEquals(960, photo.width)
        assertEquals(640, photo.height)
    }
```

Add to `AgentProfileViewsTest`:

```kotlin
    @Test
    fun `listing cards use the 240 variant`() {
        val image = ListingImage(
            id = 9001,
            url = "https://cdn.ndopify.com/listing/5001/9001.jpg",
            variants = listOf(
                ImageVariant(240, 160, "https://ik/9001-240.jpg"),
                ImageVariant(960, 640, "https://ik/9001-960.jpg"),
            ),
        )

        val view = AgentProfileViews.of(AGENT, page, mapOf(5001L to listOf(image)), LOCATIONS, formatter, null)

        assertEquals("https://ik/9001-240.jpg", view.listings.first { it.id == 5001L }.photoUrl)
    }
```

Use the test file's existing `page` fixture name; adapt if it is called something else.

- [ ] **Step 2: Run them to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingViewsTest,PublishedListingViewsTest,AgentProfileViewsTest'`
Expected: FAIL — the view records have no `fullUrl`/`srcSet`.

- [ ] **Step 3: Extend the view records**

`PhotoView` add `val fullUrl: String = ""`. `GalleryPhotoView` add:

```kotlin
    val srcSet: String = "",
    val fullUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
```

- [ ] **Step 4: Fill them in the view builders**

`ListingViews.photos`:

```kotlin
                val base = image.variants.find { it.width == 480 } ?: image.variants.maxByOrNull { it.width }
                val full = image.variants.maxByOrNull { it.width }
                PhotoView(
                    id = image.id,
                    url = base?.url ?: image.url,
                    alt = "Photo ${i + 1}",
                    note = …,
                    rejected = rejected,
                    deleteLabel = "Supprimer la photo ${i + 1}",
                    fullUrl = full?.url ?: image.url,
                )
```

`PublishedListingViews.photos`:

```kotlin
            val base = image.variants.find { it.width == 960 } ?: image.variants.maxByOrNull { it.width }
            val full = image.variants.maxByOrNull { it.width }
            GalleryPhotoView(
                url = base?.url ?: image.url,
                alt = …,
                hero = index == 0,
                extra = index >= 5,
                srcSet = image.variants.joinToString(", ") { variant -> "${variant.url} ${variant.width}w" },
                fullUrl = full?.url ?: image.url,
                width = base?.width ?: image.width ?: 0,
                height = base?.height ?: image.height ?: 0,
            )
```

`AgentProfileViews.heroImage`:

```kotlin
    private fun heroImage(summary: ListingSummary, images: Map<Long, List<ListingImage>>): String? {
        val listingImages = images[summary.id].orEmpty()
        val image = summary.heroImageId?.let { id -> listingImages.find { it.id == id } } ?: listingImages.firstOrNull()
        return image?.variants?.find { it.width == 240 }?.url?.ifBlank { null } ?: image?.url?.ifBlank { null }
    }
```

- [ ] **Step 5: Write the failing template assertions**

In a **new** test in `ListingPhotoTemplateTest` whose images carry variants, assert the grid image and the link (keep the existing raw-URL test as the fallback pin):

```kotlin
        assertEquals("https://ik/1-480.jpg", photos[0].selectFirst("img")?.attr("src"))
        assertEquals("https://ik/1-2048.jpg", photos[0].selectFirst("a[data-photo-link]")?.attr("href"))
```

Add a **new** published-gallery test with variant-bearing images (keep the existing raw-URL tests as the fallback pin) and assert:

```kotlin
        val image = gallery.selectFirst("[data-photo-link] img")!!
        assertEquals("https://ik/2-960.jpg", image.attr("src"))
        assertEquals("https://ik/2-240.jpg 240w, https://ik/2-960.jpg 960w, https://ik/2-2048.jpg 2048w", image.attr("srcset"))
        assertEquals("(min-width: 640px) 62vw, 88vw", image.attr("sizes"))
        assertEquals("https://ik/2-2048.jpg", gallery.selectFirst("[data-photo-open]")?.attr("href"))
```

Use the partner-central breakpoint `640px` (its gallery becomes a grid at 640) and tile `sizes` `(min-width: 640px) 31vw, 88vw`.

In `AgentProfileTemplateTest`, assert the card image is the 240 variant.

- [ ] **Step 6: Update the three templates**

`listing/photos.html`:

```html
                        <a class="photo__link" th:href="${photo.fullUrl}" target="_blank" rel="noopener" data-photo-link>
                            <img th:src="${photo.url}" th:alt="${photo.alt}" width="480" height="480" loading="lazy">
```

`listing.html` gallery:

```html
                        <a class="listing-gallery__item"
                           th:classappend="${photo.cssClass}"
                           th:each="photo : ${l.published.photos}"
                           th:href="${photo.fullUrl}" data-photo-link>
                            <img th:src="${photo.url}" th:alt="${photo.alt}"
                                 th:sizes="${photo.hero} ? '(min-width: 640px) 62vw, 88vw' : '(min-width: 640px) 31vw, 88vw'"
                                 th:width="${photo.width}" th:height="${photo.height}"
                                 th:attr="srcset=${photo.srcSet},fetchpriority=${photo.hero} ? 'high' : null,loading=${photo.hero} ? null : 'lazy'">
                        </a>
                    </div>
                    <a class="btn btn--secondary btn--compact listing-gallery__all"
                       th:href="${l.published.photos[0].fullUrl}" data-photo-open
```

`agent.html` line 96 keeps `th:src="${card.photoUrl}"`; only the view model changes.

- [ ] **Step 7: Run the partner-central tests**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest='ListingViewsTest,PublishedListingViewsTest,AgentProfileViewsTest,ListingPhotoTemplateTest,ListingTemplateTest,AgentProfileTemplateTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingView.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViews.kt \
        modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViews.kt \
        modules/ndopify-partner-central/src/main/resources/templates/listing/photos.html \
        modules/ndopify-partner-central/src/main/resources/templates/listing.html \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/PublishedListingViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileViewsTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingPhotoTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/listing/ListingTemplateTest.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/agent/AgentProfileTemplateTest.kt
git commit -m "feat(npc): render responsive listing images"
```

---

### Task 7: Whole-build verification

**Files:** none.

- [ ] **Step 1: Run the full build**

Run: `mvn install -Dheadless=true`
Expected: `BUILD SUCCESS` for all modules, JaCoCo gates green. The server needs MySQL.

- [ ] **Step 2: Check the working tree**

Run: `git status --short`
Expected: only the root `pom.xml` local `-SNAPSHOT` change (never committed), and nothing else. `DESIGN.md` was already modified before this story: leave it untouched.

- [ ] **Step 3: Report**

Tell the user the story is ready for `push 234`. Do not push.

---

## Release order (handled by `push 234`)

1. Cherry-pick the DTO commit to `main`; `ndopify-dto-main.yml` releases `0.0.100`.
2. On the branch, revert the local `-SNAPSHOT` and commit `fix dependencies: ndopify-dto 0.0.100`.
3. Push the branch; CI builds server, web and partner-central against the released DTO.
