# Listing Hero Image On Telegram Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every Telegram message about a listing carries the listing's hero photo, or falls back to today's text message.

**Architecture:** One `ListingTelegramSender` in `listing/server/bot` owns the photo-or-text decision: it uploads `ListingImageService.cover(listing)` with `TelegramClient.sendPhoto` when a cover exists and the text fits Telegram's caption limit, else it sends the text. The nine impacted botlets replace their `TelegramClient` dependency with this sender and pass the listing the message is about. Wording, buttons and notifiers are unchanged.

**Tech Stack:** Kotlin, Spring Boot, `kotlin.test`, `mockito-kotlin` (`com.nhaarman.mockitokotlin2`), `ListingImageService`, `StorageServiceProvider`, `TelegramClient`. No new dependency.

**Spec:** GitHub issue #204 — https://github.com/wutsi/ndopify-mono/issues/204.

## Global Constraints

- The message's wording, buttons and their order are unchanged; the text becomes the photo's caption as is.
- The caption fits only when `text.codePointCount(0, text.length) <= 1024` (the story's "1024 characters"). Longer text is sent as a text message, never truncated, and the image is not read.
- The image is uploaded from the storage (`StorageService.get` then `TelegramClient.sendPhoto` multipart); its URL is never given to Telegram.
- Photo failures (storage read, Telegram refusing the photo) only log a warning and drop the photo: the text message is still sent. Never log the bot token nor the image URL. A failing **text** message throws `TelegramException`, so the notifier's e-mail fallback still runs.
- `ListingTelegramSender` takes `ListingImageService` with `@Lazy`: `ListingImageService` → `ListingService` → `ListingPublishedNotifier` → `ListingPublishedBotlet` → the sender would be a bean cycle otherwise.
- Only `modules/ndopify-server`. No `*Mailet`, notifier, `ndopify-dto`, `ndopify-web-core`, Partner Central, `src/main/html/`, Flyway nor configuration change.
- Tests use `kotlin.test` assertions; mocks use `com.nhaarman.mockitokotlin2`.
- Each commit is conventional, touches one concern, and carries `Refs #204`.
- Run tests from `modules/ndopify-server`. Endpoint and job tests need the local MySQL (`root`, no password).

**Test migration pattern** (used by Tasks 2–5):

- In a botlet test, `private val client = mock<TelegramClient>()` becomes `private val sender = mock<ListingTelegramSender>()`, and the botlet is built with `sender` where it took `client`.
- Every `verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())` becomes `verify(sender).send(eq(1000L), eq(<listing>), text.capture(), buttons.capture())`, where `<listing>` is the listing the message is about (`listing`, or `match.listing`). Text and button assertions stay exactly as they are.
- Remove the now-unused `TelegramClient` import; add `com.wutsi.ndopify.listing.server.bot.ListingTelegramSender` (tests outside the `listing` package).

## Review Focus

- **Over-long text**: exactly 1024 code points is a photo; 1025 is a text message and the cover is never read. Tests: Task 1.
- **No cover**: same text message as today, with its buttons. Tests: Task 1, and every botlet's existing "no listing title" / optional-field tests stay green. The hero-then-oldest-VERIFIED fallback is already pinned by `ListingImageServiceTest`.
- **Photo failure**: a storage read or a refused photo sends the full text message; a refused text throws. Tests: Task 1.
- **The right listing**: each botlet passes `match.listing` or its `listing` parameter, never another listing (ex: the mandate's). Tests: Tasks 2–5.
- **The Spring wiring**: the real sender still produces the publication's photo through `TelegramClient.sendPhoto`. Test: `TelegramPublishListingsJobTest`, unchanged, run in Task 6.

---

### Task 1: The shared photo-or-text sender

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSender.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSenderTest.kt`

**Interfaces:**
- Produces: `ListingTelegramSender.send(chatId: Long, listing: ListingEntity, text: String, buttons: List<TelegramButton> = emptyList())`; `companion object { const val CAPTION_MAX_LENGTH = 1024 }`.
- Consumes: `ListingImageService.cover(listing): ListingImageEntity?`; `StorageServiceProvider.get(): StorageService` (`get(path, os)`); `TelegramClient.sendPhoto(chatId, photo, filename, contentType, caption, buttons)` and `sendMessage(chatId, text, buttons)`.

- [ ] **Step 1: Write the failing sender test**

Create `ListingTelegramSenderTest.kt` with:

```kotlin
package com.wutsi.ndopify.listing.server.bot

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.wutsi.ndopify.listing.server.domain.ListingEntity
import com.wutsi.ndopify.listing.server.domain.ListingImageEntity
import com.wutsi.ndopify.listing.server.service.ListingImageService
import com.wutsi.ndopify.platform.storage.StorageService
import com.wutsi.ndopify.platform.storage.StorageServiceProvider
import com.wutsi.ndopify.platform.telegram.TelegramClient
import com.wutsi.ndopify.platform.telegram.TelegramException
import com.wutsi.ndopify.platform.telegram.model.TelegramButton
import java.io.OutputStream
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ListingTelegramSenderTest {
    private val imageService = mock<ListingImageService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val client = mock<TelegramClient>()
    private val sender = ListingTelegramSender(imageService, storageProvider, client)

    private val listing = ListingEntity(id = 101L, tenantId = 1L, title = "Nice apartment")
    private val cover = ListingImageEntity(
        id = 7L,
        listingId = 101L,
        path = "listings/101/7.jpg",
        url = "https://cdn.ndopify.com/7.jpg",
        mimeType = "image/jpeg",
    )
    private val bytes = byteArrayOf(1, 2, 3)
    private val buttons = listOf(TelegramButton("Voir l'annonce", url = "https://www.ndopify.com/listing/101"))

    @BeforeTest
    fun setUp() {
        doReturn(storage).whenever(storageProvider).get()
        doReturn(cover).whenever(imageService).cover(listing)
        doAnswer { invocation -> (invocation.arguments[1] as OutputStream).write(bytes) }
            .whenever(storage).get(eq("listings/101/7.jpg"), any())
    }

    @Test
    fun `a cover is uploaded as the photo with the text and the buttons`() {
        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(client).sendPhoto(
            eq(1000L),
            argThat { contentEquals(bytes) },
            eq("101.jpg"),
            eq("image/jpeg"),
            eq("Nice apartment, 2 chambres"),
            eq(buttons),
        )
        verify(client, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `no cover sends the text`() {
        doReturn(null).whenever(imageService).cover(listing)

        sender.send(1000L, listing, "Nice apartment, 2 chambres", buttons)

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", buttons)
        verify(client, never()).sendPhoto(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a text over 1024 characters is sent as text without reading the image`() {
        val text = "x".repeat(1025)

        sender.send(1000L, listing, text, buttons)

        verify(client).sendMessage(1000L, text, buttons)
        verify(imageService, never()).cover(any())
    }

    @Test
    fun `a text of exactly 1024 characters fits the caption`() {
        val text = "x".repeat(1024)

        sender.send(1000L, listing, text, buttons)

        verify(client).sendPhoto(eq(1000L), any(), eq("101.jpg"), eq("image/jpeg"), eq(text), eq(buttons))
    }

    @Test
    fun `a storage failure sends the text`() {
        doThrow(IllegalStateException("no such object")).whenever(storage).get(eq("listings/101/7.jpg"), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
    }

    @Test
    fun `a refused photo sends the text`() {
        doThrow(TelegramException("wrong file identifier/HTTP URL specified"))
            .whenever(client).sendPhoto(any(), any(), any(), any(), any(), any())

        sender.send(1000L, listing, "Nice apartment, 2 chambres")

        verify(client).sendMessage(1000L, "Nice apartment, 2 chambres", emptyList())
    }

    @Test
    fun `a refused text still throws`() {
        doReturn(null).whenever(imageService).cover(listing)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(client).sendMessage(any(), any(), any())

        assertFailsWith<TelegramException> {
            sender.send(1000L, listing, "Nice apartment, 2 chambres")
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingTelegramSenderTest`
Expected: COMPILATION ERROR — `ListingTelegramSender` doesn't exist yet.

- [ ] **Step 3: Implement `ListingTelegramSender`**

Create the class in `listing/server/bot`:

```kotlin
@Service
class ListingTelegramSender(
    @Lazy private val imageService: ListingImageService,
    private val storageProvider: StorageServiceProvider,
    private val client: TelegramClient,
) {
    companion object {
        const val CAPTION_MAX_LENGTH = 1024
        private val LOGGER = LoggerFactory.getLogger(ListingTelegramSender::class.java)
    }

    fun send(chatId: Long, listing: ListingEntity, text: String, buttons: List<TelegramButton> = emptyList()) {
        if (text.codePointCount(0, text.length) <= CAPTION_MAX_LENGTH) {
            val cover = imageService.cover(listing)
            if (cover != null && sendPhoto(chatId, listing, cover, text, buttons)) {
                return
            }
        }
        client.sendMessage(chatId, text, buttons)
    }

    private fun sendPhoto(
        chatId: Long,
        listing: ListingEntity,
        cover: ListingImageEntity,
        text: String,
        buttons: List<TelegramButton>,
    ): Boolean {
        return try {
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
            true
        } catch (ex: Exception) {
            LOGGER.warn("Telegram photo failed for Listing#${listing.id} (image ${cover.id}), sending the text only", ex)
            false
        }
    }
}
```

The KDoc states: the photo-or-text decision, the caption limit, that a photo failure only drops the photo, that a text failure throws, that `imageService` is lazy because `ListingService` owns this chain, and that neither the token nor the image URL is ever logged.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingTelegramSenderTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSender.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingTelegramSenderTest.kt
git commit -m "feat(listing): send a listing message as its photo when it fits" -m "Refs #204"
```

---

### Task 2: The publication botlet uses the sender

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt`

**Interfaces:**
- Consumes: `ListingTelegramSender.send(chatId, listing, text, buttons)` from Task 1.
- Produces: `ListingPublishedBotlet.send(listing, customer, agent, chatId)` unchanged; it delegates the send to the sender.

- [ ] **Step 1: Update the failing test**

In `ListingPublishedBotletTest`:
- Replace the `imageService`, `storageProvider`, `storage` and `client` mocks with `private val sender = mock<ListingTelegramSender>()`; build the botlet as `ListingPublishedBotlet(tenantService, locationService, sender)`.
- Delete the `cover` and `bytes` fixtures and their `setUp` stubs.
- Delete the four photo tests: `the hero image is uploaded with the caption and the button`, `no cover image sends the text only`, `a failed storage read still sends the text`, `a refused photo still sends the text` — Task 1 covers them.
- Replace the remaining verifications:
  - `verify(client).sendPhoto(…, eq(caption), eq(listOf(button)))` and its `verify(client, never()).sendMessage(…))` become `verify(sender).send(1000L, listing, caption, listOf(button))`.
  - `verify(client).sendMessage(1000L, caption, listOf(button))` becomes `verify(sender).send(1000L, listing, caption, listOf(button))`.
  - `verify(client).sendMessage(1000L, caption, emptyList())` becomes `verify(sender).send(1000L, listing, caption, emptyList())`.
  - The blank-title and no-neighbourhood tests capture the text as today, with `verify(sender).send(eq(1000L), any(), text.capture(), any())`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest`
Expected: COMPILATION ERROR — the botlet still takes `ListingImageService`, `StorageServiceProvider` and `TelegramClient`.

- [ ] **Step 3: Implement the delegation**

In `ListingPublishedBotlet`:
- Constructor: `tenantService`, `locationService`, `private val sender: ListingTelegramSender`.
- Remove the `LOGGER`, `imageService`, `storageProvider`, `client`, `sendPhoto`, and the imports they used (`@Lazy`, `StorageServiceProvider`, `ByteArrayOutputStream`, `MimeUtils`, `ListingImageEntity`, `LoggerFactory`, `TelegramClient`, `TelegramException` if unused).
- Replace the cover block and the fallback with `sender.send(chatId, listing, caption, buttons)`.
- Update the KDoc: the photo-or-text decision is `ListingTelegramSender`'s; this botlet only builds the caption and the button.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt
git commit -m "feat(listing): delegate the publication message to the listing sender" -m "Refs #204"
```

---

### Task 3: The match botlets

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotletTest.kt`

**Interfaces:**
- Consumes: `ListingTelegramSender.send(chatId, listing, text, buttons)`.
- Produces: the three `send` signatures unchanged.

- [ ] **Step 1: Update the failing tests (Test migration pattern)**

- `MatchInterestBotletTest`: mock `ListingTelegramSender`; verify `send(eq(1000L), eq(listing), text, buttons)`.
- `MatchAnsweredBotletTest`: mock the sender; its `text()` and `buttons()` helpers verify `sender.send(eq(1000L), eq(match.listing), …)`.
- `MatchVisitAgentNudgeBotletTest`: mock the sender; verify `send(eq(1000L), eq(match.listing), text, buttons)`.

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestBotletTest,MatchAnsweredBotletTest,MatchVisitAgentNudgeBotletTest`
Expected: COMPILATION ERROR — the botlets still take `TelegramClient`.

- [ ] **Step 2: Implement the delegation**

In each botlet, replace the `client: TelegramClient` constructor parameter with `private val sender: ListingTelegramSender`, and:
- `MatchInterestBotlet`: `sender.send(chatId, listing, text(customer, listing), listOfNotNull(whatsapp, mandateButton))`.
- `MatchAnsweredBotlet`: `sender.send(chatId, match.listing, text(match, customer, city, formatter), buttons)` — keep the existing button expression.
- `MatchVisitAgentNudgeBotlet`: `sender.send(chatId, match.listing, text(match, customer), buttons)`.

Update each KDoc sentence that names the photo/text behavior: the sender decides; this botlet builds the text and buttons.

- [ ] **Step 3: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=MatchInterestBotletTest,MatchAnsweredBotletTest,MatchVisitAgentNudgeBotletTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotlet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotlet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchInterestBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchAnsweredBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/match/server/bot/MatchVisitAgentNudgeBotletTest.kt
git commit -m "feat(match): send match messages as the listing's photo" -m "Refs #204"
```

---

### Task 4: The visit botlets

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt`

**Interfaces:**
- Consumes: `ListingTelegramSender.send(chatId, listing, text, buttons)`.
- Produces: the four `send` signatures unchanged.

- [ ] **Step 1: Update the failing tests (Test migration pattern)**

Each test mocks `ListingTelegramSender` and verifies `sender.send(eq(1000L), eq(listing), text, buttons)`; text and button assertions stay.

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest,VisitReminderBotletTest,VisitFeedbackBotletTest,VisitCancelledBotletTest`
Expected: COMPILATION ERROR — the botlets still take `TelegramClient`.

- [ ] **Step 2: Implement the delegation**

In each botlet, replace `client: TelegramClient` with `private val sender: ListingTelegramSender` and call `sender.send(chatId, listing, <text>, <buttons>)` with the existing text and button expressions. Update each KDoc sentence that names the send.

- [ ] **Step 3: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest,VisitReminderBotletTest,VisitFeedbackBotletTest,VisitCancelledBotletTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotlet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotlet.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitReminderBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitFeedbackBotletTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitCancelledBotletTest.kt
git commit -m "feat(visit): send visit messages as the listing's photo" -m "Refs #204"
```

---

### Task 5: The co-brokerage visit botlet

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt`

**Interfaces:**
- Consumes: `ListingTelegramSender.send(chatId, listing, text, buttons)`.
- Produces: the four public methods unchanged; all four messages share the one private `send`.

- [ ] **Step 1: Update the failing test (Test migration pattern)**

Mock `ListingTelegramSender`; the class's verifications (`verify(client).sendMessage(eq(1000L), text.capture(), any())` and the button captures) become `verify(sender).send(eq(1000L), eq(listing), text.capture(), any())` and `verify(sender).send(eq(1000L), eq(listing), any(), buttons.capture())`. The four exact-text assertions and the privacy assertions stay.

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest`
Expected: COMPILATION ERROR — the botlet still takes `TelegramClient`.

- [ ] **Step 2: Implement the delegation**

Replace `client: TelegramClient` with `private val sender: ListingTelegramSender`; in the private `send`, call `sender.send(chatId, listing, lines.joinToString("\n"), <the existing button list>)`. Update the KDoc.

- [ ] **Step 3: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=CoBrokerageVisitBotletTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/CoBrokerageVisitBotletTest.kt
git commit -m "feat(visit): send the co-brokerage visit message as the listing's photo" -m "Refs #204"
```

---

### Task 6: Full verification

**Files:** none.

- [ ] **Step 1: Run the whole module's checks**

Run: `cd modules/ndopify-server && mvn verify`
Expected: `BUILD SUCCESS` — ktlint clean, all unit and endpoint tests green (including `TelegramPublishListingsJobTest`, which still expects a photo through the real sender), JaCoCo gate met.

- [ ] **Step 2: Confirm the impacted botlets no longer send directly**

Run: `grep -rn "client.sendMessage\|sendPhoto" modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/{match,visit,listing}/server/bot/`
Expected: matches only in `ListingTelegramSender.kt` (`sendPhoto`); `ListingPublishedBotlet` has neither.

- [ ] **Step 3: Confirm only the expected files changed**

Run: `git status --short` and `git diff --stat origin/main`
Expected: `ListingTelegramSender` + its test, the 9 botlets and their 9 tests, and nothing else. No `*Mailet`, notifier, DTO, web-core, `src/main/html/`, Flyway, `pom.xml` nor configuration file.

- [ ] **Step 4: Report**

Report the `mvn verify` result and the commits. Do not push: the story continues with `push <story-id>`.
