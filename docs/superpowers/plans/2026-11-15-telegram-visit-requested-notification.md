# Telegram Visit Request Notification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Tell the agent over Telegram that its customer asked to visit a listing — who, which listing, the requested slot, the type and the note — with the e-mail as the fallback for an unlinked agent or a failed send.

**Architecture:** `VisitRequestedEvent` still reaches `VisitEventListener` (async, after commit), which calls `VisitService.sendRequestedNotification` (renamed from `sendRequestedEmail`). That method delegates to a new `VisitRequestedNotifier`: the agent's chat id (`AgentTelegramService.chatId`) picks `VisitRequestedBotlet`, otherwise the existing `VisitRequestedMailet`; any botlet failure logs and sends the mailet, so a request is never lost.

**Tech Stack:** Kotlin 2.3.10, JDK 25, Spring Boot 4.1.2, Jackson 3 (`tools.jackson`), JUnit 5 + `kotlin.test` + mockito-kotlin, MySQL for the server's integration tests.

**Spec:** https://github.com/wutsi/ndopify-mono/issues/148 (the story is the spec; it was groomed on 2026-11-15 with the "show the requested visit type" decision).

## Global Constraints

- **Precondition: #136 is merged** (it is, on `main`). This plan consumes its code: `TelegramClient.sendMessage(chatId, text, buttons)`, `TelegramButton(text, callbackData, url)`, `AgentTelegramService.chatId(agentId)`, and the notifier/botlet convention (`docs/superpowers/plans/2026-11-15-telegram-assignment-notification.md`).
- Every change is under `modules/ndopify-server/`; no new Maven dependency.
- No database migration, no `ndopify-dto` / `ndopify-web-core` change, no UI page, no new config.
- The bot's text is French and mirrors `mail/visit/requested.mjml`; code, comments, logs and docs are English.
- **One addition to the e-mail's wording:** the requested visit type (`Type: Sur place` / `Type: Visite vidéo`). Everything else mirrors the mailet: customer name, WhatsApp, listing title (or `Bien n° <id>`), requested date and time, note.
- **The botlet takes the chat id:** `send(visit, customer, agent, listing, chatId)`. The story writes `send(visit, customer, agent, listing)`, but every `*Botlet` receives the link's chat id as its last parameter (`MandateOfferBotlet.send(assignment, agent, chatId)`); the notifier resolves it from `AgentTelegramService.chatId`.
- The e-mail is the fallback, not a second channel: a linked agent gets no request e-mail; a failed Telegram send logs (never the token nor the URL) and sends the mailet.
- Never log, throw or report the bot token or the Telegram URL.
- The `qa` profile must make no Telegram call; every test reaches only a mocked `TelegramClient`.
- Assertions use `kotlin.test`; mocks use `com.nhaarman.mockitokotlin2`.
- `mvn verify` in `modules/ndopify-server` stays green; its JaCoCo line threshold is 0.95.
- `ndopify-server` tests need a local MySQL (see the root `CLAUDE.md`).
- KDoc on classes and public methods; a service method's KDoc lists its error codes in the order they're checked.
- One module per commit (only `ndopify-server` here); conventional commit messages, `Refs #148` in the body of each.

## Review Focus

- A linked agent whose bot is blocked or Telegram is down: the e-mail is sent and no exception escapes the listener — a time-sensitive request is never lost.
- An unlinked agent: no Telegram call at all, only the e-mail.
- A tenant without `partnerCentralUrl`: the message carries no button, and no empty keyboard is sent.
- A `VIDEO` request: the message says `Type: Visite vidéo`, not `Sur place`; an `ON_SITE` one says `Sur place`.
- No WhatsApp number, no note: the lines are omitted, never rendered empty.
- A visit no longer `REQUESTED` when the listener runs: neither channel is called.
- Only the visit's agent is addressed: the notifier resolves one chat id, from `visit.agentId`; the customer and the listing's agent are never looked up (co-brokerage visits stay with #154).

---

### Task 1: The request botlet

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt`

**Interfaces:**
- Consumes: `TelegramClient.sendMessage(chatId: Long, text: String, buttons: List<TelegramButton> = emptyList())`; `TelegramButton(text: String, callbackData: String? = null, url: String? = null)`; `TenantService.findById(id: Long): TenantEntity`; `TenantFormatter(tenant)` (`phone`, `date`, `time`); `CustomerEntity.whatsappNumber: String?`; `VisitEntity.type: VisitType`, `.requestedAt: Date`, `.note: String?`; `ListingEntity.title: String?`.
- Produces: `VisitRequestedBotlet(tenantService, client)` with `send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity, chatId: Long)`.

- [ ] **Step 1: Write the failing botlet tests**

Model the fixtures on `VisitRequestedMailetTest` (`tenant`, `customer`, `agent`, `listing`, `visit`, its `date(...)` helper) and assert the whole text:

```kotlin
class VisitRequestedBotletTest {
    private val tenantService = mock<TenantService>()
    private val client = mock<TelegramClient>()
    private val botlet = VisitRequestedBotlet(tenantService, client)

    private val tenant = TenantEntity(
        id = 1L,
        name = "Ndopify",
        partnerCentralUrl = "https://partner.ndopify.com/",
        dateFormat = "dd/MM/yyyy",
        timeFormat = "HH:mm",
    )
    private val customer = CustomerEntity(
        id = 10L,
        party = PartyEntity(id = 100L, firstName = "Ray", lastName = "Sponsible"),
        whatsappNumber = "+237600000001",
    )
    private val agent = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
    )
    private val listing = ListingEntity(id = 101L, title = "Nice apartment")
    private val visit = VisitEntity(
        id = 1L,
        tenantId = 1L,
        matchId = 1L,
        listingId = 101L,
        agentId = 20L,
        customerId = 10L,
        status = VisitStatus.REQUESTED,
        type = VisitType.ON_SITE,
        requestedAt = date(2026, 12, 1, 10, 0),
        note = "Je viens avec ma femme",
    )

    @BeforeTest
    fun setUp() {
        doReturn(tenant).whenever(tenantService).findById(1L)
    }

    @Test
    fun `an on-site request carries the customer, the slot, the type and the note`() {
        botlet.send(visit, customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertEquals(
            """
            Bonjour Kim Possible,

            Ray Sponsible souhaite visiter un bien que vous lui avez proposé.

            Bien: Nice apartment
            Date souhaitée: 01/12/2026 à 10:00
            Type: Sur place
            WhatsApp: +237 6 00 00 00 01
            Note: Je viens avec ma femme

            Confirmez la visite avec la date et l'heure qui vous conviennent.
            """.trimIndent(),
            text.firstValue,
        )
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/1")),
            buttons.firstValue,
        )
    }

    @Test
    fun `a video request says so`() {
        botlet.send(visit.copy(type = VisitType.VIDEO), customer, agent, listing, 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Type: Visite vidéo"))
        assertFalse(text.firstValue.contains("Type: Sur place"))
    }

    @Test
    fun `no whatsapp and a blank note leave no line`() {
        botlet.send(
            visit.copy(note = " "),
            customer.copy(whatsappNumber = null),
            agent,
            listing,
            1000L,
        )

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertFalse(text.firstValue.contains("WhatsApp:"))
        assertFalse(text.firstValue.contains("Note:"))
    }

    @Test
    fun `a listing without a title is named by its id`() {
        botlet.send(visit, customer, agent, listing.copy(title = null), 1000L)

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Bien: Bien n° 101"))
    }

    @Test
    fun `a tenant without Partner Central gets no button`() {
        doReturn(tenant.copy(partnerCentralUrl = null)).whenever(tenantService).findById(1L)

        botlet.send(visit, customer, agent, listing, 1000L)

        verify(client).sendMessage(eq(1000L), any(), eq(emptyList<TelegramButton>()))
    }

    private fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Date.from(
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.of("Africa/Douala"))
                .toInstant(),
        )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest`
Expected: FAIL — `VisitRequestedBotlet` is unresolved.

- [ ] **Step 3: Implement the botlet**

`VisitRequestedBotlet(tenantService, client)`, `@Service`, one `send(visit, customer, agent, listing, chatId)`. KDoc: mirrors `VisitRequestedMailet`, the recipient's chat comes from #135's link (`AgentTelegramService.chatId`), never the agent's e-mail; Telegram refusing it throws, so the notifier falls back to the e-mail. Build the text as:

```text
Bonjour <agent.party.fullName()>,

<customer.party.fullName()> souhaite visiter un bien que vous lui avez proposé.

Bien: <listing.title, or "Bien n° <listing.id>" when blank>
Date souhaitée: <formatter.date(visit.requestedAt)> à <formatter.time(visit.requestedAt)>
Type: <"Sur place" for ON_SITE, "Visite vidéo" for VIDEO>
[WhatsApp: <formatter.phone(customer.whatsappNumber)>]   (only when it formats to a value)
[Note: <visit.note>]                                     (only when not blank)

Confirmez la visite avec la date et l'heure qui vous conviennent.
```

Leave the optional lines out entirely. Then:

```kotlin
client.sendMessage(
    chatId,
    text,
    tenant.partnerCentralUrl?.removeSuffix("/")?.let { url ->
        listOf(TelegramButton("Voir la visite", url = "$url/visits/${visit.id}"))
    } ?: emptyList(),
)
```

The `type` label: a private `when` returning `"Sur place"` / `"Visite vidéo"`; `UNKNOWN` never reaches it (the request endpoint rejects it).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedBotletTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotlet.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/bot/VisitRequestedBotletTest.kt
git commit -m "feat(visit): notify a visit request over Telegram" -m "Refs #148"
```

---

### Task 2: Route the request to Telegram or e-mail

**Files:**
- Create: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitRequestedNotifier.kt`
- Test: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitRequestedNotifierTest.kt`

**Interfaces:**
- Consumes: Task 1's `VisitRequestedBotlet.send(visit, customer, agent, listing, chatId)`; `VisitRequestedMailet.send(visit, customer, agent, listing)`; `AgentTelegramService.chatId(agentId: Long): Long?`.
- Produces: `VisitRequestedNotifier(botlet, mailet, telegramService)` with `send(visit: VisitEntity, customer: CustomerEntity, agent: AgentEntity, listing: ListingEntity)`.

- [ ] **Step 1: Write the failing notifier test**

Mirror `AssignmentOfferNotifierTest`:

```kotlin
class VisitRequestedNotifierTest {
    private val botlet = mock<VisitRequestedBotlet>()
    private val mailet = mock<VisitRequestedMailet>()
    private val telegramService = mock<AgentTelegramService>()
    private val notifier = VisitRequestedNotifier(botlet, mailet, telegramService)

    private val visit = VisitEntity(id = 1L, agentId = 20L, status = VisitStatus.REQUESTED)
    private val customer = CustomerEntity(id = 10L)
    private val agent = AgentEntity(id = 20L, party = PartyEntity(firstName = "Kim", lastName = "Possible"))
    private val listing = ListingEntity(id = 101L)

    @Test
    fun `an unlinked agent is e-mailed, Telegram is not called`() {
        doReturn(null).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).send(visit, customer, agent, listing)
        verifyNoInteractions(botlet)
    }

    @Test
    fun `a linked agent is messaged, not e-mailed`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)

        notifier.send(visit, customer, agent, listing)

        verify(botlet).send(visit, customer, agent, listing, 1000L)
        verifyNoInteractions(mailet)
    }

    @Test
    fun `a failed Telegram send falls back to the e-mail`() {
        doReturn(1000L).whenever(telegramService).chatId(20L)
        doThrow(TelegramException("Forbidden: bot was blocked by the user"))
            .whenever(botlet).send(visit, customer, agent, listing, 1000L)

        notifier.send(visit, customer, agent, listing)

        verify(mailet).send(visit, customer, agent, listing)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedNotifierTest`
Expected: FAIL — `VisitRequestedNotifier` is unresolved.

- [ ] **Step 3: Implement the notifier**

`send(visit, customer, agent, listing)`: `val chatId = telegramService.chatId(agent.id!!)`; null → `mailet.send(visit, customer, agent, listing)`; else `try { botlet.send(visit, customer, agent, listing, chatId) } catch (ex: Exception) { LOGGER.warn("Telegram visit request failed for Visit#${visit.id}, falling back to e-mail", ex); mailet.send(visit, customer, agent, listing) }`. Catch `Exception`, not only `TelegramException`. KDoc: the channel is #135's link; the e-mail is the fallback; failures are logged without the token nor the URL.

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitRequestedNotifierTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitRequestedNotifier.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitRequestedNotifierTest.kt
git commit -m "feat(visit): route a request to Telegram or e-mail" -m "Refs #148"
```

---

### Task 3: Wire the service and the listener

**Files:**
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt` (constructor, `sendRequestedEmail`)
- Modify: `modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt` (`onVisitRequested`)
- Modify: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt`

**Interfaces:**
- Consumes: Task 2's `VisitRequestedNotifier.send(visit, customer, agent, listing)`.
- Produces: `VisitService.sendRequestedNotification(visitId: Long): Unit`, replacing `sendRequestedEmail`; `VisitService`'s constructor takes `requestedNotifier: VisitRequestedNotifier` instead of `requestedMailet: VisitRequestedMailet`.

- [ ] **Step 1: Update the failing service test**

In `VisitServiceTest`, replace `private val requestedMailet = mock<VisitRequestedMailet>()` with `private val requestedNotifier = mock<VisitRequestedNotifier>()`, pass `requestedNotifier` where the constructor takes `requestedMailet`, and add:

```kotlin
@Test
fun `requested - the agent is notified`() {
    service.sendRequestedNotification(1L)

    verify(requestedNotifier).send(visit, customer, agent20, coBrokerageListing)
}

@Test
fun `requested - nothing once no longer REQUESTED`() {
    doReturn(Optional.of(visit.copy(status = VisitStatus.EXPIRED))).whenever(dao).findById(1L)

    service.sendRequestedNotification(1L)

    verifyNoInteractions(requestedNotifier)
}

@Test
fun `requested - nothing for an unknown visit`() {
    doReturn(Optional.empty<VisitEntity>()).whenever(dao).findById(999L)

    service.sendRequestedNotification(999L)

    verifyNoInteractions(requestedNotifier)
}
```

(The `setUp` already stubs `dao.findById(1L)`, `agentService.findById(20L)`, `customerService.findById(10L)` and `listingService.findById(101L)`; `coBrokerageListing` is the listing 101 fixture.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: FAIL — `sendRequestedNotification` and `VisitRequestedNotifier` are unresolved.

- [ ] **Step 3: Rewire the service and the listener**

`VisitService`: constructor `private val requestedNotifier: VisitRequestedNotifier`; rename `sendRequestedEmail(id: Long)` to `sendRequestedNotification`, keep its `@Transactional` and its guard (`visit == null` or `status != REQUESTED` → return); replace `requestedMailet.send(...)` with `requestedNotifier.send(visit, customerService.findById(visit.customerId), agentService.findById(visit.agentId), listingService.findById(visit.listingId))`. KDoc: "Notifies the agent of the request: Telegram when its chat is linked, the e-mail otherwise or as the fallback; nothing when the visit is no longer REQUESTED."

`VisitEventListener.onVisitRequested`: call `service.sendRequestedNotification(event.visitId)`; rename its KDoc sentence and keep the log keys.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd modules/ndopify-server && mvn test -Dtest=VisitServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/service/VisitService.kt \
        modules/ndopify-server/src/main/kotlin/com/wutsi/ndopify/visit/server/event/VisitEventListener.kt \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/visit/server/service/VisitServiceTest.kt
git commit -m "feat(visit): notify the agent of a request through the notifier" -m "Refs #148"
```

---

### Task 4: Full-context test of a request

**Files:**
- Create: `modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitRequestedEndpoint.sql`
- Create: `modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitRequestedEndpointTest.kt`

**Interfaces:**
- Consumes: the create-visit endpoint (`POST /v1/visits`), `VisitFixtures.sql` / `CreateVisitEndpoint.sql`, the beans from Tasks 1–3, `@MockitoBean TelegramClient`, `awaitAsyncTasks()`.
- Produces: no production code; proves the wiring, the link lookup, the message and the no-e-mail rule against MySQL.

- [ ] **Step 1: Write the seed**

`TelegramVisitRequestedEndpoint.sql` only links agent 20 to chat 1000:

```sql
-- The agent of VisitFixtures.sql's match 1 (customer 10, user 13)
INSERT INTO T_AGENT_TELEGRAM(tenant_id, agent_id, chat_id, linked_at) VALUES
    (1, 20, 1000, '2026-11-15 00:00:00');
```

- [ ] **Step 2: Write the failing endpoint test**

```kotlin
@Sql(
    value = [
        "/db/test/clean.sql",
        "/db/test/visit/VisitFixtures.sql",
        "/db/test/visit/CreateVisitEndpoint.sql",
        "/db/test/telegram/TelegramVisitRequestedEndpoint.sql",
    ],
)
class TelegramVisitRequestedEndpointTest : AuthorizationAwareEndpointIntegrationTest() {
    @MockitoBean
    private lateinit var client: TelegramClient

    @Autowired
    private lateinit var dao: VisitRepository

    private val zone = ZoneId.of("Africa/Douala")

    @Test
    fun `a request messages the linked agent instead of e-mailing it`() {
        userId = 13L // Ray Sponsible, the customer of match 1
        val response = post(type = VisitType.ON_SITE, note = "Je viens avec ma femme")
        assertEquals(HttpStatus.OK, response.statusCode)
        val id = response.body!!.visitId

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        val buttons = argumentCaptor<List<TelegramButton>>()
        verify(client).sendMessage(eq(1000L), text.capture(), buttons.capture())
        assertTrue(text.firstValue.contains("Ray Sponsible souhaite visiter un bien que vous lui avez proposé."))
        assertTrue(text.firstValue.contains("Bien: Nice apartment"))
        assertTrue(text.firstValue.contains("Type: Sur place"))
        assertTrue(text.firstValue.contains("WhatsApp: +237 6 00 00 00 01"))
        assertTrue(text.firstValue.contains("Note: Je viens avec ma femme"))
        assertEquals(
            listOf(TelegramButton("Voir la visite", url = "https://partner.ndopify.com/visits/$id")),
            buttons.firstValue,
        )

        assertFalse(smtp.waitForIncomingEmail(1000, 1), "no request e-mail for a linked agent")
    }

    @Test
    fun `a video request says it is a video visit`() {
        userId = 13L
        val response = post(type = VisitType.VIDEO)
        assertEquals(HttpStatus.OK, response.statusCode)

        awaitAsyncTasks()

        val text = argumentCaptor<String>()
        verify(client).sendMessage(eq(1000L), text.capture(), any())
        assertTrue(text.firstValue.contains("Type: Visite vidéo"))
    }

    private fun post(type: VisitType, note: String? = null) =
        rest.postForEntity(
            "/v1/visits",
            CreateVisitRequest(matchId = 1L, type = type, requestedAt = slot(1, 10, 0), note = note),
            CreateVisitResponse::class.java,
        )

    private fun slot(dayOffset: Long, hour: Int, minute: Int): Date =
        Date.from(LocalDate.now(zone).plusDays(dayOffset).atTime(hour, minute).atZone(zone).toInstant())
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitRequestedEndpointTest`
Expected: FAIL — the botlet or the notifier isn't wired yet.

- [ ] **Step 4: Fix the wiring until it passes**

Likely gaps: the notifier isn't a bean (missing `@Service`), or `VisitService` still takes the mailet. Change production code only for a real gap the test finds.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd modules/ndopify-server && mvn test -Dtest=TelegramVisitRequestedEndpointTest`
Expected: PASS (MySQL running).

- [ ] **Step 6: Commit**

```bash
git add modules/ndopify-server/src/test/resources/db/test/telegram/TelegramVisitRequestedEndpoint.sql \
        modules/ndopify-server/src/test/kotlin/com/wutsi/ndopify/platform/telegram/TelegramVisitRequestedEndpointTest.kt
git commit -m "test(telegram): cover the visit request notification" -m "Refs #148"
```

---

### Task 5: Documentation and full verification

**Files:**
- Modify: `modules/ndopify-server/CLAUDE.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the module's documentation for the next visit notification.

- [ ] **Step 1: Document the channel**

In `modules/ndopify-server/CLAUDE.md`:
- In the **visit** bullet, next to the e-mails, add the channel rule: `VisitRequestedNotifier` Telegram-messages a linked agent (`VisitRequestedBotlet`, via `AgentTelegramService.chatId`) and e-mails the others (`VisitRequestedMailet`), falling back to the e-mail on a failed send; the message carries the customer, the listing, the requested slot, the type and the note, and a `Voir la visite` button.
- In the **Asynchronous e-mails** list, rename `VisitService.sendRequestedEmail` to `sendRequestedNotification` wherever it appears, and note that the requested-visit entry now goes over Telegram or e-mail.

- [ ] **Step 2: Run the module's full verification**

Run: `cd modules/ndopify-server && mvn verify`
Expected: BUILD SUCCESS — ktlint, all tests and the JaCoCo gate (0.95 lines) pass. MySQL must be running.

- [ ] **Step 3: Commit**

```bash
git add modules/ndopify-server/CLAUDE.md
git commit -m "docs(visit): document the Telegram visit request notification" -m "Refs #148"
```
