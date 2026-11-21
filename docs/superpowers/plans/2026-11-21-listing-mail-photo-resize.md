# Listing E-mail Photo Resize Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every listing photo in an e-mail is an ImageKit URL sized for its slot (1200×800 cover, 160×120 thumbnail), never the original file.

**Architecture:** Add `platform/mail/MailImages` — the two e-mail `Transformation`s and a blank-safe `imageUrl` helper. Inject the existing `ImageService` into each mailet that renders a listing photo, and replace every raw `cover(listing)?.url` (or digest `imageUrl`) with `MailImages.imageUrl(...)`. The MJML templates are untouched: they keep receiving `image_url`.

**Tech Stack:** Kotlin, Spring Boot, `java.awt.Dimension`, `kotlin.test`, `mockito-kotlin` (`com.nhaarman.mockitokotlin2`), ImageKit URL transformations.

**Spec:** GitHub issue #223 — https://github.com/wutsi/ndopify-mono/issues/223.

## Global Constraints

- **Precondition:** #222 must be merged. This plan consumes `Transformation.quality` and the `ImageService` Spring bean from #222's plan (`docs/superpowers/plans/2026-11-21-listing-telegram-photo-resize.md`, Tasks 1–2). If `Transformation` has no `quality` or there is no `ImageService` bean, stop and report it.
- Only `modules/ndopify-server`. No `ndopify-dto` / `ndopify-web-core` change, no root `pom.xml` change, no Flyway migration, no MJML / HTML template change.
- `COVER = Transformation(dimension = Dimension(1200, 800), format = Format.JPG, quality = 75)` → `tr:w-1200,h-800,f-jpg,q-75`. Width and height are both set, so ImageKit's default crop (`maintain_ratio`) center-crops every cover to the e-mail's 3:2 slot (chosen during grooming; uploads are not ratio-checked).
- `THUMBNAIL = Transformation(dimension = Dimension(160, 120), format = Format.JPG, quality = 75)` → `tr:w-160,h-120,f-jpg,q-75` — 4:3, so the 80×60 `<img>` no longer depends on `object-fit`.
- JPEG, not WebP: Outlook desktop and older clients don't show WebP. Quality 75: about half the default size at these dimensions.
- A blank or missing cover still sends no `image_url` and no image block. `MailImages.imageUrl` returns null for a blank URL and never calls `ImageService`.
- With `ImageServiceLocal` (or a URL outside the ImageKit origin) `transform` returns the URL unchanged: local dev keeps the original, and the e-mail still sends.
- `CoBrokerageInterestDigest.imageUrl` stays the **raw** cover URL (the digest is shared by the e-mail and the Telegram channel); only `CoBrokerageInterestMailet` resizes it.
- In the mailets, the existing `imageService: ListingImageService` field is renamed `listingImageService`; the new `ImageService` field takes the name `imageService`.
- `kotlin.test` assertions, `mockito-kotlin` mocks, ktlint (`mvn validate`). Conventional commits with `Refs #223` in the body.
- Run Maven from `modules/ndopify-server`. Per-task checks use `mvn test -Dtest=…` (no MySQL). The final `mvn verify` needs local MySQL (`root`, no password).

## Review Focus

- **No cover / blank URL:** a listing without a verified cover must still produce `image_url = null` and no image block; `ImageService` is never called. Existing no-cover tests in each mailet test keep this pinned.
- **Local image service / URL outside the origin:** `MailImages.imageUrl` must return the original URL unchanged, so local dev e-mails still send. Test: `MailImagesTest`, `the local image service returns the URL unchanged`.
- **Digest stays raw:** the co-brokerage interest digest must carry the original cover URL for the Telegram channel; only the e-mail's context is resized. Test: `CoBrokerageInterestMailetTest`, raw digest assertion.
- **Every listing in a multi-listing e-mail:** `MatchNotificationMailet`, `CoBrokerageMatchMailet`, `CoBrokerageInterestMailet` and `MatchProposedMailet` render several listings; each `image_url` is resized, not just the first. Tests: their `send` / multi-listing cases.
- **No mailet left behind:** all 17 mailets that pass `image_url` are in this plan; Task 6 greps for a raw cover URL and must find none. A missed mailet fails AC 6.

---

### Task 1: `MailImages` and the test helper

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/MailImages.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailImagesTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailImagesTestSupport.kt`

**Interfaces:**
- Produces: `object MailImages` with `COVER: Transformation`, `THUMBNAIL: Transformation` and
  `fun imageUrl(url: String?, imageService: ImageService, slot: Transformation): String?`.
- Produces (test sources): `fun mockMailImageService(): ImageService` — a mock whose `transform` answers the URL
  ImageKit would build for the slot, so a mailet test's `image_url` proves both the resize and the slot.
- Consumes: `ImageService` / `Transformation` / `Format` from `platform/image` (#222).

- [ ] **Step 1: Write the failing `MailImagesTest`**

```kotlin
package com.wutsi.ndopify.platform.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.ImageServiceLocal
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MailImagesTest {
    private val url = "https://cdn.ndopify.com/7.jpg"

    @Test
    fun `cover is 1200 by 800, JPEG at quality 75`() {
        assertEquals(
            Transformation(dimension = Dimension(1200, 800), format = Format.JPG, quality = 75),
            MailImages.COVER,
        )
    }

    @Test
    fun `thumbnail is 160 by 120, JPEG at quality 75`() {
        assertEquals(
            Transformation(dimension = Dimension(160, 120), format = Format.JPG, quality = 75),
            MailImages.THUMBNAIL,
        )
    }

    @Test
    fun `imageUrl resizes a non-blank URL for the slot`() {
        val imageService = mock<ImageService>()
        doReturn("resized").whenever(imageService).transform(url, MailImages.COVER)

        assertEquals("resized", MailImages.imageUrl(url, imageService, MailImages.COVER))
    }

    @Test
    fun `imageUrl is null without a URL`() {
        val imageService = mock<ImageService>()

        assertNull(MailImages.imageUrl(null, imageService, MailImages.COVER))
        assertNull(MailImages.imageUrl(" ", imageService, MailImages.COVER))
        verify(imageService, never()).transform(any(), any())
    }

    @Test
    fun `the local image service returns the URL unchanged`() {
        assertEquals(url, MailImages.imageUrl(url, ImageServiceLocal(), MailImages.COVER))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn test -Dtest=MailImagesTest`
Expected: compilation failure — `MailImages` does not exist.

- [ ] **Step 3: Implement `MailImages`**

```kotlin
package com.wutsi.ndopify.platform.mail

import com.wutsi.ndopify.platform.image.Format
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation
import java.awt.Dimension

/**
 * The image sizes of the e-mails, and the only ones: the two transformations a mailet asks [ImageService] for.
 *
 * [COVER] is the full-width `mj-image` (2× the 600 px MJML body), [THUMBNAIL] the 80×60 `<img>` (2×). Both are JPEG at
 * quality 75 — Outlook desktop and older clients don't show WebP — with width and height both set, so ImageKit crops
 * to the exact 3:2 / 4:3 ratio and no client needs `object-fit`. A portrait cover is center-cropped to 3:2.
 */
object MailImages {
    val COVER = Transformation(
        dimension = Dimension(1200, 800),
        format = Format.JPG,
        quality = 75,
    )

    val THUMBNAIL = Transformation(
        dimension = Dimension(160, 120),
        format = Format.JPG,
        quality = 75,
    )

    /** [url] resized for [slot], or null for a blank one: what a mailet passes as `image_url`. */
    fun imageUrl(url: String?, imageService: ImageService, slot: Transformation): String? =
        url?.takeIf { it.isNotBlank() }
            ?.let { imageService.transform(it, slot) }
            ?.takeIf { it.isNotBlank() }
}
```

- [ ] **Step 4: Add the test helper**

```kotlin
package com.wutsi.ndopify.platform.mail

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.platform.image.ImageService
import com.wutsi.ndopify.platform.image.Transformation

/**
 * An [ImageService] mock whose `transform` answers the URL ImageKit would build for the slot (its dimension, format and
 * quality), so that a mailet test's `image_url` proves both the resize and the slot it asked for.
 */
fun mockMailImageService(): ImageService {
    val imageService = mock<ImageService>()
    doAnswer { invocation ->
        val slot = invocation.arguments[1] as Transformation
        val params = listOfNotNull(
            slot.dimension?.let { "w-${it.width},h-${it.height}" },
            slot.format?.let { "f-${it.name.lowercase()}" },
            slot.quality?.let { "q-$it" },
        ).joinToString(",")
        "https://ik.imagekit.io/key/tr:$params/${(invocation.arguments[0] as String).substringAfterLast('/')}"
    }.whenever(imageService).transform(any(), any())
    return imageService
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `mvn test -Dtest=MailImagesTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/mail/MailImages.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailImagesTest.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/mail/MailImagesTestSupport.kt
git commit -m "feat(server): add the e-mail image sizes" -m "Refs #223"
```

---

### Task 2: The published-listing mailet

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingPublishedMailet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingPublishedMailetTest.kt`

**Interfaces:**
- Consumes: `MailImages.COVER`, `MailImages.imageUrl(...)`, `ImageService`, `mockMailImageService()`.
- Produces: `ListingPublishedMailet`'s constructor takes `imageService: ImageService` after `listingImageService`.

- [ ] **Step 1: Resize the cover in the mailet**

In `ListingPublishedMailet`:
- rename the existing constructor field `imageService: ListingImageService` to `listingImageService`, keeping `@Lazy`, and add `private val imageService: ImageService` after it;
- change `"image_url" to imageService.cover(listing)?.url?.ifBlank { null }` to
  `"image_url" to MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER)`;
- add the imports `com.wutsi.ndopify.platform.image.ImageService` and `com.wutsi.ndopify.platform.mail.MailImages`;
- in the class KDoc, where it mentions the cover photo, add: "The photo is resized through [MailImages] for the e-mail (never the original URL)."

- [ ] **Step 2: Update `ListingPublishedMailetTest`**

- rename the field `imageService` (the `ListingImageService` mock) to `listingImageService` and every `imageService.cover(...)` to `listingImageService.cover(...)`;
- add `private val imageService = mockMailImageService()` (import `com.wutsi.ndopify.platform.mail.mockMailImageService`) and pass it last to the constructor;
- the expected `"image_url"` in the full-context assertion becomes
  `"https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg"` (the fixture cover is `https://cdn.ndopify.com/7.jpg`);
- the no-cover and blank-cover tests keep asserting `null`.

- [ ] **Step 3: Run the test**

Run: `mvn test -Dtest=ListingPublishedMailetTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/mail/ListingPublishedMailet.kt \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/mail/ListingPublishedMailetTest.kt
git commit -m "feat(server): resize the listing photo in the published e-mail" -m "Refs #223"
```

---

### Task 3: The match thumbnail mailets

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchNotificationMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageMatchMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchNotificationMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageMatchMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/CoBrokerageInterestMailetTest.kt`

**Interfaces:**
- Consumes: `MailImages.THUMBNAIL`, `MailImages.imageUrl(...)`, `ImageService`, `mockMailImageService()`.
- Produces: the three mailets take `imageService: ImageService`; `CoBrokerageInterestDigest` is unchanged.

- [ ] **Step 1: Resize the thumbnails in the mailets**

For `MatchNotificationMailet` and `CoBrokerageMatchMailet`, apply the same field rename and the same `image_url` change as Task 2, with `MailImages.THUMBNAIL` instead of `MailImages.COVER`.

For `CoBrokerageInterestMailet` (it has no `ListingImageService` field):
- add `private val imageService: ImageService` as the last constructor parameter;
- change `"image_url" to listing.imageUrl` to
  `"image_url" to MailImages.imageUrl(listing.imageUrl, imageService, MailImages.THUMBNAIL)`;
- add the `ImageService` and `MailImages` imports;
- in the class KDoc, say the e-mail's listing photos are resized through `MailImages`; the digest keeps the raw URL.

- [ ] **Step 2: Update the three tests**

`MatchNotificationMailetTest`:
- rename the `ListingImageService` field and its `cover` / `verify` calls to `listingImageService`;
- add `private val imageService = mockMailImageService()` and pass it last to the constructor;
- the cover for listing `302` now asserts
  `"https://ik.imagekit.io/key/tr:w-160,h-120,f-jpg,q-75/302.jpg"`; the listings without a cover stay `null`.

`CoBrokerageMatchMailetTest`:
- same rename / mock / constructor changes;
- add `doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(listingImageService).cover(any())` to `setUp` (import `ListingImageEntity`);
- in `send`, assert `listingCard()["image_url"]` is
  `"https://ik.imagekit.io/key/tr:w-160,h-120,f-jpg,q-75/7.jpg"`.

`CoBrokerageInterestMailetTest`:
- rename the builder's `imageService` (the `ListingImageService` mock) to `listingImageService`;
- add `private val imageService = mockMailImageService()` and construct `CoBrokerageInterestMailet(mailService, mailBodyResolver, imageService)`;
- add a cover stub to `setUp`: `doReturn(ListingImageEntity(id = 7L, url = "https://cdn.ndopify.com/7.jpg")).whenever(listingImageService).cover(any())`;
- in `send`, assert the e-mail's `listing["image_url"]` is
  `"https://ik.imagekit.io/key/tr:w-160,h-120,f-jpg,q-75/7.jpg"`;
- add a test that the digest keeps the raw URL:

```kotlin
    @Test
    fun `the digest keeps the raw cover URL`() {
        val digest = builder.build(listOf(match))

        assertEquals("https://cdn.ndopify.com/7.jpg", digest.listings.single().imageUrl)
    }
```

- [ ] **Step 3: Run the three tests**

Run: `mvn test -Dtest=MatchNotificationMailetTest,CoBrokerageMatchMailetTest,CoBrokerageInterestMailetTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail
git commit -m "feat(server): resize the listing thumbnails in the match e-mails" -m "Refs #223"
```

---

### Task 4: The match cover mailets

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitAgentNudgeMailet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchProposedMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchInterestMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitReminderMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail/MatchVisitAgentNudgeMailetTest.kt`

**Interfaces:**
- Consumes: `MailImages.COVER`, `MailImages.imageUrl(...)`, `ImageService`, `mockMailImageService()`.
- Produces: the four mailets take `imageService: ImageService` after `listingImageService`.

- [ ] **Step 1: Resize the covers in the mailets**

Apply the Task 2 pattern to each of the four mailets: rename the `ListingImageService` field to `listingImageService`, add `private val imageService: ImageService`, and replace every
`imageService.cover(listing)?.url?.ifBlank { null }` with
`MailImages.imageUrl(listingImageService.cover(listing)?.url, imageService, MailImages.COVER)`.
Add the `ImageService` / `MailImages` imports and the KDoc line from Task 2.

- [ ] **Step 2: Update the four tests**

Apply the Task 2 test pattern to each: rename the `ListingImageService` field and its `cover` calls to `listingImageService`, add `private val imageService = mockMailImageService()` and pass it last to the constructor, and change every expected cover `image_url` from `https://cdn.ndopify.com/7.jpg` to
`https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg`.
`MatchProposedMailetTest`'s no-cover test keeps asserting `null`.

- [ ] **Step 3: Run the four tests**

Run: `mvn test -Dtest=MatchProposedMailetTest,MatchInterestMailetTest,MatchVisitReminderMailetTest,MatchVisitAgentNudgeMailetTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/mail \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/mail
git commit -m "feat(server): resize the listing cover in the match e-mails" -m "Refs #223"
```

---

### Task 5: The visit mailets

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitRequestedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitFeedbackMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitRequestedMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitConfirmedMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitReminderMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCompletedMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitFeedbackMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitCancelledMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitExpiredMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/VisitNoShowMailetTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail/CoBrokerageVisitMailetTest.kt`

**Interfaces:**
- Consumes: `MailImages.COVER`, `MailImages.imageUrl(...)`, `ImageService`, `mockMailImageService()`.
- Produces: the nine mailets take `imageService: ImageService` after `listingImageService`.

- [ ] **Step 1: Resize the covers in the mailets**

Apply the Task 2 pattern to each of the nine mailets. `VisitReminderMailet` has two `val imageUrl = imageService.cover(listing)?.url?.ifBlank { null }` (customer and agent copies): replace both with the `MailImages.imageUrl(...)` expression.

- [ ] **Step 2: Update the nine tests**

Apply the Task 2 test pattern to each: rename the `ListingImageService` field and its `cover` calls to `listingImageService`, add `private val imageService = mockMailImageService()` and pass it last to the constructor, and change every expected cover `image_url` from `https://cdn.ndopify.com/7.jpg` to
`https://ik.imagekit.io/key/tr:w-1200,h-800,f-jpg,q-75/7.jpg`.
`VisitConfirmedMailetTest` has two contexts (customer and agent): change both.
Add the missing assertions:
- `VisitReminderMailetTest`: `assertEquals(COVER_URL, customerContext["image_url"])` in `sendCustomer…` and `assertEquals(COVER_URL, agentContext["image_url"])` in `sendAgent…`.
- `VisitExpiredMailetTest`: `assertEquals(COVER_URL, customerContext["image_url"])` in `send`.

- [ ] **Step 3: Run the nine tests**

Run: `mvn test -Dtest='Visit*MailetTest,CoBrokerageVisitMailetTest'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/mail \
  modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/mail
git commit -m "feat(server): resize the listing cover in the visit e-mails" -m "Refs #223"
```

---

### Task 6: Documentation, the no-raw-URL check and the full build

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module rule that e-mail listing images always go through `MailImages`.

- [ ] **Step 1: Document `MailImages`**

In the `platform/mail` bullet of `modules/ndopify-server/CLAUDE.md`, add: e-mail listing images always go through `MailImages` (`COVER` 1200×800, `THUMBNAIL` 160×120, JPEG quality 75), never the cover's original URL; a missing cover still sends no image.

- [ ] **Step 2: Check that no mailet passes a raw cover URL**

Run:

```bash
grep -rn "cover(listing)?.url?.ifBlank\|to listing.imageUrl" \
  modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/*/server/mail/
```

Expected: no match. (`CoBrokerageInterestDigestBuilder` is outside `mail/` and keeps the raw URL on purpose.)

- [ ] **Step 3: Run the full module build**

Run: `mvn verify` (from `modules/ndopify-server`, with local MySQL running)
Expected: BUILD SUCCESS — ktlint, every test and the JaCoCo gate green.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(server): document the e-mail image sizes" -m "Refs #223"
```
