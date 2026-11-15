# Telegram Bot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `ndopify-server` receive Telegram updates and answer `OK`, over long polling locally and a webhook in TEST/PROD, with the bot's health in the core group.

**Architecture:** A thin `TelegramClient` over the existing `platform/util/http/Http` (JDK `HttpClient`, Jackson 3) exposes only the five Bot API calls the story needs. A `TelegramUpdateHandler` answers every message with `OK`, and two transports feed it: a virtual-thread long poller locally, a secret-protected public webhook in TEST/PROD. Spring configuration wires the client, handler, transports and a cached `getMe` health indicator; all values live under `ndopify.telegram.*`.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), ktlint, JUnit 5 + `kotlin.test` + mockito-kotlin.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/134

## Global Constraints

- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- `TELEGRAM_TOKEN` is required: `application.yml` reads `${TELEGRAM_TOKEN}` with no default. There is no disabled mode.
- Local transport is long polling; TEST/PROD transport is the webhook at `${SERVER_URL}/telegram/webhook`.
- The webhook only accepts requests whose `X-Telegram-Bot-Api-Secret-Token` header equals `TELEGRAM_WEBHOOK_SECRET`.
- The bot's reply text is exactly `OK`.
- Never log, throw or report the bot token: the token is part of the Telegram URL, so never log that URL and never put it in an exception or a health detail.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- No `ndopify-dto`, `ndopify-web-core` or database change.
- KDoc on classes and public methods; code, comments and logs in English.

## Review Focus

- A missing or wrong secret header must return 403 and send nothing (Task 4).
- An update that is not a `message` must be ignored without sending (Task 2, Task 4).
- The token must never appear in an exception message or a health detail (Task 1, Task 5).
- The `qa` profile must never poll or call Telegram (Task 1, Task 3, Task 6).
- A blank webhook URL or secret in webhook mode must fail startup, but a transient `setWebhook` failure must not (Task 4).

---

### Task 1: Telegram models, client and configuration

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/model/TelegramModels.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramException.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Modify: `modules/ndopify-server/src/main/resources/application.yml`
- Modify: `modules/ndopify-server/src/main/resources/application-test.yml`
- Modify: `modules/ndopify-server/src/main/resources/application-prod.yml`
- Modify: `modules/ndopify-server/src/test/resources/application-qa.yml`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/ResetDBTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/momo/mtn/MtnCollectionProductTest.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/momo/mtn/impl/MtnUserProviderSandboxTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt`

**Interfaces:**
- Produces:
  - `open class TelegramClient(token: String, http: Http)` with `open fun sendMessage(chatId: Long, text: String)`,
    `open fun getMe(): TelegramUser`, `open fun getUpdates(offset: Long?, timeoutSeconds: Int): List<TelegramUpdate>`,
    `open fun setWebhook(url: String, secret: String)` and `open fun deleteWebhook()`.
  - `class TelegramException(message: String) : RuntimeException(message)`.
  - Models in `platform/telegram/model`: `TelegramUpdate(updateId: Long, message: TelegramMessage?)`,
    `TelegramMessage(messageId: Long, chat: TelegramChat, text: String?)`, `TelegramChat(id: Long)`,
    `TelegramUser(id: Long, isBot: Boolean, firstName: String, username: String?)`, plus the response envelopes
    `TelegramUserResponse`, `TelegramUpdatesResponse`, `TelegramMessageResponse`, `TelegramBooleanResponse`.
  - `TelegramClient` bean in `TelegramConfiguration`.

- [ ] **Step 1: Write the failing client test**

Create `TelegramClientTest.kt` with a mocked `Http`. Use `private val base = "https://api.telegram.org/bot123:token"`.

```kotlin
class TelegramClientTest {
    private val http = mock<Http>()
    private val client = TelegramClient("123:token", http)
    private val base = "https://api.telegram.org/bot123:token"

    @Test
    fun sendMessage() {
        doReturn(TelegramMessageResponse(ok = true))
            .whenever(http).post(
                eq("$base/sendMessage"),
                eq(mapOf("chat_id" to 123L, "text" to "OK")),
                eq(TelegramMessageResponse::class.java),
                eq(emptyMap()),
            )

        client.sendMessage(123L, "OK")

        verify(http).post(
            eq("$base/sendMessage"),
            eq(mapOf("chat_id" to 123L, "text" to "OK")),
            eq(TelegramMessageResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun getMe() {
        doReturn(TelegramUserResponse(ok = true, result = TelegramUser(id = 1L, username = "ndopify_bot")))
            .whenever(http).get(eq("$base/getMe"), eq(TelegramUserResponse::class.java), eq(emptyMap()))

        assertEquals("ndopify_bot", client.getMe().username)
    }

    @Test
    fun getUpdates() {
        doReturn(
            TelegramUpdatesResponse(
                ok = true,
                result = listOf(TelegramUpdate(updateId = 10L), TelegramUpdate(updateId = 11L)),
            ),
        ).whenever(http).get(
            eq("$base/getUpdates?offset=9&timeout=20"),
            eq(TelegramUpdatesResponse::class.java),
            eq(emptyMap()),
        )

        assertEquals(listOf(10L, 11L), client.getUpdates(9L, 20).map { it.updateId })
    }

    @Test
    fun setWebhook() {
        doReturn(TelegramBooleanResponse(ok = true))
            .whenever(http).post(
                eq("$base/setWebhook"),
                eq(mapOf("url" to "https://host/telegram/webhook", "secret_token" to "secret")),
                eq(TelegramBooleanResponse::class.java),
                eq(emptyMap()),
            )

        client.setWebhook("https://host/telegram/webhook", "secret")

        verify(http).post(
            eq("$base/setWebhook"),
            eq(mapOf("url" to "https://host/telegram/webhook", "secret_token" to "secret")),
            eq(TelegramBooleanResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `deleteWebhook posts an empty payload`() {
        doReturn(TelegramBooleanResponse(ok = true))
            .whenever(http).post(
                eq("$base/deleteWebhook"),
                eq(emptyMap<String, Any>()),
                eq(TelegramBooleanResponse::class.java),
                eq(emptyMap()),
            )

        client.deleteWebhook()

        verify(http).post(
            eq("$base/deleteWebhook"),
            eq(emptyMap<String, Any>()),
            eq(TelegramBooleanResponse::class.java),
            eq(emptyMap()),
        )
    }

    @Test
    fun `a refused call throws the description without the token`() {
        doReturn(TelegramUserResponse(ok = false, description = "Unauthorized"))
            .whenever(http).get(eq("$base/getMe"), eq(TelegramUserResponse::class.java), eq(emptyMap()))

        val ex = assertFailsWith<TelegramException> { client.getMe() }

        assertEquals("Unauthorized", ex.message)
        assertFalse(ex.message!!.contains("123:token"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: FAIL — `TelegramClient`, `TelegramException` and the model classes are unresolved.

- [ ] **Step 3: Implement the models, exception and client**

In `TelegramModels.kt`, follow the existing model style (`MtnBasicUserInfoResponse`): Kotlin data classes with `@get:JsonProperty` snake_case names and defaults. Define:

```kotlin
interface TelegramResponse {
    val ok: Boolean
    val description: String?
}
```

`TelegramUpdate` has `updateId` (`update_id`) and `message`; `TelegramMessage` has `messageId` (`message_id`), `chat` and `text`;
`TelegramChat` has `id`; `TelegramUser` has `id`, `isBot` (`is_bot`), `firstName` (`first_name`) and `username`.
The four response classes implement `TelegramResponse` with `ok`, `result` and `description`.

`TelegramException` is a `RuntimeException` whose message is the Telegram `description`.

`TelegramClient` keeps `token` and `http`, builds `url(path) = "https://api.telegram.org/bot$token/$path"`, and has a private
`<T : TelegramResponse> T.check()` that throws `TelegramException(description ?: "Telegram API error")` when `!ok`.
`getMe` and `getUpdates` call `http.get(...)`; the three write methods call `http.post(...)` with the exact payloads the test
asserts. `getUpdates` builds `"getUpdates?offset=${offset ?: ""}&timeout=$timeoutSeconds"` and returns `result ?: emptyList()`.
A null response body throws `TelegramException("Empty response from Telegram")`. No exception includes the URL.

- [ ] **Step 4: Implement `TelegramConfiguration`**

Mirror `MoMoConfiguration`'s HTTP setup:

```kotlin
@Configuration
open class TelegramConfiguration(
    @param:Value("\${ndopify.telegram.token}") private val token: String,
    private val objectMapper: JsonMapper,
    private val httpClientSettings: HttpClientSettings,
) {
    init {
        check(token.isNotBlank()) { "ndopify.telegram.token is required" }
    }

    @Bean
    open fun telegramClient(): TelegramClient = TelegramClient(token, telegramHttp())

    private fun telegramHttp() = Http(
        client = JdkHttpClientBuilder()
            .withCustomizer { builder -> builder.version(HttpClient.Version.HTTP_1_1) }
            .build(httpClientSettings.withReadTimeout(null)),
        objectMapper = objectMapper,
        readTimeout = httpClientSettings.readTimeout(),
    )
}
```

The 20-second long poll stays under the 30-second `spring.http.clients.read-timeout` from `application.yml`.

- [ ] **Step 5: Add the configuration properties and the test token**

Under `ndopify:` in `application.yml`, add:

```yaml
    telegram:
        token: ${TELEGRAM_TOKEN}           # No default: the server doesn't start without it
        transport: polling                  # polling (local) | webhook (TEST/PROD)
        webhook:
            url: ""                         # ${SERVER_URL}/telegram/webhook in TEST/PROD
            secret: ""                      # TELEGRAM_WEBHOOK_SECRET in TEST/PROD
        polling:
            enabled: true
            timeout-seconds: 20
        health:
            up-ttl-minutes: 5
            down-ttl-minutes: 1
```

Under `ndopify:` in `application-qa.yml`, add `telegram.token: qa-token-not-for-prod` and
`telegram.polling.enabled: false`. (The health indicator is disabled in this profile in Task 5, when it is added.)

Under `ndopify:` in both `application-test.yml` and `application-prod.yml`, switch to the webhook:

```yaml
    telegram:
        transport: webhook
        webhook:
            url: ${SERVER_URL}/telegram/webhook
            secret: ${TELEGRAM_WEBHOOK_SECRET}
```

Add `@ActiveProfiles("qa")` to `ResetDBTest`, `MtnCollectionProductTest` and `MtnUserProviderSandboxTest` so every
`@SpringBootTest` uses the dummy token.

- [ ] **Step 6: Run the client test, then the module's context tests**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: PASS.

Run: `cd modules/ndopify-server && mvn test -Dtest=ResetDBTest`
Expected: PASS — the context starts with the `qa` token and does not require `TELEGRAM_TOKEN`.

- [ ] **Step 7: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/main/resources/application.yml \
        modules/ndopify-server/src/main/resources/application-test.yml \
        modules/ndopify-server/src/main/resources/application-prod.yml \
        modules/ndopify-server/src/test/resources/application-qa.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/ResetDBTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/momo/mtn/MtnCollectionProductTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/momo/mtn/impl/MtnUserProviderSandboxTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt
git commit -m "feat(telegram): add the Telegram API client"
```

---

### Task 2: Answer every message with OK

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandler.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandlerTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId, text)` and the `TelegramUpdate` models from Task 1.
- Produces: `class TelegramUpdateHandler(client: TelegramClient) { fun handle(update: TelegramUpdate) }`, exposed as a bean.

- [ ] **Step 1: Write the failing handler test**

```kotlin
class TelegramUpdateHandlerTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client)

    @Test
    fun `a message gets OK`() {
        handler.handle(TelegramUpdate(updateId = 1L, message = TelegramMessage(chat = TelegramChat(id = 123L), text = "hello")))

        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `a message without text gets OK`() {
        handler.handle(TelegramUpdate(updateId = 1L, message = TelegramMessage(chat = TelegramChat(id = 123L), text = null)))

        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `an update without a message is ignored`() {
        handler.handle(TelegramUpdate(updateId = 1L, message = null))

        verifyNoInteractions(client)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramUpdateHandlerTest`
Expected: FAIL — `TelegramUpdateHandler` is unresolved.

- [ ] **Step 3: Implement the handler and register its bean**

`handle` returns early when `update.message == null`; otherwise it calls `client.sendMessage(update.message.chat.id, "OK")`.
Add to `TelegramConfiguration`:

```kotlin
@Bean
open fun telegramUpdateHandler(client: TelegramClient): TelegramUpdateHandler = TelegramUpdateHandler(client)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramUpdateHandlerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandler.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandlerTest.kt
git commit -m "feat(telegram): answer every message with OK"
```

---

### Task 3: Long polling transport

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramPollingTransport.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramPollingTransportTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.getUpdates(offset, timeoutSeconds)`, `TelegramClient.deleteWebhook()`, `TelegramUpdateHandler.handle(update)`.
- Produces: `class TelegramPollingTransport(client, handler, enabled: Boolean, timeoutSeconds: Int) : SmartLifecycle` with
  `fun pollOnce()`, `start()`, `stop()` and `isRunning()`.

- [ ] **Step 1: Write the failing polling tests**

```kotlin
class TelegramPollingTransportTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client)

    @Test
    fun `pollOnce handles every update and advances the offset`() {
        doReturn(
            listOf(
                TelegramUpdate(updateId = 10L, message = TelegramMessage(chat = TelegramChat(id = 100L), text = "a")),
                TelegramUpdate(updateId = 11L, message = TelegramMessage(chat = TelegramChat(id = 101L), text = "b")),
            ),
        ).whenever(client).getUpdates(null, 20)
        doReturn(emptyList<TelegramUpdate>()).whenever(client).getUpdates(12L, 20)

        val poller = TelegramPollingTransport(client, handler, enabled = true, timeoutSeconds = 20)
        poller.pollOnce()
        poller.pollOnce()

        verify(client).sendMessage(100L, "OK")
        verify(client).sendMessage(101L, "OK")
        verify(client).getUpdates(12L, 20)
    }

    @Test
    fun `an enabled transport clears the webhook and starts`() {
        doReturn(emptyList<TelegramUpdate>()).whenever(client).getUpdates(null, 20)
        val poller = TelegramPollingTransport(client, handler, enabled = true, timeoutSeconds = 20)

        poller.start()

        verify(client, timeout(1000)).deleteWebhook()
        assertTrue(poller.isRunning())
        poller.stop()
        assertFalse(poller.isRunning())
    }

    @Test
    fun `a failed deleteWebhook does not prevent polling`() {
        doThrow(RuntimeException("Telegram is down")).whenever(client).deleteWebhook()
        doReturn(emptyList<TelegramUpdate>()).whenever(client).getUpdates(null, 20)
        val poller = TelegramPollingTransport(client, handler, enabled = true, timeoutSeconds = 20)

        poller.start()

        assertTrue(poller.isRunning())
        poller.stop()
    }

    @Test
    fun `disabled does not start`() {
        val poller = TelegramPollingTransport(client, handler, enabled = false, timeoutSeconds = 20)

        poller.start()

        assertFalse(poller.isRunning())
        verify(client, never()).deleteWebhook()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramPollingTransportTest`
Expected: FAIL — `TelegramPollingTransport` is unresolved.

- [ ] **Step 3: Implement the transport**

It implements `SmartLifecycle`, holds `offset: Long? = null` and an `AtomicBoolean running`. `pollOnce` calls
`client.getUpdates(offset, timeoutSeconds)`, hands each update to the handler, then sets `offset = update.updateId + 1`.
`start` returns when `!enabled` or already running; otherwise it calls `deleteWebhook()` (a failure is logged as a warning,
not thrown), starts `Thread.ofVirtual().name("telegram-poller").start { loop() }`, and the loop calls `pollOnce()` while
running, logging any exception and sleeping 5 seconds before the next attempt (an `InterruptedException` exits the loop).
`stop` clears the flag and interrupts the thread. `isRunning()` returns the flag.

Log with `LoggerFactory.getLogger(TelegramPollingTransport::class.java)` and never include the client URL or token.

- [ ] **Step 4: Register the bean**

Add to `TelegramConfiguration` the `@Value` fields for `ndopify.telegram.transport`, `ndopify.telegram.polling.enabled`
(default `true`) and `ndopify.telegram.polling.timeout-seconds` (default `20`), then:

```kotlin
@Bean
open fun telegramPollingTransport(client: TelegramClient, handler: TelegramUpdateHandler): TelegramPollingTransport =
    TelegramPollingTransport(
        client,
        handler,
        pollingEnabled && transport == "polling",
        pollingTimeoutSeconds,
    )
```

The `transport` check matters: TEST/PROD run the webhook, and both transports must never run at once.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramPollingTransportTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramPollingTransport.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramPollingTransportTest.kt
git commit -m "feat(telegram): poll Telegram locally"
```

---

### Task 4: Webhook transport and public endpoint

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookTransport.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/servlet/PublicEndpoints.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookTransportTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookEndpointsTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.setWebhook(url, secret)`, `TelegramUpdateHandler.handle(update)`.
- Produces: `class TelegramWebhookTransport(client, transport: String, url: String, secret: String) : SmartLifecycle` and
  `TelegramWebhookEndpoints(handler, secret)` mapped at `POST /telegram/webhook`.

- [ ] **Step 1: Write the failing transport tests**

```kotlin
class TelegramWebhookTransportTest {
    private val client = mock<TelegramClient>()

    @Test
    fun `webhook mode registers the URL and the secret`() {
        TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "secret").start()

        verify(client).setWebhook("https://host/telegram/webhook", "secret")
    }

    @Test
    fun `a blank URL fails fast`() {
        assertFailsWith<IllegalStateException> {
            TelegramWebhookTransport(client, "webhook", "", "secret").start()
        }
    }

    @Test
    fun `a blank secret fails fast`() {
        assertFailsWith<IllegalStateException> {
            TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "").start()
        }
    }

    @Test
    fun `polling mode does nothing`() {
        TelegramWebhookTransport(client, "polling", "https://host/telegram/webhook", "secret").start()

        verifyNoInteractions(client)
    }

    @Test
    fun `a failed registration does not crash startup`() {
        doThrow(RuntimeException("Telegram is down")).whenever(client).setWebhook(any(), any())

        TelegramWebhookTransport(client, "webhook", "https://host/telegram/webhook", "secret").start()

        verify(client).setWebhook("https://host/telegram/webhook", "secret")
    }
}
```

- [ ] **Step 2: Write the failing endpoint test**

Use a standalone `MockMvc` so this task does not need the full context. The header constant is
`X-Telegram-Bot-Api-Secret-Token`.

```kotlin
class TelegramWebhookEndpointsTest {
    private val client = mock<TelegramClient>()
    private val handler = TelegramUpdateHandler(client)
    private val mockMvc = MockMvcBuilders
        .standaloneSetup(TelegramWebhookEndpoints(handler, "secret"))
        .build()

    private val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":123},"text":"hello"}}"""

    @Test
    fun `a valid secret replies OK`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isOk)

        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `a missing secret is rejected`() {
        mockMvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden)

        verifyNoInteractions(client)
    }

    @Test
    fun `a wrong secret is rejected`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "wrong")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        ).andExpect(status().isForbidden)

        verifyNoInteractions(client)
    }

    @Test
    fun `an update without a message is acknowledged without a send`() {
        mockMvc.perform(
            post("/telegram/webhook")
                .header("X-Telegram-Bot-Api-Secret-Token", "secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"update_id":3,"edited_message":{"message_id":4,"chat":{"id":123}}}"""),
        ).andExpect(status().isOk)

        verifyNoInteractions(client)
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramWebhook*Test'`
Expected: FAIL — the transport and endpoint classes are unresolved.

- [ ] **Step 4: Implement the transport**

`start` returns when `transport != "webhook"`. In webhook mode it throws `IllegalStateException` when `url` or `secret` is
blank (`check(...)` with a clear message), then calls `client.setWebhook(url, secret)` inside a `try`/`catch` that logs the
error and does not rethrow. `stop` does nothing; `isRunning()` returns `transport == "webhook"`.

- [ ] **Step 5: Implement the endpoint and open its path**

```kotlin
@RestController
@ConditionalOnProperty(name = ["ndopify.telegram.transport"], havingValue = "webhook")
class TelegramWebhookEndpoints(
    private val handler: TelegramUpdateHandler,
    @param:Value("\${ndopify.telegram.webhook.secret}") private val secret: String,
) {
    @PostMapping("/telegram/webhook")
    fun receive(
        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) token: String?,
        @RequestBody update: TelegramUpdate,
    ): ResponseEntity<Void> {
        if (token == null || !MessageDigest.isEqual(token.toByteArray(), secret.toByteArray())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        handler.handle(update)
        return ResponseEntity.ok().build()
    }
}
```

Add `"/telegram/webhook"` to `PublicEndpoints.GLOBAL` with a comment that Telegram authenticates it with the secret header,
not a JWT.

- [ ] **Step 6: Wire the webhook transport**

Add the `@Value` fields for `webhook.url` (default `""`) and `webhook.secret` (default `""`); `transport` was added
in Task 3. Then:

```kotlin
@Bean
open fun telegramWebhookTransport(client: TelegramClient): TelegramWebhookTransport =
    TelegramWebhookTransport(client, transport, webhookUrl, webhookSecret)
```

- [ ] **Step 7: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramWebhook*Test'`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookTransport.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookEndpoints.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/security/server/servlet/PublicEndpoints.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookTransportTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookEndpointsTest.kt
git commit -m "feat(telegram): receive updates over a webhook"
```

---

### Task 5: Telegram health indicator

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramHealthIndicator.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Modify: `modules/ndopify-server/src/test/resources/application-qa.yml`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramHealthIndicatorTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.getMe()`.
- Produces: `class TelegramHealthIndicator(client, clock: Clock, upTtl: Duration, downTtl: Duration) : HealthIndicator`,
  registered as `telegramHealthIndicator` (health id `telegram`).

- [ ] **Step 1: Write the failing health tests**

Mirror `ChatClientHealthIndicatorTest`: a mocked `TelegramClient`, a mocked `Clock` fixed at
`Instant.parse("2026-11-15T10:00:00Z")`, TTLs of 5 min (UP) and 1 min (DOWN).

```kotlin
class TelegramHealthIndicatorTest {
    private val client = mock<TelegramClient>()
    private val clock = mock<Clock>()
    private val now = Instant.parse("2026-11-15T10:00:00Z")
    private val health = TelegramHealthIndicator(
        client,
        clock,
        Duration.ofMinutes(5),
        Duration.ofMinutes(1),
    )

    @BeforeEach
    fun setUp() {
        doReturn(now).whenever(clock).instant()
    }

    @Test
    fun up() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()

        val result = health.health()

        assertEquals(Status.UP, result.status)
        assertEquals("ndopify_bot", result.details["username"])
    }

    @Test
    fun down() {
        doThrow(RuntimeException("token 123:secret leaked")).whenever(client).getMe()

        val result = health.health()

        assertEquals(Status.DOWN, result.status)
        assertEquals(mapOf<String, Any>("error" to "RuntimeException"), result.details)
    }

    @Test
    fun `up is cached`() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()
        health.health()

        doReturn(now.plus(Duration.ofMinutes(5)).minusMillis(1)).whenever(clock).instant()

        assertEquals(Status.UP, health.health().status)
        verify(client, times(1)).getMe()
    }

    @Test
    fun `up is checked again once expired`() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()
        health.health()

        doThrow(RuntimeException("down")).whenever(client).getMe()
        doReturn(now.plus(Duration.ofMinutes(5))).whenever(clock).instant()

        assertEquals(Status.DOWN, health.health().status)
        verify(client, times(2)).getMe()
    }

    @Test
    fun `down is cached`() {
        doThrow(RuntimeException("down")).whenever(client).getMe()
        health.health()

        doReturn(now.plusSeconds(59)).whenever(clock).instant()

        assertEquals(Status.DOWN, health.health().status)
        verify(client, times(1)).getMe()
    }

    @Test
    fun `down is checked again sooner`() {
        doThrow(RuntimeException("down")).whenever(client).getMe()
        health.health()

        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()
        doReturn(now.plus(Duration.ofMinutes(1))).whenever(clock).instant()

        assertEquals(Status.UP, health.health().status)
        verify(client, times(2)).getMe()
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramHealthIndicatorTest`
Expected: FAIL — `TelegramHealthIndicator` is unresolved.

- [ ] **Step 3: Implement the indicator**

Copy `ChatClientHealthIndicator`'s structure: a `@Synchronized override fun health()`, a cached `Health` with an
`expiresAt` instant, `upTtl` when UP and `downTtl` when DOWN. On success, return
`Health.up().withDetail("username", user.username ?: "-")`. On failure, log a warning and return
`Health.down().withDetail("error", ex.javaClass.simpleName)` — never the exception message or the token.

- [ ] **Step 4: Register the bean and keep the qa profile offline**

Add the `@Value` fields for `ndopify.telegram.health.up-ttl-minutes` and `down-ttl-minutes`, inject `Clock`, then:

```kotlin
@Bean
open fun telegramHealthIndicator(client: TelegramClient): TelegramHealthIndicator =
    TelegramHealthIndicator(
        client,
        clock,
        Duration.ofMinutes(healthUpTtlMinutes),
        Duration.ofMinutes(healthDownTtlMinutes),
    )
```

`application.yml` already has the TTLs from Task 1. Add `management.health.telegram.enabled: false` to
`application-qa.yml` (a new top-level `management:` block) so the suite makes no Telegram call.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramHealthIndicatorTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramHealthIndicator.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/test/resources/application-qa.yml \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramHealthIndicatorTest.kt
git commit -m "feat(telegram): add the Telegram health indicator"
```

---

### Task 6: Full-context tests for the webhook and the core health group

**Files:**
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookEndpointTest.kt`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramHealthEndpointTest.kt`

**Interfaces:**
- Consumes: the beans and the `/telegram/webhook` path from Tasks 1–5.
- Produces: no production code; proves the conditional endpoint, the public path and the default health group.

- [ ] **Step 1: Write the failing webhook integration test**

Extend `BaseEndpointIntegrationTest`, mock the client and switch the profile to webhook mode:

```kotlin
@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
class TelegramWebhookEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `Telegram posts an update, the bot replies OK`() {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":123},"text":"hello"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        verify(client).sendMessage(123L, "OK")
    }

    @Test
    fun `a request without the secret is rejected`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val body = """{"update_id":1,"message":{"message_id":2,"chat":{"id":123},"text":"hello"}}"""

        val response = rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), Void::class.java)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        verify(client, never()).sendMessage(anyLong(), anyString())
    }
}
```

The `setWebhook` the transport makes at startup is expected; only `sendMessage` must never run here (import `anyLong` and
`anyString` from `org.mockito.ArgumentMatchers`).

This also proves the path is public: the request carries no JWT. If the request returns 401, add `/telegram/webhook` to
`PublicEndpoints.GLOBAL` if Task 4 missed it.

- [ ] **Step 2: Write the failing health integration test**

```kotlin
@TestPropertySource(
    properties = [
        "management.health.telegram.enabled=true",
        "ndopify.telegram.health.up-ttl-minutes=0",
        "ndopify.telegram.health.down-ttl-minutes=0",
    ],
)
class TelegramHealthEndpointTest : BaseEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Test
    fun `telegram is in the corehealth group`() {
        doReturn(TelegramUser(id = 1L, username = "ndopify_bot")).whenever(client).getMe()

        val response = rest.getForEntity("/actuator/health", Map::class.java)
        val component = component(response.body!!)

        assertEquals("UP", component["status"])
        assertEquals("ndopify_bot", (component["details"] as Map<*, *>)["username"])
    }

    @Test
    fun `an unreachable Telegram makes the component DOWN`() {
        doThrow(RuntimeException("token 123:secret leaked")).whenever(client).getMe()

        val response = rest.getForEntity("/actuator/health", Map::class.java)
        val component = component(response.body!!)

        assertEquals("DOWN", component["status"])
        assertEquals("RuntimeException", (component["details"] as Map<*, *>)["error"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun component(body: Map<*, *>): Map<String, Any> =
        (body["components"] as Map<String, Any>)["telegram"] as Map<String, Any>
}
```

The 0-minute TTLs make every probe re-check, so the two tests do not share a cached result.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='Telegram*EndpointTest'`
Expected: FAIL — the endpoint test gets 404 (the controller is not created yet) or the health test has no `telegram` component.

- [ ] **Step 4: Fix the wiring until they pass**

Re-read `TelegramConfiguration` and `PublicEndpoints`; the expected causes are a missing `@ConditionalOnProperty`,
a missing secret property, or a missing `PublicEndpoints.GLOBAL` entry. Change production code only for a real gap found
by the test, then rerun.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='Telegram*EndpointTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramWebhookEndpointTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramHealthEndpointTest.kt
git commit -m "test(telegram): cover the webhook and the core health group"
```

---

### Task 7: Deployment configuration and documentation

**Files:**
- Modify: `.github/workflows/ndopify-server-main.yml`
- Modify: `CLAUDE.md`
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: the property names from Task 1 (`TELEGRAM_TOKEN`, `SERVER_URL`, `TELEGRAM_WEBHOOK_SECRET`).
- Produces: no code; the TEST Heroku app receives the three values on every deploy.

- [ ] **Step 1: Pass the values to Heroku**

In the `deploy-test` job's `Prepare Heroku Files` step, add to `env`:

```yaml
                    SERVER_URL: ${{secrets.SERVER_URL}}
                    TELEGRAM_TOKEN: ${{secrets.TELEGRAM_TOKEN}}
                    TELEGRAM_WEBHOOK_SECRET: ${{secrets.TELEGRAM_WEBHOOK_SECRET}}
```

and, next to the other `heroku config:set` lines:

```bash
                    heroku config:set --app "$HEROKU_APP" SERVER_URL="$SERVER_URL"
                    heroku config:set --app "$HEROKU_APP" TELEGRAM_TOKEN="$TELEGRAM_TOKEN"
                    heroku config:set --app "$HEROKU_APP" TELEGRAM_WEBHOOK_SECRET="$TELEGRAM_WEBHOOK_SECRET"
```

Do not add them to the `build` job: the `qa` profile supplies its own dummy token and never calls Telegram.

- [ ] **Step 2: Document the configuration**

In the root `CLAUDE.md`, add to *Environment Setup*: `TELEGRAM_TOKEN` (the dev bot locally; the production bot in
TEST/PROD), plus `SERVER_URL` and `TELEGRAM_WEBHOOK_SECRET` for deployed environments. Mention that the app registers
`${SERVER_URL}/telegram/webhook` on startup.

In `modules/ndopify-server/CLAUDE.md`:
- add `Telegram` to the `platform/` list,
- add one line on its behavior: long polling locally, webhook in TEST/PROD, health id `telegram` in the default group.

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ndopify-server-main.yml CLAUDE.md modules/ndopify-server/CLAUDE.md
git commit -m "chore(telegram): deploy and document the bot configuration"
```

---

### Task 8: Full verification

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate pass. MySQL must be running.

- [ ] **Step 2: Confirm the token requirement is real**

Run: `cd modules/ndopify-server && env -u TELEGRAM_TOKEN mvn spring-boot:run`
Expected: startup fails with `Could not resolve placeholder 'TELEGRAM_TOKEN'`. Stop the command after the failure (Ctrl-C
if it keeps running). Do not commit anything for this step.

- [ ] **Step 3: Commit any remaining fixes**

If Step 1 required a fix, commit it with a conventional message (`fix(telegram): …`). Otherwise the worktree is clean.
