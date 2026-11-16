# Telegram Button Rows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put every Telegram notification button on its own keyboard row, so long captions are never truncated.

**Architecture:** `TelegramClient` builds the `inline_keyboard` payload. `sendMessage`, `sendPhoto` and `editMessageText` emit one row per button, while `sendMessageRows` keeps taking explicit rows. No botlet, DTO or portal change.

**Tech Stack:** Kotlin, Spring Boot, `mockito-kotlin`, `kotlin.test`, Maven (ktlint + JaCoCo).

**Spec:** https://github.com/wutsi/ndopify-mono/issues/186

## Global Constraints

- `modules/ndopify-server` only; no `ndopify-dto`, `ndopify-web-core`, Partner Central, `ndopify-web`, `ndopify-esignature` nor `src/main/html/` change.
- UI text stays French; code, comments, KDoc and docs are English.
- Assertions use `kotlin.test`, never `org.junit.jupiter.api.Assertions.*`.
- No botlet signature or call site changes: `sendMessage` keeps its `List<TelegramButton>` parameter.
- `sendMessageRows` keeps its explicit-row semantics: a row with several buttons stays one row.
- Tests live in the mirrored package; a full `mvn verify` needs local MySQL (see the root `CLAUDE.md`).

## Review Focus

- A message with three or more buttons: every button gets its own row, in the order given.
- A `sendMessageRows` row holding two buttons: they stay on one row (the caller's grouping wins).
- A message with no buttons: `sendMessage` / `sendPhoto` omit `reply_markup`; `editMessageText` sends an empty keyboard.
- `sendPhoto` with two buttons: they stack, not only in `sendMessage`.
- `editMessageText` with two buttons: they stack, and the empty call still clears the keyboard.

---

### Task 1: One keyboard row per button

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt:28-119,159-165`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt`
- Modify: `modules/ndopify-server/CLAUDE.md:598-601`

**Interfaces:**
- Consumes: `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`.
- Produces: `sendMessage`, `sendPhoto` and `editMessageText` post `reply_markup.inline_keyboard` as one inner list per button, in the given order; `sendMessageRows` keeps one inner list per caller-supplied row. No signature changes.

- [ ] **Step 1: Update the failing tests**

In `TelegramClientTest`, rename `sendMessage with buttons posts one keyboard row` to `sendMessage with buttons posts one keyboard row per button`, pass three buttons and expect three rows:

```kotlin
client.sendMessage(
    123L,
    "Offre",
    listOf(
        TelegramButton("Accepter", "accept:9"),
        TelegramButton("Refuser", "decline:9"),
        TelegramButton("Voir le mandat", "view:9"),
    ),
)

verify(http).post(
    eq("$base/sendMessage"),
    eq(
        mapOf(
            "chat_id" to 123L,
            "text" to "Offre",
            "reply_markup" to mapOf(
                "inline_keyboard" to listOf(
                    listOf(mapOf("text" to "Accepter", "callback_data" to "accept:9")),
                    listOf(mapOf("text" to "Refuser", "callback_data" to "decline:9")),
                    listOf(mapOf("text" to "Voir le mandat", "callback_data" to "view:9")),
                ),
            ),
        ),
    ),
    eq(TelegramMessageResponse::class.java),
    eq(emptyMap()),
)
```

Add a test that keeps an explicit row together:

```kotlin
@Test
fun `sendMessageRows keeps a row's buttons together`() {
    doReturn(TelegramMessageResponse(ok = true))
        .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

    client.sendMessageRows(
        123L,
        "Digest",
        listOf(
            listOf(TelegramButton("Accepter", "accept:9"), TelegramButton("Refuser", "decline:9")),
            listOf(TelegramButton("Voir le mandat", url = "https://pc.example/mandates/11")),
        ),
    )

    verify(http).post(
        eq("$base/sendMessage"),
        eq(
            mapOf(
                "chat_id" to 123L,
                "text" to "Digest",
                "reply_markup" to mapOf(
                    "inline_keyboard" to listOf(
                        listOf(
                            mapOf("text" to "Accepter", "callback_data" to "accept:9"),
                            mapOf("text" to "Refuser", "callback_data" to "decline:9"),
                        ),
                        listOf(mapOf("text" to "Voir le mandat", "url" to "https://pc.example/mandates/11")),
                    ),
                ),
            ),
        ),
        eq(TelegramMessageResponse::class.java),
        eq(emptyMap()),
    )
}
```

Rename `editMessageText with a URL button replaces the keyboard` to `editMessageText with buttons posts one keyboard row per button`, pass two buttons and expect two rows instead of one:

```kotlin
client.editMessageText(
    123L,
    50L,
    "Mandat accepté.",
    listOf(
        TelegramButton("Voir le mandat", url = "https://npc.example/mandates/10"),
        TelegramButton("Voir la visite", url = "https://npc.example/visits/2"),
    ),
)
```

with the expected keyboard:

```kotlin
"reply_markup" to mapOf(
    "inline_keyboard" to listOf(
        listOf(mapOf("text" to "Voir le mandat", "url" to "https://npc.example/mandates/10")),
        listOf(mapOf("text" to "Voir la visite", "url" to "https://npc.example/visits/2")),
    ),
),
```

Rename `sendPhoto posts the image, its caption and its button` to `sendPhoto posts the image, its caption and one keyboard row per button`, pass two buttons and expect two rows in the `postMultipart` stub (its verification only checks the file bytes):

```kotlin
"reply_markup" to mapOf(
    "inline_keyboard" to listOf(
        listOf(mapOf("text" to "Voir l'annonce", "url" to "https://www.ndopify.com/listing")),
        listOf(mapOf("text" to "Voir le mandat", "url" to "https://npc.example/mandates/10")),
    ),
),
```

with `listOf(TelegramButton("Voir l'annonce", url = "https://www.ndopify.com/listing"), TelegramButton("Voir le mandat", url = "https://npc.example/mandates/10"))` as the argument.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`

Expected: FAIL — the keyboard still has one row holding every button. The new `sendMessageRows` test is a regression guard and may already pass.

- [ ] **Step 3: Implement the row layout**

In `TelegramClient`, build every keyboard with one button per row:

```kotlin
open fun sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList()) {
    val payload = buildMap<String, Any> {
        put("chat_id", chatId)
        put("text", text)
        if (buttons.isNotEmpty()) {
            put("reply_markup", mapOf("inline_keyboard" to buttons.toKeyboard()))
        }
    }
    // unchanged http.post(...)
}
```

```kotlin
/**
 * The same [sendMessage], with the keyboard's rows given explicitly: one row per button list. [sendMessage] gives
 * each button its own row; this method keeps a caller's grouping, ex: a digest labels its buttons per listing.
 */
open fun sendMessageRows(chatId: Long, text: String, rows: List<List<TelegramButton>>) {
    val keyboard = rows.filter { row -> row.isNotEmpty() }
        .map { row -> row.map { button -> button.toPayload() } }
    // unchanged payload and http.post(...)
}
```

In `editMessageText`, the payload becomes:

```kotlin
"reply_markup" to mapOf("inline_keyboard" to buttons.toKeyboard()),
```

replacing the `if (buttons.isEmpty()) emptyList<List<Any>>() else …` expression; an empty `buttons` still yields an empty keyboard.

In `sendPhoto`, the existing `if (buttons.isNotEmpty())` block becomes:

```kotlin
put("reply_markup", mapOf("inline_keyboard" to buttons.toKeyboard()))
```

Replace the private `List<TelegramButton>.toPayload()` with:

```kotlin
/** One button per keyboard row: Telegram truncates a caption that shares its row with another button. */
private fun List<TelegramButton>.toKeyboard(): List<List<Map<String, String>>> =
    map { button -> listOf(button.toPayload()) }

private fun TelegramButton.toPayload(): Map<String, String> = buildMap {
    put("text", text)
    callbackData?.let { put("callback_data", it) }
    url?.let { put("url", it) }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`

Expected: PASS.

- [ ] **Step 5: Update `modules/ndopify-server/CLAUDE.md`**

In the `platform/telegram` bullet, replace:

```text
`TelegramClient` (a thin Bot API client over `platform/util/http/Http`; `sendMessageRows` sends the keyboard's rows given explicitly, ex: one row per listing of a digest; `sendPhoto` uploads an image, never a URL Telegram would fetch, with its caption and keyboard)
```

with:

```text
`TelegramClient` (a thin Bot API client over `platform/util/http/Http`; `sendMessage`, `sendPhoto` and `editMessageText` give each button its own keyboard row, while `sendMessageRows` sends the keyboard's rows given explicitly, ex: one row per listing of a digest; `sendPhoto` uploads an image, never a URL Telegram would fetch, with its caption and keyboard)
```

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server
git commit -m "fix(telegram): stack notification buttons one per row"
```

---

### Task 2: Verify the module

**Files:**
- None.

- [ ] **Step 1: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`

Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo coverage gate pass (MySQL required).

- [ ] **Step 2: Confirm the tree is clean**

Run: `git status --porcelain`

Expected: no output.
