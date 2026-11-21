# Listing Telegram Photo Resize Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every listing Telegram message sends a 1280 px / quality-80 ImageKit URL of the cover instead of uploading the original image.

**Architecture:** Extend the existing `platform/image` layer with crop / crop mode / quality and a Spring bean, add a URL form of `TelegramClient.sendPhoto`, then make `ListingTelegramSender` transform the cover with `ImageService`: a changed URL is handed to Telegram (it fetches the resized image), an unchanged one (local) still goes through the storage read and multipart upload.

**Tech Stack:** Kotlin, Spring Boot, `java.awt.Dimension`, `kotlin.test`, `mockito-kotlin` (`com.nhaarman.mockitokotlin2`), ImageKit URL transformations.

**Spec:** GitHub issue #222 — https://github.com/wutsi/ndopify-mono/issues/222.

## Global Constraints

- Only `modules/ndopify-server`. No `ndopify-dto` / `ndopify-web-core` change, no root `pom.xml` change, no Flyway migration.
- `platform/image` is on `main` (`936b8faf`, `492a90f7`): reuse `ImageService`, `ImageKit`, `ImageServiceLocal`, `Transformation` (`java.awt.Dimension`), `Format`. Do not rewrite them.
- Transformation parameter order is fixed: `w`, `h`, `f`, `ar`, `c`, `cm`, `q`; a null parameter adds nothing, so today's URLs stay identical.
- The Telegram photo is `Transformation(dimension = Dimension(1280, 1280), format = Format.JPG, crop = Crop.AT_MAX, quality = 80)` → `tr:w-1280,h-1280,f-jpg,c-at_max,q-80`.
- A resized URL is given to Telegram as a URL; the server never downloads it. When the URL is unchanged (local, or outside the ImageKit origin), the original is read and uploaded as today.
- Any resize / photo failure only drops the photo: the text and its buttons are sent, and the original is **never** sent when a resized URL exists. Telegram refusing the text still throws (`TelegramException`) so the e-mail fallback runs.
- Never log the bot token nor the image URL.
- The caption limit is `text.codePointCount(0, text.length) <= 1024`, unchanged.
- `kotlin.test` assertions, `mockito-kotlin` mocks, ktlint (`mvn validate`). Conventional commits with `Refs #222` in the body.
- Run Maven from `modules/ndopify-server`. Unit tests need no MySQL; the final `mvn verify` needs local MySQL (`root`, no password).

## Review Focus

- A cover URL outside the ImageKit origin (or `image-kit` not configured) → original read from storage and uploaded, never a broken ImageKit URL. Test: Task 4, local path.
- `ImageService.transform` throws (bad origin / config) → text only, storage never read. Test: Task 4, failed resize.
- Telegram refuses the resized URL → text only, storage never read, no original upload. Test: Task 4, refused URL.
- Text over 1024 code points → no transform, no cover lookup, no storage read. Test: Task 4, unchanged tests.
- Parameter order and omission: `w,h,f,ar,c,cm,q`; a null crop / crop mode / quality leaves today's URL byte-for-byte. Tests: Task 1, all-parameters and no-transformation.

---

### Task 1: Crop, crop mode and quality in the image API

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/Crop.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/CropMode.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/Transformation.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image/ImageKit.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/ImageKitTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image/TransformationTest.kt`

**Interfaces:**
- Produces: `enum class Crop { MAINTAIN_RATIO, PAD_RESIZE, FORCE, AT_MAX, AT_MAX_ENLARGE, AT_LEAST }`, `enum class CropMode { EXTRACT, PAD_EXTRACT }`, and `Transformation(dimension, aspectRatio, format, crop, cropMode, quality)` where `quality: Int?` must be in `1..100`.
- Consumes: the existing `ImageKit.transform(url, transformation)` and its `tr:` builder.

- [ ] **Step 1: Write the failing `ImageKitTest` tests**

Add to `ImageKitTest` (keep the existing tests):

```kotlin
    @Test
    fun `crop values`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        Crop.entries.forEach {
            val result = service.transform(url, Transformation(crop = it))
            assertEquals(
                "https://ik.imagekit.io/cx8qxsgz4d/tr:c-${it.name.lowercase()}/foo/bar/image.jpg",
                result,
            )
        }
    }

    @Test
    fun `crop mode values`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        CropMode.entries.forEach {
            val result = service.transform(url, Transformation(cropMode = it))
            assertEquals(
                "https://ik.imagekit.io/cx8qxsgz4d/tr:cm-${it.name.lowercase()}/foo/bar/image.jpg",
                result,
            )
        }
    }

    @Test
    fun `crop with a size`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val result = service.transform(
            url,
            Transformation(dimension = Dimension(100, 200), crop = Crop.AT_MAX),
        )
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-100,h-200,c-at_max/foo/bar/image.jpg",
            result,
        )
    }

    @Test
    fun `quality alone`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val result = service.transform(url, Transformation(quality = 80))
        assertEquals("https://ik.imagekit.io/cx8qxsgz4d/tr:q-80/foo/bar/image.jpg", result)
    }

    @Test
    fun `all parameters in order`() {
        val url = "https://www.example.com/foo/bar/image.jpg"
        val result = service.transform(
            url,
            Transformation(
                dimension = Dimension(1280, 1280),
                aspectRatio = Dimension(3, 2),
                format = Format.JPG,
                crop = Crop.AT_MAX,
                cropMode = CropMode.PAD_EXTRACT,
                quality = 80,
            ),
        )
        assertEquals(
            "https://ik.imagekit.io/cx8qxsgz4d/tr:w-1280,h-1280,f-jpg,ar-3-2,c-at_max,cm-pad_extract,q-80/foo/bar/image.jpg",
            result,
        )
    }

    @Test
    fun `a URL outside the origin is unchanged with a crop and a quality`() {
        val url = "https://cdn.example.org/foo/bar/image.jpg"
        val result = service.transform(
            url,
            Transformation(crop = Crop.AT_MAX, quality = 80),
        )
        assertEquals(url, result)
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=ImageKitTest` (from `modules/ndopify-server`)
Expected: compilation failure — `Unresolved reference: Crop`, `Transformation` has no `crop` / `cropMode` / `quality`.

- [ ] **Step 3: Write the failing `TransformationTest`**

Create `TransformationTest.kt`:

```kotlin
package com.wutsi.ndopify.platform.image

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransformationTest {
    @Test
    fun `quality 1 and 100 are accepted`() {
        assertEquals(1, Transformation(quality = 1).quality)
        assertEquals(100, Transformation(quality = 100).quality)
    }

    @Test
    fun `a quality below 1 is rejected`() {
        assertFailsWith<IllegalArgumentException> { Transformation(quality = 0) }
    }

    @Test
    fun `a quality above 100 is rejected`() {
        assertFailsWith<IllegalArgumentException> { Transformation(quality = 101) }
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn test -Dtest=TransformationTest`
Expected: FAIL — the `quality` argument does not exist.

- [ ] **Step 5: Implement `Crop` and `CropMode`**

Create `Crop.kt` and `CropMode.kt`, one enum per file, with a KDoc on each saying it is ImageKit's `c-` / `cm-` value (lowercased in the URL):

```kotlin
enum class Crop {
    MAINTAIN_RATIO,
    PAD_RESIZE,
    FORCE,
    AT_MAX,
    AT_MAX_ENLARGE,
    AT_LEAST,
}
```

```kotlin
enum class CropMode {
    EXTRACT,
    PAD_EXTRACT,
}
```

- [ ] **Step 6: Add the parameters to `Transformation`**

`Transformation.kt` becomes:

```kotlin
data class Transformation(
    val dimension: Dimension? = null,
    val aspectRatio: Dimension? = null,
    val format: Format? = null,
    val crop: Crop? = null,
    val cropMode: CropMode? = null,
    val quality: Int? = null,
) {
    init {
        require(quality == null || quality in 1..100) { "quality must be between 1 and 100: $quality" }
    }
}
```

- [ ] **Step 7: Send the new parameters in `ImageKit`**

After the `aspectRatio` block in `transform`, before `if (tr.isNotEmpty())`:

```kotlin
        transformation.crop?.let {
            tr.add("c-${it.name.lowercase()}")
        }

        transformation.cropMode?.let {
            tr.add("cm-${it.name.lowercase()}")
        }

        transformation.quality?.let {
            tr.add("q-$it")
        }
```

- [ ] **Step 8: Run both tests to verify they pass**

Run: `mvn test -Dtest=ImageKitTest,TransformationTest`
Expected: PASS (existing `ImageKitTest` tests included, unchanged URLs).

- [ ] **Step 9: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/image \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/image
git commit -m "feat(server): add crop modes and quality to image transformations" -m "Refs #222"
```

---

### Task 2: Wire the `ImageService` bean

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/ImageConfiguration.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/config/platform/ImageConfigurationTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (platform section)

**Interfaces:**
- Produces: the `ImageService` Spring bean, selected from `ndopify.image.type`.
- Consumes: `ImageKit(originUrl, endpointUrl)` and `ImageServiceLocal()`.

- [ ] **Step 1: Write the failing test**

Create `ImageConfigurationTest.kt`:

```kotlin
package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.image.ImageKit
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageConfigurationTest {
    @Test
    fun `local builds an ImageServiceLocal`() {
        assertTrue(ImageConfiguration("local", "", "").imageService() is ImageServiceLocal)
    }

    @Test
    fun `image-kit builds an ImageKit`() {
        val configuration = ImageConfiguration(
            type = "image-kit",
            originUrl = "https://bucket.s3.amazonaws.com",
            endpointUrl = "https://ik.imagekit.io/key",
        )
        assertTrue(configuration.imageService() is ImageKit)
    }

    @Test
    fun `an unknown type fails`() {
        assertFailsWith<IllegalStateException> {
            ImageConfiguration("none", "", "").imageService()
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=ImageConfigurationTest`
Expected: compilation failure — `ImageConfiguration` does not exist.

- [ ] **Step 3: Implement `ImageConfiguration`**

Create `config/platform/ImageConfiguration.kt`:

```kotlin
package com.wutsi.ndopify.config.platform

import com.wutsi.ndopify.platform.image.ImageKit
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Builds the [ImageService] from `ndopify.image.type`: `image-kit` resizes storage URLs through ImageKit,
 * `local` returns them unchanged. Any other type is a configuration error, at startup.
 */
@Configuration
class ImageConfiguration(
    @param:Value("\${ndopify.image.type}") private val type: String,
    @param:Value("\${ndopify.image.image-kit.origin-url}") private val originUrl: String,
    @param:Value("\${ndopify.image.image-kit.endpoint-url}") private val endpointUrl: String,
) {
    @Bean
    fun imageService(): ImageService = when (type) {
        "image-kit" -> ImageKit(originUrl, endpointUrl)
        "local" -> ImageServiceLocal()
        else -> throw IllegalStateException("Unknown ndopify.image.type: $type")
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=ImageConfigurationTest`
Expected: PASS.

- [ ] **Step 5: Document `platform/image` in `modules/ndopify-server/CLAUDE.md`**

In the platform list, after the `platform/storage` bullet:

```markdown
- `platform/image` — resizing through ImageKit, wired by `config/platform/ImageConfiguration` from `ndopify.image.*`:
  `ImageKit` rewrites a storage URL into an `ik.imagekit.io` transformation URL (`tr:w-…,h-…,f-…,ar-…,c-…,cm-…,q-…`),
  `ImageServiceLocal` returns it unchanged. Only `ListingTelegramSender` uses it today (see *listing*).
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/ImageConfiguration.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/config/platform/ImageConfigurationTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): wire the image service from the configuration" -m "Refs #222"
```

---

### Task 3: A URL form of `TelegramClient.sendPhoto`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (`platform/telegram` bullet)

**Interfaces:**
- Produces: `TelegramClient.sendPhoto(chatId: Long, photoUrl: String, caption: String, buttons: List<TelegramButton> = emptyList())` — a JSON `post` to `sendPhoto` whose `photo` field is a URL Telegram fetches.
- Consumes: `Http.post(uri, requestPayload, responseType, headers)`.

- [ ] **Step 1: Write the failing test**

Add to `TelegramClientTest`:

```kotlin
    @Test
    fun `sendPhoto by URL posts the URL, its caption and one keyboard row per button`() {
        val photoUrl = "https://ik.imagekit.io/key/tr:w-1280,h-1280,f-jpg,c-at_max,q-80/7.jpg"
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(eq("$base/sendPhoto"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

        client.sendPhoto(
            123L,
            photoUrl,
            "En ligne",
            listOf(TelegramButton("Voir l'annonce", url = "https://www.ndopify.com/listing")),
        )

        verify(http).post(
            eq("$base/sendPhoto"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "photo" to photoUrl,
                    "caption" to "En ligne",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(mapOf("text" to "Voir l'annonce", "url" to "https://www.ndopify.com/listing")),
                        ),
                    ),
                ),
            ),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=TelegramClientTest`
Expected: compilation failure — no `sendPhoto` overload takes a `String` photo.

- [ ] **Step 3: Implement the URL overload**

Add beside the byte `sendPhoto`:

```kotlin
    /**
     * Sends the image at [photoUrl] as a photo message whose caption is [caption] and whose keyboard is [buttons].
     * Telegram fetches the URL itself: unlike the other [sendPhoto], nothing is uploaded. Used for the resized
     * listing photo (ImageKit serves it); Telegram caps the caption at 1024 characters.
     */
    open fun sendPhoto(
        chatId: Long,
        photoUrl: String,
        caption: String,
        buttons: List<TelegramButton> = emptyList(),
    ) {
        val fields = buildMap<String, Any> {
            put("chat_id", chatId)
            put("photo", photoUrl)
            put("caption", caption)
            if (buttons.isNotEmpty()) {
                put("reply_markup", mapOf("inline_keyboard" to buttons.toKeyboard()))
            }
        }
        http.post(url("sendPhoto"), fields, TelegramMessageResponse::class.java, emptyMap()).check()
    }
```

Update the byte overload's KDoc: drop "never a URL Telegram would fetch" and say it uploads the bytes, the URL form being the other `sendPhoto`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=TelegramClientTest`
Expected: PASS.

- [ ] **Step 5: Update the `platform/telegram` bullet in `CLAUDE.md`**

Replace "`sendPhoto` uploads an image, never a URL Telegram would fetch, with its caption and keyboard" with
"`sendPhoto` uploads an image, or sends a URL Telegram fetches itself (the resized listing photo), with its caption and
keyboard".

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): send a photo URL through TelegramClient" -m "Refs #222"
```

---

### Task 4: Resize the listing cover in `ListingTelegramSender`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSender.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSenderTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md` (`listing` domain, *Send*)

**Interfaces:**
- Consumes: `ImageService.transform(url, PHOTO_TRANSFORMATION)`, `TelegramClient.sendPhoto(chatId, photoUrl, caption, buttons)`, `ListingImageService.cover(listing)`.
- Produces: `ListingTelegramSender.PHOTO_TRANSFORMATION`.

- [ ] **Step 1: Update the test fixture and write the failing tests**

In `ListingTelegramSenderTest`, add the `ImageService` mock and pass it to the constructor; stub the identity transform in `setUp` so the existing tests stay on the storage path:

```kotlin
    private val listingImageService = mock<ListingImageService>()
    private val imageService = mock<ImageService>()
    private val sender = ListingTelegramSender(listingImageService, storageProvider, client, imageService)

    @BeforeTest
    fun setUp() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(cover).whenever(listingImageService).cover(listing)
        doAnswer { invocation -> (invocation.arguments[1] as OutputStream).write(bytes) }
            .whenever(storage).get(eq("listings/101/7.jpg"), any())
        doReturn(cover.url).whenever(imageService).transform(eq(cover.url), any())
    }
```

Rename the existing `imageService` references (the `ListingImageService` mock) to `listingImageService`. Add:

```kotlin
    @Test
    fun `a resized cover is given to Telegram as a URL`() {
        val resized = "https://ik.imagekit.io/key/tr:w-1280,h-1280,f-jpg,c-at_max,q-80/7.jpg"
        doReturn(resized).whenever(imageService).transform(eq(cover.url), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(imageService).transform(cover.url, ListingTelegramSender.PHOTO_TRANSFORMATION)
        verify(client).sendPhoto(1000L, resized, "Nice apartment, 2 chambres", buttons)
        verify(storage, never()).get(any(), any())
        verify(client, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `a refused resized cover sends the text without reading the storage`() {
        val resized = "https://ik.imagekit.io/key/tr:w-1280,h-1280,f-jpg,c-at_max,q-80/7.jpg"
        doReturn(resized).whenever(imageService).transform(eq(cover.url), any())
        doThrow(TelegramException("wrong file identifier/HTTP URL specified"))
            .whenever(client).sendPhoto(eq(1000L), eq(resized), any(), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
        verify(storage, never()).get(any(), any())
    }

    @Test
    fun `a failed resize sends the text without reading the storage`() {
        doThrow(IllegalStateException("bad origin")).whenever(imageService).transform(eq(cover.url), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
        verify(storage, never()).get(any(), any())
    }

    @Test
    fun `the photo transformation fits 1280 by 1280 as a JPEG at quality 80`() {
        assertEquals(
            Transformation(
                dimension = Dimension(1280, 1280),
                format = Format.JPG,
                crop = Crop.AT_MAX,
                quality = 80,
            ),
            ListingTelegramSender.PHOTO_TRANSFORMATION,
        )
    }
```

Imports to add: `com.wutsi.ndopify.platform.image.Crop`, `Format`, `ImageService`, `Transformation`, `java.awt.Dimension`, `kotlin.test.assertEquals`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=ListingTelegramSenderTest`
Expected: compilation failure — the constructor takes no `ImageService`, `PHOTO_TRANSFORMATION` does not exist.

- [ ] **Step 3: Implement the resize in `ListingTelegramSender`**

Rename the injected `ListingImageService` to `listingImageService` and add `private val imageService: ImageService`. Add to the companion:

```kotlin
        val PHOTO_TRANSFORMATION = Transformation(
            dimension = Dimension(1280, 1280),
            format = Format.JPG,
            crop = Crop.AT_MAX,
            quality = 80,
        )
```

`sendPhoto` becomes:

```kotlin
    // Resizes the cover and gives Telegram its URL; any failure only drops the photo, never the message
    private fun sendPhoto(
        chatId: Long,
        listing: ListingEntity,
        cover: ListingImageEntity,
        text: String,
        buttons: List<TelegramButton>,
    ): Boolean {
        return try {
            val url = imageService.transform(cover.url, PHOTO_TRANSFORMATION)
            if (url == cover.url) {
                uploadOriginal(chatId, listing, cover, text, buttons)
            } else {
                client.sendPhoto(chatId, url, text, buttons)
            }
            true
        } catch (ex: Exception) {
            LOGGER.warn("Telegram photo failed for Listing#${listing.id} (image ${cover.id}), sending the text only", ex)
            false
        }
    }

    // Local, or a URL the image service can't resize: upload the stored file, as before
    private fun uploadOriginal(
        chatId: Long,
        listing: ListingEntity,
        cover: ListingImageEntity,
        text: String,
        buttons: List<TelegramButton>,
    ) {
        val out = ByteArrayOutputStream()
        storageProvider.get().get(cover.path, out)
        client.sendPhoto(
            chatId,
            out.toByteArray(),
            "${listing.id}.${MimeUtils.getExtensionFromMimeType(cover.mimeType)}",
            cover.mimeType ?: "image/jpeg",
            text,
            buttons,
        )
    }
```

Update the class KDoc: the photo is the cover resized to 1280×1280 at quality 80 (`PHOTO_TRANSFORMATION`) and sent
as an ImageKit URL; when the URL can't be resized (local) it is read from the storage and uploaded; a failed resize,
read or photo only drops the photo, and the original is never sent when a resized URL exists.

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn test -Dtest=ListingTelegramSenderTest`
Expected: PASS, existing photo / text cases included.

- [ ] **Step 5: Update the *listing* Send section in `CLAUDE.md`**

Replace "the cover photo (`ListingImageService.cover`, none without one) is read from the storage and uploaded as a
`sendPhoto` …; a failed download or upload only drops the photo" with: the cover photo (`ListingImageService.cover`,
none without one) is resized to 1280×1280 at quality 80 through the `ImageService` and given to `sendPhoto` as a URL,
and read from the storage and uploaded when the URL can't be resized (local); a failed resize, download or upload only
drops the photo, never sending the original.

- [ ] **Step 6: Run the full module build**

Run: `mvn verify` (from `modules/ndopify-server`, with local MySQL running)
Expected: BUILD SUCCESS, ktlint + tests + JaCoCo gate green.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSender.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSenderTest.kt \
  modules/ndopify-server/CLAUDE.md
git commit -m "feat(server): send resized listing photos to Telegram" -m "Refs #222"
```
