# Telegram Assignment Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offer a new mandate to its agent over Telegram, with `Accepter` / `Refuser` buttons that answer the offer from the chat, and no offer e-mail for a linked agent (falling back to it if Telegram fails).

**Architecture:** `AssignmentService` calls an `AssignmentOfferNotifier` instead of the mailet: it picks the new `MandateOfferBotlet` when #135's global link has a chat id, the e-mail otherwise, and falls back to the e-mail on any Telegram failure. Telegram sends the taps as `callback_query` updates: the bot's update handler routes them to a `TelegramAssignmentCallbackHandler`, which reads the link's `tenant_id` and `agent_id` from the chat id and answers the assignment through agent-scoped `acceptForAgent` / `declineForAgent` entry points that need no JWT.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/136

## Global Constraints

- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- **Depends on #134 and #135, both built first.** This plan assumes their code: `TelegramClient`, the Telegram models, `TelegramUpdateHandler(client, commands: List<TelegramCommand>)`, `TelegramConfiguration`, the polling/webhook transports, the `/telegram/webhook` public path and `ndopify.telegram.*` config (#134, `docs/superpowers/plans/2026-11-15-telegram-bot.md`); and #135's global `T_AGENT_TELEGRAM` row (`tenant_id`, `agent_id`, unique `chat_id`), `AgentTelegramEntity`, `AgentTelegramRepository.findByAgentId` / `findByChatId`, `AgentTelegramService.isConnected` (plan `2026-11-15-telegram-agent-connect.md`). If #135 named any of them differently, adapt the calls, not the design.
- No database migration, no `ndopify-dto` nor `ndopify-web-core` change, no UI page.
- The bot's texts are French; code, comments, logs and docs are English.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- Never log, throw or report the bot token or the Telegram URL (see #134).
- The `qa` profile must make no Telegram call; a `@SpringBootTest` reaches only a mocked `TelegramClient`.
- KDoc on classes and public methods; a service method's KDoc lists its error codes in the order they're checked.
- One module per commit (only `ndopify-server` here); conventional commit messages.

## Review Focus

- An offer past its TTL, already answered, or whose mandate was cancelled: tapping a button must not change the assignment, and the message must lose its buttons with `Ce mandat n'est plus disponible.` (Task 6, Task 7).
- A tap from a chat id linked to another agent or another tenant: nothing changes, no message is edited, the toast reveals nothing (Task 2, Task 6, Task 7).
- A linked agent when Telegram refuses or is down: the offer e-mail is sent instead, so no offer is lost (Task 5).
- An unlinked agent: the offer e-mail is sent as today and Telegram is never called (Task 5).
- Malformed or unknown callback data, a callback without a message: no exception escapes the webhook (it answers 200), only a generic toast (Task 6).
- Two taps in a row on one offer: exactly one transition, the second says the offer is no longer available (Task 3, Task 6).

---

### Task 1: Telegram buttons, callback queries and message edit

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/model/TelegramModels.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt`

**Interfaces:**
- Consumes: #134's `TelegramClient(token, http)`, its private `check()` and `url(path)`; `Http.post(uri, payload: Any, responseType, headers)`.
- Produces:
  - `data class TelegramButton(val text: String, @get:JsonProperty("callback_data") val callbackData: String)`
  - `data class TelegramCallbackQuery(val id: String, val data: String? = null, val message: TelegramMessage? = null)`, and `TelegramUpdate.callbackQuery: TelegramCallbackQuery?` (`callback_query`)
  - `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())` — one row of buttons
  - `TelegramClient.answerCallbackQuery(callbackQueryId: String, text: String)`
  - `TelegramClient.editMessageText(chatId: Long, messageId: Long, text: String)` — clears the keyboard

- [ ] **Step 1: Write the failing client tests**

Add to `TelegramClientTest` (`base` is `https://api.telegram.org/bot123:token`):

```kotlin
@Test
fun `sendMessage with buttons posts one keyboard row`() {
    doReturn(TelegramMessageResponse(ok = true))
        .whenever(http).post(eq("$base/sendMessage"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

    client.sendMessage(
        123L,
        "Offre",
        listOf(TelegramButton("Accepter", "accept:9"), TelegramButton("Refuser", "decline:9")),
    )

    verify(http).post(
        eq("$base/sendMessage"),
        eq(
            mapOf(
                "chat_id" to 123L,
                "text" to "Offre",
                "reply_markup" to mapOf(
                    "inline_keyboard" to listOf(
                        listOf(
                            mapOf("text" to "Accepter", "callback_data" to "accept:9"),
                            mapOf("text" to "Refuser", "callback_data" to "decline:9"),
                        ),
                    ),
                ),
            ),
        ),
        eq(TelegramMessageResponse::class.java),
        eq(emptyMap()),
    )
}

@Test
fun answerCallbackQuery() {
    doReturn(TelegramBooleanResponse(ok = true))
        .whenever(http).post(eq("$base/answerCallbackQuery"), any(), eq(TelegramBooleanResponse::class.java), eq(emptyMap()))

    client.answerCallbackQuery("cb-1", "Mandat accepté.")

    verify(http).post(
        eq("$base/answerCallbackQuery"),
        eq(mapOf("callback_query_id" to "cb-1", "text" to "Mandat accepté.")),
        eq(TelegramBooleanResponse::class.java),
        eq(emptyMap()),
    )
}

@Test
fun `editMessageText clears the keyboard`() {
    doReturn(TelegramMessageResponse(ok = true))
        .whenever(http).post(eq("$base/editMessageText"), any(), eq(TelegramMessageResponse::class.java), eq(emptyMap()))

    client.editMessageText(123L, 50L, "Mandat accepté.")

    verify(http).post(
        eq("$base/editMessageText"),
        eq(
            mapOf(
                "chat_id" to 123L,
                "message_id" to 50L,
                "text" to "Mandat accepté.",
                "reply_markup" to mapOf("inline_keyboard" to emptyList<List<TelegramButton>>()),
            ),
        ),
        eq(TelegramMessageResponse::class.java),
        eq(emptyMap()),
    )
}
```

The existing `sendMessage` test (2 arguments, no `reply_markup`) must stay unchanged and green: the payload carries no `reply_markup` when `buttons` is empty.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: FAIL — `TelegramButton` is unresolved and `answerCallbackQuery` / `editMessageText` don't exist.

- [ ] **Step 3: Implement the models and the client methods**

In `TelegramModels.kt`, add the two data classes with `@get:JsonProperty` snake_case names (`callback_data`, `callback_query`), and `callbackQuery` as the last `TelegramUpdate` property with a `null` default. In `TelegramClient`:

- `sendMessage(chatId, text, buttons = emptyList())`: payload is `mapOf("chat_id" to chatId, "text" to text)`, plus `"reply_markup" to mapOf("inline_keyboard" to listOf(buttons.toPayload()))` only when `buttons` isn't empty; each button maps to `mapOf("text" to it.text, "callback_data" to it.callbackData)`.
- `answerCallbackQuery(callbackQueryId, text)`: POST `mapOf("callback_query_id" to callbackQueryId, "text" to text)` to `answerCallbackQuery`.
- `editMessageText(chatId, messageId, text)`: POST `mapOf("chat_id" to chatId, "message_id" to messageId, "text" to text, "reply_markup" to mapOf("inline_keyboard" to emptyList<List<Any>>()))` to `editMessageText`.

Every call goes through the existing `check()`; no exception carries the URL.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramClientTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/model/TelegramModels.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClient.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramClientTest.kt
git commit -m "feat(telegram): send buttons, answer taps and edit messages"
```

---

### Task 2: The two Telegram link lookups

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt`
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt`

**Interfaces:**
- Consumes: #135's `AgentTelegramRepository.findByAgentId(agentId: Long): AgentTelegramEntity?` / `findByChatId(chatId: Long): AgentTelegramEntity?`, and `AgentTelegramEntity(tenantId, agentId, chatId, …)`.
- Produces:
  - `AgentTelegramService.chatId(agentId: Long): Long?` — the agent's linked chat, or null.
  - `AgentTelegramService.findByChatId(chatId: Long): AgentTelegramEntity?` — the global link row of a chat (its `tenantId` and `agentId` are what the callback needs).

- [ ] **Step 1: Write the failing service tests**

Add to #135's `AgentTelegramServiceTest` (it already builds `service` over a mocked `AgentTelegramRepository`):

```kotlin
@Test
fun `chatId - the linked chat`() {
    doReturn(AgentTelegramEntity(id = 9L, tenantId = 1L, agentId = 20L, chatId = 1000L))
        .whenever(dao).findByAgentId(20L)

    assertEquals(1000L, service.chatId(20L))
}

@Test
fun `chatId - null while only a code is pending`() {
    doReturn(AgentTelegramEntity(id = 9L, tenantId = 1L, agentId = 20L, code = "123456"))
        .whenever(dao).findByAgentId(20L)

    assertNull(service.chatId(20L))
}

@Test
fun `chatId - null without a link row`() {
    doReturn(null).whenever(dao).findByAgentId(20L)

    assertNull(service.chatId(20L))
}

@Test
fun `findByChatId - the link of the chat, with its tenant`() {
    val row = AgentTelegramEntity(id = 9L, tenantId = 2L, agentId = 90L, chatId = 2000L, linkedAt = Date(now))
    doReturn(row).whenever(dao).findByChatId(2000L)

    assertEquals(row, service.findByChatId(2000L))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramServiceTest`
Expected: FAIL — `chatId` and `findByChatId` are unresolved.

- [ ] **Step 3: Implement the two lookups**

`chatId(agentId) = dao.findByAgentId(agentId)?.chatId` (have `isConnected(agentId)` delegate to `chatId(agentId) != null`, so there is one read path); `findByChatId(chatId) = dao.findByChatId(chatId)`. KDoc on `findByChatId`: the link table is global and names the tenant, which is what a Telegram update (no JWT, no `X-Tenant-ID`) acts in.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=AgentTelegramServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/agent/server/service/AgentTelegramServiceTest.kt
git commit -m "feat(agent): look up an agent's Telegram link"
```

---

### Task 3: Answer an offer on behalf of its agent

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt`

**Interfaces:**
- Consumes: the existing `findById`, `ensurePending`, `close`, `callerService`, `agentService`, `dao`, `eventPublisher`.
- Produces:
  - `AssignmentService.acceptForAgent(agentId: Long, id: Long): AssignmentEntity`
  - `AssignmentService.declineForAgent(agentId: Long, id: Long): AssignmentEntity`
  - `accept(id)` / `decline(id)` keep their HTTP behaviour and delegate to them after resolving the caller's agent.

- [ ] **Step 1: Write the failing service tests**

First make the fixture's offers still PENDING when the service reads them: `setUp` stubs `clock.millis() = now`, so give `offer()`'s default `expiresAt = Date(now + 60_000)`.

Add to `AssignmentServiceTest` (it already mocks `dao`, `callerService`, `agentService`, `eventPublisher`; `dao.findById` takes and returns an `Optional`):

```kotlin
// --- acceptForAgent / declineForAgent (the Telegram path: no JWT)

@Test
fun `acceptForAgent - accepts the offer of that agent`() {
    doReturn(Optional.of(offer(1, 10))).whenever(dao).findById(1L)

    val accepted = service.acceptForAgent(agentId = 20L, id = 1L)

    assertEquals(AssignmentStatus.ACCEPTED, accepted.status)
    verify(eventPublisher).publishEvent(AssignmentAcceptedEvent(assignmentId = 1L, tenantId = 1L))
}

@Test
fun `acceptForAgent - another agent's offer is forbidden, before the status`() {
    doReturn(Optional.of(offer(1, 10).copy(status = AssignmentStatus.ACCEPTED))).whenever(dao).findById(1L)

    val ex = assertFailsWith<ForbiddenException> { service.acceptForAgent(agentId = 30L, id = 1L) }

    assertEquals(ErrorCode.ASSIGNMENT_ACCESS_DENIED, ex.error.code)
    verify(dao, never()).save(any())
}

@Test
fun `acceptForAgent - an offer past its TTL is not pending`() {
    doReturn(Optional.of(offer(1, 10).copy(expiresAt = Date(now - 1)))).whenever(dao).findById(1L)

    val ex = assertFailsWith<ConflictException> { service.acceptForAgent(agentId = 20L, id = 1L) }

    assertEquals(ErrorCode.ASSIGNMENT_NOT_PENDING, ex.error.code)
}

@Test
fun `declineForAgent - declines the offer of that agent`() {
    doReturn(Optional.of(offer(1, 10))).whenever(dao).findById(1L)

    val declined = service.declineForAgent(agentId = 20L, id = 1L)

    assertEquals(AssignmentStatus.DECLINED, declined.status)
    verify(eventPublisher).publishEvent(AssignmentClosedEvent(assignmentId = 1L, mandateId = 10L, tenantId = 1L))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=AssignmentServiceTest`
Expected: FAIL — `acceptForAgent` / `declineForAgent` are unresolved.

- [ ] **Step 3: Refactor the two entry points**

Replace `findPendingForAssignee` with `findPendingForAgent(id, agentId)`: `findById` (404 `ASSIGNMENT_NOT_FOUND`), then a plain `assignment.agentId != agentId` → `ForbiddenException(ASSIGNMENT_ACCESS_DENIED)`, then `ensurePending` (409 `ASSIGNMENT_NOT_PENDING`). `accept(id)` resolves the caller's party, its agent (as `ensureAssignee` does today) and calls `acceptForAgent(agent.id!!, id)`; `decline(id)` the same. Both new methods are `@Transactional`, keep the current body (mandate `ASSIGNED` + `AssignmentAcceptedEvent` / `close(..., DECLINED)`), and are public with a KDoc stating that they take the agent id because their caller has no JWT (the Telegram bot), where `accept(id)` answers 401/403 through `CallerService`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='AssignmentServiceTest,AcceptAssignmentEndpointTest,DeclineAssignmentEndpointTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt
git commit -m "feat(mandate): answer an offer on behalf of its agent"
```

---

### Task 4: The offer botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/TelegramOfferCallback.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/TelegramOfferCallbackTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/MandateOfferBotletTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId, text, buttons)` and `TelegramButton` (Task 1); `TenantService`, `LocationService`, `TenantFormatter`, `MandateMailLabels`.
- Produces:
  - `internal object TelegramOfferCallback`: `enum class Action { ACCEPT, DECLINE }`, `data class Call(val action: Action, val assignmentId: Long)`, `fun data(action: Action, assignmentId: Long): String` (`accept:9` / `decline:9`), `fun parse(data: String?): Call?`
  - `MandateOfferBotlet.send(assignment: AssignmentEntity, agent: AgentEntity, chatId: Long)` — the recipient's chat, from #135's link (KDoc); throws when Telegram refuses.

- [ ] **Step 1: Write the failing callback-format test**

```kotlin
class TelegramOfferCallbackTest {
    @Test
    fun `round-trip`() {
        assertEquals(TelegramOfferCallback.Call(Action.ACCEPT, 9L), TelegramOfferCallback.parse("accept:9"))
        assertEquals(TelegramOfferCallback.Call(Action.DECLINE, 9L), TelegramOfferCallback.parse("decline:9"))
        assertEquals("accept:9", TelegramOfferCallback.data(Action.ACCEPT, 9L))
        assertEquals("decline:9", TelegramOfferCallback.data(Action.DECLINE, 9L))
    }

    @Test
    fun `anything else is not a call`() {
        assertNull(TelegramOfferCallback.parse(null))
        assertNull(TelegramOfferCallback.parse(""))
        assertNull(TelegramOfferCallback.parse("accept"))
        assertNull(TelegramOfferCallback.parse("accept:"))
        assertNull(TelegramOfferCallback.parse("accept:abc"))
        assertNull(TelegramOfferCallback.parse("open:9"))
        assertNull(TelegramOfferCallback.parse("accept:9:extra"))
    }
}
```

- [ ] **Step 2: Write the failing botlet test**

Mirror `MandateOfferMailetTest`'s fixtures (`tenant`, `mandate`, `assignment`), with `party` named and a linked agent:

```kotlin
class MandateOfferBotletTest {
    private val tenantService = mock<TenantService>()
    private val locationService = mock<LocationService>()
    private val client = mock<TelegramClient>()
    private val botlet = MandateOfferBotlet(tenantService, locationService, client)

    private val tenant = TenantEntity(id = 1L, name = "Ndopify", timeZone = "Africa/Douala")
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
        doReturn(LocationEntity(id = 2370201L, name = "Yaounde")).whenever(locationService).findByIdOrNull(2370201L)
        doReturn(LocationEntity(id = 111L, name = "Bastos")).whenever(locationService).findByIdOrNull(111L)
    }

    @Test
    fun `a search offer carries its criteria, its deadline and the two buttons`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 1L, tenantId = 1L, cityId = 2370201L, neighborhoodIds = listOf(111L),
                listingType = ListingType.RENTAL, propertyType = PropertyType.APARTMENT, rooms = "2",
                price = 150000, currencyCode = "XAF", pricePeriodicityType = PeriodicityType.MONTHLY,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Bonjour Kim Possible"))
        assertTrue(text.firstValue.contains("Recherche: Location - Appartement"))
        assertTrue(text.firstValue.contains("Quartier(s): Bastos"))
        assertTrue(text.firstValue.contains("Budget: 150"))
        assertTrue(text.firstValue.contains("Vous avez jusqu'au"))
        assertEquals(listOf("Accepter", "Refuser"), buttons.firstValue.map { it.text })
        assertEquals(listOf("accept:99", "decline:99"), buttons.firstValue.map { it.callbackData })
    }

    @Test
    fun `a listing offer carries the price, the area and the availability`() {
        val assignment = AssignmentEntity(
            id = 99L,
            mandate = MandateEntity(
                id = 2L, tenantId = 1L, cityId = 2370201L, listingType = ListingType.SALE,
                propertyType = PropertyType.VILLA, price = 25_000_000, currencyCode = "XAF",
                area = 250, availableFrom = LocalDate.of(2026, 12, 1), mandateType = MandateType.LISTING,
            ),
            agentId = 20L, status = AssignmentStatus.PENDING, expiresAt = Date(1_800_000_000_000L),
        )

        botlet.send(assignment, agent, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Bien: Vente - Villa"))
        assertTrue(text.firstValue.contains("Prix: 25"))
        assertTrue(text.firstValue.contains("Surface: 250 m²"))
        assertTrue(text.firstValue.contains("Disponible à partir du: 01/12/2026"))
    }
}
```

Use `argumentCaptor` on `sendMessage` as shown. The fixture's `dateFormat` is blank, so `TenantFormatter` falls back to `dd/MM/yyyy`: `01/12/2026` is what the listing test asserts.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramOfferCallbackTest,MandateOfferBotletTest'`
Expected: FAIL — the bot and its callback object are unresolved.

- [ ] **Step 4: Implement the callback object and the botlet**

`TelegramOfferCallback.parse`: split on `:`, require 2 parts, `toLongOrNull`, and map `accept` / `decline`; anything else `null`.

`MandateOfferBotlet.send(assignment, agent, chatId)`: load the tenant (`tenantService.findById(mandate.tenantId!!)`), the city name, the formatter, then build the text with `MandateMailLabels.listingType / propertyType / neighbourhoods / budget / priceLabel / price`. Exact copy:

```text
Bonjour <recipient_name>,

<Un client recherche un bien à <city>, et nous vous proposons son mandat en exclusivité. | Un propriétaire vous propose son mandat en exclusivité à <city>.>

<Recherche|Bien>: <listing_type>[- <property_type>]
[Pièces: <rooms>]
Ville: <city>
[Quartier(s): <neighbourhoods>]
[Surface: <area> m²]                                  (listing only, when set)
[<price_label>: <price>]                              (listing only)
[Disponible à partir du: <available_from>]            (listing only, when set)
[Budget: <budget>]                                    (search only)

Vous avez jusqu'au <deadline_date> à <deadline_time> pour accepter ce mandat. Passé ce délai, il sera proposé à un autre agent.
```

Match the mail templates' wording (`offer.mjml` / `offer_listing.mjml`); omit a line whose value is null or blank, one item per line. Then `client.sendMessage(chatId, text, listOf(TelegramButton("Accepter", TelegramOfferCallback.data(ACCEPT, assignment.id!!)), TelegramButton("Refuser", TelegramOfferCallback.data(DECLINE, assignment.id!!))))`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramOfferCallbackTest,MandateOfferBotletTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot
git commit -m "feat(mandate): offer a mandate over Telegram"
```

---

### Task 5: Route the offer to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentOfferNotifier.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/event/AssignmentEventListener.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentOfferNotifierTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/event/AssignmentEventListenerTest.kt`

**Interfaces:**
- Consumes: `MandateOfferBotlet.send` (Task 4), the existing `MandateOfferMailet.send`, `AgentTelegramService.chatId(agentId)` (Task 2).
- Produces:
  - `AssignmentOfferNotifier.send(assignment: AssignmentEntity, agent: AgentEntity)`
  - `AssignmentService.sendOfferNotification(id: Long)` replacing `sendOfferEmail` — same contract: `@Transactional`, only a PENDING offer, failures propagate to the listener which logs them.
  - `AssignmentService`'s constructor takes `offerNotifier: AssignmentOfferNotifier` instead of `offerMailet: MandateOfferMailet`.

- [ ] **Step 1: Write the failing notifier test**

```kotlin
class AssignmentOfferNotifierTest {
    private val botlet = mock<MandateOfferBotlet>()
    private val mailet = mock<MandateOfferMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = AssignmentOfferNotifier(botlet, mailet, telegramService)

    private val assignment = AssignmentEntity(id = 99L, agentId = 20L, status = AssignmentStatus.PENDING)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(assignment, agent)

        verify(mailet).send(assignment, agent)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(assignment, agent)

        verify(botlet).send(assignment, agent, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(assignment, agent, 1000L)

        notifier.send(assignment, agent)

        verify(mailet).send(assignment, agent)
    }
}
```

- [ ] **Step 2: Write the failing service / listener tests**

In `AssignmentServiceTest`, make the inline `agentService = mock()` a field `private val agentService = mock<AgentService>()`, add `private val offerNotifier = mock<AssignmentOfferNotifier>()`, and pass both to the constructor in place of `agentService = mock()` and `offerMailet = mock()`. Then add:

```kotlin
@Test
fun `sendOfferNotification - a pending offer goes to the notifier`() {
    val assignment = offer(1, 10)
    val agent = AgentEntity(id = 20L)
    doReturn(Optional.of(assignment)).whenever(dao).findById(1L)
    doReturn(agent).whenever(agentService).findById(20L)

    service.sendOfferNotification(1L)

    verify(offerNotifier).send(assignment, agent)
}

@Test
fun `sendOfferNotification - a closed offer is not notified`() {
    doReturn(Optional.of(offer(1, 10).copy(status = AssignmentStatus.EXPIRED))).whenever(dao).findById(1L)

    service.sendOfferNotification(1L)

    verifyNoInteractions(offerNotifier)
}
```

In `AssignmentEventListenerTest`, rename the two `sendOfferEmail(1L)` stubs / verifies to `sendOfferNotification(1L)` and the test names `the agent is notified, in its tenant` / `a failing notification is only logged`.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='AssignmentOfferNotifierTest,AssignmentServiceTest,AssignmentEventListenerTest'`
Expected: FAIL — `AssignmentOfferNotifier` and `sendOfferNotification` are unresolved.

- [ ] **Step 4: Implement the notifier and rewire the offer**

`AssignmentOfferNotifier`: `val chatId = telegramService.chatId(agent.id!!)`; `if (chatId == null) { mailet.send(assignment, agent); return }`, then `try { botlet.send(assignment, agent, chatId) } catch (ex: Exception) { LOGGER.warn("Telegram offer failed for Assignment#${assignment.id}, falling back to e-mail", ex); mailet.send(assignment, agent) }`. Catching `Exception`, not only `TelegramException`: any transport failure must fall back. KDoc: the channel is #135's link, the e-mail is the fallback, and the failure is logged without the token nor the URL.

`AssignmentService`: rename the method and call `offerNotifier.send(assignment, agentService.findById(assignment.agentId))`; keep the PENDING guard and `@Transactional`. Update `AssignmentEventListener.onAssignmentCreated` to `service.sendOfferNotification(event.assignmentId)`. Update any other construction of `AssignmentService` (tests) to `offerNotifier`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='AssignmentOfferNotifierTest,AssignmentServiceTest,AssignmentEventListenerTest'`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentOfferNotifier.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/event/AssignmentEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentOfferNotifierTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/service/AssignmentServiceTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/event/AssignmentEventListenerTest.kt
git commit -m "feat(mandate): route an offer to Telegram or e-mail"
```

---

### Task 6: Answer an offer from the chat

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCallbackHandler.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandler.kt`
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/TelegramAssignmentCallbackHandler.kt`
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/TelegramAssignmentCallbackHandlerTest.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandlerTest.kt`

**Interfaces:**
- Consumes: Task 1's `TelegramClient.answerCallbackQuery` / `editMessageText`, `TelegramCallbackQuery`; Task 2's `AgentTelegramService.findByChatId(chatId)` returning #135's `AgentTelegramEntity` (`tenantId`, `agentId`); Task 3's `acceptForAgent` / `declineForAgent`; Task 4's `TelegramOfferCallback.parse`; #135's `TelegramUpdateHandler(client, commands)`.
- Produces:
  - `interface TelegramCallbackHandler { fun handle(callback: TelegramCallbackQuery) }` (`platform/telegram`)
  - `TelegramUpdateHandler(client, commands: List<TelegramCommand>, callbackHandler: TelegramCallbackHandler? = null)`: a `callbackQuery` update goes to the handler (and never gets the `OK` reply); a message keeps #135's command dispatch and `OK`.
  - `TelegramAssignmentCallbackHandler(client, telegramService, service) : TelegramCallbackHandler`.

- [ ] **Step 1: Write the failing handler tests**

```kotlin
class TelegramAssignmentCallbackHandlerTest {
    private val client = mock<TelegramClient>()
    private val telegramService = mock<AgentTelegramService>()
    private val service = mock<AssignmentService>()
    private val handler = TelegramAssignmentCallbackHandler(client, telegramService, service)

    private val link = AgentTelegramEntity(id = 9L, tenantId = 1L, agentId = 20L, chatId = 1000L)
    private val message = TelegramMessage(messageId = 50L, chat = TelegramChat(id = 1000L), text = "Offre")

    private fun callback(data: String?, m: TelegramMessage? = message) =
        TelegramCallbackQuery(id = "cb-1", data = data, message = m)

    @BeforeTest
    fun setUp() {
        doReturn(link).whenever(telegramService).findByChatId(1000L)
    }

    @AfterTest
    fun tearDown() {
        TenantContext.remove()
    }

    @Test
    fun `accept answers the offer and edits the message`() {
        handler.handle(callback("accept:99"))

        verify(service).acceptForAgent(20L, 99L)
        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nMandat accepté.")
    }

    @Test
    fun `decline answers the offer and edits the message`() {
        handler.handle(callback("decline:99"))

        verify(service).declineForAgent(20L, 99L)
        verify(client).answerCallbackQuery("cb-1", "Mandat refusé.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nMandat refusé.")
    }

    @Test
    fun `an offer that is no longer pending says so and drops the buttons`() {
        doThrow(ConflictException(Error(code = ErrorCode.ASSIGNMENT_NOT_PENDING)))
            .whenever(service).acceptForAgent(20L, 99L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Ce mandat n'est plus disponible.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nCe mandat n'est plus disponible.")
    }

    @Test
    fun `another agent's offer changes nothing and reveals nothing`() {
        doThrow(ForbiddenException(Error(code = ErrorCode.ASSIGNMENT_ACCESS_DENIED)))
            .whenever(service).acceptForAgent(20L, 99L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verify(client, never()).editMessageText(any(), any(), any())
    }

    @Test
    fun `an unknown chat id changes nothing`() {
        doReturn(null).whenever(telegramService).findByChatId(1000L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verifyNoInteractions(service)
        verify(client, never()).editMessageText(any(), any(), any())
    }

    @Test
    fun `malformed data is only answered`() {
        handler.handle(callback("open:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verifyNoInteractions(service)
        verify(client, never()).editMessageText(any(), any(), any())
    }

    @Test
    fun `a callback without a message is only answered`() {
        handler.handle(callback("accept:99", m = null))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verifyNoInteractions(service)
    }

    @Test
    fun `a failing edit does not undo the answer`() {
        doThrow(RuntimeException("message to edit not found"))
            .whenever(client).editMessageText(any(), any(), any())

        handler.handle(callback("accept:99"))

        verify(service).acceptForAgent(20L, 99L)
        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
    }

    @Test
    fun `an unexpected failure is contained and the tap still answered`() {
        doThrow(RuntimeException("boom")).whenever(service).acceptForAgent(20L, 99L)

        handler.handle(callback("accept:99"))

        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
    }
}
```

Add to `TelegramUpdateHandlerTest` (#135 constructs it as `TelegramUpdateHandler(client, commands)`; use `emptyList()` for the commands):

```kotlin
@Test
fun `a callback goes to the callback handler, not to OK`() {
    val callbacks = mock<TelegramCallbackHandler>()
    val routed = TelegramUpdateHandler(client, emptyList(), callbacks)

    routed.handle(TelegramUpdate(updateId = 1L, callbackQuery = TelegramCallbackQuery(id = "cb-1", data = "accept:9")))

    verify(callbacks).handle(TelegramCallbackQuery(id = "cb-1", data = "accept:9", message = null))
    verifyNoInteractions(client)
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramAssignmentCallbackHandlerTest,TelegramUpdateHandlerTest'`
Expected: FAIL — `TelegramAssignmentCallbackHandler` and `TelegramCallbackHandler` are unresolved.

- [ ] **Step 3: Implement the routing and the handler**

`TelegramUpdateHandler.handle`: `update.callbackQuery?.let { callbackHandler?.handle(it); return }` then #135's command dispatch (`update.message ?: return; commands.firstNotNullOfOrNull { it.replyTo(message) } ?: "OK"`).

`TelegramAssignmentCallbackHandler.handle`, in one `try` / `finally`:

1. `var toast = "Action impossible."`; `val call = TelegramOfferCallback.parse(callback.data) ?: return`; `val chatId = callback.message?.chat?.id ?: return`.
2. `val link = telegramService.findByChatId(chatId) ?: return`; `TenantContext.set(link.tenantId)` — the link table is global, and its row names the tenant the action runs in.
3. `try { when (call.action) { ACCEPT -> service.acceptForAgent(link.agentId, call.assignmentId); DECLINE -> service.declineForAgent(link.agentId, call.assignmentId) }; toast = if (ACCEPT) "Mandat accepté." else "Mandat refusé."; edit(callback, toast) }`
4. `catch (ex: ConflictException) { toast = "Ce mandat n'est plus disponible."; edit(callback, toast) }`
5. `catch (ex: WutsiException) { LOGGER.warn("Telegram callback refused: ${ex.error.code}") }` — a 404 / 403 changes nothing and edits nothing: it must not reveal that the assignment exists.
6. `catch (ex: Exception) { LOGGER.error("Telegram callback failed", ex) }`
7. `finally { client.answerCallbackQuery(callback.id, toast) }` — once per tap, whatever happened; and `TenantContext.remove()`.

`edit(callback, outcome)`: `callback.message?.let { client.editMessageText(it.chat.id, it.messageId, listOfNotNull(it.text, outcome).joinToString("\n\n")) }`, in its own `try`/`catch` that logs a warning: a deleted message must not undo the transition.

Wire in `TelegramConfiguration`: `telegramUpdateHandler(client, commands: List<TelegramCommand>, callbackHandler: TelegramCallbackHandler)` → `TelegramUpdateHandler(client, commands, callbackHandler)`.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest='TelegramAssignmentCallbackHandlerTest,TelegramUpdateHandlerTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramCallbackHandler.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandler.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/mandate/server/bot/TelegramAssignmentCallbackHandler.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/config/platform/TelegramConfiguration.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/mandate/server/bot/TelegramAssignmentCallbackHandlerTest.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramUpdateHandlerTest.kt
git commit -m "feat(mandate): answer an offer from the Telegram chat"
```

---

### Task 7: Full-context test of a tap

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramAssignmentCallbackEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramAssignmentCallbackEndpointTest.kt`

**Interfaces:**
- Consumes: the webhook endpoint and its public path (#134), the beans from Tasks 2, 3 and 6.
- Produces: no production code; proves the wiring, the global link lookup and the transitions against MySQL.

- [ ] **Step 1: Write the seed**

`clean.sql` then two tenants; agent 20 (party 200, user 11) in tenant 1 and agent 90 (party 900) in tenant 2, each with a `T_AGENT_TELEGRAM` link row (`tenant_id`, `agent_id`, `chat_id`, `linked_at`): chat 1000 for agent 20, chat 2000 for agent 90. Assignments: 1 PENDING for agent 20 (mandate 1), 4 PENDING for agent 20 but past its TTL (`expires_at = '2020-01-01'`), 9 PENDING for agent 90 (mandate 9, tenant 2). Model it on `db/test/mandate/DeclineAssignmentEndpoint.sql` plus #135's `T_AGENT_TELEGRAM` table.

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
@TestPropertySource(
    properties = [
        "ndopify.telegram.transport=webhook",
        "ndopify.telegram.webhook.url=https://test.example/telegram/webhook",
        "ndopify.telegram.webhook.secret=test-secret",
    ],
)
@Sql(value = ["/db/test/clean.sql", "/db/test/telegram/TelegramAssignmentCallbackEndpoint.sql"])
class TelegramAssignmentCallbackEndpointTest : TenantAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var assignmentDao: AssignmentRepository

    @Autowired
    private lateinit var mandateDao: MandateRepository

    private fun tap(data: String, chatId: Long, messageId: Long = 50L): ResponseEntity<String> {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("X-Telegram-Bot-Api-Secret-Token", "test-secret")
        }
        val body = """
            {"update_id":1,"callback_query":{"id":"cb-1","data":"$data",
             "message":{"message_id":$messageId,"chat":{"id":$chatId},"text":"Offre"}}}
        """.trimIndent()
        return rest.postForEntity("/telegram/webhook", HttpEntity(body, headers), String::class.java)
    }

    @Test
    fun `accept answers the offer`() {
        assertEquals(HttpStatus.OK, tap("accept:1", 1000L).statusCode)

        assertEquals(AssignmentStatus.ACCEPTED, assignmentDao.findById(1L).get().status)
        assertEquals(MandateStatus.ASSIGNED, mandateDao.findById(1L).get().status)
        verify(client).answerCallbackQuery("cb-1", "Mandat accepté.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nMandat accepté.")
        awaitAsyncTasks() // the customer's assigned e-mail
        assertTrue(smtp.waitForIncomingEmail(5000, 1), "no email received")
    }

    @Test
    fun `a tap from another tenant's chat answers that tenant's offer`() {
        assertEquals(HttpStatus.OK, tap("decline:9", 2000L).statusCode)

        TenantContext.set(2L)
        try {
            assertEquals(AssignmentStatus.DECLINED, assignmentDao.findById(9L).get().status)
        } finally {
            TenantContext.set(TENANT_ID)
        }
        verify(client).answerCallbackQuery("cb-1", "Mandat refusé.")
    }

    @Test
    fun `a tap on an expired offer changes nothing`() {
        assertEquals(HttpStatus.OK, tap("accept:4", 1000L).statusCode)

        assertEquals(AssignmentStatus.PENDING, assignmentDao.findById(4L).get().status)
        verify(client).answerCallbackQuery("cb-1", "Ce mandat n'est plus disponible.")
        verify(client).editMessageText(1000L, 50L, "Offre\n\nCe mandat n'est plus disponible.")
    }

    @Test
    fun `a tap from a chat of another agent changes nothing`() {
        assertEquals(HttpStatus.OK, tap("accept:1", 2000L).statusCode)

        assertEquals(AssignmentStatus.PENDING, assignmentDao.findById(1L).get().status)
        verify(client).answerCallbackQuery("cb-1", "Action impossible.")
        verify(client, never()).editMessageText(any(), any(), any())
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramAssignmentCallbackEndpointTest`
Expected: FAIL — the link rows or the callback routing are missing, until the wiring is right.

- [ ] **Step 4: Fix the wiring until it passes**

Likely gaps: the `TelegramAssignmentCallbackHandler` isn't injected into `TelegramUpdateHandler` (Task 6), `/telegram/webhook` isn't in `PublicEndpoints.GLOBAL` (#134), or the seed misses a column. Change production code only for a real gap the test finds.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramAssignmentCallbackEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramAssignmentCallbackEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramAssignmentCallbackEndpointTest.kt
git commit -m "test(telegram): cover an offer answered from the chat"
```

---

### Task 8: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's rules, for the next notification.

- [ ] **Step 1: Document the botlet convention and the offer channel**

In `modules/ndopify-server/CLAUDE.md`:
- Add `bot/` to the domain layering next to `mail/`: a `*Botlet` is the Telegram counterpart of a `*Mailet` — a `@Service` that builds one message and hands it to the `platform/telegram` client; no shared interface. The offer `*Botlet` is `MandateOfferBotlet`; callback handlers that answer a bot message are `*CallbackHandler` in the same package.
- In the **mandate** bullet's dispatch paragraph, replace "`MandateOfferMailet` e-mails the agent" with the channel rule: `AssignmentOfferNotifier` Telegram-messages a linked agent (`AgentTelegramService.chatId(agentId)`) through `MandateOfferBotlet`, e-mails the others, and falls back to the e-mail when Telegram fails; tapping `Accepter` / `Refuser` answers through the agent-scoped `acceptForAgent` / `declineForAgent` (no JWT), and an offer already closed is only reported as no longer available.
- In the **agent** bullet, note that `AgentTelegramService.findByChatId` reads #135's global link and names the tenant, which is how a Telegram update (no JWT nor `X-Tenant-ID`) acts in it.
- Rename `AssignmentService.sendOfferEmail` to `sendOfferNotification` wherever the *Asynchronous e-mails* list names it.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines, which fails the build if coverage drops) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(mandate): document the Telegram offer notifications"
```
