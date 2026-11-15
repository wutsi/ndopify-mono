# Telegram Listing Published Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the agent over Telegram that its listing is published, with the listing's hero image uploaded to Telegram, and the e-mail as the fallback.

**Architecture:** `ListingPublishedEvent` still reaches `ListingEventListener` (async, after commit), which calls `ListingService.sendPublishedNotification`. That method delegates to a new `ListingPublishedNotifier`: a linked agent (`AgentTelegramService.chatId`) gets `ListingPublishedBotlet`, which downloads the listing's cover image from storage and uploads it to Telegram as a photo whose caption is the message and whose keyboard is the `Voir l'annonce` button; an unlinked agent gets `ListingPublishedMailet`, and a failed text send falls back to it. A failed image download or photo upload only drops the image: the text is sent anyway, so the notification is never lost.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/158 (the story is the spec; it was groomed with the hero-image decision).

## Global Constraints

- **Precondition: #136 is merged.** This plan consumes its code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, callbackData, url)`, `AgentTelegramService.chatId(agentId)`, and the notifier/botlet convention (`docs/superpowers/plans/2026-11-15-telegram-assignment-notification.md`). #136 is on `feat/telegram-assignment-notification`; do not start before it lands on `main`. If it named anything differently, adapt the calls, not the design.
- Every change is under `modules/ndopify-server/`; no new Maven dependency (the multipart body uses the JDK's `java.net.http`).
- **Managed:** the image is the one `ListingImageService.cover(listing)` returns — the listing's `heroImageId` when that image is `VERIFIED` (the publisher sets it from the model's choice, else the highest-quality/largest verified photo), otherwise the oldest verified photo.
- **Uploaded, never fetched by Telegram:** the server reads the image bytes from its storage (`StorageService.get(path, outputStream)`), then posts a `multipart/form-data` `sendPhoto`. The image's public URL is never given to Telegram as a `photo` URL.
- **One message:** the photo carries the text as its caption and the `Voir l'annonce` button as its inline keyboard. The caption is under Telegram's 1024-character limit by construction (title ≤ 100 characters).
- **The photo never costs the notification:** a failed download or `sendPhoto` logs and falls back to a plain `sendMessage` with the same text and button; only a failed text send reaches the notifier's e-mail fallback.
- The bot's text is French; code, comments, logs and docs are English.
- No database migration, no `ndopify-dto` nor `ndopify-web-core` change, no UI page.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- Never log, throw or report the bot token or the Telegram URL.
- The `qa` profile must make no Telegram call; every test reaches only a mocked `TelegramClient`.
- KDoc on classes and public methods; a service method's KDoc lists its error codes in the order they're checked.
- One module per commit (only `ndopify-server` here); conventional commit messages.

## Review Focus

- No cover image (no verified photo): a plain text message with the button, no `sendPhoto` call.
- The storage read fails (missing file, S3 error): the text message is still sent; no exception escapes.
- `sendPhoto` fails (Telegram refuses the upload): the text message is still sent; no exception escapes.
- The text send fails too: the notifier falls back to the e-mail; the failure is logged without the token nor the URL.
- A tenant without `publicPortalUrl`, or a listing without a `url`: no button, and no empty keyboard.
- A listing published after `MAX_RETRIES` without a title: `Annonce: Bien n° <id>`.
- No neighbourhood: the `Quartier:` line is omitted.
- The e-mail for an unlinked agent is unchanged, and the customer still receives nothing.

---

### Task 1: Multipart POST in `Http`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/Http.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/HttpFile.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/util/http/HttpMultipartTest.kt`

**Interfaces:**
- Consumes: the existing `Http` (`client`, `objectMapper`, `readTimeout`, private `handle`), `HttpException`.
- Produces:
  - `data class HttpFile(val fieldName: String, val filename: String, val contentType: String, val content: ByteArray)`
  - `Http.postMultipart(uri: String, fields: Map<String, Any?>, file: HttpFile, responseType: Class<T>, headers: Map<String, String?>): T?` — a `String` field is written as-is; every other value is Jackson-serialized (so a `Long` becomes `1000` and a `Map` its JSON).

- [ ] **Step 1: Write the failing multipart test**

Create a real local server (the pattern of `UrlUtilsTest`), capture the request, and assert the wire format:

```kotlin
class HttpMultipartTest {
    private lateinit var server: HttpServer
    private var contentType: String? = null
    private var body: ByteArray = ByteArray(0)

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/multipart") { exchange ->
            contentType = exchange.requestHeaders.getFirst("Content-Type")
            body = exchange.requestBody.readBytes()
            val response = """{"ok":true}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
    }

    @AfterTest
    fun tearDown() = server.stop(0)

    @Test
    fun `postMultipart sends its fields and its file`() {
        val http = Http(HttpClient.newBuilder().build(), JsonMapper())

        val response = http.postMultipart(
            "http://127.0.0.1:${server.address.port}/multipart",
            mapOf(
                "chat_id" to 1000L,
                "caption" to "En ligne",
                "reply_markup" to mapOf("inline_keyboard" to emptyList<List<Any>>()),
            ),
            HttpFile("photo", "1.jpg", "image/jpeg", byteArrayOf(1, 2, 3)),
            Map::class.java,
            emptyMap(),
        )

        assertTrue(contentType!!.startsWith("multipart/form-data; boundary="))
        val text = String(body, Charsets.UTF_8)
        assertTrue(text.contains("""name="chat_id""""))
        assertTrue(text.contains("1000"))
        assertTrue(text.contains("""name="caption""""))
        assertTrue(text.contains("En ligne"))
        assertTrue(text.contains("""name="reply_markup""""))
        assertTrue(text.contains("""{"inline_keyboard":[]}"""))
        assertTrue(text.contains("""name="photo"; filename="1.jpg""""))
        assertTrue(text.contains("Content-Type: image/jpeg"))
        assertTrue(text.contains(String(byteArrayOf(1, 2, 3), Charsets.ISO_8859_1)))
        assertEquals(true, response!!["ok"])
    }

    @Test
    fun `postMultipart throws on a non-2xx answer`() {
        server.createContext("/error") { exchange ->
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        val http = Http(HttpClient.newBuilder().build(), JsonMapper())

        assertFailsWith<HttpException> {
            http.postMultipart(
                "http://127.0.0.1:${server.address.port}/error",
                emptyMap(),
                HttpFile("photo", "1.jpg", "image/jpeg", byteArrayOf(1)),
                Map::class.java,
                emptyMap(),
            )
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=HttpMultipartTest`
Expected: FAIL — `postMultipart` and `HttpFile` are unresolved.

- [ ] **Step 3: Implement `HttpFile` and `Http.postMultipart`**

`HttpFile`: the four fields above, in `platform/util/http/HttpFile.kt`, KDoc "A file of a `multipart/form-data` request".

`Http.postMultipart`: build the body in a `ByteArrayOutputStream` with a random boundary (`"----ndopify-" + UUID.randomUUID()`): for each field, `--<boundary>`, `Content-Disposition: form-data; name="<name>"`, a blank line, the value (String as-is, else `objectMapper.writeValueAsString(value)`), then CRLF; then the file part with `Content-Disposition: form-data; name="<fieldName>"; filename="<filename>"`, `Content-Type: <contentType>`, a blank line, the bytes; then `--<boundary>--`. POST with `BodyPublishers.ofByteArray`, the caller's headers, and `Content-Type: multipart/form-data; boundary=<boundary>` (never the JSON one), through the existing `handle`. KDoc: the same response handling as `post`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='HttpMultipartTest,HttpTest'`
Expected: PASS (the existing timeout tests stay green).

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/Http.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/util/http/HttpFile.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/util/http/HttpMultipartTest.kt
git commit -m "feat(http): post multipart requests"
```

---

### Task 2: `TelegramClient.sendPhoto`

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt`

**Interfaces:**
- Consumes: Task 1's `Http.postMultipart(...)` and `HttpFile(...)`; #136's `TelegramButton` and its private `List<TelegramButton>.toPayload()`.
- Produces: `TelegramClient.sendPhoto(chatId: Long, photo: ByteArray, filename: String, contentType: String, caption: String, buttons: List<TelegramButton> = emptyList())` — POSTs to `sendPhoto` with fields `chat_id`, `caption`, `reply_markup` (only when `buttons` isn't empty) and the `photo` file.

- [ ] **Step 1: Write the failing client test**

```kotlin
@Test
fun `sendPhoto posts the image, its caption and its button`() {
    val bytes = byteArrayOf(1, 2, 3)
    doReturn(TelegramMessageResponse(ok = true))
        .whenever(http).postMultipart(
            eq("$base/sendPhoto"),
            eq(
                mapOf(
                    "chat_id" to 123L,
                    "caption" to "En ligne",
                    "reply_markup" to mapOf(
                        "inline_keyboard" to listOf(
                            listOf(mapOf("text" to "Voir l'annonce", "url" to "https://www.ndopify.com/listing")),
                        ),
                    ),
                ),
            ),
            argThat { fieldName == "photo" && filename == "1.jpg" && contentType == "image/jpeg" && content.contentEquals(bytes) },
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )

    client.sendPhoto(
        123L,
        bytes,
        "1.jpg",
        "image/jpeg",
        "En ligne",
        listOf(TelegramButton("Voir l'annonce", url = "https://www.ndopify.com/listing")),
    )

    verify(http).postMultipart(
        eq("$base/sendPhoto"),
        any(),
        argThat { content.contentEquals(bytes) },
        eq(TelegramMessageResponse::class.java),
        eq(emptyMap()),
    )
}
```

`HttpFile` holds a `ByteArray`, whose `equals` is referential: compare with `argThat { content.contentEquals(bytes) }`, never `eq(HttpFile(...))`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: FAIL — `sendPhoto` is unresolved.

- [ ] **Step 3: Implement `sendPhoto`**

```kotlin
open fun sendPhoto(
    chatId: Long,
    photo: ByteArray,
    filename: String,
    contentType: String,
    caption: String,
    buttons: List<TelegramButton> = emptyList(),
) {
    val fields = buildMap<String, Any> {
        put("chat_id", chatId)
        put("caption", caption)
        if (buttons.isNotEmpty()) {
            put("reply_markup", mapOf("inline_keyboard" to listOf(buttons.toPayload())))
        }
    }
    http.postMultipart(
        url("sendPhoto"),
        fields,
        HttpFile("photo", filename, contentType, photo),
        TelegramMessageResponse::class.java,
        emptyMap(),
    ).check()
}
```

KDoc: Telegram's `caption` is capped at 1024 characters and the `photo` is uploaded by us (a `multipart/form-data` file), never a URL Telegram would fetch.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt
git commit -m "feat(telegram): upload a photo with a caption and a button"
```

---

### Task 3: The publication botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt`

**Interfaces:**
- Consumes: Task 2's `sendPhoto`; #136's `sendMessage(chatId, text, buttons)`; `ListingImageService.cover(listing): ListingImageEntity?`; `StorageServiceProvider.get(): StorageService` and `StorageService.get(path: String, os: OutputStream)`; `ListingUrls.publicUrl(tenant, listing): String?`; `TenantService`, `LocationService`, `TenantFormatter` if a formatted value is needed.
- Produces: `ListingPublishedBotlet(tenantService, locationService, imageService, storageProvider, client)` with `send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity, chatId: Long)`.

- [ ] **Step 1: Write the failing botlet tests**

Fixtures mirror `ListingPublishedMailetTest`. The caption is pinned in full:

```kotlin
class ListingPublishedBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val imageService = mock<ListingImageService>()
    private val storageProvider = mock<StorageServiceProvider>()
    private val storage = mock<StorageService>()
    private val client = mock<TelegramClient>()
    private val botlet = ListingPublishedBotlet(
        tenantService,
        locationService,
        imageService,
        storageProvider,
        client,
    )

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", publicPortalUrl = "https://www.ndopify.com/")
    private val listing = ListingEntity(
        id = 1L,
        tenantId = 1L,
        cityId = 2370201L,
        neighborhoodId = 111L,
        title = "Appartement à louer, 3 chambres, Yaounde (Bastos)",
        url = "/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
    )
    private val cover = ListingImageEntity(
        id = 7L,
        listingId = 1L,
        path = "listings/1/7.jpg",
        url = "https://cdn.ndopify.com/7.jpg",
        mimeType = "image/jpeg",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val bytes = byteArrayOf(1, 2, 3)

    private val caption = """
        Bonjour Kim Possible,

        L'annonce du bien de Ray Sponsible est désormais publiée.

        Annonce: Appartement à louer, 3 chambres, Yaounde (Bastos)
        Ville: Yaounde
        Quartier: Bastos

        Prochaine étape : partagez le lien de l'annonce et organisez les visites.
    """.trimIndent()
    private val button = TelegramButton(
        "Voir l'annonce",
        url = "https://www.ndopify.com/yaounde/bastos/listing/1/appartement-a-louer-3-chambres-yaounde-bastos",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
        doReturn(cover).whenever(imageService).cover(listing)
        doReturn(storage).whenever(storageProvider).get()
        doAnswer { invocation -> (invocation.arguments[1] as OutputStream).write(bytes) }
            .whenever(storage).get(eq("listings/1/7.jpg"), any())
    }

    @Test
    fun `the hero image is uploaded with the caption and the button`() {
        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendPhoto(
            eq(1000L),
            argThat { contentEquals(bytes) },
            eq("1.jpg"),
            eq("image/jpeg"),
            eq(caption),
            eq(listOf(button)),
        )
        verify(client, never()).sendMessage(any(), any(), any())
    }

    @Test
    fun `no cover image sends the text only`() {
        doReturn(null).whenever(imageService).cover(listing)

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, listOf(button))
        verify(client, never()).sendPhoto(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `a failed storage read still sends the text`() {
        doThrow(IllegalStateException("no such object"))
            .whenever(storage).get(eq("listings/1/7.jpg"), any())

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, listOf(button))
    }

    @Test
    fun `a refused photo still sends the text`() {
        doThrow(TelegramException("wrong file identifier/HTTP URL specified"))
            .whenever(client).sendPhoto(any(), any(), any(), any(), any(), any())

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, listOf(button))
    }

    @Test
    fun `no public page means no button`() {
        doReturn(tenant.copy(publicPortalUrl = null)).whenever(tenantService).findById(1L)
        doReturn(null).whenever(imageService).cover(listing)

        botlet.send(listing, customer, agent, 1000L)

        verify(client).sendMessage(1000L, caption, emptyList())
    }

    @Test
    fun `a blank title falls back to the listing id`() {
        doReturn(null).whenever(imageService).cover(listing)
        val text = argumentCaptor<String>()

        botlet.send(listing.copy(title = null), customer, agent, 1000L)

        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Annonce: Bien n° 1"))
    }

    @Test
    fun `no neighbourhood omits its line`() {
        doReturn(null).whenever(imageService).cover(listing)
        val text = argumentCaptor<String>()

        botlet.send(listing.copy(neighborhoodId = null), customer, agent, 1000L)

        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("Quartier:"))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest`
Expected: FAIL — `ListingPublishedBotlet` is unresolved.

- [ ] **Step 3: Implement the botlet**

`@Service`, `send(listing, customer, agent, chatId)`, KDoc: mirrors `ListingPublishedMailet`; the image is downloaded from storage and uploaded, not fetched by Telegram; an image failure only drops the photo.

Build the caption, omitting an empty line:

```text
Bonjour <agent.party.fullName()>,

L'annonce du bien de <customer.party.fullName()> est désormais publiée.

Annonce: <title or "Bien n° <id>">
[Ville: <city>]                 (only when it resolves)
[Quartier: <neighbourhood>]     (only when it resolves)

Prochaine étape : partagez le lien de l'annonce et organisez les visites.
```

Then:

```kotlin
val buttons = ListingUrls.publicUrl(tenant, listing)
    ?.let { url -> listOf(TelegramButton("Voir l'annonce", url = url)) }
    ?: emptyList()
val cover = imageService.cover(listing)
if (cover != null && sendPhoto(chatId, cover, caption, buttons)) {
    return
}
client.sendMessage(chatId, caption, buttons)
```

with a private `sendPhoto` that wraps the download **and** the upload in one `try/catch (ex: Exception)`, logs a warning (never the token nor the URL), and returns `false` on any failure. The file is `"${listing.id}.${MimeUtils.getExtensionFromMimeType(cover.mimeType)}"`, the content type `cover.mimeType ?: "image/jpeg"`, the bytes from `storageProvider.get().get(cover.path, ByteArrayOutputStream())`. Inject `ListingImageService` with `@Lazy`: the mailet does, because `ListingService` owns the caller and `ListingImageService` depends on it.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/bot/ListingPublishedBotletTest.kt
git commit -m "feat(listing): announce a publication over Telegram with its hero image"
```

---

### Task 4: Route the publication to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingPublishedNotifier.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt` (constructor and `sendPublishedEmails`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListener.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingPublishedNotifierTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListenerTest.kt`

**Interfaces:**
- Consumes: Task 3's `ListingPublishedBotlet.send(listing, customer, agent, chatId)`; `ListingPublishedMailet.send(listing, customer, agent)`; `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces:
  - `ListingPublishedNotifier(botlet, mailet, telegramService)` with `send(listing: ListingEntity, customer: CustomerEntity, agent: AgentEntity)`.
  - `ListingService.sendPublishedNotification(id: Long): Boolean`, replacing `sendPublishedEmails`.
  - `ListingService`'s constructor takes `publishedNotifier: ListingPublishedNotifier` instead of `publishedMailet: ListingPublishedMailet`.

- [ ] **Step 1: Write the failing notifier test**

```kotlin
class ListingPublishedNotifierTest {
    private val botlet = mock<ListingPublishedBotlet>()
    private val mailet = mock<ListingPublishedMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = ListingPublishedNotifier(botlet, mailet, telegramService)

    private val listing = ListingEntity(id = 1L)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(listing, customer, agent)

        verify(mailet).send(listing, customer, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(listing, customer, agent)

        verify(botlet).send(listing, customer, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed text send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(listing, customer, agent, 1000L)

        notifier.send(listing, customer, agent)

        verify(mailet).send(listing, customer, agent)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=ListingPublishedNotifierTest`
Expected: FAIL — `ListingPublishedNotifier` is unresolved.

- [ ] **Step 3: Implement the notifier**

Same shape as `AssignmentOfferNotifier`: `val chatId = telegramService.chatId(agent.id!!)`; null → `mailet.send(listing, customer, agent)`; else `try { botlet.send(listing, customer, agent, chatId) } catch (ex: Exception) { LOGGER.warn("Telegram publication failed for Listing#${listing.id}, falling back to e-mail", ex); mailet.send(listing, customer, agent) }`. KDoc: the channel is #135's link, the e-mail is the fallback, the photo failure is already handled inside the botlet.

- [ ] **Step 4: Update the failing listener test**

In `ListingEventListenerTest`: rename `sendPublishedEmails(10L)` to `sendPublishedNotification(10L)` in the stubs and verifies, and the test name to `published - the agent is notified, in its tenant`.

- [ ] **Step 5: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='ListingPublishedNotifierTest,ListingEventListenerTest'`
Expected: FAIL — `sendPublishedNotification` is unresolved.

- [ ] **Step 6: Rewire the service and the listener**

`ListingService`: constructor `private val publishedNotifier: ListingPublishedNotifier`; rename `sendPublishedEmails(id: Long): Boolean` to `sendPublishedNotification`; keep its `@Transactional`, its `Boolean` result and its guard (`status != PUBLISHED` → `false`); call `publishedNotifier.send(listing, customerService.findById(listing.mandate.customerId), listing.agent)`. KDoc: Telegram for a linked agent, the e-mail otherwise or as the fallback.

`ListingEventListener.onListingPublished`: call `service.sendPublishedNotification(event.listingId)`; rename the log key `email_sent` to `notification_sent`; KDoc "Notifies the agent of a listing that it's published...".

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='ListingPublishedNotifierTest,ListingEventListenerTest,ListingPublishedMailetTest'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingPublishedNotifier.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/service/ListingService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/service/ListingPublishedNotifierTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/event/ListingEventListenerTest.kt
git commit -m "feat(listing): route a publication to Telegram or e-mail"
```

---

### Task 5: Full-context test of a publication

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramPublishListingEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramPublishListingsJobTest.kt`

**Interfaces:**
- Consumes: the publication job and `ListingPublishedEvent` (as `PublishListingsJobTest` does), the beans from Tasks 3 and 4, `PublishListingsJob.sql`'s fixtures (listing 100, agent 20, tenant 1, `public_portal_url`), and `StorageServiceLocal`.
- Produces: no production code; proves the wiring, the storage read, the upload and the e-mail fallback against MySQL.

- [ ] **Step 1: Write the seed**

`TelegramPublishListingEndpoint.sql` adds what `PublishListingsJob.sql` lacks: a VERIFIED hero image for listing 100 and the agent's link.

```sql
INSERT INTO T_LISTING_IMAGE(id, tenant_id, listing_id, path, url, mime_type, status, quality, width, height) VALUES
    (7, 1, 100, 'listings/100/7.jpg', 'https://cdn.ndopify.com/100.jpg', 'image/jpeg', 'VERIFIED', 80, 1200, 800);

INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing job test**

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/listing/PublishListingsJob.sql",
        "/db/test/telegram/TelegramPublishListingEndpoint.sql",
    ],
)
class TelegramPublishListingsJobTest : BaseEndpointIntegrationTest() {
    @Autowired
    private lateinit var jobs: ListingJobs

    @MockitoBean
    private lateinit var generator: ListingContentGenerator

    @MockitoBean
    private lateinit var client: TelegramClient

    @MockitoBean
    private lateinit var storageProvider: StorageServiceProvider

    @TempDir
    lateinit var dir: File

    @Test
    fun `a publication uploads the hero image to the linked agent`() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val storage = StorageServiceLocal(dir.absolutePath, "/storage", 8080)
        storage.store("listings/100/7.jpg", ByteArrayInputStream(bytes), "image/jpeg")
        doReturn(storage).whenever(storageProvider).get()
        doReturn(ListingContentInfo(title = "Appartement à louer, 3 chambres, Yaoundé (Bastos)"))
            .whenever(generator).generate(any())

        jobs.publish()
        awaitAsyncTasks()

        val caption = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendPhoto(
            eq(1000L),
            argThat { contentEquals(bytes) },
            eq("100.jpg"),
            eq("image/jpeg"),
            caption.capture(),
            buttons.capture(),
        )
        assertTrue(caption.firstValue.contains("Bonjour Kim Possible"))
        assertTrue(caption.firstValue.contains("Annonce: Appartement à louer, 3 chambres, Yaoundé (Bastos)"))
        assertEquals(
            listOf(
                TelegramButton(
                    "Voir l'annonce",
                    url = "https://www.ndopify.com/yaounde/bastos/listing/100/appartement-a-louer-3-chambres-yaounde-bastos",
                ),
            ),
            buttons.firstValue,
        )
        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no publication e-mail for a linked agent")
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramPublishListingsJobTest`
Expected: FAIL — the notifier isn't wired, or the storage mock isn't reached.

- [ ] **Step 4: Fix the wiring until it passes**

Likely gaps: the botlet isn't a bean (missing `@Service`), `ListingService` still takes the mailet, or the `@Lazy` image service isn't proxied. Change production code only for a real gap the test finds.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramPublishListingsJobTest`
Expected: PASS (MySQL running).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramPublishListingEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/listing/server/job/TelegramPublishListingsJobTest.kt
git commit -m "test(telegram): cover the listing publication with its hero image"
```

---

### Task 6: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's documentation for the next notification.

- [ ] **Step 1: Document the channel and the new platform calls**

In `modules/ndopify-server/CLAUDE.md`:
- In the **listing** bullet, replace the `ListingPublishedMailet` sentence with the channel rule: `ListingPublishedNotifier` Telegram-messages a linked agent through `ListingPublishedBotlet` (the cover image downloaded from storage and uploaded as `sendPhoto`, with the text as caption and a `Voir l'annonce` button; a failed image drops only the photo) and e-mails the others, falling back to the e-mail on a failed text send.
- In the **Asynchronous e-mails** list, rename `ListingService.sendPublishedEmails` to `sendPublishedNotification`.
- In the **platform** list, note that `platform/util/http` now has `postMultipart` (a `multipart/form-data` POST, a JSON `post` beside it) and that `TelegramClient` has `sendPhoto` for an uploaded image (never a URL Telegram fetches).
- If #136 didn't already document the `bot/` layering, add it next to `mail/`: a `*Botlet` is the Telegram counterpart of a `*Mailet`; a `*Notifier` picks the channel and owns the e-mail fallback.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(listing): document the Telegram publication notification"
```
