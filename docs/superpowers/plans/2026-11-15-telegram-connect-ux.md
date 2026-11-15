# Telegram Connect UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Telegram connection follow the order of its steps — the code request in step 1, a bot button in step 2 that unlocks once a code is pending — and have the bot's `/start` explain `/connect` to an unlinked chat.

**Architecture:** Partner Central renders both CTA states from one `telegramCodePending` flag: the flash attribute covers the redirect right after the request, and a session attribute carrying the code's expiry covers a refresh for the same 15 minutes. The bot gains a second `TelegramCommand` (`/start`) beside `/connect`, backed by one lookup on the existing link table.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Thymeleaf, JUnit 5 + `kotlin.test` + mockito-kotlin, ktlint.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/144

## Global Constraints

- The prototype in `modules/ndopify-partner-central/src/main/html/` is the visual reference and must match the served template. It was built and reviewed for this story.
- All user-facing text is French; code, comments and logs are English.
- No `ndopify-dto`, `ndopify-web-core` or database change.
- `mvn verify` stays green in `ndopify-partner-central` and `ndopify-server`; each touched module keeps its JaCoCo gate.
- The bot token, its URL and the webhook secret are never logged, thrown or returned.
- The page works without JavaScript: both CTA states are rendered on the server.

## Review Focus

- A refresh (F5) after the code was sent must keep step 2 enabled for the remaining 15 minutes — the state must not live only in the flash.
- A blank `TELEGRAM_BOT_USERNAME` on any environment must render `https://t.me/ndopify_bot`, never `https://t.me/`.
- An already-linked chat sending `/start` must be told it is connected, not taught how to connect.
- A code whose 15 minutes elapsed must re-disable step 2, turn step 1 back into the primary action, and not re-show the `Code envoyé` alert forever.
- `/start@ndopify_bot` (the form Telegram sends in a group) and `/start <payload>` (a deep link) must get the same reply as `/start`.

---

## File Structure

- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramRules.kt` — flash/session keys, the default bot name, the bot URL.
- `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramController.kt` — the pending flag (flash + session) and the blank-username fallback.
- `modules/ndopify-partner-central/src/main/resources/application.yml` — `ndopify.telegram.code-ttl-minutes`.
- `modules/ndopify-partner-central/src/main/resources/templates/telegram.html` — the two steps and their two CTA states.
- `modules/ndopify-partner-central/src/main/resources/public/assets/css/telegram.css` — the in-step action spacing and the disabled link.
- `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt` — `isLinked(chatId)`.
- `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramStartCommand.kt` — the `/start` reply.
- Tests mirror the main paths under each module's `src/test/kotlin/`.

---

### Task 1: A pending code is known for its TTL

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramRules.kt`
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramController.kt:29-70`
- Modify: `modules/ndopify-partner-central/src/main/resources/application.yml:76-79`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramControllerTest.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramTemplateTest.kt` (constructor only)

**Interfaces:**
- Consumes: `Clock` (the `npc/config/ClockConfiguration` bean), `TelegramRules.SENT` (flash).
- Produces: `TelegramRules.SENT_UNTIL = "telegramCodeSentUntil"` (session key, holding a `Long` epoch-millis); model attribute `telegramCodePending: Boolean`; constructor `TelegramController(currentAgent, agentClient, botUsername, clock, codeTtlMinutes)`.

- [ ] **Step 1: Write the failing controller tests**

Build the controller in `setUp` with a fixed clock — `val now = Instant.parse("2026-11-15T10:00:00Z").toEpochMilli()`, `Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)`, `codeTtlMinutes = 15` — then:

```kotlin
@Test
fun `a successful request remembers the code for its ttl`() {
    val result = mvc.perform(post("/telegram/code")).andReturn()

    assertEquals(now + 15 * 60_000, result.request.getSession().getAttribute(TelegramRules.SENT_UNTIL))
}

@Test
fun `a pending code keeps step 2 open after a refresh`() {
    val result = mvc.perform(
        get("/telegram").sessionAttr(TelegramRules.SENT_UNTIL, now + 60_000)
    ).andReturn()

    assertEquals(true, result.modelAndView!!.model["telegramCodePending"])
    assertNull(result.flashMap[TelegramRules.SENT])
}

@Test
fun `an expired code closes step 2 again`() {
    val result = mvc.perform(
        get("/telegram").sessionAttr(TelegramRules.SENT_UNTIL, now - 1)
    ).andReturn()

    assertEquals(false, result.modelAndView!!.model["telegramCodePending"])
    assertNull(result.request.getSession().getAttribute(TelegramRules.SENT_UNTIL))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramControllerTest`
Expected: FAIL — the constructor has no clock or TTL, and `SENT_UNTIL` / `telegramCodePending` do not exist.

- [ ] **Step 3: Implement the pending flag**

In `TelegramRules`, add `const val SENT_UNTIL = "telegramCodeSentUntil"`.

In `TelegramController`:

```kotlin
class TelegramController(
    private val currentAgent: CurrentAgent,
    private val agentClient: NdAgentClient,
    @Value("\${ndopify.telegram.username}") private val botUsername: String,
    private val clock: Clock,
    @Value("\${ndopify.telegram.code-ttl-minutes:15}") private val codeTtlMinutes: Long,
)
```

- `show(model, session: HttpSession)` adds `telegramCodePending` (a private `pending(model, session)`), true when the flash attribute `SENT` is present, or the session's `SENT_UNTIL` is a `Long` greater than `clock.millis()`. An `SENT_UNTIL` at or before now is removed from the session and does not count.
- `requestCode(redirect, session: HttpSession)`: on the success path only, `session.setAttribute(SENT_UNTIL, clock.millis() + codeTtlMinutes * 60_000)` beside the existing flash.
- `telegramCodeSent` stays the flash attribute, read by the alert only, so a refresh does not re-show `Code envoyé`. KDoc both points: the flash is the confirmation, the session is the state that outlives it because the code does.

In `application.yml`, under `ndopify.telegram`:

```yaml
code-ttl-minutes: 15   # Mirrors ndopify.agent.telegram.code-ttl-minutes on ndopify-server
```

Update the `TelegramController` construction in `TelegramTemplateTest` with the fixed clock and `15`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramControllerTest,TelegramTemplateTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram \
        modules/ndopify-partner-central/src/main/resources/application.yml \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram
git commit -m "feat(npc): keep the telegram code pending state for its ttl"
```

---

### Task 2: The page's two actions, inside their steps

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/resources/templates/telegram.html:89-119`
- Modify: `modules/ndopify-partner-central/src/main/resources/public/assets/css/telegram.css:73-94`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramTemplateTest.kt`

**Interfaces:**
- Consumes: `telegramCodePending`, `telegramBotUrl`, `telegramConnected` (Task 1 and existing model attributes).
- Produces: the markup the CSS and tests select — `form[data-telegram-form]` inside the first step, `[data-telegram-open]` on step 2's anchor, `[data-telegram-open-disabled]` on its disabled button.

- [ ] **Step 1: Write the failing template tests**

Replace the `not connected - the three steps and the button` test's form assertions and add the two states:

```kotlin
@Test
fun `not connected - the code action is in step 1 and the bot button is disabled`() {
    val page = render(get("/telegram"))

    assertEquals(3, page.select(".connect-steps > li").size)
    val form = page.selectFirst(".connect-steps > li:first-child form[data-telegram-form]")!!
    assertEquals("/telegram/code", form.attr("action"))
    assertEquals("Recevoir mon code par e-mail", form.selectFirst("button")?.text())
    assertTrue(form.selectFirst("button")!!.hasClass("btn--primary"))

    val disabled = page.selectFirst("[data-telegram-open-disabled]")!!
    assertEquals("Ouvrir le bot Ndopify", disabled.text())
    assertTrue(disabled.hasAttr("disabled"))
    assertNull(page.selectFirst("a[data-telegram-open]"))
}

@Test
fun `a pending code flips the emphasis to the bot button`() {
    val page = render(get("/telegram").flashAttr(TelegramRules.SENT, true))

    val code = page.selectFirst(".connect-steps > li:first-child form[data-telegram-form] button")!!
    assertEquals("Recevoir un nouveau code", code.text())
    assertTrue(code.hasClass("btn--outline"))

    val open = page.selectFirst("a[data-telegram-open]")!!
    assertEquals("https://t.me/ndopify_bot", open.attr("href"))
    assertEquals("_blank", open.attr("target"))
    assertEquals("noopener", open.attr("rel"))
    assertTrue(open.hasClass("btn--primary"))
    assertNull(page.selectFirst("[data-telegram-open-disabled]"))
}
```

Keep the existing connected-state and alert tests.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramTemplateTest`
Expected: FAIL — the form is not inside the first step, and `[data-telegram-open-disabled]` / `a[data-telegram-open]` are absent.

- [ ] **Step 3: Rewrite the steps in `telegram.html`**

Inside `<th:block th:unless="${telegramConnected}">`:

- Step 1 title becomes `Demandez votre code`; its `<div>` gets, after the paragraph:

```html
<form class="connect-cta connect-cta--step" method="post" th:action="@{/telegram/code}" data-telegram-form>
    <button class="btn" type="submit" data-telegram-code
            th:classappend="${telegramCodePending} ? 'btn--outline' : 'btn--primary'"
            th:text="${telegramCodePending} ? 'Recevoir un nouveau code' : 'Recevoir mon code par e-mail'">
        Recevoir mon code par e-mail
    </button>
</form>
```

- Step 2 title becomes plain text `Ouvrez Telegram`; its paragraph keeps the `Démarrer` hint; then:

```html
<div class="connect-cta connect-cta--step">
    <a th:if="${telegramCodePending}" class="btn btn--primary" th:href="${telegramBotUrl}"
       target="_blank" rel="noopener" data-telegram-open>Ouvrir le bot Ndopify</a>
    <button th:unless="${telegramCodePending}" class="btn btn--outline" type="button"
            disabled data-telegram-open-disabled>Ouvrir le bot Ndopify</button>
</div>
```

- Delete the page-level `<form class="connect-cta">` at the foot and the `connect-cta__hint` paragraph.

- [ ] **Step 4: Update the step CSS**

In `telegram.css`, keep `.connect-cta` and `.connect-cta .btn` (full width below 640px), add `margin-top: var(--space-3)` for `.connect-cta--step`, delete the `.connect-cta__hint` rule.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramTemplateTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-partner-central/src/main/resources/templates/telegram.html \
        modules/ndopify-partner-central/src/main/resources/public/assets/css/telegram.css \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramTemplateTest.kt
git commit -m "feat(npc): order the telegram connect actions in the steps"
```

---

### Task 3: A blank bot username never renders `https://t.me/`

**Files:**
- Modify: `modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramRules.kt`
- Test: `modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramControllerTest.kt`

**Interfaces:**
- Produces: `TelegramRules.DEFAULT_BOT_USERNAME = "ndopify_bot"`; `TelegramRules.botUrl(username: String)` returns `https://t.me/<username>` when the username is not blank and `https://t.me/ndopify_bot` when it is.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `a blank bot username falls back to the default bot`() {
    val blank = TelegramController(currentAgent, agentClient, "", clock, 15)
    val mvc = MockMvcBuilders.standaloneSetup(blank).build()

    val result = mvc.perform(get("/telegram")).andReturn()

    assertEquals("https://t.me/ndopify_bot", result.modelAndView!!.model["telegramBotUrl"])
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramControllerTest`
Expected: FAIL — the URL is `https://t.me/`.

- [ ] **Step 3: Implement the fallback**

```kotlin
const val DEFAULT_BOT_USERNAME = "ndopify_bot"

fun botUrl(username: String) = "https://t.me/" + username.ifBlank { DEFAULT_BOT_USERNAME }
```

- [ ] **Step 4: Run it to verify it passes**

Run: `cd modules/ndopify-partner-central && mvn test -Dtest=TelegramControllerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-partner-central/src/main/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramRules.kt \
        modules/ndopify-partner-central/src/test/kotlin/com/wutsi/ndopify/npc/ui/telegram/TelegramControllerTest.kt
git commit -m "fix(npc): fall back to the default bot when the username is blank"
```

---

### Task 4: `/start` explains how to link the account

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramStartCommand.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramStartCommandTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt`

**Interfaces:**
- Consumes: `AgentTelegramRepository.findByChatId(chatId: Long): AgentTelegramEntity?`, `TelegramCommand`, `TelegramMessage(chat: TelegramChat(id), text: String?)`.
- Produces: `AgentTelegramService.isLinked(chatId: Long): Boolean`; `AgentTelegramStartCommand(service)` implementing `TelegramCommand`, with `WELCOME` and `ALREADY_CONNECTED` constants.

- [ ] **Step 1: Write the failing service test**

```kotlin
@Test
fun `a linked chat is recognized`() {
    doReturn(AgentTelegramEntity(id = 9L, tenantId = 1L, agentId = 20L, chatId = 777L))
        .whenever(dao).findByChatId(777L)

    assertTrue(service.isLinked(777L))
}

@Test
fun `an unknown chat is not linked`() {
    doReturn(null).whenever(dao).findByChatId(777L)

    assertFalse(service.isLinked(777L))
}
```

- [ ] **Step 2: Write the failing command tests**

```kotlin
class AgentTelegramStartCommandTest {
    private val service = mock<AgentTelegramService>()
    private val command = AgentTelegramStartCommand(service)

    @Test
    fun `start explains how to connect an unlinked chat`() {
        doReturn(false).whenever(service).isLinked(777L)

        assertEquals(AgentTelegramStartCommand.WELCOME, command.replyTo(message("/start")))
    }

    @Test
    fun `start on a linked chat says it is already connected`() {
        doReturn(true).whenever(service).isLinked(777L)

        assertEquals(AgentTelegramStartCommand.ALREADY_CONNECTED, command.replyTo(message("/start")))
    }

    @Test
    fun `the group form and a deep-link payload get the same reply`() {
        doReturn(false).whenever(service).isLinked(777L)

        assertEquals(AgentTelegramStartCommand.WELCOME, command.replyTo(message("/start@ndopify_bot")))
        assertEquals(AgentTelegramStartCommand.WELCOME, command.replyTo(message("/start 123456")))
    }

    @Test
    fun `another message is not its command`() {
        assertNull(command.replyTo(message("bonjour")))
        assertNull(command.replyTo(TelegramMessage(chat = TelegramChat(id = 777L), text = null)))
        verifyNoInteractions(service)
    }

    private fun message(text: String?) = TelegramMessage(chat = TelegramChat(id = 777L), text = text)
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramServiceTest,AgentTelegramStartCommandTest`
Expected: FAIL — `isLinked` and `AgentTelegramStartCommand` are unresolved.

- [ ] **Step 4: Implement the lookup and the command**

`AgentTelegramService`:

```kotlin
/** True when the chat is linked to an agent account. */
fun isLinked(chatId: Long): Boolean = dao.findByChatId(chatId) != null
```

`AgentTelegramStartCommand` — a `@Component` mirroring `AgentTelegramCommand`'s head parsing (trim, split on whitespace with limit 2, lowercase, accept `/start` and `/start@…`); it returns `null` for anything else, ignores the payload, and answers `ALREADY_CONNECTED` when `service.isLinked(message.chat.id)`, `WELCOME` otherwise. Exact text:

```kotlin
val WELCOME = """
    Bienvenue sur le bot Ndopify.

    Ce bot vous envoie vos notifications Ndopify (mandats, visites, messages).

    Pour le connecter à votre compte :
    1. Dans votre espace agent, ouvrez la page Telegram et demandez un code par e-mail.
    2. Revenez ici et envoyez /connect suivi du code, par exemple /connect 123456.

    Une fois connecté, vos notifications arrivent dans cette conversation.
""".trimIndent()

const val ALREADY_CONNECTED =
    "Votre compte Telegram est déjà connecté à Ndopify. Vos notifications arrivent dans cette conversation."
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramServiceTest,AgentTelegramStartCommandTest,AgentTelegramCommandTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramStartCommand.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/telegram/AgentTelegramStartCommandTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt
git commit -m "feat(telegram): explain how to connect on start"
```

---

### Task 5: Deployment — the missing bot username secret

**Files:** none (GitHub and Heroku configuration).

- [ ] **Step 1: Create the secret**

```bash
gh secret set -R wutsi/ndopify-mono TELEGRAM_BOT_USERNAME -b ndopify_bot
```

Only a maintainer can do this; without it, `ndopify-partner-central-main.yml` sets the Heroku var to `""` and Task 3's fallback is what keeps the link correct.

- [ ] **Step 2: Verify the deployed link**

Open the TEST deployment's `/telegram` page as a signed-in agent, copy the "Ouvrir le bot" link and check it is `https://t.me/ndopify_bot`. If step 2 still shows the disabled button, request a code first and check the enabled link.

---

### Task 6: Whole-module verification

- [ ] **Step 1: Verify Partner Central**

Run: `cd modules/ndopify-partner-central && mvn verify`
Expected: PASS, coverage gate met.

- [ ] **Step 2: Verify the server**

Run: `cd modules/ndopify-server && mvn verify`
Expected: PASS, coverage gate met (needs the local MySQL described in the root `CLAUDE.md`).

- [ ] **Step 3: Update the story**

Comment the commit links on #144 and move it to `Done` once the branch merges. The story is `Ready` when this plan lands on `main`.
